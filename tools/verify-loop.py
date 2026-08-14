#!/usr/bin/env python3
"""Verifies one processed loop. Exits non-zero on any failed check.

Two of these checks exist because the obvious one is not enough:

  - The seam is measured twice. A 250ms RMS window cannot see a one-sample
    click, and a per-sample difference cannot see a level step. Both matter.
  - The RMS seam is judged against the file's OWN largest interior jump, not
    a fixed threshold. ocean swings 16dB between wave crest and trough by
    nature; a fixed threshold would either fail it or be too loose for
    everything else.
"""
import subprocess
import sys

import numpy as np

SR = 48000


def decode(path):
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
        capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


SILENT_DBFS = -80.0   # below this a window is not real material -- see below


def main(path, reference_wav):
    x = decode(path)
    failures = []
    # The reference is the lossless intermediate this file was encoded from, not
    # a duration in seconds. Comparing against it is what actually proves Opus
    # round-tripped sample-exactly; comparing against seconds would also fold in
    # resampler rounding and container metadata, and ocean_loop.ogg proved that
    # container metadata can simply be wrong (it claims 45.000s, decodes 44.991s).
    ref = decode(reference_wav)
    if len(x) != len(ref):
        failures.append(f"length {len(x)} samples, reference {len(ref)} "
                        f"(delta {(len(x) - len(ref)) / SR * 1000:+.2f} ms)")

    xx = np.concatenate([x, x])          # simulate one wrap

    # Seam in RMS, 250ms windows.
    w = int(SR * 0.25)
    k = len(xx) // w
    r = 20 * np.log10(np.maximum(np.sqrt((xx[:k * w].reshape(k, w) ** 2).mean(axis=1)), 1e-12))
    d = np.abs(np.diff(r))
    j = len(x) // w

    # Only compare against jumps between windows that are both real material.
    # ocean_loop.ogg is noise-gated to digital zero in its quiet passages, and a
    # jump out of a -126dBFS window is ~90dB. Left in, that baseline is so large
    # that any seam whatsoever passes, which is worse than no check at all.
    loud = r > SILENT_DBFS
    real_pair = loud[:-1] & loud[1:]
    real_pair[[j - 1, j]] = False
    interior = d[real_pair]
    seam_rms = d[j - 1]
    seam_is_silent = not (loud[j - 1] and loud[j])
    if len(interior) == 0:
        failures.append("no non-silent window pairs to compare the seam against")
    elif seam_is_silent:
        print(f"    note: seam falls in a gated/silent passage; RMS check not "
              f"meaningful here, relying on the per-sample check")
    elif seam_rms > interior.max():
        failures.append(f"seam RMS jump {seam_rms:.2f} dB exceeds interior max "
                        f"{interior.max():.2f} dB")
    interior_max = interior.max() if len(interior) else float("nan")

    # Seam sample-to-sample. A click is a single-sample outlier.
    diff = np.abs(np.diff(xx))
    seam_step, p9999 = diff[len(x) - 1], np.percentile(diff, 99.99)
    if seam_step > p9999:
        failures.append(f"seam sample step {seam_step:.3e} exceeds p99.99 {p9999:.3e}")

    print(f"    length      {len(x)} samples = {len(x) / SR:.6f}s")
    print(f"    seam RMS    {seam_rms:.2f} dB   (interior max {interior_max:.2f}, p95 "
          f"{np.percentile(interior, 95):.2f})")
    print(f"    seam sample {seam_step:.3e}  (file p99.99 {p9999:.3e})")

    for f in failures:
        print(f"    FAIL: {f}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
