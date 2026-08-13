package com.jbgsoft.ambio.ui.effects

import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Matches AudioServiceConnection.FADE_IN_DURATION_MS. */
const val INTENSITY_BUILDUP_DURATION_MS = 8000f

/** Matches AudioServiceConnection.FADE_OUT_DURATION_MS. */
const val INTENSITY_FADEOUT_DURATION_MS = 3000f

/** A stall must not teleport the field. */
const val MAX_DELTA_MS = 100f

private const val SPAWN_GATE = 0.01f
private const val SWAY_PERIOD_MS = 900f
private const val EMBER_DRIFT_DP = 26f
private const val EMBER_DRIFT_DECAY = 0.9f
private const val SPAWN_MARGIN_DP = 24f
private const val CULL_MARGIN_DP = 32f
internal const val COLORS_PER_TYPE = 4

/**
 * The whole simulation, free of Compose and Android so it can be exercised on
 * the JVM — the same reason MixPlayer was split out of AudioService.
 *
 * [random] is injected so a seeded field replays identically; without it none of
 * this is assertable. Nothing here is snapshot state: the overlay invalidates
 * the draw phase itself once per frame.
 */
class ParticleField(
    private val random: Random = Random.Default,
    private val budget: Int = PARTICLE_BUDGET,
    private val floorPerSource: Int = FLOOR_PER_SOURCE
) {
    private val _particles = ArrayList<Particle>(PARTICLE_BUDGET * 2)
    val particles: List<Particle> get() = _particles

    var intensity: Float = 0f
        private set

    /**
     * Keyed by type rather than by position in the source list, so adding or
     * removing a sound never shifts another sound's accumulator.
     */
    private val spawnAccumulator = FloatArray(ParticleType.entries.size)

    /**
     * Quotas change only when [sources] itself changes, not every frame, but
     * [spawn] runs at up to 60fps. Recomputing [allocate] unconditionally would
     * mean six-odd collection allocations a frame for numbers that are almost
     * always identical to last frame's; caching keyed on structural equality
     * (`FieldSource` is a data class, so list `!=` is a real content
     * comparison, not a reference check) makes that recomputation happen only
     * on an actual mix change.
     */
    private var cachedSources: List<FieldSource> = emptyList()
    private var cachedQuotas: IntArray = IntArray(0)

    fun update(
        deltaMs: Float,
        isPlaying: Boolean,
        sources: List<FieldSource>,
        widthPx: Float,
        heightPx: Float,
        density: Float
    ) {
        val clamped = min(deltaMs, MAX_DELTA_MS).coerceAtLeast(0f)
        updateIntensity(clamped, isPlaying)

        // An inactive type must not carry an accumulator across an absence, or
        // re-adding it emits an immediate burst.
        val activeTypes = sources.map { it.type }.toSet()
        ParticleType.entries.forEach { type ->
            if (type !in activeTypes) spawnAccumulator[type.ordinal] = 0f
        }

        if (widthPx <= 0f || heightPx <= 0f) return

        if (isPlaying && intensity > SPAWN_GATE) {
            spawn(clamped, sources, widthPx, heightPx, density)
        }
        // Existing particles keep integrating while paused so they drain rather
        // than vanish, and particles of a removed source die naturally.
        integrate(clamped, density)
        cull(widthPx, heightPx, density)
    }

    /** Restores a ramp saved across navigation, so it does not restart from zero. */
    fun restoreIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
    }

    private fun updateIntensity(deltaMs: Float, isPlaying: Boolean) {
        val target = if (isPlaying) 1f else 0f
        intensity = when {
            intensity < target -> min(target, intensity + deltaMs / INTENSITY_BUILDUP_DURATION_MS)
            intensity > target -> (intensity - deltaMs / INTENSITY_FADEOUT_DURATION_MS).coerceAtLeast(0f)
            else -> intensity
        }
    }

    private fun spawn(
        deltaMs: Float,
        sources: List<FieldSource>,
        widthPx: Float,
        heightPx: Float,
        density: Float
    ) {
        val quotas = quotasFor(sources)
        val deltaSeconds = deltaMs / 1000f

        sources.forEachIndexed { index, source ->
            val spec = specFor(source.type)
            val quota = quotas[index]
            if (quota <= 0) return@forEachIndexed

            // Derived from the quota rather than tuned separately, which is what
            // let the old spawn rate and maxParticles contradict each other.
            val meanLifeSeconds =
                (spec.lifetimeMsRange.first + spec.lifetimeMsRange.last) / 2000f
            val rate = quota / meanLifeSeconds

            spawnAccumulator[source.type.ordinal] += rate * deltaSeconds
            while (spawnAccumulator[source.type.ordinal] >= 1f) {
                spawnAccumulator[source.type.ordinal] -= 1f
                if (countOf(source.type) >= quota) break
                _particles.add(newParticle(spec, widthPx, heightPx, density))
            }
        }
    }

    private fun countOf(type: ParticleType): Int = _particles.count { it.type == type }

    private fun quotasFor(sources: List<FieldSource>): IntArray {
        if (sources != cachedSources) {
            // A defensive copy: List.equals short-circuits on identity, so
            // holding the caller's own instance would let a caller that
            // mutates a MutableList in place keep these quotas stale forever.
            cachedSources = sources.toList()
            cachedQuotas = allocate(sources, budget, floorPerSource)
        }
        return cachedQuotas
    }

    private fun newParticle(
        spec: ParticleSpec,
        widthPx: Float,
        heightPx: Float,
        density: Float
    ): Particle {
        val margin = SPAWN_MARGIN_DP * density
        // Spawn regions are deliberately wide: leaves used to enter only from the
        // left edge in the top half, and embers only from the middle 40%.
        val x: Float
        val y: Float
        when (spec.type) {
            ParticleType.DROPLET -> {
                x = random.nextFloat() * widthPx
                y = -margin
            }
            ParticleType.EMBER -> {
                x = widthPx * (0.15f + random.nextFloat() * 0.7f)
                y = heightPx + margin
            }
            ParticleType.LEAF -> {
                x = -margin + random.nextFloat() * (widthPx * 0.9f + margin)
                y = -margin + random.nextFloat() * (heightPx * 0.35f + margin)
            }
            ParticleType.BUBBLE -> {
                x = random.nextFloat() * widthPx
                y = heightPx + margin
            }
            ParticleType.WISP -> {
                x = random.nextFloat() * widthPx
                y = random.nextFloat() * heightPx
            }
        }

        return Particle(
            x = x,
            y = y,
            baseX = x,
            vx = inRange(spec.velocityXDpRange) * density,
            vy = inRange(spec.velocityYDpRange) * density,
            radiusPx = inRange(spec.sizeDpRange) * density,
            baseAlpha = inRange(spec.alphaRange),
            lifetimeMs = spec.lifetimeMsRange.first +
                (random.nextFloat() * (spec.lifetimeMsRange.last - spec.lifetimeMsRange.first)).toLong(),
            ageMs = 0L,
            seed = random.nextFloat() * 100f,
            colorIndex = random.nextInt(COLORS_PER_TYPE),
            type = spec.type
        )
    }

    private fun integrate(deltaMs: Float, density: Float) {
        val deltaSeconds = deltaMs / 1000f

        for (index in _particles.indices) {
            val particle = _particles[index]
            val spec = specFor(particle.type)
            particle.ageMs += deltaMs.toLong()

            if (particle.type == ParticleType.EMBER) {
                // Mean-reverting, so drift cannot compound with age the way it did
                // when the random walk was added straight into velocity.
                val push = (random.nextFloat() - 0.5f) * EMBER_DRIFT_DP * density
                particle.vx += (push - particle.vx * EMBER_DRIFT_DECAY) * deltaSeconds
            }

            particle.baseX += particle.vx * deltaSeconds
            particle.y += particle.vy * deltaSeconds

            // Sway is a positional offset. Integrating it into velocity — which is
            // what the old code did — turns sin into a -cos drift, so leaves curved
            // away instead of oscillating.
            particle.x = if (spec.swayAmplitudeDp > 0f) {
                particle.baseX +
                    sin(particle.ageMs / SWAY_PERIOD_MS + particle.seed) *
                    spec.swayAmplitudeDp * density
            } else {
                particle.baseX
            }
        }
    }

    private fun cull(widthPx: Float, heightPx: Float, density: Float) {
        _particles.removeAll { particle ->
            val margin = particle.radiusPx + CULL_MARGIN_DP * density
            !particle.isAlive ||
                particle.x < -margin || particle.x > widthPx + margin ||
                particle.y < -margin || particle.y > heightPx + margin
        }
    }

    private fun inRange(range: ClosedFloatingPointRange<Float>): Float =
        range.start + random.nextFloat() * (range.endInclusive - range.start)
}
