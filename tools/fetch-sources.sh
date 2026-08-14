#!/usr/bin/env bash
#
# Rebuilds the pipeline inputs that audio-src/ does not carry as audio.
#
# audio-src/sources.json records, for each of the six Freesound-sourced loops,
# the sound id, a sha256 of the uploaded original, and the window of it we use.
# This script downloads each original, refuses it if the checksum does not
# match, and cuts that window to a lossless FLAC that process-audio.sh consumes.
#
# Why the checksum matters: a Freesound uploader can replace a sound's file
# in place. Without the hash, a silent substitution upstream would flow all the
# way into a shipped asset and the only symptom would be that the app sounds
# different. A mismatch here is a hard failure, not a warning.
#
# Freesound serves ORIGINAL files only to OAuth2-authenticated clients -- an API
# key alone is not enough, it only reaches search and the lossy previews. So this
# needs an access token:
#
#   1. Register an app at https://freesound.org/apiv2/apply/ and leave the
#      callback URL empty.
#   2. Open https://freesound.org/apiv2/oauth2/authorize/?client_id=$ID&response_type=code
#      and authorize; Freesound shows a code on screen.
#   3. Exchange it:
#        curl -X POST https://freesound.org/apiv2/oauth2/access_token/ \
#          -d client_id=$ID -d client_secret=$SECRET \
#          -d grant_type=authorization_code -d code=$CODE
#   4. export FREESOUND_TOKEN=<the access_token from that response>
#
# Tokens last 24h. Output goes to build/audio-src-fetched/, which is gitignored
# and wiped by `./gradlew clean` -- like process-audio.sh, this script never
# writes into a directory that something else treats as a source of truth.
#
# Usage: FREESOUND_TOKEN=... tools/fetch-sources.sh [OUT_DIR]

set -euo pipefail

MANIFEST="audio-src/sources.json"
OUT="${1:-build/audio-src-fetched}"

[ -f "$MANIFEST" ] || { echo "missing $MANIFEST (run from the repo root)"; exit 1; }
command -v ffmpeg >/dev/null || { echo "ffmpeg not found"; exit 1; }
: "${FREESOUND_TOKEN:?set FREESOUND_TOKEN -- see the comment at the top of this script}"

mkdir -p "$OUT" "$OUT/.originals"
failed=0

# One line per sound: name id sha256 start duration
while read -r name id sha start dur; do
  out="$OUT/$name.flac"
  if [ -f "$out" ]; then
    echo "have    $name"
    continue
  fi

  raw="$OUT/.originals/$name.src"
  if [ ! -s "$raw" ]; then
    echo "fetch   $name  (freesound $id)"
    code=$(curl -sL --max-time 900 -H "Authorization: Bearer $FREESOUND_TOKEN" \
      "https://freesound.org/apiv2/sounds/$id/download/" -o "$raw" -w "%{http_code}")
    if [ "$code" != "200" ]; then
      echo "  FAIL http $code -- an expired or missing token returns 401/403"
      rm -f "$raw"; failed=1; continue
    fi
  fi

  got=$(shasum -a 256 "$raw" | cut -d' ' -f1)
  if [ "$got" != "$sha" ]; then
    echo "  FAIL checksum for $name"
    echo "    expected $sha"
    echo "    got      $got"
    echo "    The uploader may have replaced the file. Do NOT ship this; verify by ear"
    echo "    and update sources.json deliberately if the new version is acceptable."
    failed=1; continue
  fi

  # -ss before -i seeks fast; the re-encode to FLAC is what makes the cut exact.
  ffmpeg -hide_banner -loglevel error -y -ss "$start" -t "$dur" -i "$raw" \
    -ar 48000 -sample_fmt s16 -c:a flac "$out"
  echo "  cut ${start}s +${dur}s  ->  $(du -h "$out" | cut -f1)"
done < <(python3 -c "
import json
for s in json.load(open('$MANIFEST'))['sounds']:
    t = s['trim']
    print(s['name'], s['freesound_id'], s['sha256'], t['start'], t['duration'])
")

if [ "$failed" = 0 ]; then
  echo
  echo "all sources ready in $OUT"
  echo "next: tools/synth-noise.sh && tools/process-audio.sh"
else
  echo
  echo "FAILURES -- do not process these"
fi
exit $failed
