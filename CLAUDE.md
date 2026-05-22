# Notes for Claude working in this repo

SleepSound — AMOLED-friendly sleep-sound app. Primary docs:
[README.md](README.md), [PLAN.md](PLAN.md), [BUILDING.md](BUILDING.md),
[CHANGELOG.md](CHANGELOG.md), [store/](store/),
[tools/audio/SOURCING.md](tools/audio/SOURCING.md).

Notes below are session-learned gotchas not obvious from those docs or
the code.

---

## Don't touch the phone while the user is using it

`adb shell input ...` mutates whatever screen the user is on right now
— including their own. Only run input commands when the user has
explicitly handed off, or you're iterating without them present.
`adb exec-out screencap -p` is fine (read-only).

---

## Toolchain (Intel Mac, May 2026)

| Tool | Path |
|---|---|
| JDK 17 | `/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` |
| Android SDK | `~/Android/sdk` (`adb`, `apksigner` under here) |
| `ffmpeg` | `/usr/local/bin/ffmpeg` (8.x; **no libvorbis** — encode samples as Opus) |

`~/.zshrc` sets `JAVA_HOME` / `ANDROID_HOME` / `PATH`. Bash tool calls
don't share env — export inline when needed:

```bash
export JAVA_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

## Build / install / launch (debug)

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop io.github.drediger.sleepsoundly.debug
adb shell am start -n io.github.drediger.sleepsoundly.debug/com.sleepsound.MainActivity
adb exec-out screencap -p > /tmp/x.png   # then Read tool
```

- **Debug applicationId**: `io.github.drediger.sleepsoundly.debug` (the
  `.debug` suffix comes from `applicationIdSuffix`).
- **Release applicationId**: `io.github.drediger.sleepsoundly`.
- **Kotlin namespace** is still `com.sleepsound` — launcher class is
  `com.sleepsound.MainActivity`. `applicationId` was renamed for Play
  Console without refactoring `package com.sleepsound.*` declarations.
- `adb shell pm clear <pkg>` wipes onboarding + last-mix + entitlements
  — cruel if the user is mid-test.

## Release AAB → sambashare

```bash
./gradlew :app:bundleRelease
cp app/build/outputs/bundle/release/app-release.aab \
   ~/sambashare/sleepsound-<versionName>-vc<versionCode>.aab
chmod 700 ~/sambashare/sleepsound-*.aab
```

Signing pulls `SLEEPSOUND_KEYSTORE_*` from `~/.gradle/gradle.properties`
(keystore `~/keys/sleepsound-upload.jks`, alias `sleepsound`, valid to
2053-10-04). Verify with `$JAVA_HOME/bin/jarsigner -verify <aab>` —
should show signed entries under `META-INF/SLEEPSOU.{SF,RSA}`.

---

## Reading on-device state

| Want | Command |
|---|---|
| Screen | `adb exec-out screencap -p > /tmp/x.png` |
| Playback prefs | `adb shell run-as <pkg> cat shared_prefs/playback_state.xml` |
| Entitlements | `adb shell run-as <pkg> cat shared_prefs/entitlements.xml` |
| Logs | `adb logcat -d -t 200 \| grep -iE "SleepSound\|AudioTrack\|MediaFocus\|MediaSession\|SampleSource"` |
| Audio focus stack | `adb shell dumpsys audio \| grep -A 8 "Audio Focus"` |
| Installed version | `adb shell dumpsys package <pkg> \| grep -E "versionName\|versionCode"` |

If audio is selected but silent: 1) check the MixPanel slider / mute
icon, 2) check the audio-focus stack — another media app may hold focus
(we use `setAcceptsDelayedFocusGain(false)` so denial is immediate),
3) Bluetooth ghost — `BecomingNoisyReceiver` pauses on phantom BT
disconnects; forget the BT device to confirm.

---

## Audio pipeline

- **Procedural** (brown/pink/white/violet noise, dryer, fan): pure-Kotlin
  generators in `audio/procedural/*.kt`. Math definitions — don't
  replace with recordings, they ARE the thing they're named for.
- **Sample** (rain, ocean, thunderstorm, fireplace): CC0 recordings at
  `assets/sounds/<id>.opus`. Streaming `MediaCodec` decoder
  (`SampleSource.kt`); memory stays bounded for any asset length.
- **`SoundCatalog.create(id)`** prefers sample → procedural fallback.
  Drop `assets/sounds/<id>.opus` to swap an override in.
- **Formats**: OGG, OPUS, MP3, FLAC, WAV (`SoundCatalog.sampleExtensions`
  + `androidResources.noCompress` in `build.gradle.kts`).
- **Encode as Opus** — brew ffmpeg lacks libvorbis (GPL-incompatible).
- **CC0 only.** CC-BY-SA is poison — share-alike would force the whole
  audio bundle into the same license. archive.org has CC0 nature
  content without login walls; Freesound's CC0 filter requires login.
- **No synthetic audio processing.** Gain + peak limiter OK; reverb,
  ML upsampling, AI enhancement off-limits. Hard user rule.
- **Loudness target**: ~−22 LUFS integrated. Audit with
  `ffmpeg -i FILE -filter_complex "loudnorm=print_format=summary" -f null -`.

---

## Code-level invariants

- **Wake-lock timeout > max timer.** `AudioEngine.WAKE_LOCK_TIMEOUT_MS`
  (currently 13 h) must exceed `TimerSelector.MAX_CUSTOM_MINUTES`
  (currently 720 = 12 h). Raise both if extending the timer ceiling.
- **Explicit `Locale` on every user-facing `.format()`.** Default to
  `Locale.US` for pure-digit labels. Robo crawler runs random locales —
  ar-SA crashes on the bidi-formatter path without an explicit locale.
- **`PlaybackController.stopPlayback`** clears `_timerMinutes` — an
  explicit Stop is a "fresh start" boundary. For focus-loss / Becoming
  Noisy paths where the user may want to resume the same timer, use
  `notifyServiceStopped(preserveTimer = true)`.
- **`MediaSessionController`** reads title + artist from `R.string`.
  Don't hardcode English; brand string is `app_name` = "Sleep Soundly".
- **`MediaSession.setActions()`** advertises STOP + PLAY (not PAUSE) so
  SystemUI shows Stop on the lockscreen / compact view. The PAUSE
  callback is still routed for BT media keys, just not surfaced as a
  tappable chip.
- **`POST_NOTIFICATIONS`** is requested on first sound tap with a
  rationale-snackbar action, not on first composition. Don't move it
  back to `LaunchedEffect(Unit)` — an immediate ask after onboarding's
  "No tracking" pillar reads as a contradiction.
- **No INTERNET permission.** Stripped via `tools:node="remove"` so
  Play Billing's `transport-backend-cct` transitive can't reintroduce
  it. Don't add it back without re-reading `store/PRIVACY.md`.

---

## Visual conventions (moonlit-night palette)

Tokens in `ui/theme/AmoledTheme.kt`. Names are historical; values are
slate-blue, not neutral grey.

| Token | Hex | Role |
|---|---|---|
| `PureBlack` | `#000000` | bg (AMOLED true-black) |
| `SurfaceDark` | `#1A2236` | slate-blue card surface |
| `DimmerGrey` | `#2D3548` | slate tertiary (borders, scrims) |
| `DimGrey` / `IconGrey` | `#A8B0C0` | cool inactive content |
| `SoftWhite` | `#E0E4EC` | primary text + active accent |

Two-tier text:
- **Primary** (titles, status, button labels, sound-tile labels both
  active and inactive): `SoftWhite`.
- **Secondary** (subtitles, captions, version, dropdown items): `DimGrey`.

Sound-tile labels are `SoftWhite` in *both* active and inactive states
— `DimGrey` on `SurfaceDark` is 3.7:1 and fails WCAG AA body text.
Active vs inactive is conveyed by the 2 dp `SoftWhite` border + icon
tint, not by label color.

---

## Style + behavior

- **Don't add UI features the user didn't ask for.** Big speculative
  changes get reverted.
- **Don't soften load-bearing marketing claims silently.** If a code
  change makes a claim in `store/STORE_LISTING.md` / `PLAN.md` false,
  update the copy in the same commit. Bad claims are unacceptable.
- **Don't drop the OEM-killer row** in `SettingsBottomSheet.kt` — it's
  promised in 4 docs + onboarding p2. Regressed once in commit
  `a8bcd74`, restored in rc3; the row reads `Build.MANUFACTURER` and
  links to `dontkillmyapp.com/<lowercased manufacturer>`.
- **OSS attribution** is `assets/licenses.txt` rendered in an in-app
  `AlertDialog`. Don't pull `play-services-oss-licenses` — would add a
  GMS dep to a no-network app.

---

## Known gaps (post-rc3)

- **No test infrastructure beyond JUnit** for procedural audio +
  `SoundId` invariants. No Compose UI / screenshot / instrumented tests.
- **No CI** (GitHub Actions).
- **Real-device validation on non-Samsung OEMs** for the
  `dontkillmyapp.com/<slug>` target — confirm Xiaomi, Huawei, OnePlus,
  etc. land on a real per-OEM page, not a 404.
- **Dryer + Fan recordings** if procedural turns out to be unacceptable
  (current samples are ~1.5-min loops).
