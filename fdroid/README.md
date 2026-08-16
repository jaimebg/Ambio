# F-Droid

Ambio ships on F-Droid as a **reproducible build**: F-Droid compiles the pinned
commit on its own builders, compares the result byte-for-byte against the APK we
publish on the GitHub release, and on a match distributes *our* binary instead of
re-signing it with F-Droid's key.

That is the whole reason the release ritual below is fussy. A normal F-Droid app
only has to compile; ours has to compile to the *same bytes* on a Debian builder
as it does on your Mac. Anything that varies between the two machines — the JDK,
the git HEAD — breaks it.

- Submission MR: [fdroiddata!45842](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45842)
- Fork: `jaimebg/fdroiddata`, branch `add-com.jbgsoft.ambio`
- Verified reproducible since 2.0.0 (versionCode 3, commit `e8d54f3f`).

---

## Releasing a new version

### 1. Prepare the source

```sh
# app/build.gradle.kts: bump versionCode and versionName
# metadata/en-US/changelogs/<versionCode>.txt: add the changelog (max 500 chars)
./gradlew validateFdroidMetadata
git commit -am "chore: release <versionName>"
git tag v<versionName>
git push && git push --tags
```

Pushing the tag makes CI create the GitHub release. **CI does not attach the
APK** — it has no keystore, and signing happens on your machine only.

### 2. Build the reference APK

In a throwaway **clone parked on the tag** — never your working checkout, never a
`git worktree`. Both details are load-bearing; see [Trap 2](#trap-2-the-vcs-stamp).

```sh
AMBIO=~/Documents/GitHub/Ambio
git clone "$AMBIO" /tmp/ambio-release && cd /tmp/ambio-release
git checkout v<versionName>

cp "$AMBIO/keystore.properties" .
cp "$AMBIO/app/release-keystore.jks" app/

export JAVA_HOME=~/.sdkman/candidates/java/21.0.12-tem   # JDK 21, see Trap 1
./gradlew --stop && ./gradlew clean :app:assembleRelease
```

### 3. Verify before uploading

All four must pass. Each one has failed at least once.

```sh
APK=app/build/outputs/apk/release/app-release.apk

# signing certificate must equal AllowedAPKSigningKeys in the metadata
apksigner verify --print-certs $APK | grep "SHA-256 digest"
#   expected: 4af641cff280d0c867f858328cdd8b94645b4555f563dcad56207c8f196e91b9

# VCS stamp must be the tagged commit, not your working HEAD
unzip -p $APK META-INF/version-control-info.textproto
#   expected: revision: "<hash of v<versionName>>"

# version must match the metadata
aapt2 dump badging $APK | head -1

# signing block must not carry AGP's dependency blob (0x504b4453)
python3 - $APK <<'PY'
import struct,sys
d=open(sys.argv[1],'rb').read(); i=d.rfind(b'APK Sig Block 42')
size=struct.unpack('<Q',d[i-8:i])[0]; p=i+16-size-8+8
while p < i-8:
    l=struct.unpack('<Q',d[p:p+8])[0]
    print(f"0x{struct.unpack('<I',d[p+8:p+12])[0]:08x}"); p+=8+l
PY
#   expected: 0x7109871a and 0x42726577 only
```

### 4. Publish and clean up

```sh
cp $APK Ambio-<versionName>.apk
gh release upload v<versionName> Ambio-<versionName>.apk --repo jaimebg/Ambio

cd - && rm -rf /tmp/ambio-release      # holds a copy of the signing key
```

The `Ambio-<versionName>.apk` filename is load-bearing. The metadata says
`binary: .../download/v%v/Ambio-%v.apk`, where `%v` is the versionName, so the
URL resolves for every future tag without editing anything.

### 5. Let F-Droid pick it up

You do **not** need to touch fdroiddata for a routine version bump.
`AutoUpdateMode: Version` + `UpdateCheckMode: Tags` means F-Droid's bot spots the
new tag, resolves it to a commit hash, and writes the new `Builds:` entry itself.

Upload the APK promptly, though: if the bot builds before the asset exists, that
version's build fails until someone retries it.

---

## The metadata file

`com.jbgsoft.ambio.yml` here is a **byte-identical copy** of
`metadata/com.jbgsoft.ambio.yml` in fdroiddata. Copy it verbatim; check it with
`diff`.

It is byte-identical on purpose. This file used to carry explanatory comments,
which meant it could never be compared with the submitted one — it silently
drifted and lost `AutoName`. All prose lives in this README instead, where it
cannot leak into the submission.

**Comments do not survive.** fdroiddata CI runs `fdroid rewritemeta` and fails
any file it would rewrite; rewritemeta re-serialises from the parsed object, so
comments are dropped. No metadata file in fdroiddata has one. Prose meant for
F-Droid maintainers goes in a `MaintainerNotes:` field.

Check both CI jobs locally before pushing to the MR:

```sh
pip install fdroidserver
mkdir -p /tmp/fdt/metadata && cd /tmp/fdt
cp <this-dir>/com.jbgsoft.ambio.yml metadata/
printf 'repo_url: https://example.com/fdroid/repo\nrepo_name: t\nrepo_description: t\n' > config.yml
chmod 0600 config.yml
fdroid rewritemeta com.jbgsoft.ambio && git diff --no-index metadata/ <this-dir>/
fdroid lint com.jbgsoft.ambio
```

`rewritemeta` producing no diff is the check that matters. (Locally, `lint` will
complain that the categories are invalid — it has no `config/categories.yml`.
Ignore that; the real one has them.)

Canonicalisation gotchas found so far:

- `AllowedAPKSigningKeys` is a **scalar** with one key, a list with two or more.
  Copying the list form from another app fails CI.
- `commit:` takes the full 40-character hash, never a tag or branch. Tags are
  movable — `v2.0.0` was re-pointed once mid-submission and named two different
  trees on two different days.
- Categories must exist in fdroiddata's `config/categories.yml`. An early draft
  used `Time`, dropped when F-Droid replaced the coarse category set.

---

## The three traps

Each of these cost a failed F-Droid build. They are listed with the evidence, so
a future failure can be matched against them quickly.

### Trap 1: the JDK

**The app builds on any JDK 17 or newer, but only a JDK 21 build reproduces.**

```
ERROR: APK Signature Scheme v2 signer #1: APK integrity check failed.
       CHUNKED_SHA256 digest mismatch.
compared built binary to supplied reference binary but failed
  content/assets/dexopt/baseline.prof differ
  content/classes.dex differ
```

F-Droid's builder is Debian trixie with `openjdk-21` (21.0.12+8) and takes Gradle
from our wrapper, so Gradle 9.6.1 and AGP 9.3.1 already match on both sides. The
JDK is the only toolchain variable left — and it is a live one *because*
`build-logic` pins `jvmTarget` instead of a toolchain. That was the right call
(F-Droid's image has no JDK 17 and disables auto-provisioning), but it means
whichever JDK runs Gradle compiles the bytecode R8 then dexes.

Measured, not assumed:

- two clean builds on the same JDK give a byte-identical `classes.dex`, so the
  build itself is deterministic;
- switching *only* the JDK 17→21 changes `classes.dex` and `baseline.prof` and
  nothing else — exactly the pair F-Droid reported.

`baseline.prof` is never an independent problem: it is merged from AndroidX AARs
and encodes indices into `classes.dex`, so it moves whenever the dex moves.

Temurin `21.0.12-tem` is the same upstream build as Debian's `21.0.12+8`. If
F-Droid's image moves to another JDK, match it rather than guessing — read the
`apt-get install` lines at the top of the build log.

CI still builds on JDK 17. That checks the source compiles; it does not reproduce
what F-Droid produces. Only the locally built, locally signed APK has to be
byte-identical. Bumping CI to 21 would make CI catch toolchain drift early —
worth doing, not yet done.

### Trap 2: the VCS stamp

**Build from a clone checked out at the tag, or this field will be wrong.**

AGP writes `META-INF/version-control-info.textproto` into the release APK and
stamps it with the git HEAD *of the machine doing the build*:

```
repositories {
  system: GIT
  local_root_path: "$PROJECT_DIR"
  revision: "e8d54f3f50aac7bbb42e54adbf54a92fa089dfec"
}
```

F-Droid builds the pinned commit, so it stamps that hash. Any other HEAD and the
comparison fails on this one file:

```
diff -r .../META-INF/version-control-info.textproto
4c4
<   revision: "d35bfaef1af15eb602617c43194e3545da30b127"
>   revision: "e8d54f3f50aac7bbb42e54adbf54a92fa089dfec"
```

This is the easiest one to trip over, because it depends on repository state
rather than on anything in the build. The 2.0.0 reference APK was correct at
first *by accident* — it was built while HEAD happened to be the tagged commit.
Rebuilding it later, after two unrelated commits, broke a field that had been
right. Nothing about the build changed; only where HEAD pointed.

A `git worktree` is **not** a workaround: its `.git` is a file rather than a
directory, AGP's reader cannot follow it, and the stamp degrades to
`generate_error_reason: NO_VALID_GIT_FOUND` — a different mismatch, not a fix.

`local_root_path` is already normalised to `$PROJECT_DIR`, and building from a
different directory leaves `classes.dex` byte-identical, so the build path itself
is not a factor. `revision` is the only volatile field.

**The durable fix**, for a future version bump:

```kotlin
release {
    vcsInfo { include = false }
}
```

That stops emitting the file and removes the footgun for good. It is a source
change, so it moves the commit F-Droid builds, and it costs Play the
crash-to-revision mapping — which is why it was not done mid-review.

### Trap 3: AGP's dependency blob

F-Droid's scanner rejects the APK outright:

```
Found extra signing block 'Dependency metadata' in com.jbgsoft.ambio_3.apk
```

AGP writes a Google-specific protobuf of the dependency tree into the APK signing
block by default, and the scanner treats what it cannot read as an opaque blob.
`app/build.gradle.kts` already handles this, and configures APK and bundle
separately rather than switching the feature off: Play reads this data to flag
vulnerable dependencies, so the AAB keeps it and only the APK drops it.

---

## Key custody

`AllowedAPKSigningKeys` pins `4af641cf…196e91b9`. Once published, **F-Droid
accepts no other key for `com.jbgsoft.ambio`, ever.** There is no rotation story;
losing the key means losing the app's identity on F-Droid.

The key is `app/release-keystore.jks` with `keystore.properties` beside it at the
repo root. Both are gitignored, both exist only on this machine, and both are
needed. Back them up offline.

They deliberately never reach CI — that is the whole reason releases are signed
by hand rather than by a workflow.

---

## When a build fails

Read the comparison, don't guess. The F-Droid job log ends with:

```
==== detail begin ====
verification of APK with copied signature failed
Comparing reference APK to APK with copied signature...
Unexpected diff output:
  <the actual differing files>
==== detail end ====
```

That list is a `diff -r` of both APKs unpacked, so it names every differing entry
at once. Match the names against the traps above:

| differing entry | cause |
|---|---|
| `classes.dex`, `assets/dexopt/baseline.prof` | built on the wrong JDK (Trap 1) |
| `META-INF/version-control-info.textproto` | built at the wrong commit (Trap 2) |
| resources, manifest, `lib/` | genuinely new — investigate from scratch |

Fetch the log without credentials:

```sh
curl -sL https://gitlab.com/jaimebg/fdroiddata/-/jobs/<job-id>/raw \
  | sed -e 's/\x1b\[[0-9;]*[a-zA-Z]//g' -e 's/\r//g' \
  | sed -n '/detail begin/,/detail end/p'
```

Re-running after a fix needs no push: the job re-downloads the APK from GitHub at
build time, so uploading a corrected asset and hitting **Retry** on the job is
enough.

Two ground rules that made the difference both times:

1. **Confirm the build is deterministic before blaming the environment.** Two
   clean local builds producing an identical `classes.dex` is what turned "R8 is
   flaky" into "one variable differs between the machines".
2. **Change one variable and re-measure locally.** Both root causes were
   confirmed on this machine — JDK 17 vs 21, HEAD here vs there — before spending
   a seven-minute pipeline run on them.
