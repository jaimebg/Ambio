#!/usr/bin/env bash
#
# Generates the end-of-plan success chime: three rising sine notes (C5, E5, G5)
# with a short decay, mono, 48 kHz, ~1.4 s.
#
# Synthesised rather than sourced for the same reason as the noise beds: it is
# public domain by construction and needs no licence record. Every parameter is
# fixed, so the output is reproducible. process-audio.sh loudness-matches it
# like timer_chime (one-shot path: no loop bake).
#
# Usage: tools/synth-success-chime.sh [OUT_DIR]

set -euo pipefail

OUT="${1:-build/audio-src-fetched}"
RATE=48000
AMPLITUDE=0.2   # well under full scale; the pipeline sets the shipped level

command -v ffmpeg >/dev/null || { echo "ffmpeg not found"; exit 1; }
mkdir -p "$OUT"

out="$OUT/success_chime.flac"

# Each note: a sine with a soft second harmonic, faded out over its length.
# The second and third notes are delayed so the three ring in sequence.
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "sine=frequency=523.25:sample_rate=${RATE}:duration=1.4" \
  -f lavfi -i "sine=frequency=659.25:sample_rate=${RATE}:duration=1.2" \
  -f lavfi -i "sine=frequency=783.99:sample_rate=${RATE}:duration=1.0" \
  -filter_complex "
    [0:a]volume=${AMPLITUDE},afade=t=out:st=0.05:d=1.3[n0];
    [1:a]volume=${AMPLITUDE},afade=t=out:st=0.05:d=1.1,adelay=180[n1];
    [2:a]volume=${AMPLITUDE},afade=t=out:st=0.05:d=0.9,adelay=360[n2];
    [n0][n1][n2]amix=inputs=3:normalize=0,aformat=channel_layouts=mono[o]" \
  -map "[o]" -ar "$RATE" -sample_fmt s16 -c:a flac "$out"

dur=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out")
printf "success chime  %ss  %s\n" "$dur" "$(du -h "$out" | cut -f1)"
python3 -c "import sys; d=float('$dur'); sys.exit(0 if 1.2 < d < 1.6 else 1)" || {
  echo "  FAIL: expected a chime between 1.2 and 1.6 s"; exit 1; }

echo "success chime ready in $OUT"
