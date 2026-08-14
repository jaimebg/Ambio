# audio-src

The pipeline's *input*. `core/data/src/main/res/raw/` is its *output*.

Everything in `res/raw/` is generated from here by `tools/process-audio.sh`
(high-pass where needed, seamless loop bake, loudness match, Opus encode). The
sources are kept — rather than treating the shipped `.ogg` files as the source
of truth — so the pipeline stays reproducible: a processed asset can always be
regenerated from the original, and running the pipeline again never means
feeding it its own output.

Do not run `tools/process-audio.sh` against `core/data/src/main/res/raw/`.

## Two kinds of source

**Committed here as audio.** `rain`, `fireplace`, `forest`, `ocean`, `cave` and
`timer_chime`, as `.ogg`. These predate the manifest below and their originals
are no longer retrievable, so the files themselves are the only record.

**Recorded in `sources.json`, not committed.** The five loops added later —
`stream`, `crickets`, `wind`, `birds`, `cafe`. The manifest holds the
Freesound id, uploader, licence, a `sha256` of the uploaded original, and the
window to cut from it. Storing ~26MB of FLAC in git bought nothing that the
manifest does not, since these files are still on Freesound.

**Generated, not stored at all.** `white_noise` and `brown_noise`, produced by
`tools/synth-noise.sh` from fixed seeds.

## Rebuilding the non-committed sources

```bash
export FREESOUND_TOKEN=...        # see below
tools/fetch-sources.sh            # downloads, verifies sha256, cuts the windows
tools/synth-noise.sh              # generates the two noise beds
tools/process-audio.sh            # -> build/processed-audio
cp build/processed-audio/*.ogg core/data/src/main/res/raw/
```

Both scripts write to `build/audio-src-fetched/`, which is gitignored.

### The credential requirement

`fetch-sources.sh` needs a Freesound **OAuth2 access token**. An API key alone
reaches only search and the lossy previews; original files require OAuth2. This
is the real cost of keeping the audio out of the repo — without a token, the five
Freesound-sourced loops cannot be rebuilt from scratch, though the already-built
assets in `res/raw/` are unaffected and ship fine.

1. Register an app at <https://freesound.org/apiv2/apply/>, leaving the callback
   URL **empty**.
2. Visit
   `https://freesound.org/apiv2/oauth2/authorize/?client_id=$ID&response_type=code`
   and authorize. With no callback registered, Freesound displays the code
   on screen.
3. Exchange it:
   ```bash
   curl -X POST https://freesound.org/apiv2/oauth2/access_token/ \
     -d client_id=$ID -d client_secret=$SECRET \
     -d grant_type=authorization_code -d code=$CODE
   ```
4. `export FREESOUND_TOKEN=<access_token>`. Tokens last 24 hours.

### Why the checksums

A Freesound uploader can replace a sound's file in place. Without the hash a
silent substitution upstream would flow into a shipped asset, and the only
symptom would be that the app sounds different. `fetch-sources.sh` treats a
mismatch as a hard failure.

## Choosing the window

`tools/select-window.py` picks which stretch of a long recording becomes the
loop, and the result is written into `sources.json` as `trim.start`. It minimises
level wander, one-off events and sub-80Hz rumble, so the winner is the most
featureless window available.

It also requires the window to begin and end quietly, since `process-audio.sh`
crossfades the tail over the head. A candidate that ignored this put an event
across the seam and `verify-loop.py` correctly rejected it.
