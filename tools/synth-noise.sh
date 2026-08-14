#!/usr/bin/env bash
#
# Generates the white and brown noise beds.
#
# These are synthesised rather than downloaded on purpose. A noise recording is
# strictly worse on every axis that matters here: it carries whatever room, mic
# and preamp the recordist had, it needs a licence checked, and it cannot loop
# perfectly. Generated noise is public domain by construction, is stationary so
# a loop is undetectable, and is exact.
#
# Two independent seeds feed the two channels. A single source copied to both
# would be correlated noise, which images as a hard point in the centre of the
# head rather than the wide, enveloping field the other ambiences have. The
# seeds are fixed so the output is reproducible.
#
# Length is SECONDS, which process-audio.sh then shortens by its crossfade. The
# crossfade is unnecessary for stationary noise but harmless, and going through
# the same path as everything else means one pipeline rather than two.
#
# Usage: tools/synth-noise.sh [OUT_DIR]

set -euo pipefail

OUT="${1:-build/audio-src-fetched}"
SECONDS_LEN=34        # 34s in, 30s out after the 4s crossfade
RATE=48000

# anoisesrc defaults to amplitude 1.0, which is full scale: the result clips
# (measured +5.5 dBTP) and its integrated loudness comes out POSITIVE, which
# loudnorm rejects outright -- measured_I is only valid over [-99, 0], so
# process-audio.sh dies with "Result too large" rather than any audio complaint.
# 0.1 leaves the beds quiet, unclipped, and comfortably inside that range; the
# pipeline's loudness match sets the level that actually ships anyway.
AMPLITUDE=0.1

command -v ffmpeg >/dev/null || { echo "ffmpeg not found"; exit 1; }
mkdir -p "$OUT"

for colour in white brown; do
  out="$OUT/${colour}_noise_loop.flac"
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "anoisesrc=color=${colour}:seed=11:amplitude=${AMPLITUDE}:sample_rate=${RATE}:duration=${SECONDS_LEN}" \
    -f lavfi -i "anoisesrc=color=${colour}:seed=22:amplitude=${AMPLITUDE}:sample_rate=${RATE}:duration=${SECONDS_LEN}" \
    -filter_complex "[0:a][1:a]amerge=inputs=2[o]" -map "[o]" \
    -ar "$RATE" -sample_fmt s16 -c:a flac "$out"

  # Spectral slope check. White must stay flat and brown must fall steeply; if a
  # future ffmpeg changes what anoisesrc means by a colour name, this catches it
  # rather than letting a mislabelled bed ship.
  lo=$(ffmpeg -hide_banner -nostats -i "$out" -af "lowpass=f=200,astats=metadata=1:reset=0" \
        -f null - 2>&1 | grep "RMS level dB" | tail -1 | awk '{print $NF}')
  hi=$(ffmpeg -hide_banner -nostats -i "$out" -af "highpass=f=4000,astats=metadata=1:reset=0" \
        -f null - 2>&1 | grep "RMS level dB" | tail -1 | awk '{print $NF}')
  slope=$(python3 -c "print(f'{$hi - ($lo):+.1f}')")

  case "$colour" in
    white) ok=$(python3 -c "print(1 if $slope > 5 else 0)")
           want="high band above low (flat spectrum, wide band has more energy)" ;;
    brown) ok=$(python3 -c "print(1 if $slope < -5 else 0)")
           want="high band well below low (-6dB/octave)" ;;
  esac

  printf "%-5s noise  %ss  %s  slope %s dB\n" "$colour" "$SECONDS_LEN" "$(du -h "$out" | cut -f1)" "$slope"
  if [ "$ok" != "1" ]; then
    echo "  FAIL: expected $want"
    exit 1
  fi
done

echo "noise beds ready in $OUT"
