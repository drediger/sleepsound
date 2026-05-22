# Changelog

All notable changes to Sleep Soundly are recorded here. Format follows
[Keep a Changelog](https://keepachangelog.com/). Dates are YYYY-MM-DD.

## [1.0.0-rc3] — 2026-05-22

Pre-launch polish pass. Three rounds of independent agent audits (UX,
code, marketing/listing, privacy) plus on-device reproduction surfaced
~30 items spanning crash risk, false marketing claims, accessibility,
billing UX, and copy. This RC trues-up every claim against the shipped
code and lands the items that don't need post-launch test data.

### Fixed — crash / policy risk
- `SoundTile.CountdownBadge.label` now formats with `Locale.US` instead
  of the system default. The pre-launch report's Robo crawler runs in
  random locales (incl. `ar-SA`); the previous bare `"%d:%02d".format()`
  would throw on the bidi-formatter path for any locked tile in preview.
- `PlaybackController.stopPlayback` now clears `_timerMinutes` along
  with `_timerExpiryMs`. Live reproduction in iter-2: hitting Stop with
  a 30 m timer running left the pill in "Timer 30m" pending state, and
  the next sound tap re-armed the timer at the full 30 m.
- `notifyServiceStopped(preserveTimer: Boolean = false)` — audio-focus
  loss (`onFocusLostPermanent`) and `BecomingNoisyReceiver` now preserve
  `_timerMinutes` so a brief phone call or a headphone yank doesn't wipe
  a multi-hour sleep timer.
- `AudioEngine.WAKE_LOCK_TIMEOUT_MS` bumped from 10 h → 13 h so the
  documented 12 h custom-timer ceiling actually plays for 12 hours.
- `MediaSessionController` reads title + artist from `R.string` instead
  of the hardcoded English literals `"SleepSound"` / `"Playing N sounds"`.
  Lockscreen, Android Auto, and Bluetooth car decks now show
  `Sleep Soundly` and the localized count, single-sourced from
  `strings.xml`.
- `AndroidManifest.application@tools:targetApi` aligned to `35` (was
  `34`) to match `compileSdk` + `targetSdk`.

### Fixed — visual / a11y
- Inactive tile labels promoted to `SoftWhite` (from `DimGrey #A8B0C0`
  which read ~3.7:1 on `SurfaceDark` — below WCAG AA body-text 4.5:1).
  Active vs inactive is now conveyed by the icon tint + the 2 dp
  border, not by the label color.
- Active-tile border bumped from 1 dp → 2 dp for a more decisive
  "this is in your mix" cue at a glance.
- Lock badge enlarged from 12 dp → 16 dp so locked-vs-off is readable
  without leaning into the screen.
- Onboarding page-indicator inactive dot promoted from `DimmerGrey
  #2D3548` (~1.5:1 on PureBlack — invisible) to `DimGrey` (~8.5:1,
  clearly visible). Active dot promoted to `SoftWhite`.
- MixPanel mute icon hit-target bumped 32 dp → 48 dp (Material/WCAG
  minimum touch target). Icon stays 18 dp.
- Onboarding moon icon swapped from stock `Icons.Default.Bedtime` to
  the in-house `ic_notification_moon` crescent vector tinted
  `SoftWhite` for brand consistency with the launcher + notification.

### Fixed — lockscreen / notification
- Lockscreen media controls now show **Stop**, not Pause. Dropped
  `ACTION_PAUSE` from `MediaSessionController.setActions()` so SystemUI
  picks STOP as the visible compact-view action. PAUSE callbacks are
  still routed for BT media keys, just not surfaced as a tappable chip
  — sleep users overwhelmingly want Stop overnight.
- `POST_NOTIFICATIONS` system prompt no longer fires on first
  composition (right after onboarding's "No tracking" pillar lands —
  felt like a contradiction). Now deferred until the user starts their
  first sound, and prefaced with a snackbar action: *"Lets you stop
  audio from the lockscreen — not for marketing." [Allow]*.

### Restored — settings sheet
- OEM-killer guidance row (silently regressed in commit `a8bcd74`).
  Reliability section now includes a `{Manufacturer} settings` row
  linking to `https://dontkillmyapp.com/{manufacturer}`. Fixes the
  promise made on onboarding p2 and in `STORE_LISTING.md`, `PRIVACY.md`,
  `CHANGELOG.md` rc1, and `CLAUDE.md`. Falls back to the index page
  if `Build.MANUFACTURER` is empty (rare emulator case).
- Open-source license attribution. About row now opens an in-app
  dialog rendering `assets/licenses.txt` (bundled NOTICE-equivalent
  for AndroidX / Material3 / Play Billing). Closes Apache-2 §4(d)
  without pulling in `play-services-oss-licenses` (which would have
  added a GMS dependency to a no-network app).

### Changed — settings sheet
- Sheet content wrapped in `Modifier.heightIn(min = 480.dp)` so the
  bundle row vanishing after the 5 s billing timeout no longer snaps
  the sheet shorter. Sheet height now stable through the timeout
  transition.
- `settings_restore_subtitle` changed from "Restore premium unlocks
  from your Google account" to "Restore purchases on this device" —
  removes the "Google account" collision with the "No account" pillar
  in About.
- Privacy-policy row subtitle changed from the raw URL to *"Read what
  we collect (nothing)"*. URL still opens via `Intent.ACTION_VIEW` on
  tap; the row just no longer looks like raw text.
- Deleted dead master-volume code path
  (`PlaybackController._masterVolume`, `setMasterVolume`,
  `KEY_MASTER_VOLUME`; `AudioEngine.setMasterVolume` + its render-loop
  multiplier; `SleepAudioService` collector). The value defaulted to
  `1f` and was never written by any UI; system volume rocker already
  covers this need.

### Changed — billing
- Locked tiles now show the cached price (e.g. `$0.99`) beneath the
  label when known. Lock badge alone was the single largest conversion
  blocker per the first-time-user audit. Suppressed during preview
  (countdown is the active signal) and during Buy chip (price already
  in the chip).
- `BillingManager.launchPurchaseFlow*` guards with a
  `@Volatile purchaseInFlight` flag against double-taps of the Buy
  chip / bundle row while Play sheet is opening. Cleared by the
  `PurchasesUpdatedListener` on every response code.
- Pending-purchase handling: `Purchase.PurchaseState.PENDING` (slow
  card-auth flow) now emits `PurchaseResult.Pending` instead of being
  silently dropped at the `purchaseState != PURCHASED` guard. UI shows
  the new *"Purchase pending — we'll unlock it when it clears"*
  snackbar. The actual unlock still happens via `queryExistingPurchases`
  on the next `onResume`.
- `BillingResult.responseCode` is now categorized into offline-vs-other
  failures. `Failure(offline = true)` triggers the new
  *"Couldn't reach Google Play — check your connection"* snackbar for
  `SERVICE_UNAVAILABLE`, `NETWORK_ERROR`, `SERVICE_DISCONNECTED` instead
  of the generic "Purchase failed — try again."
- `Failure.reason` logged at WARN before being routed to the snackbar
  so logcat captures the actual response code for debugging.
- `launchPurchaseFlow*` failure paths route through `emitFailure()`
  too — previously some early-returns (no client / no ProductDetails)
  emitted Failure with no log.

### Changed — player UX
- Status header bumped from 14 sp Light → 16 sp Normal. Persistent
  feedback ("Playing 3 sounds") now carries weight comparable to the
  settings gear instead of being out-weighed by it.
- Stop chip enlarged from 48 dp → 56 dp, icon 22 dp → 26 dp. Matches
  the visual weight of the Timer pill on the opposite corner; Stop is
  the primary overnight action.
- 10-tile grid orphan no longer center-bracketed with empty cells.
  Natural row-by-row fill puts the orphan at row-4 col-1; survives
  vertical compression cleanly when MixPanel grows to 4+ rows.
- MixPanel capped at `heightIn(max = 220.dp)` with vertical scroll.
  Live-reproduced in iter-2 (6 active sounds): the tile grid was
  squeezed below 3 rows. Cap keeps the grid usable; rows beyond the
  cap scroll inside the panel.
- Onboarding p2 body changed from "Android may stop Sleep Soundly
  mid-night to save battery" to "Android sometimes silences sleep
  apps overnight. Tap below to keep audio playing until your timer
  ends." — concrete about what happens and what the button does.
- Player resume copy changed from "Tap a sound to resume" to "Last
  mix saved — tap any sound to start" — warmer, less system-message.

### Fixed — store + privacy documentation
- `store/STORE_LISTING.md`: removed false claims about screen
  auto-dimming (IdleDimmer was removed in rc2). Replaced
  "crossfade-bridged" with the actual loop behavior; specified
  durations (rain/ocean/thunderstorm/fireplace 10 min,
  fan/dryer ~1.5 min) instead of the misleading "multi-minute".
  Re-worded the OEM-battery-killer bullet to the now-true
  "link to dontkillmyapp.com". Added the `$3.99 bundle_all_sounds`
  SKU to the in-app-purchase decision row.
- `docs/privacy/index.html`: dropped the "Whether you have opted into
  'resume on reboot'" data bullet (feature removed in rc2). Migrated
  CSS palette from neutral greys to the moonlit-night slate-blue
  tokens for visual consistency with the app. Added one-line GDPR
  Article 15-17 acknowledgement (rights moot because we collect no
  data, but the words inoculate against lazy regulator questions).
- `store/PRIVACY.md`: Contact section now resolves to
  `djrediger@gmail.com` directly instead of deferring to the hosted
  HTML.
- `store/DATA_SAFETY.md`: dropped the "if Pro tier ships in v1"
  conditional in the Financial-info row — IAPs shipped.

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

