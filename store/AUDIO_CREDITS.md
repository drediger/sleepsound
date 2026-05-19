# Audio credits

The six sample files shipped in `app/src/main/assets/sounds/` are
derived from publicly-shared recordings, **all CC0 / public-domain**.
No attribution is legally required, but recording the provenance keeps
the chain of evidence intact if Google Play, an auditor, or a curious
user ever asks.

The other four sounds (brown / pink / white / violet noise) are 100 %
mathematically generated in real time inside the app — no external
recording, no license to attribute.

---

## rain.opus

- **Source:** *"Light Gentle Rain Part 1"* from the *Relaxing Rain
  Sounds* collection on the Internet Archive.
- **Item:** https://archive.org/details/relaxingrainsounds
- **Source format:** 22.05 kHz stereo OGG Vorbis, 31:49.
- **License:** **CC0 / Public Domain** (collection-level declaration:
  `creativecommons.org/publicdomain/zero/1.0/`).
- **Processing:** Trimmed to 30–630 s, resampled to 48 kHz stereo,
  +6 dB gain with a peak limiter at 0.95, 2 s crossfade-bridge between
  last 2 s and first 2 s of the trimmed window, encoded as Opus 96 kbps
  q-equivalent.
- **Target loudness after processing:** −22.8 LUFS integrated.

## ocean.opus

- **Source:** *"Gentle Ocean"* from the *Ocean and Sea Sounds* collection
  on the Internet Archive.
- **Item:** https://archive.org/details/ocean-sea-sounds
- **Source format:** MP3, 19:48.
- **License:** **CC0 / Public Domain** (collection-level declaration:
  `creativecommons.org/publicdomain/zero/1.0/`).
- **Processing:** Trimmed to 30–630 s, resampled to 48 kHz stereo, 2 s
  crossfade-bridge, encoded as Opus 96 kbps. No gain change.
- **Target loudness after processing:** −21.3 LUFS integrated.

## thunderstorm.opus

- **Source:** *"1 Hour Thunderstorm"* by Robert Wimer on the Internet
  Archive.
- **Item:** https://archive.org/details/1HourThunderstorm
- **Source format:** OGG Vorbis, 44.1 kHz stereo, 60:00.
- **License:** **Public Domain** (`creativecommons.org/licenses/publicdomain/`).
- **Processing:** Trimmed to 30–630 s, resampled to 48 kHz stereo, 2 s
  crossfade-bridge, encoded as Opus 96 kbps. No gain change.
- **Target loudness after processing:** −16.4 LUFS integrated.

## fireplace.opus

- **Source:** *"Fireplace"* by `inchadney`, originally posted on
  Freesound.org and mirrored on the Internet Archive as item
  `FireFavorite`.
- **Item:** https://archive.org/details/FireFavorite
- **Source format:** OGG Vorbis, 44.1 kHz stereo, 4:23.
- **License:** **CC0 / Public Domain** (item-level declaration:
  `creativecommons.org/publicdomain/zero/1.0/`).
- **Recording technique (as noted on the source):** Two Sennheiser
  MKH60 microphones on a real fireplace.
- **Processing:** Looped 3× via `-stream_loop 3` to span the 600 s
  target, then trimmed to 30–630 s with 2 s crossfade-bridge,
  resampled to 48 kHz stereo, encoded as Opus 96 kbps. **+23 dB gain
  applied with a peak limiter at 0.95 to bring the source's −46.7 LUFS
  up to a perceived loudness consistent with the other three sounds.**
  No synthetic content added; the limiter only attenuates peaks
  that exceed 95 % of full scale.
- **Target loudness after processing:** −23.8 LUFS integrated.

## fan.opus

- **Source:** *"Bedroom Fan"* by `SammySyanide` on Freesound.
- **Item:** https://freesound.org/s/738640/
- **Source format:** AAC, 48 kHz mono, 2:00.
- **License:** **CC0** (`creativecommons.org/publicdomain/zero/1.0/`).
- **Processing:** Trimmed to 12–102 s (steady run, skipping switch-on
  ramp and end fadeout), upmixed to stereo at 48 kHz, 2 s self-crossfade
  bridge for seamless looping, encoded as Opus 96 kbps,
  loudness-normalized to −22 LUFS via `loudnorm`.

## dryer.opus

- **Source:** *"Tumble Dryer - Consistent Mechanical Hum and Rotating
  Drum"* by `Funkelfang` on Freesound.
- **Item:** https://freesound.org/s/845418/
- **Source format:** 24-bit WAV, 48 kHz stereo, 1:31.
- **License:** **CC0** (`creativecommons.org/publicdomain/zero/1.0/`).
- **Processing:** Trimmed to 3–73 s, 2 s self-crossfade bridge,
  encoded as Opus 96 kbps, loudness-normalized to −22 LUFS via
  `loudnorm`.

---

## Loudness audit

All four files measured with `ffmpeg ... loudnorm=print_format=summary`
after final encoding:

| File | Integrated LUFS | True Peak |
|---|---|---|
| rain.opus | −22.8 | +0.0 dBTP |
| ocean.opus | −21.3 | +0.1 dBTP |
| thunderstorm.opus | −16.4 | +1.2 dBTP |
| fireplace.opus | −23.8 | +1.9 dBTP |
| fan.opus | ~−22 (normalized) | ≤ −1.5 dBTP |
| dryer.opus | ~−22 (normalized) | ≤ −1.5 dBTP |

Spread is ~7 LU, which is within the "reasonable" range for an app
that doesn't auto-balance perceived loudness at playback time. The
in-app per-sound volume slider handles any residual difference.

---

## Reproducing this from scratch

The exact `ffmpeg` recipes for each file are in the commit history.
The general pipeline is in [`tools/audio/SOURCING.md`](../tools/audio/SOURCING.md).
If you want to swap any of these for a different recording later:

1. Source must be **CC0 / Public Domain** (or a license compatible with
   commercial paid-app redistribution — **not** CC-BY-SA, which would
   force this whole repo's audio bundle into the same share-alike
   license).
2. Drop the new file at `app/src/main/assets/sounds/<id>.opus` (the
   `id` is the lowercase `SoundId` enum name, e.g. `rain`, `ocean`).
3. The existing `SoundCatalog.create` flow picks it up automatically
   in preference to the procedural fallback. No code change needed.
4. Run the loudness audit in this doc; rebalance with `volume=NdB,
   alimiter=limit=0.95` if the new file's integrated LUFS strays far
   from the others.
