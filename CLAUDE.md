# Notes for Claude working in this repo

This is the [SleepSound](README.md) Android project — an AMOLED-friendly
sleep-sound app. Positioning, scope, and architecture are documented:

- **[README.md](README.md)** — quick overview + repo layout
- **[PLAN.md](PLAN.md)** — full positioning, v1 scope, planned-vs-actual
  architecture table
- **[BUILDING.md](BUILDING.md)** — toolchain install, signing, Play Console
  release flow, on-device reliability test recipes
- **[CHANGELOG.md](CHANGELOG.md)** — versioned change log
- **[store/](store/)** — Play Store submission artifacts (listing copy,
  privacy policy, data-safety answers, audio credits)
- **[tools/audio/SOURCING.md](tools/audio/SOURCING.md)** — where to find
  CC0 nature recordings and how to convert them

Read those first. The notes below are session-learned gotchas that
*aren't* obvious from the code or those docs.

---

## Don't touch the phone while the user is using it

The single biggest mistake from earlier sessions: running
`adb shell input tap X Y` while the user is interacting with their
phone. The synthetic tap lands on whatever screen is currently shown —
including the user's screen. They thought white noise was buggy because
my taps were toggling the tile underneath them.

**Rule:** `adb exec-out screencap -p` is fine (read-only). `adb shell input ...`
is *not* — it mutates the phone's state, racing with the user. Only
use input commands when the user has explicitly handed off control, or
when iterating without the user present.

---

## Local toolchain layout (Intel Mac, May 2026)

| Tool | Path | Set by |
|---|---|---|
| JDK 17 | `/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` | brew formula `openjdk@17` (keg-only) |
| Gradle (host) | `/usr/local/bin/gradle` (9.5.x) | brew formula `gradle` |
| Android SDK | `~/Android/sdk` | manual cmdline-tools install + `sdkmanager` |
| `adb` | `~/Android/sdk/platform-tools/adb` | sdkmanager |
| `ffmpeg` | `/usr/local/bin/ffmpeg` (8.x) | brew formula |

`~/.zshrc` has a guarded block exporting `JAVA_HOME`, `ANDROID_HOME`, and
the right `PATH` entries. New shells pick these up automatically. Bash
tool calls in this session don't share env between calls; if you need
the toolchain in a one-off command, export inline:

```bash
export JAVA_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

---

## The build → install → launch loop

Once the user's phone is connected via USB with USB debugging on:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop io.github.drediger.sleepsoundly.debug   # optional, ensures clean state
adb shell am start -n io.github.drediger.sleepsoundly.debug/com.sleepsound.MainActivity
adb exec-out screencap -p > /tmp/sleepsound.png   # read the image with the Read tool
```

Notes:
- The **debug package ID** is `io.github.drediger.sleepsoundly.debug` (the `.debug` suffix
  comes from `applicationIdSuffix` in the debug build type).
- The **release package ID** is `io.github.drediger.sleepsoundly` — they coexist on a
  device.
- The **Kotlin namespace** (where source files actually live) is still
  `com.sleepsound`, by design — `applicationId` was renamed for Play
  Console without refactoring every `package com.sleepsound.*`
  declaration. So launcher class is `com.sleepsound.MainActivity` but
  the installed app is `io.github.drediger.sleepsoundly[.debug]`.
- `adb shell pm clear io.github.drediger.sleepsoundly.debug` wipes ALL app state
  (onboarding flag, persisted active sounds, per-sound layer gains,
  entitlements). Useful for verifying first-run behavior; cruel if the
  user has gone through onboarding and you blow it away.

---

## Reading on-device state

| Want to see | Command |
|---|---|
| Current screen | `adb exec-out screencap -p > /tmp/x.png`, then Read tool |
| Persisted prefs | `adb shell run-as io.github.drediger.sleepsoundly.debug cat /data/data/io.github.drediger.sleepsoundly.debug/shared_prefs/playback_state.xml` |
| Entitlement prefs | Same path but `shared_prefs/entitlements.xml` |
| Logs (focus + media) | `adb logcat -d -t 200 \| grep -iE "AudioTrack\|MediaFocusControl\|MediaSession\|sleepsound\|SampleSource"` |
| Audio focus stack | `adb shell dumpsys audio \| grep -A 8 "Audio Focus"` |
| Active app pid | `adb shell pidof io.github.drediger.sleepsoundly.debug` |

---

## Audio pipeline

- **Procedural sounds** (brown / pink / white / violet noise, TV static,
  Dryer, Fan): pure-Kotlin generators in `audio/procedural/*.kt`. Math
  definitions, not approximations of recordings. Don't replace these
  with recordings — they ARE the thing they're called.
- **Sample sounds** (rain, ocean, thunderstorm, fireplace): real CC0 /
  public-domain field recordings in `app/src/main/assets/sounds/<id>.opus`.
  Streaming decoder in `audio/engine/SampleSource.kt` decodes
  buffer-by-buffer via `MediaCodec` — memory stays bounded regardless
  of asset length.
- **`SoundCatalog.create(id)`** prefers a sample if one exists, falls
  back to procedural. Adding a new file at `assets/sounds/<id>.opus`
  is the only step needed to swap an override in.
- **Acceptable sample formats:** OGG Vorbis, OPUS, MP3, FLAC, WAV.
  All listed in `SoundCatalog.sampleExtensions` and in
  `build.gradle.kts`'s `androidResources.noCompress`.

### Audio gotchas
- **brew ffmpeg doesn't ship `libvorbis`** (GPL-incompatible). It does
  ship `libopus`. Encode as Opus, not Vorbis. The `.opus` container is
  natively supported by Android API 21+.
- **CC0 / Public Domain only.** CC-BY-SA is poison — share-alike would
  force the whole audio bundle into the same license. Freesound has a
  CC0 filter but their downloads require login that this session can't
  do; archive.org doesn't require login and has plenty of CC0 nature
  content.
- **Loudness audit:**
  `ffmpeg -i FILE -filter_complex "loudnorm=print_format=summary" -f null -`.
  Target ~-22 LUFS integrated across files; current spread is ~7 LU.
- **Don't add anything synthetic to nature samples.** The user has been
  explicit: "no fake ai bullshit." Gain + peak limiter for level
  balancing is fine (no content added). Reverb, synthesized
  enhancement, ML upsampling — all off-limits.

---

## Visual review without an emulator

`tools/preview/` has HTML mirrors of the Compose screens.
`tools/preview/screenshot.mjs` uses Playwright (looked up from
`~/Projects/riftlens/frontend/node_modules` by default, override with
`SLEEPSOUND_PLAYWRIGHT_DIR`) to render PNGs at two scales: dev preview
(dpr 2) and Play-Store-grade (dpr 2.625 → ~1080×2400). The
`feature-graphic.html` is rendered at exact 1024×500 dpr 1 (Play spec).

Both output dirs (`screenshots/`, `play-assets/`) are gitignored —
regenerable.

This is now somewhat redundant for screens that exist on-device —
the phone is the source of truth. But it's still useful for screens
that require complex states (purchase prompts, idle dim, settings
sheet) or for generating Play Store screenshots without setting up the
emulator.

---

## Diagnosing audio that "doesn't play"

If a sound is selected and the UI shows "Playing N sounds" but no audio
comes out:

1. Check the `MixPanel` slider for the sound — if it's at 0 or the
   mute icon is engaged, that's the user's gain.
2. Check the persisted layer gain: `cat .../shared_prefs/playback_state.xml`.
3. Watch the focus log right after the tap:
   ```
   adb logcat -c && (tap on phone) && adb logcat -d \
     | grep -iE "MediaFocusControl|MediaSessionService"
   ```
   If `MediaSession state` flips from PLAYING to STOPPED within a few
   ms of the tap, `handleStop()` is being called — most likely because
   `focusManager.acquire()` returned false (another media app holds
   focus). Open the other app, stop it, retry.
4. If the audio focus stack lists another app at the top: that's the
   culprit. SleepSound's `AudioFocusManager` uses
   `setAcceptsDelayedFocusGain(false)`, so a busy focus stack denies
   immediately rather than queueing.
5. Bluetooth ghost connections: a paired-but-disconnected BT device
   can cause `ACTION_AUDIO_BECOMING_NOISY` to fire on `AudioTrack`
   creation, which our receiver handles by stopping the service.
   Forget the BT device or disable BT to confirm.

---

## What's still missing for v1 launch

See [`CHANGELOG.md`](CHANGELOG.md) "Known gaps" section. The big ones:

- **Production signing key** (`SLEEPSOUND_KEYSTORE_*` properties).
  Wired in `build.gradle.kts`, just needs a real keystore.
- **Hosted privacy-policy URL.** `docs/privacy/index.html` is ready to
  publish via GitHub Pages; `strings.xml#privacy_policy_url` is still
  the `example.com` placeholder.
- **Real device validation on non-Samsung OEMs** for the OEM-killer
  guidance.
- **Dryer + Fan recordings** if we decide procedural isn't acceptable
  for those two.
- **Test infrastructure** — no JUnit / Compose UI / screenshot tests
  yet.

---

## Style + behavior conventions

- **Don't add UI features the user didn't ask for.** This codebase has
  been built incrementally with the user reviewing each iteration on a
  real device. Big speculative changes are likely to be undone.
- **Don't soften load-bearing marketing claims silently.** If a claim
  in `STORE_LISTING.md` or `PLAN.md` becomes false because of a code
  change (e.g. shipping samples broke "no looped audio"), update the
  copy in the same commit. The user has been explicit that bad claims
  are unacceptable.
- **Moonlit-night palette** lives in `ui/theme/AmoledTheme.kt`. Bg
  stays `PureBlack #000` (AMOLED-true-black, part of the four-pillar
  promise); everything else is cool-tinted slate-blue to harmonize
  with the slate-and-cream launcher moon icon. Token names are
  historical — values are now slate-blue, not neutral grey.
  - `PureBlack #000` — background.
  - `SurfaceDark #1A2236` — slate-blue card surface (~3.6:1 on bg).
  - `DimmerGrey #2D3548` — slate tertiary (borders, scrims).
  - `DimGrey #A8B0C0` / `IconGrey #A8B0C0` — cool inactive content.
  - `SoftWhite #E0E4EC` — cool moonlight primary text + active accent.
  - **Primary text** (titles, status, sound names, active tile labels,
    button labels, active tile border) → `SoftWhite`.
  - **Secondary text** (subtitles, captions, inactive tile labels,
    dropdown items, version/promise) → `DimGrey`.
  - Palette settled May 2026 after iterating from neutral-grey
    (#666/#111) through neutral-bumped (#888/#1F1F1F) to the current
    moon-themed slate. Earlier values were tuned only for bedroom
    brightness; daytime testing surfaced the need for both brighter
    surfaces AND a more inviting hue.
