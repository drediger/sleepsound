# Changelog

All notable changes to Sleep Soundly are recorded here. Format follows
[Keep a Changelog](https://keepachangelog.com/). Dates are YYYY-MM-DD.

## [1.0.0-rc2] — 2026-05-22

Second release candidate. On-device review polish + billing UX fixes +
full Play Console IAP wiring validated end-to-end.

### Removed
- `IdleDimmer`. Interacted badly with the modal Settings sheet (taps on
  the sheet didn't reset the dimmer's idle timer, so the page faded
  to black with no way to recover) and the OS screen-timeout already
  handled its intended purpose.
- Resume-on-reboot opt-in (`BootReceiver`, manifest entry, settings
  toggle, `RECEIVE_BOOT_COMPLETED` permission). The 8-hour guard was
  the actual safety mechanism; the toggle just confused users.
  Last-mix persistence stays — selected sounds, layer gains, master
  volume restore on next launch.
- Obsolete `-keep class com.sleepsound.BootReceiver` proguard rule
  referencing the deleted class. R8 was silently warning every
  release build.

### Changed — visual identity
- New "moonlit-night" palette harmonized with the slate-and-cream
  launcher icon. PureBlack stays as the background (four-pillar AMOLED
  promise); surfaces and content shifted to cool slate-blue. Token
  values: `SurfaceDark #1A2236`, `DimGrey #A8B0C0`, `SoftWhite
  #E0E4EC`. Two-tier text scheme — primary text (titles, status, sound
  names, active tile labels, button labels) uses `SoftWhite`,
  secondary text (subtitles, captions, inactive tile labels, dropdown
  items) uses `DimGrey`.
- Every sound tile now gets a `SurfaceDark` card background. Inactive
  tiles previously had transparent `PureBlack` bg and read as floating
  glyphs.
- Active tile border bumped to `SoftWhite` (was `IconGrey`, same value
  as inactive text — couldn't distinguish active from inactive at a
  glance).
- Lock badges, timer dropdown items, stop button, and settings gear
  promoted to `SoftWhite` — readable as primary controls.

### Changed — UX fixes from on-device review
- Settings → "Allow background playback" row shows a checkmark and
  "Allowed — Android won't kill audio overnight" subtitle once
  granted, with an `ON_RESUME` lifecycle observer so it reflects state
  when the user returns from the system dialog. Same pattern applied
  to the onboarding battery-exemption pill, which previously never
  flipped to its fulfilled state.

### Changed — billing
- Bundle purchase fires a dedicated `PurchaseResult.BundleSuccess` so
  the snackbar reads "All sounds unlocked" instead of an arbitrary
  single-sound name picked from the bundle's expanded `SoundId` set
  (set iteration order isn't deterministic).
- `launchPurchaseFlowForProduct` emits `PurchaseResult.Failure` when
  the billing client isn't ready or product details weren't cached.
  User sees the existing "Purchase failed" snackbar instead of a
  silent no-op tap.
- `EntitlementStore.unlockMany` batches a bundle purchase into one
  `SharedPreferences` write (was six separate writes).
- Settings bundle row hides itself if `BillingClient` hasn't returned
  the price after 5 s — instead of "Bundle price loading…" stuck
  forever on devices without Google Play Services.
- Validated end-to-end on a Galaxy S25 via license-tester card:
  bundle purchase ✓, all 6 entitlements unlocked ✓, auto-restore via
  `queryExistingPurchases` on cold launch after `pm clear` ✓, manual
  Restore shows "Nothing to restore" when already unlocked ✓.

### Changed — IAP product registration (Play Console)
- Seven one-time products registered and Active: `sound_pink_noise`,
  `sound_violet_noise`, `sound_thunderstorm`, `sound_dryer`,
  `sound_fan`, `sound_fireplace` at $0.99 each; `bundle_all_sounds`
  at $3.99. All non-consumable, "Digital app sales" tax category,
  173 countries / regions, "All ages" rating.
- IAP product icon archived at `store/iap-icon.png` (512×512 32-bit
  PNG generated from the moon artwork via ImageMagick).

### Changed — store assets
- Refreshed Play Store device screenshots in `store/device-screenshots/`
  with the moonlit palette.
- Reconciled `STORE_LISTING.md`, `DATA_SAFETY.md`, `PRIVACY.md`,
  `QA_MATRIX.md`, `PLAN.md`, `README.md`, `CLAUDE.md` with the
  dropped resume-on-reboot feature.

### Fixed
- `SoundTier.kt` math comment was "7 x $0.99 = $6.93"; there are 6
  premium sounds, so the bundle's discount math is "6 x $0.99 = $5.94"
  (vs $3.99 bundle = ~33% savings).

## [1.0.0-rc1] — 2026-05-19

First release candidate. Feature-complete for the Play Store launch
flow. Pre-launch tasks remaining: production signing key, hosted
privacy-policy URL, Play Console content rating + closed-test track,
on-device validation, optional gradle-wrapper commit.

### Added — audio engine
- 10 procedurally-generated sources: brown / pink / white / violet
  noise, rain, thunderstorm, ocean, fireplace, fan, dryer.
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
- In-app "Reliability" settings: battery-optimization exemption,
  OEM-killer guidance via `dontkillmyapp.com`.

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
- First-run onboarding carousel: what Sleep Soundly is, battery-opt opt-in,
  OEM-killer link. Skippable. Persists shown flag.
- Circular stop button (media-player feel) instead of pill chip.
- Snackbar surfacing Play Billing purchase results.

### Added — monetization (Play Billing 8.3.0)
- Free tier (4 sounds): brown noise, white noise, rain, ocean.
- Premium tier (6 × $0.99): pink noise, violet noise,
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
- No Compose UI / screenshot / instrumented tests. JVM unit tests
  cover procedural audio + SoundId invariants only.
- No CI configuration (GitHub Actions).
- No Crashlytics integration — first user crash will be the only
  signal until that's added.

