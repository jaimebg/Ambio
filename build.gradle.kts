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
                //
                // Counted untrimmed, because Play counts the trailing newline.
                // This gate used to trim it and passed an Italian changelog of
                // exactly 500 that Play then rejected at 501. The measure has to
                // be the bytes supply actually sends, not a tidier version of
                // them.
                val text = file.readText()
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

// The published F-Droid listing. Unlike `fastlane/`, this directory IS tracked,
// so unlike validateStoreText this gate can and does run on CI.
val fdroidMetadataDir = layout.projectDirectory.dir("metadata").asFile

/**
 * Checks the F-Droid listing in `metadata/` is complete, correctly named and
 * within F-Droid's limits.
 *
 * F-Droid builds its listing from whatever is in the repo at the tagged commit.
 * A missing file there is not a build failure, it is a listing that silently
 * ships wrong — no description, or no screenshots — and the next chance to fix
 * it is the next release. So this runs on CI, on every commit.
 */
tasks.register("validateFdroidMetadata") {
    group = "verification"
    description = "Checks the published F-Droid listing in metadata/."

    val metadataDir = fdroidMetadataDir
    val buildFile = appBuildFile

    doLast {
        if (!metadataDir.isDirectory) {
            throw GradleException(
                "metadata/ is missing. Regenerate it with ./gradlew syncFdroidMetadata"
            )
        }

        val versionCode = Regex("""versionCode\s*=\s*(\d+)""")
            .find(buildFile.readText())
            ?.groupValues
            ?.get(1)
            ?: throw GradleException("Could not read versionCode from ${buildFile.path}")

        // F-Droid's limits, which are looser than Play's only for the title.
        val limits = linkedMapOf(
            "title.txt" to 50,
            "short_description.txt" to 80,
            "full_description.txt" to 4000,
            "changelogs/$versionCode.txt" to 500
        )

        // Language, optionally a region: two/three letters, then a 2-letter
        // country, a 3-digit UN region (es-419), or a 4-letter script.
        val localePattern = Regex("""^[a-z]{2,3}(-([A-Z]{2}|[0-9]{3}|[A-Z][a-z]{3}))?$""")

        // Deprecated subtags Play still emits. Renaming these is the sync
        // task's job, so seeing one here means the sync did not run.
        val deprecated = mapOf("iw" to "he", "in" to "id", "no" to "nb")

        val locales = metadataDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            .orEmpty()

        if (locales.isEmpty()) {
            throw GradleException("metadata/ contains no locale directories.")
        }

        val problems = mutableListOf<String>()
        var checked = 0

        locales.forEach { localeDir ->
            val name = localeDir.name
            if (!localePattern.matches(name)) {
                problems += "$name: not a valid locale code"
            }
            deprecated[name.substringBefore('-')]?.let { modern ->
                problems += "$name: deprecated subtag, should be '$modern'"
            }
            limits.forEach { (relativePath, limit) ->
                val file = localeDir.resolve(relativePath)
                if (!file.isFile) {
                    problems += "$name/$relativePath: missing"
                    return@forEach
                }
                // Code points, untrimmed — the same measure validateStoreText
                // settled on after Play counted a trailing newline as content.
                val text = file.readText()
                val length = text.codePointCount(0, text.length)
                if (length > limit) {
                    problems += "$name/$relativePath: $length characters, limit is $limit"
                } else {
                    checked++
                }
            }
        }

        // en-US is F-Droid's fallback for every locale without its own images,
        // so these three are the whole app's graphics, not just English's.
        listOf(
            "en-US/images/icon.png",
            "en-US/images/featureGraphic.png"
        ).forEach { relativePath ->
            if (!metadataDir.resolve(relativePath).isFile) {
                problems += "$relativePath: missing"
            }
        }
        val shots = metadataDir.resolve("en-US/images/phoneScreenshots")
            .listFiles()
            ?.filter { it.extension == "png" }
            .orEmpty()
        if (shots.isEmpty()) {
            problems += "en-US/images/phoneScreenshots: no screenshots"
        }

        logger.lifecycle(
            "validateFdroidMetadata: ${locales.size} locales, $checked text files within limit, " +
                "${shots.size} screenshots, ${problems.size} problems."
        )

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("The F-Droid listing is not ready to publish.")
                    appendLine()
                    problems.forEach { appendLine("  - $it") }
                }
            )
        }
    }
}
