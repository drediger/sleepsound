# Sleep Soundly

> A no-ads / no-account / no-subscription Android sleep-sound app.
> AMOLED-true-black. Won't wake you at 3 am. One-time $0.99 unlocks per
> premium sound, free base app.

---

## What this is

Sleep Soundly is positioned against the subscription-app pattern (Calm,
BetterSleep, Headspace) on the Play Store. The full positioning and
five-pillar promise live in [`PLAN.md`](./PLAN.md) §1, with the public
store copy in [`store/STORE_LISTING.md`](./store/STORE_LISTING.md).

**Headline differences:**
- 11 procedurally-generated sounds (no looped clips)
- True AMOLED-black Compose UI, auto-dims after 30 s idle
- Foreground media-playback service hardened for overnight survival
  (Doze + audio-focus + BecomingNoisy + MediaSession + wake lock)
- Zero data collection (no `INTERNET` permission, no analytics SDKs)
- Free tier: 4 sounds. Premium: 7 sounds @ $0.99 each, or all-in
  bundle at $3.99. 30-second live preview before purchase.

## Repo layout

```
.
├── app/                     # Android module
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/sleepsound/
│       │   ├── MainActivity.kt
│       │   ├── PlaybackController.kt       singleton playback state
│       │   ├── BootReceiver.kt             optional resume-on-reboot
│       │   ├── audio/                      SoundId, tier, catalog
│       │   │   ├── engine/                 AudioTrack pipeline + mixer
│       │   │   └── procedural/             noise generators
│       │   ├── billing/                    Play Billing + entitlement
│       │   ├── service/                    foreground service + session
│       │   └── ui/                         Compose screens / components
│       └── res/                            AMOLED theme, drawables, strings
├── store/                   # Play Console submission artifacts
│   ├── STORE_LISTING.md     # title / desc / what's-new (char-counted)
│   ├── PRIVACY.md           # Google-policy-aligned privacy policy
│   └── DATA_SAFETY.md       # Data Safety form answers
├── docs/                    # Hosted via GitHub Pages
│   └── privacy/index.html   # Privacy policy public URL
├── tools/preview/           # HTML mockups → Playwright screenshots
├── BUILDING.md              # How to build + sign + release
├── CHANGELOG.md
└── PLAN.md                  # Original product/architecture plan
```

## Getting started

Full instructions in [`BUILDING.md`](./BUILDING.md). TL;DR:

1. Install JDK 17 and the Android command-line tools (or Android Studio
   Hedgehog or later).
2. Open the project in Android Studio — it'll generate the Gradle wrapper
   on first sync.
3. Run the `app` configuration on a device or emulator.

## Tech notes

| Piece | Choice |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| Min / Target SDK | 29 / 35 |
| Audio (procedural) | `AudioTrack` USAGE_MEDIA / CONTENT_MUSIC, 48 kHz PCM16 stereo |
| Audio (sample hook) | Custom `MediaExtractor`+`MediaCodec` decode → in-memory PCM |
| Foreground service | `LifecycleService` + `MediaSessionCompat` + MediaStyle notification |
| Billing | Play Billing Library 8.3.0 |
| DI | None — long-lived singletons (`PlaybackController`, `EntitlementStore`, `BillingManager`) |
| Persistence | `SharedPreferences` (one for playback state, one for entitlements) |
| Tests | Not yet — punch-list item |

See [`PLAN.md`](./PLAN.md) §4 for the planned-vs-actual divergences table.

## Status

**v1.0.0-rc1.** Feature-complete for first launch. Still pre-launch
tasks: hosted privacy policy URL, production signing key, Play Console
content rating + closed-test track, real device validation. See
[`CHANGELOG.md`](./CHANGELOG.md).

## License

Source code: All Rights Reserved (the Play Store product is paid).
Procedural noise algorithms reference common public-domain DSP
techniques (Paul Kellet pink-noise filter, etc.).
