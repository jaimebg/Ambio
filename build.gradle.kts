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
                val text = file.readText()
                // Blank check is trimmed: whitespace the file might open with
                // (or consist entirely of) should not count as content.
                if (text.isBlank()) {
                    problems += "$name/$relativePath: empty"
                    return@forEach
                }
                // Code points, untrimmed — the same measure validateStoreText
                // settled on after Play counted a trailing newline as content.
                val length = text.codePointCount(0, text.length)
                if (length > limit) {
                    problems += "$name/$relativePath: $length characters, limit is $limit"
                } else {
                    checked++
                }
            }
        }

        // en-US is F-Droid's fallback for every locale without its own images,
        // so these two are the whole app's graphics, not just English's.
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

// Play's codes carry two deprecated language subtags. Renamed rather than
// passed through: both forms work on F-Droid, but the bare modern tag is the
// dominant convention there and matches a broader set of device locales than a
// region-qualified one does.
val fdroidLocaleRenames = mapOf("iw-IL" to "he", "no-NO" to "nb")

val storeImagesDir = layout.projectDirectory
    .dir("fastlane/metadata/android/en-US/images/phoneScreenshots").asFile
val appIconFile = layout.projectDirectory.file("assets/app-icon.png").asFile
val featureGraphicFile = layout.projectDirectory.file("assets/gplay-feature-graphic.png").asFile

/**
 * Copies the publishable slice of `fastlane/` into the tracked `metadata/`.
 *
 * `fastlane/` is gitignored in full and must stay that way: it holds the Play
 * service account key. So the listing is not published by un-ignoring part of
 * that directory — one wrong negation pattern there would put a credential in a
 * public repo forever — but by copying a named whitelist somewhere clean.
 *
 * Whitelist, not blacklist: every published file is named below, so a new file
 * appearing in `fastlane/` cannot leak into the listing by default.
 *
 * Text ships for all 48 locales; images only for en-US, which F-Droid falls
 * back to for every locale without its own. The full localised image set is
 * 344MB and git would carry it forever.
 */
tasks.register<Sync>("syncFdroidMetadata") {
    group = "publishing"
    description = "Copies the publishable store listing from fastlane/ into metadata/."

    // Captured into a local val rather than read directly inside `onlyIf`: that
    // spec runs at execution time, and a lambda referencing the top-level val
    // there would need to capture the whole script object, which the
    // configuration cache cannot serialize.
    val metadataSourceDir = storeMetadataDir

    // Without this, a run on CI — where fastlane/ does not exist — would see an
    // empty source and Sync would delete the tracked listing.
    onlyIf { metadataSourceDir.isDirectory }

    into(layout.projectDirectory.dir("metadata"))

    from(storeMetadataDir) {
        include("*/title.txt")
        include("*/short_description.txt")
        include("*/full_description.txt")
        include("*/changelogs/*.txt")
        // A supply convention F-Droid has no use for; it reads changelogs by
        // versionCode only.
        exclude("*/changelogs/default.txt")
    }

    from(storeImagesDir) {
        into("en-US/images/phoneScreenshots")
        include("*.png")
    }

    from(appIconFile) {
        into("en-US/images")
        rename { "icon.png" }
    }

    from(featureGraphicFile) {
        into("en-US/images")
        rename { "featureGraphic.png" }
    }

    exclude("**/.DS_Store")
    includeEmptyDirs = false

    val renames = fdroidLocaleRenames
    eachFile {
        val segments = relativePath.segments
        val replacement = renames[segments.firstOrNull()]
        if (replacement != null) {
            relativePath = RelativePath(
                true,
                replacement,
                *segments.drop(1).toTypedArray()
            )
        }
    }
}
