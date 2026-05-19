# Sleep Sound App — Project Plan

> A nighttime-respecting Android sleep sound and white noise app: no ads, no account, no subscription, no looping audio, AMOLED-friendly, gentle on interruptions, won't wake you at 3am. One-time purchase ~$4.

---

## 1. Positioning

**The pitch.** Every popular sleep-sound app on the Play Store is built for daytime browsing and monetized for engagement: bright marketing UIs, account requirements, ads or subscription pop-ups that fire at 3am, and audio that dies mid-night under Doze or Bluetooth interruptions. This app inverts every one of those defaults.

**The single-sentence ad.** *"Sleep sound app. No ads. No account. No subscription. Won't wake you. Free, with $0.99 per premium sound."*

**Target user.** Android power users (Reddit r/Android, r/androidapps regulars), light-sleepers, parents of babies, audiophiles, and anyone burned by Calm/BetterSleep subscription patterns. Not the meditation-content market — we don't compete with Calm on guided meditation; we compete on the sound-maker job-to-be-done.

**What we will not build.**

- Guided meditation, sleep stories, narrated content
- Social features, streaks, daily quests, gamification
- Sign-in, accounts, cloud sync (v1)
- Sleep tracking, sleep scoring (that's Sleep as Android's lane)
- Ads in any form, ever
- Subscription model

---

## 2. Competitive Landscape

### Top incumbents (mid-2026)

| App | Installs | Rating | Model | Key flaw |
|---|---|---|---|---|
| **Calm** | ~70M (Play) | 4.4 | $70-80/yr sub | Subscription jumpscares, audio dies in Doze, account required |
| **Sleep as Android** | 10M+ | 4.6 | $50/yr or lifetime | Tracker-first, sounds are afterthought, no AMOLED-pure UI |
| **BetterSleep** (Relax Melodies) | 10M+ | ~4.5 | $60/yr sub | Surprise billing, slow startup, account required |
| **White Noise (TMSoft)** | 6.4M (Lite) | 4.5 | Free+ads / $3 paid | Audio cuts off mid-night, battery hostile, dated UI |
| **Atmosphere** | 2.4M | 4.63 | Free + opaque IAP | Best ratings; pricing opacity, not AMOLED-true-black |
| **myNoise** | 500K+ | very high | $20-30 one-time | Audiophile UI, not bedside-friendly, no smart wake |

### The 5 universal nighttime UX failures (where to plant the flag)

1. **Audio dies overnight.** The #1 1-star complaint across the category. Doze, OEM task killers (Samsung One UI, Xiaomi MIUI, OnePlus), Bluetooth re-pairing all break playback. TMSoft's official support tells users to manually build a 9-min looping playlist. *This is the most defensible feature gap.*

2. **The 3am ad/paywall jumpscare.** Free apps interstitial mid-night; freemium apps pop "Try Premium" on resume; even paying Calm users see promo overlays.

3. **Bright UIs that aren't AMOLED-true-black.** Gradient purples, marketing imagery, off-black greys. Painful at 2am on AMOLED at any brightness.

4. **Account-required for what's a stateless utility.** Calm, BetterSleep, Headspace all require sign-in for paid features. Breaks offline use, creates "I changed phones at 11pm" failures, generates marketing email spam.

5. **Short looped audio masquerading as continuous noise.** Most apps loop 30-120s clips. The looping is subliminally detectable and pulls light sleepers awake.

**Our wedge:** Attack #1 (reliability), #3 (true AMOLED), and #5 (real-time math for noise colors / appliance sounds; 10-minute seamless field recordings for nature sounds — not 30-second clips on repeat). The best of the existing crop nail one of these; none nails all three in a single bedside-app form.

---

## 3. v1 Feature Scope

### In v1.0 (Play Store launch)

- 10 sources (brown, pink, white, violet noise — procedural; rain, thunderstorm, dryer, ocean, fan, fireplace — CC0 sample recordings in `app/src/main/assets/sounds/`). Each naturalistic sound's procedural generator remains in-tree as a fallback if the asset is missing. TV static was dropped 2026-05-19 because it was audibly indistinguishable from white noise.
- Multi-layer mixer: every active source mixes simultaneously with per-source volume sliders and a mute toggle per row (no fixed cap).
- Foreground audio service that keeps audio alive through screen-off, Doze, and app-swipe-away. **Bluetooth or headphone disconnect pauses cleanly** (Android best-practice — speakers don't blast at 3am).
- MediaSession integration (lock-screen controls, notification, BT media keys, Android Auto) via `MediaSessionCompat` + MediaStyle notification.
- True AMOLED-black Compose UI, auto-dim after 30 s idle, no ripple animations.
- Gentle fade-in / fade-out (master 1.5 s equal-power, per-layer 800 ms linear) on play/stop, timer expiry, and audio-focus loss.
- Audio focus handling: transient-loss → pause; can-duck → 30 % duck; permanent-loss → stop. Resumes on focus regain.
- Partial wake lock acquired while rendering (10 h safety timeout) for explicit Doze survival.
- Sleep timer with presets (15 / 30 / 60 / 90 min) **plus a custom value up to 12 h** via an in-app dialog.
- Optional resume-on-reboot (opt-in, off by default) via `BootReceiver`.
- In-app Reliability section pointing at the system battery-optimization dialog and per-OEM `dontkillmyapp.com` guidance. (A dedicated first-run onboarding carousel is still on the punch list.)
- Play Billing 8.x per-sound one-time IAPs ($0.99 each, no subscription tier). 30-second live preview-then-buy mechanic.

**Monetization model** *(decided 2026-05-19)*: free base app with **per-sound $0.99 one-time purchases**. Every premium sound is previewable for 30 seconds inside the live mixer; when the preview elapses the sound stops and a "Buy $0.99 →" pill replaces the tile label until the user buys or taps elsewhere. All app-level features (mixer, timer, AMOLED UI, audio-reliability, MediaSession, fade-out) are free for everyone — the IAP gates *content*, not features.

| Tier | Sounds (sample-or-procedural) |
|---|---|
| **Free (4)** | Brown noise, White noise, Rain, Ocean |
| **Premium ($0.99 each)** | Pink noise, Violet noise, Thunderstorm, Dryer, Fan, Fireplace |

The free four are the four most-searched on the Play Store for noise / sleep apps. Premium covers everything else, including any future additions. Open decision: whether to also offer an "all-access" bundle SKU at ~$3.99 for users who want everything in one purchase.

### Deferred to v1.x

- ~~**Bundled high-quality sample assets** (rain, thunder, ocean, fire, fan, dryer recordings to override procedural generators).~~ **Shipped 2026-05-19.** Six CC0 / public-domain field recordings (rain, ocean, thunderstorm, fireplace, fan, dryer) in `app/src/main/assets/sounds/`, license attributions in [`store/AUDIO_CREDITS.md`](store/AUDIO_CREDITS.md). Nature sounds are 10-minute seamless Opus loops at 48 kHz stereo with 2-second crossfade-bridges; fan/dryer are shorter (70–90 s) but still self-crossfaded for seamless looping. All loudness-normalized to ~-22 LUFS.
- **Built-in presets + user-saved mixes** (e.g. "Storm night", "Office afternoon"). Local-only persistence.
- **Onboarding carousel** for first-run battery-optimization + OEM-killer walkthrough (today the same guidance lives in Settings → Reliability, but it's not pushed at first launch).
- v1.1: Smart wake (gradual transition to wake sound at alarm time).
- v1.2: Home-screen widget (Glance API "resume last mix").
- v1.3: Battery-saver mode (lower sample rate procedural, fewer layers).
- v1.5: Wear OS companion (control phone playback).

### Permanently out of scope

Sleep tracking, social, meditation content, accounts, cloud sync, telemetry beyond crash reports.

---

## 4. Technical Architecture

> **Implementation-status note (2026-05-19).** Section 4 below is the *original* architecture plan. The actual code in this repo (now at **v1.0.0-rc1**) intentionally diverged on several lines — the divergences are simpler, not regressions, and are listed in the table below. Re-evaluate before scaling up the codebase.

### Tech stack — planned vs actual

| Concern | Planned | Actual in repo | Notes |
|---|---|---|---|
| Language | Kotlin 2.1.x (K2) | Kotlin 2.1.0 | ✓ matches |
| UI | Jetpack Compose + Material 3 | Compose + Material 3 (BOM 2024.12.01) | ✓ matches |
| Min SDK | 29 (Android 10) | 29 | ✓ matches |
| Target SDK | 35 (Android 15) | 35 | ✓ matches |
| Audio (samples) | Media3 / ExoPlayer | Custom `MediaExtractor`+`MediaCodec` decode → in-memory PCM via `SampleSource` | Diverged. Custom decoder is ~80 LOC and gives us byte-identical loops with no offload uncertainty. Trade-off: no automatic gapless/crossfade. |
| Audio (procedural) | `AudioTrack` STREAM_MUSIC | Single `AudioTrack` USAGE_MEDIA / CONTENT_MUSIC, all sources mixed in `LayerMixer` then written to one track | Same family, slightly different routing. |
| MediaSession | Media3 `MediaSessionService` | Platform `MediaSessionCompat` + `LifecycleService` + MediaStyle notification | Diverged. We don't need Media3's MediaController plumbing for this app. |
| DI | Hilt + KSP | Singleton `object`s (`PlaybackController`, `EntitlementStore`, `BillingManager`) | Diverged. Hilt is overkill for an app with ~5 long-lived singletons. |
| Persistence | DataStore (Proto) | `SharedPreferences` (one for playback state, one for entitlements) | Diverged. Two small flat prefs files, no schema migrations needed yet. Switch to DataStore when a third store appears. |
| Async | Coroutines + Flow | Same | ✓ matches |
| Billing | Play Billing 7+ | Play Billing 8.3.0 (`billing-ktx`) | ✓ — bumped to 8.x. |
| Tests | JUnit5 + Turbine + Compose UI test | **None yet.** Whole testing setup is still on the punch list. | Gap. |

**Single-module Gradle project for v1.** Multi-module is overhead until you have >2 devs or >50k LOC.

### Package structure

```
com.yourapp.sleep/
  app/                # Application class, Hilt entry points
  ui/
    screens/          # @Composable screen-level functions
    components/       # LayerSlider, FadeButton, PresetCard
    theme/            # AMOLED color scheme, typography
    idle/             # auto-dim detection
  audio/
    engine/           # AudioEngine facade (the only public type for UI)
    playback/         # Media3-based sample playback
    procedural/       # noise generators (Brown, Pink, White, Violet)
    mixer/            # AudioTrack mixer for procedural sources
    focus/            # AudioFocusRequest + BecomingNoisyReceiver
  service/            # SleepAudioService (MediaSessionService)
  data/
    prefs/            # DataStore wrappers
    presets/          # built-in presets, user mixes
    samples/          # SoundCatalog, asset metadata
  domain/             # plain Kotlin: PlayState, Layer, Timer, Preset
  billing/            # interface + no-op v1 implementation
  util/               # FadeCurve, time helpers
```

**Rules:** `ui/` knows about `domain/`, never `audio/` directly. `service/` orchestrates `audio/`. `audio/` knows nothing about Android UI.

### Audio architecture (the part most apps get wrong)

**Sample playback — use Media3/ExoPlayer.** One `ExoPlayer` per layer, sharing `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)`. ExoPlayer gives you for free: gapless looping (`REPEAT_MODE_ONE`), crossfade between samples (two players with cross-faded volumes), per-layer volume, asset streaming.

Encode samples as **OGG Vorbis or OPUS, not MP3.** MP3 encoder padding breaks gapless on some devices. Lock the encoding pipeline before falling in love with any sample. Click-at-loop-point is a bug killer.

**Don't use:** `MediaPlayer` (no gapless guarantee), `SoundPool` (1MB cap, SFX-only), or Oboe/AAudio (low-latency music apps; we want efficiency and offload).

**Procedural noise — write the math yourself.** ~150 lines of Kotlin, no dep needed. Run a single `AudioTrack` at 48kHz stereo PCM16, fed from a `Dispatchers.Default` coroutine. Generator math:

- **White:** uniform random `[-1, 1]`
- **Brown (-6dB/octave):** leaky integrator: `brown = brown * 0.999 + white * 0.02`
- **Pink (-3dB/octave):** Paul Kellet 7-band parallel filter (see appendix)
- **Violet (+6dB/octave):** differentiate white noise: `violet = white - prevWhite`

Each generator implements `interface NoiseSource { fun fillBuffer(out: ShortArray, frames: Int) }`. The mixer sums enabled sources, scales by per-source gain, clips, writes to `AudioTrack`.

**Combine the two pipelines.** Run sample players (Media3) and procedural (AudioTrack) as **separate audio streams**. Android's audio system mixes them in hardware. Don't try to route everything through a custom mixer — you lose ExoPlayer's offload and gain nothing.

The `AudioEngine` facade owns: a list of `SamplePlayer`s + one `ProceduralPlayer`. It exposes `setLayer(id, source, volume)`, `setMasterVolume(float)`, and applies fades on a 16ms tick using equal-power curves (`sin(t * PI/2)`), not linear.

### Foreground service (where beginners get burned)

Use **`MediaSessionService`** from Media3, not a raw `Service`. You inherit MediaSession plumbing, media-style notification, swipe-away contract, lock screen + BT keys + Android Auto support nearly for free.

**Manifest (the critical bits):**

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>

<service
    android:name=".service.SleepAudioService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService"/>
    </intent-filter>
</service>
```

`foregroundServiceType="mediaPlayback"` is mandatory on Android 14+ and is the only legitimate type for this app. Don't put bare `startForegroundService()` calls in odd places; let `MediaSessionService` manage lifecycle via playback state.

**Lifecycle map:**
- `MediaSession.Callback.onPlay()` → `AudioEngine.start()`
- `onStop()` → release focus, stop service
- `onTaskRemoved()` → keep playing (correct for this app)
- `BOOT_COMPLETED` receiver checks opt-in flag, restarts service with last preset

### Audio focus & interruptions

Single `AudioFocusRequest` owned by `AudioEngine`:

| Event | Action |
|---|---|
| `AUDIOFOCUS_GAIN` (start) | Fade in over 1.5s |
| `AUDIOFOCUS_LOSS_TRANSIENT` (incoming call) | Pause + fade out 500ms |
| `..._CAN_DUCK` (Maps directions) | Duck to 30% volume |
| `AUDIOFOCUS_LOSS` (other media app) | Stop service (user wants Spotify) |
| Regain after transient | Fade back in over 1.5s |
| `ACTION_AUDIO_BECOMING_NOISY` (headphones/BT unplug) | Pause immediately, no fade (speakers already audible) |

Register the `BECOMING_NOISY` receiver **dynamically in the service**, not in the manifest — manifest receivers for this intent don't fire on Android 8+.

### AMOLED UI principles

- Custom `darkColorScheme()` with `background` and `surface` = `Color(0xFF000000)` — true black, not M3 default near-black
- Text: dim grays (`0xFF666666` primary, `0xFF333333` inactive)
- No gradients, no elevation shadows (they appear as gray patches on AMOLED)
- Auto-dim: idle timer resets on every `MotionEvent`. After 30s idle, animate brightness to `0.02f` and UI alpha to 0.3, hide system bars. Any tap restores.
- Touch targets ≥ 56dp; sliders use thick thumbs; play/stop is a single 96dp circle in lower third (thumb reach when lying down)
- No haptics, no transition animations > 150ms
- `FLAG_KEEP_SCREEN_ON` only on the player screen, never globally

### Critical Android gotchas

- **Battery optimization:** offer a one-time prompt via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Without it, Doze throttles audio threads on some OEMs after hours.
- **OEM killers:** Xiaomi (MIUI), Huawei, OnePlus, Samsung One UI ("Deep Sleep") kill foreground services overnight regardless of code. Detect manufacturer, surface one-time onboarding screen pointing to that OEM's "don't kill this app" setting. Link to [dontkillmyapp.com](https://dontkillmyapp.com).
- **Doze + FGS:** `mediaPlayback` is exempt from Doze CPU throttling **while audio actively plays**. If you pause for >N minutes, system can Doze. Keep audio flowing (silence is fine) if you must stay alive through pauses.
- **Audio offload:** ExoPlayer can use hardware offload (great for battery) but kills smooth real-time volume fades. Disable offload (`AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED`) on layers you fade frequently.
- **Notification permission (API 33+):** request `POST_NOTIFICATIONS` before starting playback or the media notification is invisible and users assume the app crashed.
- **Buffer underrun on low-end:** test on a sub-$200 Moto. Use ≥4x min buffer size for procedural `AudioTrack`; monitor `getUnderrunCount()`.

**Test devices (own or borrow):** Pixel (baseline), Samsung S-series (One UI quirks), one Xiaomi (MIUI), one BT speaker, one BT headset, one USB-C DAC, Android Auto head unit if possible.

---

## 5. Phased Milestones

### v0.1 — Proof of Concept (~1 week)
Single screen, single tap-to-toggle button. Plays procedurally-generated brown noise via `AudioTrack` in a foreground service with `mediaPlayback` FGS type. Ongoing notification with stop action. Equal-power fade in/out (1.5s). **Acceptance:** survives screen off and app swipe-away for 30+ minutes on the dev device.

*Deviation from original plan:* v0.1 leads with procedural brown noise instead of Media3 sample playback. Reasons: (1) no sample asset to bundle yet, (2) procedural is the differentiator and worth validating end-to-end first, (3) `MediaSessionService` requires a `Player` implementation which is non-trivial without `ExoPlayer`. Media3 + MediaSessionService migration moves to v0.5.

### v0.5 — Personal Alpha (scaffolded ✓ — hybrid sample/procedural)

**Hybrid audio strategy.** Pure noises (brown / pink / white / violet / TV static / ocean) ship procedurally and are correct by construction. Naturalistic sounds (rain, thunderstorm, dryer, fan, fireplace) ship with procedural approximations as fallback; dropping `app/src/main/assets/sounds/<sound_id>.<ogg|opus|mp3|flac|wav>` overrides any sound with a real recording at runtime — `SoundCatalog` does sample-first lookup, fallback to procedural. Loop seamlessness is the source asset's responsibility.

Scaffolded:
- **11 procedural sources** — `BrownNoise`, `PinkNoise` (Paul Kellet), `WhiteNoise`, `VioletNoise`, `TvStatic` (white + 60Hz hum), `Rain` (pink + HF + LFO), `Thunderstorm` (rain + LP-filtered thunder rumbles), `Dryer` (brown + hum + tumble bursts), `Ocean` (pink + slow LFO), `Fan` (brown + blade tone), `Fireplace` (brown + crackle impulses)
- **`AudioDecoder` + `SampleSource`** — MediaCodec decode to PCM16 stereo 48kHz, async lazy load, looped playback. Mono auto-upmix, non-48k linear resample. Loads in `ArrayList<ShortArray>` chunks to avoid boxing every Short.
- **`SoundCatalog`** — sample-first / procedural fallback per SoundId.
- **`LayerMixer`** — n-layer composite NoiseSource with per-layer 800ms linear fade, skips inactive sources for CPU savings
- **`AudioEngine`** — owns mixer, equal-power master fade, exposes `setLayerActive`, `setLayerGain`, `setMasterVolume`, `setDucked`
- **`LifecycleService`-based `SleepAudioService`** — collects `PlaybackController` flows, drives mixer. `mediaPlayback` FGS type.
- **`AudioFocusManager`** — full focus state machine (LOSS/TRANSIENT/DUCK/GAIN) with duck-to-30%
- **`BecomingNoisyReceiver`** — dynamic registration, pauses on headphone/BT disconnect
- **`SleepTimer`** — coroutine-backed, only ticks while playing, fires fade-out on expiry
- **`PlaybackController` singleton** — `StateFlow`-based, persists active sounds + per-layer gains + master volume + resume-on-reboot opt-in via SharedPreferences
- **Sound-picker UI** — 3-column grid of `SoundTile`s, animated active state
- **`MixPanel`** — per-active-sound volume sliders (always visible when sounds are active)
- **`TimerSelector`** — dropdown + live countdown (mm:ss) while playing
- **`IdleDimmer`** — auto-dims activity brightness to 2% after 30s of no touch; tap-anywhere overlay restores
- **`SettingsBottomSheet`** — Reliability section with three rows: battery-opt request (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), OEM-specific instructions (opens `dontkillmyapp.com/<manufacturer>`), resume-on-reboot toggle
- **`BootReceiver`** — opt-in, restarts service with last active sounds on `BOOT_COMPLETED`
- **Adaptive app icon** — crescent-moon foreground vector on `#0F0F2A` deep-night background. Placeholder design; replace pre-store.
- **Auto-orchestration** — tap any tile from stopped state auto-starts; clearing all tiles auto-stops

Deferred to v1.0:
- Replacement audio assets (curate real recordings for Rain / Thunderstorm / Dryer / Fan / Fireplace from freesound.org / BBC SFX)
- Real (non-placeholder) app icon
- Play Store listing copy + screenshots + privacy policy URL
- Crashlytics integration
- Real-device QA matrix (Pixel + Samsung + Xiaomi + Auto + BT)

**Acceptance:** Use it nightly for a week without falling back to another app.

### v1.0 — Play Store (~6-8 weeks after v0.5)
Everything in v0.5 plus:
- All 4 procedural noise types
- Full preset system (save/load user mixes, ~10 built-ins)
- Auto-dim with idle detection
- Battery optimization + OEM killer onboarding
- Resume-on-reboot (opt-in)
- Billing wired to Pro gate
- Crash reporting (Crashlytics — only telemetry, disclosed in privacy policy)
- Play Store listing, screenshots, privacy policy
- Real-device QA across 4-5 devices

### v1.x — Post-launch roadmap
- **v1.1 Smart wake:** `setAlarmClock()` triggers transition from sleep mix to wake sound over 15 min
- **v1.2 Widget:** Glance API home-screen "resume last mix"
- **v1.3 Battery-saver mode:** lower sample rate procedural for older phones
- **v1.5 Wear OS companion:** separate module, controls phone playback via MediaController

---

## 6. Effort Estimate

Assuming a competent solo dev at ~10-15 hr/week, somewhat new to Android specifically:

| Milestone | Elapsed | Dev hours |
|---|---|---|
| v0.1 | 1 week | 10-15 |
| v0.5 | +4-5 weeks | +40-60 |
| v1.0 | +6-8 weeks | +60-90 |
| **Total to ship v1.0** | **~3 months** | **~150-180 hr** |
| v1.1 smart wake | +2-3 weeks | +25-35 |

The last 30% of v1.0 (Play Store paperwork, device testing, OEM quirks, billing) takes as long as the first 70%. Don't compress this.

---

## 7. Top Technical Risks

1. **OEM background-kill behavior.** No code fix. Educate users; expect 1-star reviews from MIUI users regardless.
2. **Gapless looping on samples.** Any encoder padding = audible click each loop. Lock down encoding pipeline (OPUS or OGG Vorbis, verified gapless) before committing to samples.
3. **Audio offload + volume fades.** Smooth fades are the product's *feel*. Offload makes them stepped. Get this right in v0.5.
4. **Procedural noise CPU on low-end.** Snapdragon 4xx + 4 noise sources + 3 sample players + AMOLED UI can struggle. Profile on real cheap hardware; plan a "battery saver" mode with reduced sample rate.
5. **Doze + long playback.** "Works for 30 min" ≠ "works 8 hours on a Samsung." Run real overnight tests early and often on multiple devices.
6. **FGS start restrictions on Android 14+.** v1.1 smart wake will hit `ForegroundServiceStartNotAllowedException` if not designed correctly. Use `setAlarmClock()` + activity-trampoline for wake.
7. **Play Billing edge cases.** Refunds, account transfers, multi-device entitlement, network-offline first launches. Budget more time than feels reasonable.

---

## 8. Open Decisions (to settle before v0.5)

- **App name.** Working candidates: "Hush", "Nightcap", "Lull", "Slumber", "Nocturne", "Hush Mode". Verify .app and Play Store namespace availability.
- **Free vs Pro split.** Proposed: free = 4 samples + white/brown noise + 1 layer + 30-min timer. Pro = everything. Alternative: free trial (14 days), then $4 unlocks all.
- **Price point.** $3.99 (impulse) vs $4.99 (US "round $5") vs $5.99 (premium signal). Recommend $4.99.
- **Sample sources.** Self-record (zero-cost, controls quality, ~10-20 hr work) vs license from Pond5/Splice (~$200-500 budget, faster). For nighttime sounds (rain, thunder, fire), self-record is feasible with a phone + cheap field recorder. Cafe/forest are harder.
- **Crash reporting choice.** Crashlytics (Firebase) vs Sentry vs none. Recommend Crashlytics — free, well-integrated, disclose in privacy policy.
- **Privacy policy hosting.** Will need a public URL. Can use a GitHub Pages markdown page; no need for a site.
- **Wear OS scope for v1.0.** Recommend defer entirely to v1.5 — doesn't gate launch and adds significant scope.

---

## 9. First Code — v0.1 scaffolded ✓

Five anchor files (package `com.sleepsound`):

1. `app/src/main/AndroidManifest.xml` — FGS declaration + permissions ✓
2. `app/src/main/java/com/sleepsound/service/SleepAudioService.kt` — plain `Service` in v0.1; migrates to `MediaSessionService` in v0.5 ✓
3. `app/src/main/java/com/sleepsound/audio/engine/AudioEngine.kt` — `AudioTrack` pipeline with equal-power fades ✓
4. `app/src/main/java/com/sleepsound/audio/procedural/NoiseSource.kt` + `BrownNoise.kt` — interface + leaky-integrator brown noise ✓
5. `app/src/main/java/com/sleepsound/ui/theme/AmoledTheme.kt` — true-black Material 3 theme ✓

Plus Gradle config (root + app + version catalog + wrapper props), Compose UI (`MainActivity.kt`, `PlayerScreen.kt`), and resources. The v0.1 milestone — "tap to play brown noise, survives screen off" — is ready to test as soon as the toolchain is installed.

---

## Appendix: Pink Noise (Paul Kellet)

```kotlin
// state across calls
var b0 = 0f; var b1 = 0f; var b2 = 0f
var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f

fun pinkSample(white: Float): Float {
    b0 = 0.99886f * b0 + white * 0.0555179f
    b1 = 0.99332f * b1 + white * 0.0750759f
    b2 = 0.96900f * b2 + white * 0.1538520f
    b3 = 0.86650f * b3 + white * 0.3104856f
    b4 = 0.55000f * b4 + white * 0.5329522f
    b5 = -0.7616f * b5 - white * 0.0168980f
    val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f) * 0.11f
    b6 = white * 0.115926f
    return pink
}
```
