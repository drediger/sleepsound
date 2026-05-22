# Real-device QA matrix

> Pre-launch test plan for Sleep Soundly. Three of these checks are
> load-bearing for the app's positioning (overnight audio survival,
> AMOLED true-black, no-data-collection) — fail any of those and the
> launch is blocked, not just delayed.

---

## Devices to cover

| Tier | Device | Reason |
|---|---|---|
| Must-have | **Samsung Galaxy S-series** (One UI) | Most-installed OEM globally; aggressive "Deep Sleep" battery killer. We have an S25 in-house. |
| Must-have | **Pixel** (stock Android) | Reference platform — if it doesn't work here, the bug is in our code, not an OEM quirk. |
| Should-have | **Xiaomi / Redmi** (MIUI / HyperOS) | Tied with One UI as the worst overnight-audio killer. `dontkillmyapp.com` rates it as one of the worst offenders. |
| Should-have | **OnePlus** (OxygenOS) | Aggressive background-kill behavior since OxygenOS 12. |
| Nice-to-have | **Motorola** (~$200) | Low-end Snapdragon CPU. Stress-tests procedural noise + sample decode under tight resources. |
| Nice-to-have | **Tablet** (any 7"+ Android) | Verifies the player grid reflow at wider widths. |

In practice, ship after the two must-haves pass; document Xiaomi /
OnePlus / Motorola gaps in `CHANGELOG.md` "Known gaps" and let users
report.

---

## Per-device checklist

### 1. Overnight audio survival (load-bearing)

1. Connect device to charger (so battery-low doesn't taint the test).
2. Launch the app, choose a sample-backed sound (Rain) and a
   procedural-backed sound (Brown noise), so both code paths exercise.
3. Set a 10-hour timer.
4. Lock the screen.
5. Leave overnight (≥ 8 hours).
6. In the morning: audio should still be playing, the notification
   should still be present, the timer should still be counting down.

**Pass criteria:** audio uninterrupted for the entire timer duration.

**If it fails:** capture `adb logcat -d -t 2000` immediately on wake
and look for `handleStop: reason=...`. The reason field (added in the
v1.0 polish commit) names the killer.

### 2. AMOLED true-black at low brightness (load-bearing)

1. Bedroom lights off, screen brightness minimum.
2. Open the app from a fully-dim state (force-stop first, then
   relaunch).
3. Verify: status text, tile icons, settings cog, and timer pill are
   readable; background is genuinely black (no grey rectangle around
   the app), no gradients, no faint colour casts.
4. Tap a sound — active border + brighter label should differentiate
   without being eye-blinding.
5. Wait 30 s for `IdleDimmer` to kick in. Tap to wake — the screen
   restores brightness on the same frame (no 3–5 s lag).

**Pass criteria:** all UI elements visible at min brightness; no
gradient artefacts; instant tap-to-wake.

### 3. Audio focus + interruption matrix

| Trigger | Expected behaviour |
|---|---|
| Receive a phone call while playing | App pauses cleanly; resumes after the call ends. |
| Spotify / YouTube Music starts playing | App stops (`focusLossPermanent` in logcat); notification clears. |
| Google Maps directions fire | App ducks to ~30 % volume; un-ducks when prompt ends. |
| Unplug wired headphones mid-playback | App stops (`becomingNoisy` in logcat); no speaker blast. |
| Bluetooth headphones disconnect | Same as wired unplug — app stops. |
| Lock screen → unlock | Audio uninterrupted, notification persists. |
| Swipe app away from recents | Audio uninterrupted (sleep-app design). |
| Tap "Stop" in the notification | Notification clears within ~200 ms; audio fades out over 1.5 s in background. |

### 4. Bluetooth ghost-connection (One UI quirk)

Galaxy phones with paired-but-not-connected Bluetooth devices can
fire `ACTION_AUDIO_BECOMING_NOISY` spuriously on `AudioTrack` create.

1. Pair a Bluetooth device (e.g. car stereo) but keep it powered off /
   out of range.
2. Start a sound in the app.
3. If audio cuts within 2–3 s with `becomingNoisy` in the log:
   reproducible bug. Forget the BT device and retry.

### 5. OEM battery-killer survival

The Settings → "Allow background playback" row opens Android's
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog. Verify on each device:

1. Open Settings sheet, tap "Allow background playback".
2. System dialog appears with "Allow" / "Don't allow".
3. Tap Allow.
4. Re-open Settings sheet — the row is dismissible (no infinite-loop
   re-prompts).
5. Confirm in OS settings: Apps → Sleep Soundly → Battery → unrestricted.

### 6. Play Billing flow (signed release build only)

1. Install the **signed release** AAB via internal-testing track.
2. Tap a locked premium sound. Preview starts.
3. Wait 30 s for the preview to expire — tile flips to `$0.99 →`.
4. Tap the buy pill. Google Play Billing sheet appears.
5. Complete a test purchase (Play Console → License Testing).
6. Sound unlocks. Verify: persists across app force-stop + relaunch.
7. Tap Settings → Restore purchases. Verifies entitlement re-sync.
8. **Refund test:** request a refund in Play Console; on next
   `onResume`, the sound re-locks.

### 7. Reboot resume

1. Toggle Settings → "Resume on reboot" on.
2. Start Rain + Brown noise.
3. Reboot the device.
4. After reboot, before unlocking: audio should resume automatically
   within ~10 s.

### 8. No-tracking audit

This is the visible-claim audit; failure means the privacy copy is
false.

1. Connect device to a packet-capture proxy (mitmproxy, Charles, etc.)
   with a system-trusted CA.
2. Launch the app from cold start.
3. Toggle every feature: pick sounds, change volume, set timer,
   open Settings, tap privacy link.
4. **Expect:** zero outbound HTTPS connections from
   `io.github.drediger.sleepsoundly`. The Play Billing flow's
   connections go through the Play Services process, not our package.
5. Verify the `INTERNET` permission is *not* declared in
   `AndroidManifest.xml` (it isn't, but re-confirm before release).

---

## Per-version smoke test (every CI build)

The above is full QA. For each tag / build, the minimum smoke test:

1. Install debug APK.
2. Launch — no crash on cold start.
3. Tap a sound — audio plays.
4. Tap stop — audio fades, notification clears.
5. Tap stop *immediately* after starting a new sound (within 1 s) —
   second sound continues playing past 3 s mark (regression guard for
   the `AudioEngine.stop` watchdog vs. `start` race fixed
   2026-05-19).
6. Background → return to app — no state loss.
7. `./gradlew :app:testDebugUnitTest` — all unit tests pass.

---

## What's not tested here

- **Wear OS** — explicitly v1.5; no companion module yet.
- **Android Auto** — `MediaSession` integration should pick this up
  for free but we don't have an Auto head unit on hand.
- **Foldables** — Compose layouts adapt automatically; no specific
  testing planned for v1.0.
- **Right-to-left locales** — only the volume-up/down icons are
  `AutoMirrored`. Other strings are LTR-only; we'd want full RTL
  audit before localizing.
