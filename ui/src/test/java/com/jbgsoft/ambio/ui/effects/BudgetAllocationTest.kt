package com.jbgsoft.ambio.ui.effects

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BudgetAllocationTest {

    private fun sources(vararg pairs: Pair<ParticleType, Float>) =
        pairs.map { FieldSource(it.first, it.second) }

    @Test
    fun `one source takes the whole budget`() {
        val counts = allocate(sources(ParticleType.DROPLET to 1f))

        assertThat(counts.toList()).containsExactly(36)
    }

    @Test
    fun `three sources split the budget by weight and sum to it exactly`() {
        val counts = allocate(
            sources(
                ParticleType.DROPLET to 1.0f,
                ParticleType.LEAF to 0.7f,
                ParticleType.BUBBLE to 0.4f
            )
        )

        assertThat(counts.sum()).isEqualTo(36)
        assertThat(counts.toList()).containsExactly(16, 12, 8).inOrder()
    }

    @Test
    fun `the floor never pushes the total past the budget`() {
        // The naive formula — split proportionally, then clamp up to the floor —
        // gives 33 + 6 + 6 = 45 here. Reserving the floor first cannot.
        val counts = allocate(
            sources(
                ParticleType.DROPLET to 1.0f,
                ParticleType.LEAF to 0.05f,
                ParticleType.BUBBLE to 0.05f
            )
        )

        assertThat(counts.sum()).isEqualTo(36)
        assertThat(counts.min()).isAtLeast(FLOOR_PER_SOURCE)
    }

    @Test
    fun `a silent source still gets its floor`() {
        val counts = allocate(
            sources(ParticleType.DROPLET to 1.0f, ParticleType.LEAF to 0f)
        )

        assertThat(counts[1]).isEqualTo(FLOOR_PER_SOURCE)
        assertThat(counts.sum()).isEqualTo(36)
    }

    @Test
    fun `all-silent sources split the budget evenly`() {
        val counts = allocate(
            sources(ParticleType.DROPLET to 0f, ParticleType.LEAF to 0f)
        )

        assertThat(counts.sum()).isEqualTo(36)
        assertThat(counts.toList()).containsExactly(18, 18)
    }

    @Test
    fun `the wisp ceiling caps the total below the budget and is not redistributed`() {
        val counts = allocate(sources(ParticleType.WISP to 1f))

        assertThat(counts.toList()).containsExactly(16)
        assertThat(counts.sum()).isLessThan(36)
    }

    @Test
    fun `what a ceiling frees does not leak to the other sources`() {
        val counts = allocate(
            sources(ParticleType.WISP to 1f, ParticleType.DROPLET to 1f)
        )

        // Both would get 18; the wisp is capped at 16 and the droplet keeps 18.
        assertThat(counts.toList()).containsExactly(16, 18).inOrder()
    }

    @Test
    fun `a budget smaller than the reserved floor still sums exactly`() {
        val counts = allocate(
            sources(
                ParticleType.DROPLET to 1f,
                ParticleType.LEAF to 1f,
                ParticleType.BUBBLE to 1f
            ),
            budget = 6
        )

        assertThat(counts.sum()).isEqualTo(6)
        assertThat(counts.toList()).containsExactly(2, 2, 2)
    }

    @Test
    fun `no sources allocates nothing`() {
        assertThat(allocate(emptyList()).toList()).isEmpty()
    }

    @Test
    fun `allocation does not depend on the order of the sources`() {
        val forwards = allocate(
            sources(ParticleType.DROPLET to 1.0f, ParticleType.LEAF to 0.4f)
        )
        val backwards = allocate(
            sources(ParticleType.LEAF to 0.4f, ParticleType.DROPLET to 1.0f)
        )

        assertThat(forwards.toList()).containsExactly(*backwards.reversed().toTypedArray())

        // DROPLET 0.25 / LEAF 0.75 land on a tied largest-remainder fraction
        // (both exact shares end in .5), so this pair specifically exercises
        // the tie-break. An index-keyed tie-break would pick a different
        // winner depending on which side of the list each source sits on; a
        // source-keyed one (ParticleType.ordinal) picks the same winner
        // either way, so reversing the input still reverses the output.
        val tiedForwards = allocate(
            sources(ParticleType.DROPLET to 0.25f, ParticleType.LEAF to 0.75f)
        )
        val tiedBackwards = allocate(
            sources(ParticleType.LEAF to 0.75f, ParticleType.DROPLET to 0.25f)
        )

        assertThat(tiedForwards.toList()).containsExactly(*tiedBackwards.reversed().toTypedArray())
    }
}
