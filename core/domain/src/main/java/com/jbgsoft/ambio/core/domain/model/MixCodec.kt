package com.jbgsoft.ambio.core.domain.model

import java.util.Locale

/**
 * The storage format for a mix, shared by DataStore and Room.
 *
 * A comma-joined list of ids is a backward-compatible superset of a single id:
 * "rain" (the format written before the mixer existed) still reads as a mix of
 * one, so neither store needs a migration. Levels are an optional ":level"
 * suffix — Room omits them, DataStore writes them.
 */
object MixCodec {

    private const val DEFAULT_LEVEL = 1.0f

    /**
     * The mix holds at most three sounds. Stored mixes written before this ceiling
     * existed can hold five, so [decode] truncates rather than rejecting: treating
     * the long string as invalid would drop those users to the default mix.
     */
    const val MAX_ACTIVE_SOUNDS = 3

    /**
     * Never returns an empty list: a string with no usable id falls back to the
     * first sound, which is what [List.first] did before the mixer.
     */
    fun decode(encoded: String, allSounds: List<Sound>): List<ActiveSound> {
        val levelsById = encoded.split(',')
            .mapNotNull { segment ->
                val trimmed = segment.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val id = trimmed.substringBefore(':')
                val level = trimmed.substringAfter(':', "")
                    .toFloatOrNull()
                    ?.coerceIn(0f, 1f)
                    ?: DEFAULT_LEVEL
                id to level
            }
            .toMap()

        val mix = allSounds
            .filter { levelsById.containsKey(it.id) }
            .take(MAX_ACTIVE_SOUNDS)
            .map { ActiveSound(it, levelsById.getValue(it.id)) }

        return mix.ifEmpty { listOf(ActiveSound(allSounds.first(), DEFAULT_LEVEL)) }
    }

    /**
     * Emits ids in the order of the sound list, not the order they were activated,
     * so the same mix always produces the same string.
     */
    fun encode(mix: List<ActiveSound>, withLevels: Boolean): String =
        mix.joinToString(",") { active ->
            if (withLevels) {
                "${active.sound.id}:${String.format(Locale.ROOT, "%.2f", active.level)}"
            } else {
                active.sound.id
            }
        }
}
