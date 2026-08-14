"""Choose which stretch of a long recording becomes the loop.

A field recording is not uniformly usable: it drifts, a car goes past, the
recordist handles the mic at the start and end. Picking the window by
measurement beats taking the first N seconds, and records why in sources.json.

The score punishes short-term level wander, one-off events, and sub-80Hz rumble,
so the winner is the most featureless window available.

The window must also BEGIN and END quietly, because process-audio.sh crossfades
the tail over the head. If either end carries an event, the crossfade sums two
different signals and leaves a step at the seam that verify-loop.py rejects --
a dropped thunder source failed exactly this way, with a seam step of 5.0e-02
against a p99.99 of 8.2e-03.

Usage: tools/select-window.py SOURCE TARGET_SECONDS
Prints: START_SECONDS DURATION_SECONDS  note
"""
import subprocess, sys
import numpy as np

SR = 24000
HOP = 0.5          # seconds per analysis frame
EDGE_S = 3.0       # ignore the head and tail of the source: handling noise
SEAM_S = 2.0       # how much of each end must sit in the quiet floor


def decode(path):
    p = subprocess.run(f'ffmpeg -v error -i "{path}" -ac 1 -ar {SR} -f f32le -',
                       shell=True, capture_output=True)
    return np.frombuffer(p.stdout, dtype=np.float32).astype(np.float64)


def frames(x):
    n = int(HOP * SR)
    nf = x.size // n
    w = x[:nf * n].reshape(nf, n)
    spec = np.abs(np.fft.rfft(w * np.hanning(n), axis=1)) ** 2
    freq = np.fft.rfftfreq(n, 1 / SR)
    broad = np.maximum(spec.sum(axis=1), 1e-20)
    low = np.maximum(spec[:, freq < 80].sum(axis=1), 1e-20)
    return 10 * np.log10(broad), 10 * np.log10(low / broad)


def pick(path, target_s):
    x = decode(path)
    dur = x.size / SR
    if dur < target_s + 2 * EDGE_S:
        return 0.0, dur, "source too short to trim; using it whole"

    b_db, rumble_db = frames(x)
    per_s = 1.0 / HOP
    w = int(target_s * per_s)
    seam = max(1, int(SEAM_S * per_s))
    lo, hi = int(EDGE_S * per_s), len(b_db) - int(EDGE_S * per_s) - w
    if hi <= lo:
        return 0.0, min(dur, target_s), "no room after edge trim; using head"

    best, best_score, best_note = None, None, ""
    fallback, fb_score, fb_note = lo, None, ""

    for s in range(lo, hi + 1):
        seg = b_db[s:s + w]
        rum = rumble_db[s:s + w]
        head, tail = seg[:seam].mean(), seg[-seam:].mean()
        local = np.median(seg)
        quiet_ends = head <= local and tail <= local

        score = (seg.std() * 2.0
                 + (np.percentile(seg, 99) - local)
                 + (local - np.percentile(seg, 1))
                 + max(0.0, rum.mean() + 12.0)
                 + max(0.0, head - local) + max(0.0, tail - local))
        note = f"wander {seg.std():.2f}dB  rumble {rum.mean():.1f}dB"

        if fb_score is None or score < fb_score:
            fallback, fb_score, fb_note = s, score, note
        if quiet_ends and (best_score is None or score < best_score):
            best, best_score, best_note = s, score, note

    if best is None:
        return fallback / per_s, target_s, fb_note + "  [no window had quiet ends]"
    return best / per_s, target_s, best_note


if __name__ == "__main__":
    start, length, note = pick(sys.argv[1], float(sys.argv[2]))
    print(f"{start:.2f} {length:.2f} {note}")
