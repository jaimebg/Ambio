package com.jbgsoft.ambio.ui.effects

import kotlin.math.floor
import kotlin.math.min

/**
 * A starting value, not a result. Confirmed or adjusted by measuring frame
 * timing on real hardware — see the spec's termination criterion 8.
 */
const val PARTICLE_BUDGET = 36

/**
 * The smallest share an active source may get. A sound at 5% still has its card
 * lit, so something has to be on screen for it. Kept low on purpose: with three
 * sources a floor of 6 would reserve half the budget and flatten the weighting.
 */
const val FLOOR_PER_SOURCE = 3

/** One active sound, reduced to what the field needs: a type and a level. */
data class FieldSource(
    val type: ParticleType,
    val weight: Float
)

/**
 * Splits one budget across the active sources, returning counts parallel to
 * [sources].
 *
 * The floor is reserved *before* the proportional split, not clamped after it.
 * Clamping after lets the total exceed the budget: with weights 1.0/0.05/0.05
 * a proportional split gives 33/2/2, and raising the last two to a floor of 3
 * yields 39 against a budget of 36. Reserving first cannot overshoot.
 *
 * A per-type [ParticleSpec.ceiling] is applied last and what it frees is
 * deliberately **not** redistributed: handing it back to the same type would
 * defeat the ceiling, which exists to bound fill rate.
 */
fun allocate(
    sources: List<FieldSource>,
    budget: Int = PARTICLE_BUDGET,
    floorPerSource: Int = FLOOR_PER_SOURCE
): IntArray {
    val n = sources.size
    if (n == 0) return IntArray(0)

    // A budget too small to give everyone the floor shrinks the floor instead of
    // breaking the total.
    val effectiveFloor = min(floorPerSource, budget / n)
    val remainder = (budget - effectiveFloor * n).coerceAtLeast(0)
    val totalWeight = sources.sumOf { it.weight.toDouble() }

    val exact = DoubleArray(n) { i ->
        val share = if (totalWeight > 0.0) {
            remainder * sources[i].weight / totalWeight
        } else {
            remainder.toDouble() / n
        }
        effectiveFloor + share
    }

    val counts = IntArray(n) { floor(exact[it]).toInt() }

    // Largest remainder, so the counts sum to the budget exactly. Ties are
    // broken by ParticleType.ordinal rather than list index: the winner must
    // be determined by what the source *is*, not by where it happens to sit
    // in the list. An index-keyed tie-break would make the result depend on
    // input order — e.g. DROPLET 0.25 / LEAF 0.75 both land on a tied .5
    // fraction, and swapping their list position would swap which one wins.
    var leftover = budget - counts.sum()
    val byFraction = (0 until n).sortedWith(
        compareByDescending<Int> { exact[it] - counts[it] }
            .thenBy { sources[it].type.ordinal }
    )
    var i = 0
    while (leftover > 0 && n > 0) {
        counts[byFraction[i % n]]++
        leftover--
        i++
    }

    for (index in 0 until n) {
        counts[index] = min(counts[index], specFor(sources[index].type).ceiling)
    }
    return counts
}
