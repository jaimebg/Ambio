# Attribution

Ambio ships thirteen audio files. All are CC0 / public domain. This file records
what is known about each, including where the record is incomplete.

## Traceable to a source (5)

Recorded in [`audio-src/sources.json`](audio-src/sources.json) with the
Freesound id, uploader, licence and a sha256 of the uploaded original. The
checksum is enforced by `tools/fetch-sources.sh`, which treats a mismatch as a
hard failure — a Freesound uploader can replace a file in place, and without the
hash a silent substitution upstream would flow into a shipped asset.

| Sound | Freesound id | Uploader | Licence |
|---|---|---|---|
| `stream_loop` | 220528 | turbostream | CC0 1.0 |
| `crickets_loop` | 425425 | AnthonyRamirez | CC0 1.0 |
| `wind_loop` | 521736 | Fission9 | CC0 1.0 |
| `birds_loop` | 234315 | nick121087 | CC0 1.0 |
| `cafe_loop` | 625112 | sonically_sound | CC0 1.0 |

## Generated, not recorded (2)

`white_noise_loop` and `brown_noise_loop` are produced by
`tools/synth-noise.sh` from fixed seeds. They are public domain by construction:
there is no recording and no third party involved.

## Sourced as CC0, upstream provenance not retained (6)

`rain_loop`, `fireplace_loop`, `forest_loop`, `ocean_loop`, `cave_loop` and
`timer_chime`.

These predate the `sources.json` manifest. They were obtained from CC0 /
public-domain sources, but which upstream file each came from — the specific
source, its uploader and a licence URL — was never recorded, and cannot be
reconstructed now.

The source audio itself is not lost: [`audio-src/`](audio-src/README.md) tracks
an unprocessed `.ogg` master for each of these six, distinct from and larger
than the versions shipped in `core/data/src/main/res/raw/` (`cave_loop`, for
example, is 1,626,352 bytes in `audio-src/` versus 1,322,332 bytes shipped).
What is missing is the upstream record, not the audio.

This is recorded as an open gap rather than papered over. Re-sourcing these six
from traceable originals would close it, at the cost of changing how the app
sounds and regenerating every store screenshot.
