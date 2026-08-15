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

## Sourced as CC0, originals not retained (6)

`rain_loop`, `fireplace_loop`, `forest_loop`, `ocean_loop`, `cave_loop` and
`timer_chime`.

These predate the `sources.json` manifest. They were obtained from CC0 /
public-domain sources, but the originals were not kept and the specific upstream
files can no longer be identified, so there is no per-file record to point at.
The processed `.ogg` files in `core/data/src/main/res/raw/` are the only
surviving artefact.

This is recorded as an open gap rather than papered over. Re-sourcing these six
from traceable originals would close it, at the cost of changing how the app
sounds and regenerating every store screenshot.
