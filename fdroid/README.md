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

Build it in a throwaway **clone** parked on the tagged commit — never from your
working checkout, and never from a `git worktree`. Both details are load-bearing;
see "Stamp the pinned commit" below.

```sh
git clone . /tmp/ambio-release && cd /tmp/ambio-release
git checkout v<versionName>
cp ~/path/to/Ambio/keystore.properties .
cp ~/path/to/Ambio/app/release-keystore.jks app/

export JAVA_HOME=~/.sdkman/candidates/java/21.0.12-tem   # JDK 21, see below
./gradlew --stop && ./gradlew clean :app:assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
apksigner verify --print-certs $APK      # cert must match AllowedAPKSigningKeys
unzip -p $APK META-INF/version-control-info.textproto   # revision must be the pinned commit
cp $APK Ambio-<versionName>.apk
gh release upload v<versionName> Ambio-<versionName>.apk

cd - && rm -rf /tmp/ambio-release          # it holds a copy of the signing key
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

## Stamp the pinned commit

AGP writes `META-INF/version-control-info.textproto` into the release APK and
stamps it with the git HEAD **of the machine doing the build**:

```
repositories {
  system: GIT
  local_root_path: "$PROJECT_DIR"
  revision: "e8d54f3f50aac7bbb42e54adbf54a92fa089dfec"
}
```

F-Droid builds the commit pinned in the metadata, so it stamps that hash. If our
HEAD is anywhere else, this one file differs and the whole comparison fails:

```
diff -r .../META-INF/version-control-info.textproto
4c4
<   revision: "d35bfaef1af15eb602617c43194e3545da30b127"
>   revision: "e8d54f3f50aac7bbb42e54adbf54a92fa089dfec"
```

That is easy to trip over, because it depends on repository state rather than on
anything in the build. The first reference APK was fine here purely by accident —
it was built while HEAD happened to be the tagged commit. Rebuilding it later,
after a couple of unrelated commits, broke a field that had been correct.

`local_root_path` is already normalised to `$PROJECT_DIR`, and building from a
different directory does not change `classes.dex`, so the build path itself is
not a problem. `revision` is the only volatile field.

A `git worktree` does **not** work as the build directory: its `.git` is a file
rather than a directory, AGP's reader cannot follow it, and the stamp becomes
`generate_error_reason: NO_VALID_GIT_FOUND` — a different mismatch, not a fix.
Use a real clone.

The durable fix is to stop emitting the file at all, which removes the footgun:

```kotlin
release {
    vcsInfo { include = false }
}
```

That is a source change, so it moves the commit F-Droid builds, and it drops the
data Play uses to map crashes back to a revision. Worth doing on the next version
bump rather than mid-review.
