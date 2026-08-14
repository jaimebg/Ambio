# audio-src

The unprocessed CC0 originals for Ambio's ambience loops and the timer chime.

Everything in `core/data/src/main/res/raw/` is generated from these files by
`tools/process-audio.sh` (high-pass where needed, seamless loop bake, loudness
match, Opus encode). These sources are kept — rather than treating the shipped
`.ogg` files as the source of truth — so the pipeline stays reproducible: a
processed asset can always be regenerated from the original, and running the
pipeline again never means feeding it its own output.

Do not run `tools/process-audio.sh` against `core/data/src/main/res/raw/`.
That directory is the pipeline's *output*; these files are its *input*.
