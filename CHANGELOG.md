# Changelog

All notable changes to SleepSound are recorded here. Format follows
[Keep a Changelog](https://keepachangelog.com/). Dates are YYYY-MM-DD.

## [1.0.0-rc1] — 2026-05-19

First release candidate. Feature-complete for the Play Store launch
flow. Pre-launch tasks remaining: production signing key, hosted
privacy-policy URL, Play Console content rating + closed-test track,
on-device validation, optional gradle-wrapper commit.

### Added — audio engine
- 11 procedurally-generated sources: brown / pink / white / violet
  noise, rain, thunderstorm, ocean, fireplace, fan, dryer, TV static.
- Custom `AudioTrack` pipeline at 48 kHz PCM16 stereo with `LayerMixer`
  applying per-layer 800 ms linear fades.
- Master 1.5 s equal-power (sin) fade applied on play/stop/timer/focus.
- `MediaExtractor` + `MediaCodec` sample decoder (`SampleSource`)
  loads `assets/sounds/<id>.{ogg,opus,mp3,flac,wav}` if present and
  overrides the procedural fallback.

### Added — reliability
- Foreground media-playback service hardened for overnight survival.
- `AudioFocusManager` requesting `AUDIOFOCUS_GAIN`:
  `LOSS_TRANSIENT` → pause cleanly (not duck), `CAN_DUCK` → 30 % duck,
  `LOSS` → stop. Resumes audio on focus regain.
- `BecomingNoisyReceiver` pauses on headphone/BT disconnect (no
  speaker blast at 3 am).
- `MediaSessionCompat` + `MediaStyle` notification — lock-screen
  controls, BT media keys, Android Auto all wired.
- Partial wake lock acquired around the render loop (10 h safety
  timeout).
- Optional `BootReceiver` resumes the last mix after reboot (opt-in).
- In-app "Reliability" settings: battery-optimization exemption,
  OEM-killer guidance via `dontkillmyapp.com`, resume-on-reboot.

### Added — UI (Compose + Material 3, AMOLED-true-black)
- 3-column sound grid with haptic feedback + press-scale animation.
- `MixPanel` with per-row volume slider + mute toggle (remembers last
  gain), `AnimatedVisibility` fade/expand as sounds become active.
- `TimerSelector` with 15 / 30 / 60 / 90 min presets + custom value
  up to 12 h via dialog.
- `IdleDimmer` auto-dims the activity at 30 s of no touch.
- Settings sheet: Reliability / Purchases / About sections with
  divider, version pulled from `BuildConfig.VERSION_NAME`, privacy
  link, "Restore purchases" row.
- First-run onboarding carousel: what SleepSound is, battery-opt opt-in,
  OEM-killer link. Skippable. Persists shown flag.
- Circular stop button (media-player feel) instead of pill chip.
- Snackbar surfacing Play Billing purchase results.

### Added — monetization (Play Billing 8.3.0)
- Free tier (4 sounds): brown noise, white noise, rain, ocean.
- Premium tier (7 × $0.99): pink noise, violet noise, TV static,
  thunderstorm, dryer, fan, fireplace.
- All-sounds bundle SKU (`bundle_all_sounds`) at $3.99 unlocks every
  premium SoundId in one purchase.
- 30-second live preview on locked sounds, with a 5 s fade-out at
  expiry. Tile flips to `$0.99 →` pill afterwards; tap launches the
  Play Billing flow.
- `EntitlementStore` persists paid unlocks locally; Play queryPurchases
  runs on every `onResume` so cross-device purchases + refunds sync.
- Restore Purchases entry in Settings.

### Added — privacy
- Zero developer-side data collection. Manifest does **not** declare
  `INTERNET`.
- No analytics / Crashlytics / Sentry / ad SDKs in the dependency graph.
- `docs/privacy/index.html` ready to publish via GitHub Pages.

### Added — store-listing artifacts (`store/`)
- `STORE_LISTING.md` — title / short / full / what's-new copy,
  hand-counted against Google's character limits with sources cited.
- `PRIVACY.md` — six-section policy matching Google Play User Data
  policy requirements.
- `DATA_SAFETY.md` — copy-paste answers for the Console form.

### Added — tooling
- `tools/preview/` HTML mirrors of player / empty-state / settings /
  onboarding / feature-graphic at Pixel 7 + Play Store dimensions.
- `tools/preview/screenshot.mjs` — Playwright runner, portable across
  Playwright install locations.

### Known gaps (deferred)
- Gradle wrapper jar / scripts not committed — `./gradlew` won't
  bootstrap from a fresh clone. Android Studio will generate on first
  sync, or run `gradle wrapper --gradle-version 8.11.1` once.
- No unit / UI / screenshot tests yet.
- No CI configuration (GitHub Actions).
- No bundled sample audio recordings — sounds are procedural-only.
  `SampleSource` hook exists for future drops.
- Launcher icon is a basic crescent vector; a polished design pass is
  punch-list.
- Privacy-policy URL in `strings.xml` is still a placeholder.
