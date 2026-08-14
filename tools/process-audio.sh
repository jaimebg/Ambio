#!/usr/bin/env bash
#
# Turns the raw audio assets into the files the app ships.
#
# Loops get three transformations, in this order, all on uncompressed PCM so the
# result is encoded exactly once. The sources are already lossy Vorbis, so one
# generation of loss is unavoidable; two or three would not be.
#
#   1. optional high-pass   (forest only -- see HIGHPASS below)
#   2. seamless loop bake   (tail crossfaded over head, output trimmed by XFADE)
#   3. loudness match       (linear gain to TARGET_I, no dynamic compression)
#      + resample to 48k, then a single Opus encode.
#
# One-shots (the timer chime) skip 1 and 2 and get only the loudness match. They
# still need it: with the ambience at -30 LUFS the chime was 7.4dB hotter than
# the bed it plays over, and ChimePlayer uses its own MediaPlayer outside the
# master volume, so at half volume the gap was closer to 13dB.
#
# Opus rather than Vorbis: Homebrew's ffmpeg ships no libvorbis, only FFmpeg's
# markedly worse native encoder, and spending the one unavoidable lossy
# generation on that would be the worst possible use of it. minSdk is 31 and
# Ogg/Opus is native from API 29, so nothing in the app has to change.
#
# Every new sound added to the catalogue has to go through here.
#
# This script never writes into core/data/src/main/res/raw/ itself -- OUT defaults
# to build/processed-audio, which is gitignored and wiped by `./gradlew clean`.
# Once a run reports "all files passed", copy the results into place by hand:
#
#   cp build/processed-audio/*.ogg core/data/src/main/res/raw/
#
# Usage: tools/process-audio.sh [SRC_DIR] [OUT_DIR]

set -euo pipefail

# SRC must default to the untouched originals (audio-src/), never to the app's
# res/raw/ output directory. This pipeline is destructive-looking but not
# idempotent: res/raw/ already holds crossfaded, once-encoded Opus files, so
# pointing SRC at it would bake a second crossfade onto an already-seamed loop
# and Opus-encode an already-lossy file a second time. verify-loop.py cannot
# catch this either -- it compares the output against the intermediate this
# same run produced, so a second-generation file still "passes".
SRC="${1:-audio-src}"
OUT="${2:-build/processed-audio}"

XFADE=4                # seconds of loop crossfade, also how much shorter the output is
TARGET_I=-30           # LUFS. Set by cave: it peaks at -3.1dB and cannot go up.
TARGET_TP=-3           # dBTP
TARGET_LRA=11          # LU
BITRATE=128k

LOOPS=(cave_loop fireplace_loop forest_loop ocean_loop rain_loop)
ONESHOTS=(timer_chime)

# Per-file high-pass, in Hz. forest's loudest band is 63-125Hz at -20.6dB and it
# barely varies (3.7dB between the whole file and its quietest tenth) -- steady
# strong bass is not forest content, it is wind on the mic or distant traffic.
# rain's equivalent band sits 10dB lower. Costs 0.1dB of integrated loudness
# because K-weighting already discounts the region.
highpass_for() {
  case "$1" in
    forest_loop) echo 80 ;;
    *)           echo 0  ;;
  esac
}

command -v ffmpeg >/dev/null || { echo "ffmpeg not found"; exit 1; }
ffmpeg -hide_banner -encoders 2>/dev/null | grep -q libopus || {
  echo "this ffmpeg has no libopus encoder"; exit 1; }

mkdir -p "$OUT"
failed=0

# Loudness-match a lossless intermediate and encode it once. Shared by both
# paths because the only difference between a loop and a one-shot is what
# happened before this point.
normalise_and_encode() {
  local pre="$1" out="$2"

  local m
  m=$(ffmpeg -hide_banner -nostats -i "$pre" \
        -af "loudnorm=I=$TARGET_I:TP=$TARGET_TP:LRA=$TARGET_LRA:print_format=json" \
        -f null - 2>&1 | sed -n '/{/,/}/p')
  local mi mtp mlra mthresh moff
  read -r mi mtp mlra mthresh moff <<<"$(python3 -c "
import json
j=json.loads('''$m''')
print(j['input_i'],j['input_tp'],j['input_lra'],j['input_thresh'],j['target_offset'])")"
  echo "    measured    I=$mi  TP=$mtp  LRA=$mlra   gain $(python3 -c "print(f'{$TARGET_I-($mi):+.1f}')") dB"

  # linear=true is the point: a pumping ambience is worse than an uneven one.
  local log
  log=$(ffmpeg -hide_banner -nostats -y -i "$pre" -af \
    "loudnorm=I=$TARGET_I:TP=$TARGET_TP:LRA=$TARGET_LRA:measured_I=$mi:measured_TP=$mtp:\
measured_LRA=$mlra:measured_thresh=$mthresh:offset=$moff:linear=true:print_format=summary,\
aresample=48000" \
    -c:a libopus -b:a "$BITRATE" -vbr on -application audio "$out" 2>&1)

  # Abort rather than let it fall back to dynamic compression unnoticed.
  local ntype
  ntype=$(echo "$log" | sed -n 's/.*Normalization Type: *//p' | tr -d ' ')
  if [ "$ntype" != "Linear" ]; then
    echo "    FAIL: loudnorm fell back to '$ntype', not Linear"; failed=1
  fi

  local out_i out_lra codec rate
  out_i=$(ffmpeg -hide_banner -nostats -i "$out" -af ebur128=framelog=quiet -f null - 2>&1 \
            | sed -n 's/^ *I: *//p' | tail -1 | awk '{print $1}')
  out_lra=$(ffmpeg -hide_banner -nostats -i "$out" -af ebur128=framelog=quiet -f null - 2>&1 \
            | sed -n 's/^ *LRA: *//p' | tail -1 | awk '{print $1}')
  IFS=, read -r codec rate <<<"$(ffprobe -v error -select_streams a:0 \
      -show_entries stream=codec_name,sample_rate -of csv=p=0:nk=1 "$out")"

  echo "    result      I=$out_i LUFS  LRA=$out_lra LU  $codec @ $rate  ($(du -h "$out" | cut -f1))"
  python3 -c "import sys; sys.exit(0 if abs(float('$out_i')-($TARGET_I))<=1 else 1)" \
    || { echo "    FAIL: $out_i LUFS is outside $TARGET_I +/-1"; failed=1; }
  [ "$rate" = "48000" ] || { echo "    FAIL: sample rate $rate"; failed=1; }
  [ "$codec" = "opus" ] || { echo "    FAIL: codec $codec"; failed=1; }
}

for name in "${LOOPS[@]}"; do
  src="$SRC/$name.ogg"
  [ -f "$src" ] || { echo "missing $src"; exit 1; }

  # Duration from the DECODED sample count, never from container metadata.
  # ocean_loop.ogg advertises 45.000000s and decodes to 44.991293s; trusting the
  # header left acrossfade with only 3.99s of tail for a 4s crossfade, which
  # produced a seam with an 87dB jump in it.
  sr=$(ffprobe -v error -select_streams a:0 -show_entries stream=sample_rate -of csv=p=0 "$src")
  bytes=$(ffmpeg -v error -i "$src" -ac 1 -f f32le - | wc -c)
  ns=$((bytes / 4))
  d=$(python3 -c "print(f'{$ns/$sr:.6f}')")
  l=$(python3 -c "print(f'{$ns/$sr - $XFADE:.6f}')")
  hp=$(highpass_for "$name")

  echo "=== $name  ${d}s -> ${l}s$([ "$hp" != 0 ] && echo "  (high-pass ${hp}Hz)")"

  # The high-pass runs first so the filter settles on real material, and it must
  # precede loudnorm, which measures whatever comes out of here.
  pre_filter=""
  [ "$hp" != 0 ] && pre_filter="highpass=f=${hp}:poles=2,"
  ffmpeg -hide_banner -loglevel error -y -i "$src" -filter_complex \
    "[0:a]${pre_filter}asplit=2[a][b];\
     [a]atrim=start=${l},asetpts=PTS-STARTPTS[tail];\
     [b]atrim=end=${l},asetpts=PTS-STARTPTS[body];\
     [tail][body]acrossfade=d=${XFADE}:c1=qsin:c2=qsin,aresample=48000[o]" \
    -map "[o]" -c:a pcm_f32le "$OUT/$name.pre.wav"

  normalise_and_encode "$OUT/$name.pre.wav" "$OUT/$name.ogg"
  python3 tools/verify-loop.py "$OUT/$name.ogg" "$OUT/$name.pre.wav" || failed=1

  rm -f "$OUT/$name.pre.wav"
  echo
done

for name in "${ONESHOTS[@]}"; do
  src="$SRC/$name.ogg"
  [ -f "$src" ] || { echo "missing $src"; exit 1; }
  echo "=== $name  (one-shot: loudness only, no loop bake)"

  ffmpeg -hide_banner -loglevel error -y -i "$src" -af aresample=48000 \
    -c:a pcm_f32le "$OUT/$name.pre.wav"
  normalise_and_encode "$OUT/$name.pre.wav" "$OUT/$name.ogg"
  rm -f "$OUT/$name.pre.wav"
  echo
done

[ "$failed" = 0 ] && echo "all files passed" || echo "FAILURES -- do not ship these files"
exit $failed
