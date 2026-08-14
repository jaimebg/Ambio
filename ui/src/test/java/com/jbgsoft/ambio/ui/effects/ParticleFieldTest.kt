package com.jbgsoft.ambio.ui.effects

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
    fun `changing a source's weight without changing arity still changes the quotas`() {
        // Quotas are cached and only recomputed when the source list changes,
        // which is fine as long as "changes" means real content equality and
        // not something weaker like a size comparison -- moving a volume
        // slider or swapping one sound for another at the same source count is
        // the common runtime case, and a size-only invalidation check would
        // silently keep serving the old split forever.
        val f = ParticleField(random = Random(1))
        val dropletHeavy = listOf(
            FieldSource(ParticleType.DROPLET, 0.9f),
            FieldSource(ParticleType.LEAF, 0.1f)
        )
        repeat(200) { f.update(100f, true, dropletHeavy, width, height, density) }
        assertThat(f.particles.count { it.type == ParticleType.DROPLET })
            .isGreaterThan(f.particles.count { it.type == ParticleType.LEAF })

        // Same two types, same list size -- only the weights swap. Long enough
        // for droplets (max lifetime 2200ms) to fully turn over under the new,
        // smaller quota and for leaves (max lifetime 8000ms) to build up under
        // the new, larger one.
        val leafHeavy = listOf(
            FieldSource(ParticleType.DROPLET, 0.1f),
            FieldSource(ParticleType.LEAF, 0.9f)
        )
        repeat(200) { f.update(100f, true, leafHeavy, width, height, density) }
        assertThat(f.particles.count { it.type == ParticleType.LEAF })
            .isGreaterThan(f.particles.count { it.type == ParticleType.DROPLET })
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
        // Both bounds, because they catch opposite mutations. isAtMost catches
        // a spawn gate that ignores isPlaying and keeps growing the population
        // while paused; isAtLeast catches a pause handler that clears the
        // population outright instead of letting it drain. "nothing spawns
        // while paused" cannot substitute for the isAtMost half here: that test
        // never plays, so intensity never leaves 0 and the spawn gate blocks on
        // intensity regardless of isPlaying -- it can't catch a dropped
        // isPlaying check. 24 of ~31 droplets survive a 500ms pause, so
        // isAtLeast(whilePlaying / 2) has real margin.
        assertThat(f.particles.size).isAtMost(whilePlaying)
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
        // A budget of 17 (not the default 36) makes the leftover a guaranteed
        // arithmetic fact, not a lucky draw. At this budget, DROPLET's rate is
        // 17/1.8 ≈ 9.444/s, so each 100ms step contributes ≈0.9444 -- under 1.
        // An increment under 1 can cross an integer threshold at most once per
        // step, so after N steps the spawn loop has decremented the
        // accumulator exactly floor(N × 0.9444) times, full stop -- that count
        // does not depend on whether any of those decrements happened to be
        // "wasted" by the quota check (a wasted decrement still consumes 1.0,
        // same as a productive one), so it does not depend on population,
        // deaths, or the RNG seed. After 23 steps: floor(23 × 0.9444) =
        // floor(21.722) = 21, leaving the accumulator at 21.722 − 21 ≈ 0.722,
        // unconditionally. The quota's cap check never even has to fire for
        // this leftover to exist; it is just the ordinary "still mid-cycle"
        // remainder that any non-integer rate leaves behind, which is exactly
        // why the reset has to clear it regardless of how it arose.
        //
        // The default budget of 36 does not work for this because its rate
        // (36/1.8 = 20/s) makes the per-step increment exactly 2.0 -- a whole
        // number. Whenever the population has room for both units (true most
        // of the time, since mean population settles well under 36), the loop
        // either spawns twice or spawns once and wastes the second decrement
        // on a check that still consumes it, and either way the accumulator
        // still lands on exactly 0.0. A nonzero remainder is possible there
        // too, but only in the narrow case where the population is *already*
        // at the full cap when a step begins, so the entire 2.0 gets only one
        // decrement before the loop breaks -- confirmed to happen (accumulator
        // = 1.0, population already 36 going in) but only 3 times in 1000
        // simulated 100ms steps for this seed, which is too rare and too
        // seed-dependent to build a deterministic test on. The smaller budget
        // sidesteps that rarity entirely by making the leftover a property of
        // the arithmetic instead of of population timing.
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
    fun `dropping one source out of several still resets its own accumulator`() {
        // The per-type reset is `if (type !in activeTypes) spawnAccumulator[...] = 0f`
        // for every type, evaluated regardless of whether the source list as a
        // whole is empty. A mutation that instead reset only `if
        // (sources.isEmpty())` would pass the existing stale-accumulator test,
        // because that test always drops to an empty list. This one drops LEAF
        // while DROPLET stays active, so `sources` is never empty and the
        // mutation gets no chance to fire.
        //
        // Budget 17 (not the default 36) with equal-weight DROPLET/LEAF gives
        // quotas of 9/8 (DROPLET wins the ordinal tie-break on the leftover
        // unit -- see `allocate`'s own tests). LEAF's rate is then
        // 8 / ((5000+8000)/2000) = 8/6.5 = 1.230769.../s, so each 100ms step
        // contributes 0.1230769... -- not a round number, so accumulating it
        // never lands on an exact integer.
        //
        // Running 8 such steps sums to 8 * 0.1230769 = 0.9846153..., which is
        // still under 1 -- deliberately stopped one step short of a spawn, so
        // zero LEAF particles exist yet and the accumulator alone carries the
        // near-1 leftover.
        val f = ParticleField(random = Random(4), budget = 17)
        val rainAndLeaves = listOf(
            FieldSource(ParticleType.DROPLET, 1f),
            FieldSource(ParticleType.LEAF, 1f)
        )
        repeat(8) { f.update(100f, true, rainAndLeaves, width, height, density) }
        assertThat(f.particles.none { it.type == ParticleType.LEAF }).isTrue()

        // Drop LEAF but keep DROPLET active -- sources is [DROPLET], never
        // empty. Correct code resets LEAF's accumulator to 0 on the very next
        // call and it stays there; the isEmpty() mutation never resets it, so
        // the 0.9846153 leftover survives untouched (LEAF isn't a current
        // source, so nothing else touches its accumulator either way).
        val rainOnly = listOf(FieldSource(ParticleType.DROPLET, 1f))
        repeat(20) { f.update(100f, true, rainOnly, width, height, density) }

        // Re-add LEAF and advance by exactly one 100ms step (rate unchanged,
        // since the quotas for [DROPLET, LEAF] at this budget are the same
        // 9/8 as before): that alone contributes 0.1230769, which cannot
        // cross 1 from a freshly-reset 0 -- but comfortably crosses 1 on top
        // of the stale 0.9846153 leftover (sum 1.1076923). A leaf particle
        // appearing on this single step is therefore proof the accumulator
        // was never reset.
        f.update(100f, true, rainAndLeaves, width, height, density)
        assertThat(f.particles.none { it.type == ParticleType.LEAF }).isTrue()
    }

    @Test
    fun `spawn positions cover the intended region instead of a narrow band`() {
        // Read directly off ParticleField.newParticle so these expected
        // ranges track the implementation rather than a guess (margin =
        // SPAWN_MARGIN_DP(24) * density = 66px at this test's density):
        //   DROPLET x in [0, width)         y == -margin           (top edge, full width)
        //   EMBER   x in [0.15w, 0.85w)     y == height + margin    (bottom edge, middle 70%)
        //   LEAF    x in [-margin, 0.9w)    y in [-margin, 0.35h)   (top band, near-full width)
        //   BUBBLE  x in [0, width)         y == height + margin    (bottom edge, full width)
        //   WISP    x in [0, width)         y in [0, height)        (full canvas)
        //   MOTE    x in [0, width)         y in [0, height)        (full canvas)
        //
        // The two mutations this must kill both regress LEAF and EMBER's x:
        // leaves pinned to a constant x = -margin (span collapses to ~0, and
        // the max never approaches 0.9w), and embers narrowed back to the
        // middle 40% (x in [0.3w, 0.7w), which never approaches the 0.15w/
        // 0.85w bounds below).
        val margin = 24f * density
        data class Expected(
            val xRange: ClosedFloatingPointRange<Float>,
            val yRange: ClosedFloatingPointRange<Float>
        )
        val expected = mapOf(
            ParticleType.DROPLET to Expected(0f..width, -margin..-margin),
            ParticleType.EMBER to Expected(width * 0.15f..width * 0.85f, (height + margin)..(height + margin)),
            ParticleType.LEAF to Expected(-margin..(width * 0.9f), -margin..(height * 0.35f)),
            ParticleType.BUBBLE to Expected(0f..width, (height + margin)..(height + margin)),
            ParticleType.WISP to Expected(0f..width, 0f..height),
            ParticleType.MOTE to Expected(0f..width, 0f..height)
        )

        val stepMs = 16f
        ParticleType.entries.forEach { type ->
            val f = ParticleField(random = Random(99))
            val source = listOf(FieldSource(type, 1f))
            val xs = mutableListOf<Float>()
            val ys = mutableListOf<Float>()

            // Collect at least ~200 spawn positions. A particle whose ageMs
            // equals exactly one step's worth was born on THIS call: spawn()
            // adds it at age 0 and the same call's integrate() ages every
            // particle by stepMs immediately after, so this is an unambiguous
            // "just spawned" filter with no risk of matching an older
            // particle (their ages are larger multiples of stepMs). One step
            // of motion at these velocities is negligible next to the
            // pixel-scale thresholds below, so this is effectively the raw
            // spawn position. Capped at 20,000 steps (320s simulated) as a
            // safety valve -- WISP's low ceiling (16) and ~5.5s mean
            // lifetime make it the slowest to turn over roughly 200 births.
            var steps = 0
            while (xs.size < 200 && steps < 20_000) {
                f.update(stepMs, true, source, width, height, density)
                f.particles.asSequence()
                    .filter { it.type == type && it.ageMs == stepMs.toLong() }
                    .forEach { xs += it.x; ys += it.y }
                steps++
            }
            assertWithMessage("${type.name} sample count").that(xs.size).isAtLeast(200)

            val (xRange, yRange) = expected.getValue(type)
            // Tolerance is 5% of the theoretical span, floored at 40px so a
            // (near-)constant coordinate -- DROPLET/EMBER/BUBBLE's y -- still
            // gets slack for the one step of motion described above, instead
            // of demanding an exact float match against a zero-width span.
            val xTol = maxOf((xRange.endInclusive - xRange.start) * 0.05f, 40f)
            val yTol = maxOf((yRange.endInclusive - yRange.start) * 0.05f, 40f)

            assertWithMessage("${type.name} x min").that(xs.min()).isLessThan(xRange.start + xTol)
            assertWithMessage("${type.name} x max").that(xs.max()).isGreaterThan(xRange.endInclusive - xTol)
            assertWithMessage("${type.name} y min").that(ys.min()).isLessThan(yRange.start + yTol)
            assertWithMessage("${type.name} y max").that(ys.max()).isGreaterThan(yRange.endInclusive - yTol)
        }
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
    fun `ember drift decays monotonically back toward zero after an extreme kick`() {
        // The old bounded-cap version of this test used
        // velocityXDpRange.endInclusive * density * 3f = 12 * 2.75 * 3 = 99 px/s
        // as its ceiling, but the random walk this simulation actually
        // produces never gets close: sigma is roughly 5.8 px/s over a 5s
        // lifetime on top of a spawn |vx| <= 33, so 99 was unreachable
        // whether or not mean reversion existed, and removing the decay term
        // entirely (or zeroing EMBER_DRIFT_DECAY) still passed. What "mean
        // reversion" actually promises is that a large |vx| decays toward
        // zero -- so test that property directly, by seeding vx far outside
        // it and watching it shrink.
        //
        // Particle.vx is a public var, reachable from the field's own
        // `particles` list without widening any visibility.
        val f = field()
        val embers = listOf(FieldSource(ParticleType.EMBER, 1f))
        f.run(forMs = 500, sources = embers)
        val ember = f.particles.first { it.type == ParticleType.EMBER }

        // Far outside the spec range (velocityXDpRange.endInclusive * density
        // ~= 33 px/s), so any real decay is unmistakable. Age reset to 0 so
        // the particle cannot expire mid-test: EMBER's minimum lifetime is
        // 3000ms and this test runs for 1600ms.
        ember.vx = 1000f
        ember.ageMs = 0L

        // A canvas far larger than any displacement this vx could cause
        // keeps cull() from ever removing the particle for going off-screen,
        // isolating the assertion to the velocity decay itself. (Displacement
        // integrates to roughly vx0 / EMBER_DRIFT_DECAY over the run, on the
        // order of 1e3 px here -- 1e7 leaves enormous headroom.)
        val hugeCanvas = 10_000_000f

        val samples = mutableListOf(ember.vx)
        repeat(100) {
            f.update(16f, true, embers, hugeCanvas, hugeCanvas, density)
            assertThat(f.particles).contains(ember)
            samples += ember.vx
        }

        // Per integrate(): vx_new = vx + (push - vx * EMBER_DRIFT_DECAY) *
        // deltaSeconds, i.e. vx_new = vx * (1 - 0.9 * 0.016) + push * 0.016
        // = 0.9856 * vx + push * 0.016, with push bounded by
        // +-(EMBER_DRIFT_DP/2) * density = +-35.75 px/s. Whenever
        // vx * 0.0144 (the decay term's magnitude) exceeds 35.75 * 0.016 =
        // 0.572 (the noise term's max magnitude) -- i.e. whenever |vx| > ~40
        // -- the decrease is deterministic regardless of the random draw's
        // sign, so |vx_new| < |vx| unconditionally. Starting at 1000 and
        // decaying with time constant 1/0.9 ~= 1.11s, |vx| after 1600ms is
        // ~1000 * exp(-1.6/1.11) ~= 235 even under worst-case adversarial
        // noise (bounded contribution ~30 px/s) -- comfortably above the ~40
        // threshold for the entire 100-step run, so this assertion is not
        // seed-dependent.
        for (i in 1 until samples.size) {
            assertWithMessage("step %s: |vx|=%s should be less than the previous |vx|=%s", i, samples[i], samples[i - 1])
                .that(kotlin.math.abs(samples[i])).isLessThan(kotlin.math.abs(samples[i - 1]))
        }
        // Confirms real decay happened, not merely a slow crawl: well under
        // half the seeded value.
        assertThat(kotlin.math.abs(samples.last())).isLessThan(500f)
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
    fun `restoreIntensity restores a saved ramp instead of resetting it`() {
        val f = field()

        f.restoreIntensity(0.42f)
        assertThat(f.intensity).isEqualTo(0.42f)
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
