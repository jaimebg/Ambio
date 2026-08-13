package com.jbgsoft.ambio.ui.effects

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class ParticleFieldTest {

    private val width = 1080f
    private val height = 2400f
    private val density = 2.75f

    private fun field(seed: Int = 1) = ParticleField(random = Random(seed))

    private fun ParticleField.run(
        forMs: Int,
        isPlaying: Boolean = true,
        sources: List<FieldSource> = listOf(FieldSource(ParticleType.DROPLET, 1f)),
        stepMs: Float = 16f
    ) {
        repeat((forMs / stepMs).toInt()) {
            update(stepMs, isPlaying, sources, width, height, density)
        }
    }

    @Test
    fun `a seeded field replays identically`() {
        // DROPLET alone never exercises the sway offset (swayAmplitudeDp = 0)
        // or the ember drift push, and x/y/ageMs alone never surface baseAlpha,
        // seed or colorIndex at all -- those three are read only by the
        // renderer, not by update(). LEAF and EMBER are included so every
        // random draw a particle makes is on a path this test can observe.
        val sources = listOf(
            FieldSource(ParticleType.DROPLET, 1f),
            FieldSource(ParticleType.LEAF, 1f),
            FieldSource(ParticleType.EMBER, 1f)
        )
        val a = field(seed = 42).apply { run(forMs = 4000, sources = sources) }
        val b = field(seed = 42).apply { run(forMs = 4000, sources = sources) }

        assertThat(a.particles.size).isEqualTo(b.particles.size)
        a.particles.zip(b.particles).forEach { (pa, pb) ->
            assertThat(pa.x).isEqualTo(pb.x)
            assertThat(pa.y).isEqualTo(pb.y)
            assertThat(pa.ageMs).isEqualTo(pb.ageMs)
            assertThat(pa.radiusPx).isEqualTo(pb.radiusPx)
            assertThat(pa.vx).isEqualTo(pb.vx)
            assertThat(pa.vy).isEqualTo(pb.vy)
            assertThat(pa.baseAlpha).isEqualTo(pb.baseAlpha)
            assertThat(pa.seed).isEqualTo(pb.seed)
            assertThat(pa.colorIndex).isEqualTo(pb.colorIndex)
        }
    }

    @Test
    fun `intensity reaches full after the eight second ramp`() {
        val f = field()
        f.run(forMs = 8000)

        assertThat(f.intensity).isWithin(0.02f).of(1f)
    }

    @Test
    fun `intensity is not already saturated halfway through the ramp`() {
        // The old MIN_SPAWN_INTENSITY offset made the ramp finish at 5.6s.
        val f = field()
        f.run(forMs = 4000)

        assertThat(f.intensity).isWithin(0.05f).of(0.5f)
    }

    @Test
    fun `intensity falls to zero three seconds after stopping`() {
        val f = field()
        f.run(forMs = 8000)

        // Pinned from both sides, like the buildup's halfway check: a fade that
        // is ten times too fast (already at/near zero here) or ten times too
        // slow (barely moved) both land outside this window.
        f.run(forMs = 1500, isPlaying = false)
        assertThat(f.intensity).isWithin(0.05f).of(0.5f)

        // 1700ms more (3200ms of fade total, not 3000): the harness steps in
        // 16ms and (3000/16).toInt() is 187 steps = 2992ms, which stops 8ms
        // short of the fade and leaves a sliver.
        f.run(forMs = 1700, isPlaying = false)
        assertThat(f.intensity).isEqualTo(0f)
    }

    @Test
    fun `the population converges to the allocated quota`() {
        val f = field()
        f.run(forMs = 12000)

        assertThat(f.particles.size).isAtMost(PARTICLE_BUDGET)
        assertThat(f.particles.size).isAtLeast(PARTICLE_BUDGET / 2)
    }

    @Test
    fun `the population never exceeds the budget with three sources`() {
        val f = field()
        f.run(
            forMs = 12000,
            sources = listOf(
                FieldSource(ParticleType.DROPLET, 1.0f),
                FieldSource(ParticleType.LEAF, 0.7f),
                FieldSource(ParticleType.BUBBLE, 0.4f)
            )
        )

        assertThat(f.particles.size).isAtMost(PARTICLE_BUDGET)
    }

    @Test
    fun `a wisp-only field is held at its ceiling, not at the budget`() {
        val f = field()
        f.run(forMs = 14000, sources = listOf(FieldSource(ParticleType.WISP, 1f)))

        assertThat(f.particles.size).isAtMost(specFor(ParticleType.WISP).ceiling)
    }

    @Test
    fun `no particle outlives its lifetime`() {
        val f = field()
        f.run(forMs = 10000)

        f.particles.forEach { assertThat(it.isAlive).isTrue() }
    }

    @Test
    fun `nothing spawns while paused`() {
        val f = field()
        f.run(forMs = 5000, isPlaying = false)

        assertThat(f.particles).isEmpty()
    }

    @Test
    fun `existing particles drain away after pausing instead of vanishing`() {
        val f = field()
        f.run(forMs = 6000)
        val whilePlaying = f.particles.size
        assertThat(whilePlaying).isGreaterThan(0)

        f.run(forMs = 500, isPlaying = false)
        // A lower bound, not just an upper one: "nothing spawns while paused"
        // already forbids growth, so an isAtMost check here can't fail even if
        // every particle is cleared outright the moment the mix pauses. Roughly
        // 26 of ~36 droplets survive a 500ms pause, so this has real margin.
        assertThat(f.particles.size).isAtLeast(whilePlaying / 2)

        f.run(forMs = 12000, isPlaying = false)
        assertThat(f.particles).isEmpty()
    }

    @Test
    fun `removing a source stops its spawning but lets its particles die naturally`() {
        val f = field()
        val both = listOf(
            FieldSource(ParticleType.DROPLET, 1f),
            FieldSource(ParticleType.WISP, 1f)
        )
        f.run(forMs = 9000, sources = both)
        assertThat(f.particles.any { it.type == ParticleType.WISP }).isTrue()

        f.run(forMs = 200, sources = listOf(FieldSource(ParticleType.DROPLET, 1f)))
        assertThat(f.particles.any { it.type == ParticleType.WISP }).isTrue()

        f.run(forMs = 9000, sources = listOf(FieldSource(ParticleType.DROPLET, 1f)))
        assertThat(f.particles.none { it.type == ParticleType.WISP }).isTrue()
    }

    @Test
    fun `re-adding a source does not spawn a burst from a stale accumulator`() {
        // The spawn loop always decrements the accumulator by 1.0 before
        // checking the quota, even on the iteration that discovers the type is
        // capped and breaks without spawning -- so a frame that reaches quota
        // mid-step can leave a genuine fractional remainder behind rather than
        // draining to zero. With a budget of 17 and DROPLET alone (rate ≈
        // 17/1.8 ≈ 9.44/s, so ≈0.944 accumulated per 100ms step), reaching that
        // quota lands mid-step for this seed and leaves the accumulator at
        // ≈0.722 rather than 0 -- confirmed by instrumenting the accumulator
        // directly against this exact seed/budget/step-count combination
        // before writing this assertion. (36/100ms's own quota is a poor fit
        // for this: population rarely sits exactly at that cap long enough for
        // a death-free frame, so the leftover is almost always 0 -- verified
        // empirically over 1000 simulated frames before landing on this
        // smaller, more reliable budget instead.)
        val f = ParticleField(random = Random(4), budget = 17)
        val rain = listOf(FieldSource(ParticleType.DROPLET, 1f))

        repeat(23) { f.update(100f, true, rain, width, height, density) }
        assertThat(f.particles.size).isEqualTo(17)

        // Remove the source and let every particle die out naturally (droplet's
        // longest possible lifetime is 2200ms; 25 steps of 100ms is 2500ms).
        repeat(25) { f.update(100f, true, emptyList(), width, height, density) }
        assertThat(f.particles).isEmpty()

        // Re-add with a step sized so a real backlog crosses the spawn
        // threshold but a freshly-reset accumulator does not: at this budget's
        // rate, 40ms alone contributes ≈0.378, comfortably under 1 by itself,
        // but ≈0.722 (the stale backlog) + 0.378 clears 1.
        f.update(40f, true, rain, width, height, density)
        assertThat(f.particles).isEmpty()
    }

    @Test
    fun `particles are culled once they leave the canvas`() {
        val f = field()
        f.run(forMs = 20000)

        f.particles.forEach {
            assertThat(it.y).isLessThan(height + 400f)
            assertThat(it.y).isGreaterThan(-400f)
        }
    }

    @Test
    fun `leaf sway is an offset, so it never accumulates into the path`() {
        val f = field()
        f.run(forMs = 9000, sources = listOf(FieldSource(ParticleType.LEAF, 1f)))
        val sway = specFor(ParticleType.LEAF).swayAmplitudeDp * density

        f.particles.filter { it.type == ParticleType.LEAF }.forEach {
            assertThat(kotlin.math.abs(it.x - it.baseX)).isAtMost(sway + 0.01f)
        }
    }

    @Test
    fun `leaf sway oscillates around the path instead of drifting off it`() {
        val f = field()
        val leaves = listOf(FieldSource(ParticleType.LEAF, 1f))
        f.run(forMs = 2000, sources = leaves)

        // The youngest leaf has the most life left, so it survives the whole sample.
        val leaf = f.particles.filter { it.type == ParticleType.LEAF }.minByOrNull { it.ageMs }
        assertThat(leaf).isNotNull()

        val offsets = mutableListOf<Float>()
        repeat(250) {
            f.update(16f, true, leaves, width, height, density)
            if (f.particles.any { it === leaf }) offsets += leaf!!.x - leaf.baseX
        }

        // The old code integrated sin into velocity, so the offset only ever grew in one
        // direction. A positional offset must cross zero.
        assertThat(offsets.any { it > 0f }).isTrue()
        assertThat(offsets.any { it < 0f }).isTrue()
    }

    @Test
    fun `ember drift stays bounded instead of compounding with age`() {
        val f = field()
        f.run(forMs = 14000, sources = listOf(FieldSource(ParticleType.EMBER, 1f)))
        val cap = specFor(ParticleType.EMBER).velocityXDpRange.endInclusive * density * 3f

        f.particles.filter { it.type == ParticleType.EMBER }.forEach {
            assertThat(kotlin.math.abs(it.vx)).isLessThan(cap)
        }
    }

    @Test
    fun `a huge delta is clamped so a stall cannot teleport the field`() {
        val f = field()
        val rain = listOf(FieldSource(ParticleType.DROPLET, 1f))
        f.update(16f, true, rain, width, height, density)
        val before = f.intensity

        f.update(5000f, true, rain, width, height, density)

        // MAX_DELTA_MS is 100, so a five-second stall advances the ramp by 100ms.
        assertThat(f.intensity - before)
            .isWithin(0.0001f)
            .of(MAX_DELTA_MS / INTENSITY_BUILDUP_DURATION_MS)
    }

    @Test
    fun `sizes and velocities scale with screen density`() {
        val rain = listOf(FieldSource(ParticleType.DROPLET, 1f))
        val lowDensity = ParticleField(random = Random(7))
        val highDensity = ParticleField(random = Random(7))

        // 320ms: enough to clear the 0.01 spawn gate (80ms) and fill the
        // accumulator, and short enough that nothing is culled at either density.
        repeat(20) { lowDensity.update(16f, true, rain, width, height, density = 1f) }
        repeat(20) { highDensity.update(16f, true, rain, width, height, density = 3f) }

        // Same seed and the same number of random draws, so the two fields differ
        // only by the dp→px conversion.
        assertThat(lowDensity.particles).isNotEmpty()
        assertThat(highDensity.particles.size).isEqualTo(lowDensity.particles.size)

        val low = lowDensity.particles.first()
        val high = highDensity.particles.first()
        assertThat(high.radiusPx).isWithin(0.01f).of(low.radiusPx * 3f)
        assertThat(high.vy).isWithin(0.01f).of(low.vy * 3f)
    }

    @Test
    fun `the spec holds dp, so nothing in it is already a pixel value`() {
        // A radius of 30-60 was the old px range for wisps. In dp it must be smaller.
        assertThat(specFor(ParticleType.WISP).sizeDpRange.endInclusive).isLessThan(30f)
    }

    @Test
    fun `a zero-sized canvas spawns nothing and does not crash`() {
        val f = field()
        repeat(100) {
            f.update(16f, true, listOf(FieldSource(ParticleType.DROPLET, 1f)), 0f, 0f, density)
        }

        assertThat(f.particles).isEmpty()
    }
}
