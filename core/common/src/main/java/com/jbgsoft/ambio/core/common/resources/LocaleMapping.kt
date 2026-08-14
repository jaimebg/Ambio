package com.jbgsoft.ambio.core.common.resources

/**
 * The bridge between the two locale naming systems this project straddles:
 * Play Console's codes (which name the store listing directories) and Android's
 * resource qualifiers (which name the `values-*` directories).
 *
 * They disagree often enough, and *silently* enough, to be worth pinning here.
 * A wrong qualifier does not fail the build or trip lint — it produces a
 * directory the resource resolver simply never looks in, so the locale ships
 * in English and nobody finds out until a user complains.
 *
 * The three families of disagreement:
 *  - a region suffix Android does not need, because only one variant ships
 *  - locales whose region or script cannot be written in the old
 *    `values-xx-rYY` form at all, needing the BCP-47 `b+` form
 *  - languages whose ISO code changed, where Android kept the legacy one
 */

/** Play code to the suffix after `values-`. Empty string means the default directory. */
val PLAY_TO_RESOURCE_QUALIFIER: Map<String, String> = mapOf(
    "en-US" to "",

    // Region dropped: only one variant of each of these ships.
    "af" to "af",
    "ar" to "ar",
    "bg" to "bg",
    "ca" to "ca",
    "cs-CZ" to "cs",
    "da-DK" to "da",
    "de-DE" to "de",
    "el-GR" to "el",
    "es-ES" to "es",
    "et" to "et",
    "fi-FI" to "fi",
    "fr-FR" to "fr",
    "hi-IN" to "hi",
    "hr" to "hr",
    "hu-HU" to "hu",
    "it-IT" to "it",
    "ja-JP" to "ja",
    "ko-KR" to "ko",
    "lt" to "lt",
    "lv" to "lv",
    "ms" to "ms",
    "nl-NL" to "nl",
    "pl-PL" to "pl",
    "ro" to "ro",
    "ru-RU" to "ru",
    "sk" to "sk",
    "sl" to "sl",
    "sr" to "sr",
    "sv-SE" to "sv",
    "sw" to "sw",
    "th" to "th",
    "tr-TR" to "tr",
    "uk" to "uk",
    "vi" to "vi",

    // Region kept: the language also ships a region-less variant above, so
    // dropping the region here would collide with it.
    "en-GB" to "en-rGB",
    "fr-CA" to "fr-rCA",

    // BCP-47 form required: a UN M.49 region code, or a region that must stay
    // explicit because both variants ship.
    "es-419" to "b+es+419",
    "pt-BR" to "b+pt+BR",
    "pt-PT" to "b+pt+PT",
    "bn-BD" to "b+bn+BD",

    // Chinese selects on script, not region.
    "zh-CN" to "b+zh+Hans",
    "zh-TW" to "b+zh+Hant+TW",
    "zh-HK" to "b+zh+Hant+HK",

    // Legacy ISO codes Android still resolves against.
    "iw-IL" to "iw",   // Hebrew: modern code is `he`
    "id" to "b+id",    // Indonesian: legacy code is `in`

    // Play says Norwegian; Android wants Bokmal specifically.
    "no-NO" to "nb",

    // Filipino, not Tagalog.
    "fil" to "fil"
)

/** Play code to the BCP-47 tag used in `locales_config.xml`. */
val PLAY_TO_BCP47: Map<String, String> = mapOf(
    "en-US" to "en-US",
    "af" to "af", "ar" to "ar", "bg" to "bg", "ca" to "ca",
    "cs-CZ" to "cs", "da-DK" to "da", "de-DE" to "de", "el-GR" to "el",
    "es-ES" to "es", "et" to "et", "fi-FI" to "fi", "fr-FR" to "fr",
    "hi-IN" to "hi", "hr" to "hr", "hu-HU" to "hu", "it-IT" to "it",
    "ja-JP" to "ja", "ko-KR" to "ko", "lt" to "lt", "lv" to "lv",
    "ms" to "ms", "nl-NL" to "nl", "pl-PL" to "pl", "ro" to "ro",
    "ru-RU" to "ru", "sk" to "sk", "sl" to "sl", "sr" to "sr",
    "sv-SE" to "sv", "sw" to "sw", "th" to "th", "tr-TR" to "tr",
    "uk" to "uk", "vi" to "vi",
    "en-GB" to "en-GB", "fr-CA" to "fr-CA",
    "es-419" to "es-419", "pt-BR" to "pt-BR", "pt-PT" to "pt-PT",
    "bn-BD" to "bn-BD",
    "zh-CN" to "zh-Hans", "zh-TW" to "zh-Hant-TW", "zh-HK" to "zh-Hant-HK",
    "iw-IL" to "iw", "id" to "id",
    "no-NO" to "nb",
    "fil" to "fil"
)

/**
 * The resource directory name for a Play locale — `values` for the default,
 * `values-<qualifier>` otherwise.
 *
 * @throws IllegalArgumentException if the code is not one the store listing ships,
 *   because a typo here would otherwise create a directory nothing ever reads.
 */
fun resourceDirFor(playCode: String): String {
    val qualifier = requireNotNull(PLAY_TO_RESOURCE_QUALIFIER[playCode]) {
        "No resource qualifier mapped for Play locale '$playCode'"
    }
    return if (qualifier.isEmpty()) "values" else "values-$qualifier"
}
