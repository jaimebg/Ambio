plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Captured at configuration time: reaching for `project` inside a task action
// would opt this build out of the configuration cache.
val storeMetadataDir = layout.projectDirectory.dir("fastlane/metadata/android").asFile
val appBuildFile = layout.projectDirectory.file("app/build.gradle.kts").asFile

/**
 * Checks every Play Store text file against Play's character limits, before
 * anything is uploaded.
 *
 * Play rejects the whole upload when one string is a character over, and a
 * rejected upload costs a full release cycle. German compounds and non-Latin
 * scripts overrun these limits routinely, so this is worth seconds locally.
 *
 * Deliberately not wired into `check`: `fastlane/` is local-only, so on CI there
 * is nothing here to validate and the task simply says so. It is a release gate,
 * run by hand before `bundleRelease`.
 */
tasks.register("validateStoreText") {
    group = "verification"
    description = "Checks Play Store text against Play's character limits."

    val metadataDir = storeMetadataDir
    val buildFile = appBuildFile

    doLast {
        if (!metadataDir.isDirectory) {
            logger.lifecycle(
                "validateStoreText: no ${metadataDir.path}, nothing to validate. " +
                    "The store listing is local-only; this is expected on CI."
            )
            return@doLast
        }

        // The changelog is named after the versionCode it ships with, so the
        // limit table cannot be built without reading it first.
        val versionCode = Regex("""versionCode\s*=\s*(\d+)""")
            .find(buildFile.readText())
            ?.groupValues
            ?.get(1)
            ?: throw GradleException("Could not read versionCode from ${buildFile.path}")

        val limits = linkedMapOf(
            "title.txt" to 30,
            "short_description.txt" to 80,
            "full_description.txt" to 4000,
            "changelogs/$versionCode.txt" to 500
        )

        val locales = metadataDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            .orEmpty()

        val missing = mutableListOf<String>()
        val tooLong = mutableListOf<String>()
        var withinLimit = 0

        locales.forEach { localeDir ->
            limits.forEach { (relativePath, limit) ->
                val file = localeDir.resolve(relativePath)
                if (!file.isFile) {
                    missing += "${localeDir.name}/$relativePath"
                    return@forEach
                }
                // Count code points, not UTF-16 units: a String of one emoji has
                // length 2, and failing a listing over that would be nonsense.
                // Trailing newlines are not content and supply does not upload them.
                val text = file.readText().trim()
                val length = text.codePointCount(0, text.length)
                if (length > limit) {
                    tooLong += "${localeDir.name}/$relativePath: $length characters, limit is $limit"
                } else {
                    withinLimit++
                }
            }
        }

        logger.lifecycle(
            "validateStoreText: ${locales.size} locales, $withinLimit files within limit, " +
                "${tooLong.size} over, ${missing.size} missing."
        )

        // Every problem at once. Reporting only the first would mean one upload
        // cycle per bad string across 48 locales.
        if (tooLong.isNotEmpty() || missing.isNotEmpty()) {
            val report = buildString {
                appendLine("Store text is not ready to upload.")
                if (tooLong.isNotEmpty()) {
                    appendLine()
                    appendLine("Over Play's limit:")
                    tooLong.forEach { appendLine("  - $it") }
                }
                if (missing.isNotEmpty()) {
                    appendLine()
                    appendLine("Missing:")
                    missing.forEach { appendLine("  - $it") }
                }
            }
            throw GradleException(report)
        }
    }
}
