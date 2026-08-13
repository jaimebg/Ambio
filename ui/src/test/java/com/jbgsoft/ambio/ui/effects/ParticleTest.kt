package com.jbgsoft.ambio.ui.effects

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class ParticleTest {

    private fun particle(ageMs: Long, lifetimeMs: Long = 1000L) = Particle(
        x = 0f, y = 0f, baseX = 0f, vx = 0f, vy = 0f,
        radiusPx = 4f, baseAlpha = 1f,
        lifetimeMs = lifetimeMs, ageMs = ageMs,
        seed = 0f, colorIndex = 0, type = ParticleType.DROPLET
    )

    @Test
    fun `a particle fades in over the first 15 percent of its life`() {
        assertThat(particle(ageMs = 0L).lifeAlpha).isEqualTo(0f)
        assertThat(particle(ageMs = 75L).lifeAlpha).isWithin(0.001f).of(0.5f)
        assertThat(particle(ageMs = 150L).lifeAlpha).isWithin(0.001f).of(1f)
    }

    @Test
    fun `a particle holds full alpha in the middle of its life`() {
        assertThat(particle(ageMs = 400L).lifeAlpha).isEqualTo(1f)
        assertThat(particle(ageMs = 700L).lifeAlpha).isEqualTo(1f)
    }

    @Test
    fun `a particle fades out over the last 30 percent of its life`() {
        assertThat(particle(ageMs = 850L).lifeAlpha).isWithin(0.001f).of(0.5f)
        assertThat(particle(ageMs = 1000L).lifeAlpha).isWithin(0.001f).of(0f)
    }

    @Test
    fun `a particle is alive until its lifetime elapses`() {
        assertThat(particle(ageMs = 999L).isAlive).isTrue()
        assertThat(particle(ageMs = 1000L).isAlive).isFalse()
    }

    @Test
    fun `every type has a spec with sane ranges`() {
        ParticleType.entries.forEach { type ->
            val spec = specFor(type)
            assertWithMessage("%s type", type.name).that(spec.type).isEqualTo(type)
            assertWithMessage("%s size", type.name)
                .that(spec.sizeDpRange.start).isGreaterThan(0f)
            assertWithMessage("%s lifetime", type.name)
                .that(spec.lifetimeMsRange.first).isGreaterThan(0L)
            assertWithMessage("%s ceiling", type.name)
                .that(spec.ceiling).isGreaterThan(0)
            assertWithMessage("%s coreFraction", type.name)
                .that(spec.coreFraction).isAtLeast(0f)
            assertWithMessage("%s coreFraction", type.name)
                .that(spec.coreFraction).isAtMost(1f)
        }
    }

    @Test
    fun `the wisp ceiling is the low one, because wisps are the expensive type`() {
        assertThat(specFor(ParticleType.WISP).ceiling).isEqualTo(16)
        ParticleType.entries.filter { it != ParticleType.WISP }.forEach { type ->
            assertWithMessage("%s", type.name)
                .that(specFor(type).ceiling).isGreaterThan(specFor(ParticleType.WISP).ceiling)
        }
    }

    @Test
    fun `rain falls fast enough to read as rain`() {
        // The old value was 400-600 px/s, which is 4-6 seconds to cross a 2400px
        // screen and reads as drifting dots. In dp/s it now crosses in 1.4-2.2s.
        assertThat(specFor(ParticleType.DROPLET).velocityYDpRange.start).isAtLeast(430f)
    }

    @Test
    fun `only fire and cave add light`() {
        assertThat(ParticleType.entries.filter { specFor(it).additive })
            .containsExactly(ParticleType.EMBER, ParticleType.WISP)
    }

    @Test
    fun `specFor returns a prebuilt instance rather than allocating per call`() {
        ParticleType.entries.forEach { type ->
            assertThat(specFor(type)).isSameInstanceAs(specFor(type))
        }
    }

    @Test
    fun `every type has exactly four colours, because colorIndex is drawn from four`() {
        // 4 here must match COLORS_PER_TYPE in ParticleField.kt, which is private
        // to that file. ParticleField.newParticle does
        // random.nextInt(COLORS_PER_TYPE) with no bounds check on the palette
        // side, so a colorsFor branch with fewer than four entries is an
        // out-of-range crash at runtime, not a compile error.
        ParticleType.entries.forEach { type ->
            assertWithMessage("%s", type.name).that(colorsFor(type)).hasSize(4)
        }
    }
}
