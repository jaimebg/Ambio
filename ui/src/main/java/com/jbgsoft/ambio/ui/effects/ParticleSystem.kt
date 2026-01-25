package com.jbgsoft.ambio.ui.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random

private const val INTENSITY_BUILDUP_DURATION_MS = 8000f
private const val INTENSITY_FADEOUT_DURATION_MS = 3000f
private const val MAX_DELTA_TIME_MS = 100f
private const val MIN_SPAWN_INTENSITY = 0.3f
private const val UPDATE_INTERVAL_MS = 16L

@Stable
class ParticleSystemState(
    initialConfig: EffectConfig
) {
    val particles = mutableStateListOf<Particle>()
    var intensity by mutableFloatStateOf(0f)
    var config by mutableStateOf(initialConfig)
    var canvasSize by mutableStateOf(Size.Zero)
    private var particleIdCounter = 0L
    private var spawnAccumulator = 0f
    private var lastUpdateTime = 0L

    fun update(currentTimeMs: Long, isPlaying: Boolean) {
        val deltaTimeMs = if (lastUpdateTime == 0L) 16f else (currentTimeMs - lastUpdateTime).toFloat()
        lastUpdateTime = currentTimeMs

        val clampedDelta = min(deltaTimeMs, MAX_DELTA_TIME_MS)
        val deltaSeconds = clampedDelta / 1000f

        updateIntensity(clampedDelta, isPlaying)

        if (canvasSize.width > 0 && canvasSize.height > 0) {
            // Only spawn new particles when playing and intensity is building up
            if (isPlaying && intensity > 0.01f) {
                spawnParticles(deltaSeconds)
            }

            // Always update existing particles so they can fade out naturally
            if (particles.isNotEmpty()) {
                updateParticles(clampedDelta)
                removeDeadParticles()
            }
        }

        // Only reset spawn accumulator when fully stopped and all particles are gone
        if (!isPlaying && intensity <= 0.01f && particles.isEmpty()) {
            spawnAccumulator = 0f
        }
    }

    private fun updateIntensity(deltaTimeMs: Float, isPlaying: Boolean) {
        val target = if (isPlaying) 1f else 0f

        intensity = if (intensity < target) {
            min(target, intensity + (deltaTimeMs / INTENSITY_BUILDUP_DURATION_MS))
        } else if (intensity > target) {
            (intensity - (deltaTimeMs / INTENSITY_FADEOUT_DURATION_MS)).coerceAtLeast(0f)
        } else {
            intensity
        }
    }

    private fun spawnParticles(deltaSeconds: Float) {
        val spawnIntensity = (intensity + MIN_SPAWN_INTENSITY).coerceAtMost(1f)
        val spawnRate = lerp(config.spawnRateRange.start, config.spawnRateRange.endInclusive, spawnIntensity)
        spawnAccumulator += spawnRate * deltaSeconds

        while (spawnAccumulator >= 1f && particles.size < config.maxParticles) {
            spawnAccumulator -= 1f
            spawnParticle()
        }
    }

    private fun spawnParticle() {
        val startPosition = when (config.type) {
            ParticleType.DROPLET -> Offset(
                x = Random.nextFloat() * canvasSize.width,
                y = -20f
            )
            ParticleType.EMBER -> Offset(
                x = canvasSize.width * (0.3f + Random.nextFloat() * 0.4f),
                y = canvasSize.height + 20f
            )
            ParticleType.LEAF -> Offset(
                x = -20f,
                y = Random.nextFloat() * canvasSize.height * 0.5f
            )
            ParticleType.BUBBLE -> Offset(
                x = Random.nextFloat() * canvasSize.width,
                y = canvasSize.height + 20f
            )
            ParticleType.WISP -> Offset(
                x = -50f,
                y = Random.nextFloat() * canvasSize.height
            )
        }

        val alphaIntensity = (intensity + MIN_SPAWN_INTENSITY).coerceAtMost(1f)
        val particle = Particle(
            id = particleIdCounter++,
            position = startPosition,
            velocity = Offset(
                x = randomInRange(config.velocityXRange),
                y = randomInRange(config.velocityYRange)
            ),
            size = randomInRange(config.sizeRange),
            baseAlpha = randomInRange(config.alphaRange) * alphaIntensity,
            rotation = Random.nextFloat() * 360f,
            lifetime = config.lifetimeRange.random(),
            age = 0L,
            color = config.colors.random(),
            type = config.type
        )

        particles.add(particle)
    }

    private fun updateParticles(deltaTimeMs: Float) {
        val deltaSeconds = deltaTimeMs / 1000f

        particles.forEach { particle ->
            particle.position = Offset(
                x = particle.position.x + particle.velocity.x * deltaSeconds,
                y = particle.position.y + particle.velocity.y * deltaSeconds
            )

            particle.rotation += randomInRange(config.rotationSpeedRange) * deltaSeconds * 100f
            particle.age += deltaTimeMs.toLong()

            if (config.type == ParticleType.EMBER) {
                particle.velocity = Offset(
                    x = particle.velocity.x + (Random.nextFloat() - 0.5f) * 10f * deltaSeconds,
                    y = particle.velocity.y
                )
            }

            if (config.type == ParticleType.LEAF) {
                particle.velocity = Offset(
                    x = particle.velocity.x + kotlin.math.sin(particle.age / 500f) * 20f * deltaSeconds,
                    y = particle.velocity.y
                )
            }
        }
    }

    private fun removeDeadParticles() {
        particles.removeAll { particle ->
            !particle.isAlive || isOffScreen(particle)
        }
    }

    private fun isOffScreen(particle: Particle): Boolean {
        val margin = 50f
        return particle.position.x < -margin ||
            particle.position.x > canvasSize.width + margin ||
            particle.position.y < -margin ||
            particle.position.y > canvasSize.height + margin
    }

    private fun randomInRange(range: ClosedFloatingPointRange<Float>): Float {
        return range.start + Random.nextFloat() * (range.endInclusive - range.start)
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}

@Composable
fun rememberParticleSystemState(
    config: EffectConfig
): ParticleSystemState {
    val state = remember { ParticleSystemState(config) }
    LaunchedEffect(config) {
        if (state.config != config) {
            state.config = config
            state.particles.clear()
        }
    }
    return state
}

@Composable
fun useParticleAnimation(
    state: ParticleSystemState,
    isPlaying: Boolean
): Float {
    // Use infinite transition to drive continuous updates
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleFrame"
    )

    // Update particles on every frame using LaunchedEffect
    LaunchedEffect(isPlaying) {
        while (true) {
            val currentTime = System.currentTimeMillis()
            state.update(currentTime, isPlaying)
            delay(UPDATE_INTERVAL_MS)
        }
    }

    // Return animation progress to force recomposition
    return animationProgress
}
