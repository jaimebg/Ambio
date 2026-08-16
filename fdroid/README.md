# F-Droid submission

`com.jbgsoft.ambio.yml` is a byte-identical copy of `metadata/com.jbgsoft.ambio.yml`
in [fdroiddata](https://gitlab.com/fdroid/fdroiddata). Submission MR:
[fdroiddata!45842](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45842),
from the fork `jaimebg/fdroiddata`, branch `add-com.jbgsoft.ambio`.

Byte-identical on purpose. An earlier version of this file carried explanatory
comments, drifted from the submitted one, and lost `AutoName` in the process.
The notes live here instead, where they cannot leak into the MR.

## Copy it verbatim

fdroiddata CI runs `fdroid rewritemeta` (`.gitlab-ci.yml`) and fails the pipeline
on any file that is not in canonical form. `rewritemeta` re-serialises from the
parsed object, so **comments do not survive** — a commented copy fails CI. None of
the metadata files in fdroiddata carry comments. Field order and blank lines here
are already canonical; keep them.

Prose meant for F-Droid maintainers goes in a `MaintainerNotes:` field, not in
comments.

## Things already learned the hard way

- **Categories** must exist in `config/categories.yml` in fdroiddata. An early
  draft used `Time`, which F-Droid dropped when it replaced the coarse category
  set with the granular one; lint rejects it.
- **`commit:` takes the full 40-character hash, never a tag or branch.** Tags are
  movable, and `v2.0.0` was in fact re-pointed once mid-submission, so the tag
  named two different trees on two different days.
- **The APK must not carry AGP's dependency blob.** F-Droid's scanner rejects the
  build with `Found extra signing block 'Dependency metadata'`. `app/build.gradle.kts`
  keeps it out of the APK while leaving it in the AAB, which is what Play reads.

## Reproducible build

`binary:` points at our own signed APK on the GitHub release. F-Droid builds the
pinned commit itself and compares; on a match it publishes our binary instead of
re-signing it. `AllowedAPKSigningKeys` is the SHA-256 of that APK's signing
certificate, from `apksigner verify --print-certs`.

Releases are signed locally, not in CI — the keystore stays off GitHub. For each
new tag:

```sh
export JAVA_HOME=~/.sdkman/candidates/java/21.0.12-tem   # JDK 21, see below
./gradlew --stop && ./gradlew clean :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk   # cert must match AllowedAPKSigningKeys
cp app/build/outputs/apk/release/app-release.apk Ambio-<versionName>.apk
gh release upload v<versionName> Ambio-<versionName>.apk
```

The `Ambio-%v.apk` filename is load-bearing: `%v` is the versionName, so the
`binary:` URL resolves for every later tag without editing the metadata.

Once `AllowedAPKSigningKeys` is published, F-Droid accepts no other key for
`com.jbgsoft.ambio`. Losing `keystore.properties` and the keystore it points at
means losing the app's identity on F-Droid — back both up offline.

## Build the release APK on JDK 21

The app builds on any JDK 17 or newer, but only a **JDK 21** build reproduces.
The first attempt was built on JDK 17 and F-Droid rejected it:

```
ERROR: APK Signature Scheme v2 signer #1: APK integrity check failed.
       CHUNKED_SHA256 digest mismatch.
compared built binary to supplied reference binary but failed
  content/assets/dexopt/baseline.prof differ
  content/classes.dex differ
```

F-Droid's builder runs Debian trixie with `openjdk-21` (21.0.12+8) and takes
Gradle from our wrapper, so Gradle and AGP already matched; the JDK was the only
toolchain difference left. `build-logic` pins `jvmTarget` rather than a
toolchain — deliberately, so the build does not demand one exact JDK — which
means whichever JDK runs Gradle is the one that compiles the bytecode R8 then
dexes.

Measured rather than assumed:

- two clean builds on the same JDK produce byte-identical `classes.dex`, so the
  build itself is deterministic;
- switching only the JDK from 17 to 21 changes `classes.dex` and
  `baseline.prof`, and nothing else in the APK — exactly the two entries F-Droid
  reported.

`baseline.prof` is not an independent problem: it is merged from AndroidX AARs
and encodes indices into `classes.dex`, so it moves whenever the dex moves.

Temurin `21.0.12-tem` is the same upstream build as Debian's `21.0.12+8`. If
F-Droid's image moves to a different JDK, match it rather than guessing.

Note that CI still builds on JDK 17, which checks that the source compiles but
does not reproduce what F-Droid produces. Only the locally built, locally signed
APK uploaded to the release has to be byte-identical.
