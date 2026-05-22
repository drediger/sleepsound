# Building Sleep Soundly

End-to-end instructions for a fresh machine through a signed Play Store
upload bundle.

---

## 1. Toolchain

### Minimum versions

| Tool | Version | Why |
|---|---|---|
| JDK | 17 | Gradle 8.11 + Android Gradle Plugin 8.7 target Java 17 |
| Android SDK | platform 35, build-tools 35.0.0 | `targetSdk = 35` |
| Gradle | 8.11.1 | declared in `gradle/wrapper/gradle-wrapper.properties` |
| Android Studio | Hedgehog (2023.1.1) or newer | Compose preview + AGP 8.7+ |

### macOS install (Homebrew)

```bash
brew install --cask temurin@17        # JDK 17
brew install --cask android-commandlinetools
yes | sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Set environment in your shell rc:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
```

### Windows install

Install JDK 17 from Adoptium and Android Studio. Both put themselves on
`PATH` automatically. Set `JAVA_HOME` and `ANDROID_HOME` in *System
Properties → Environment Variables*.

---

## 2. First-time clone setup

This repo does not commit a Gradle wrapper jar (`gradlew` / `gradlew.bat`
/ `gradle/wrapper/gradle-wrapper.jar`). Android Studio will generate one
on first sync. If you prefer the command-line:

```bash
# from a machine that already has gradle installed (`brew install gradle`)
gradle wrapper --gradle-version 8.11.1
```

Then commit the four generated files. Alternatively, open the project in
Android Studio once and let it produce them.

---

## 3. Building

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK (needs signing config)
./gradlew :app:bundleRelease      # release .aab for Play Console
./gradlew :app:testDebugUnitTest  # JVM unit tests (procedural audio + SoundId)
```

Debug builds use the application ID `io.github.drediger.sleepsoundly.debug` so they can
be installed alongside a production install.

The unit-test suite is JVM-only (no instrumented Android tests yet) —
JUnit 4 with two test classes covering procedural audio determinism +
SoundId tier invariants. Run them before every commit; they finish in
~15 s including Gradle warm-up. Compose UI and instrumented tests are
still on the punch list.

---

## 4. Release signing

The release build is wired through the standard `signingConfigs` block
in [`app/build.gradle.kts`](./app/build.gradle.kts). Credentials are
read from `gradle.properties` (or environment variables) so nothing
secret lives in the repo.

### Generate the upload keystore (one time)

```bash
keytool -genkey -v \
  -keystore sleepsound-upload.jks \
  -storetype JKS \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias sleepsound
```

Keep this `.jks` somewhere outside the repo. **Never commit it.**

### Wire credentials

Add the following to your **user-global** `~/.gradle/gradle.properties`
(not the project-local one):

```properties
SLEEPSOUND_KEYSTORE_PATH=/Users/you/keys/sleepsound-upload.jks
SLEEPSOUND_KEYSTORE_PASSWORD=...
SLEEPSOUND_KEY_ALIAS=sleepsound
SLEEPSOUND_KEY_PASSWORD=...
```

Or export the same names as environment variables when invoking gradle:

```bash
SLEEPSOUND_KEYSTORE_PATH=/path/to/key.jks \
SLEEPSOUND_KEYSTORE_PASSWORD=... \
SLEEPSOUND_KEY_ALIAS=sleepsound \
SLEEPSOUND_KEY_PASSWORD=... \
./gradlew :app:bundleRelease
```

If any of those four properties are missing, the release build falls
back to the debug keystore (so local QA still works) and a warning
prints. Play Console will reject a debug-signed `.aab`, so set them
properly before upload.

### Play App Signing

Google strongly recommends enrolling in Play App Signing on first
upload. Google manages the signing key after upload; you only ever
sign with your upload key. Enroll in Play Console → Setup → App
integrity.

---

## 5. Running on a device

```bash
./gradlew :app:installDebug
adb shell am start -n io.github.drediger.sleepsoundly.debug/com.sleepsound.MainActivity
```

For audio-reliability testing (the load-bearing claim — overnight
survival under Doze + OEM-killers):

```bash
# Force Doze mode for a minimum 10 minutes of overnight simulation
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle step       # advance through states
adb shell dumpsys deviceidle unforce    # restore normal

# Reproduce the "audio-becoming-noisy" path
adb shell media dispatch headsetunplug

# Trigger audio focus loss (simulates phone call)
adb shell am start -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  -n com.google.android.dialer/.app.MainActivity
```

---

## 6. Releasing to Play Console

1. `./gradlew :app:bundleRelease` → produces
   `app/build/outputs/bundle/release/app-release.aab`
2. Verify the AAB locally:
   ```bash
   bundletool build-apks --bundle=app-release.aab --output=test.apks \
     --connected-device
   bundletool install-apks --apks=test.apks
   ```
3. Upload the AAB to Play Console.
4. Fill out the listing using copy from [`store/STORE_LISTING.md`](./store/STORE_LISTING.md).
5. Complete the Data Safety form using answers from [`store/DATA_SAFETY.md`](./store/DATA_SAFETY.md).
6. Provide the hosted privacy-policy URL (`docs/privacy/` published via
   GitHub Pages — see below).
7. Register six $0.99 per-sound product IDs plus the bundle:
   ```
   sound_pink_noise
   sound_violet_noise
   sound_thunderstorm
   sound_dryer
   sound_fan
   sound_fireplace
   bundle_all_sounds      ($3.99)
   ```
   These must match the IDs in
   [`app/src/main/java/com/sleepsound/audio/SoundTier.kt`](./app/src/main/java/com/sleepsound/audio/SoundTier.kt)
   and
   [`app/src/main/java/com/sleepsound/billing/BillingManager.kt`](./app/src/main/java/com/sleepsound/billing/BillingManager.kt).
8. First release: push to **Internal testing** (or **Closed testing**) —
   not Production. Iterate any review issues before promoting.

---

## 7. Publishing the privacy policy

Google Play requires a publicly accessible, non-PDF, non-editable URL.

Sleep Soundly's policy is served via GitHub Pages from this repo at
<https://drediger.github.io/sleepsound/privacy/>. Pages source is
`main` branch, `/docs` folder. The repo must be public for free-tier
Pages — Sleep Soundly's is.

To rotate the URL (e.g. moving to a custom domain):

1. Edit `docs/privacy/index.html` (or add a new file).
2. Update `app/src/main/res/values/strings.xml#privacy_policy_url`.
3. Update `store/STORE_LISTING.md` PRIVACY section.
4. Push to `main` — Pages rebuilds within ~30 s.

---

## 8. Real-device QA

Pre-launch test matrix lives in
[`store/QA_MATRIX.md`](./store/QA_MATRIX.md). Three checks are
load-bearing for the app's positioning (overnight survival, true
AMOLED black, no-tracking audit); the rest are device-coverage.

---

## 9. Generating preview screenshots

Independent of the Android build. Used during UI iteration without
hitting an emulator.

```bash
# Defaults to looking for Playwright in ../../../riftlens/frontend/node_modules
node tools/preview/screenshot.mjs

# Or override the Playwright location
SLEEPSOUND_PLAYWRIGHT_DIR=/path/to/some/node_modules \
  node tools/preview/screenshot.mjs

# Or `npm install playwright` inside tools/preview/ and it'll auto-find it
```

PNGs land in `tools/preview/screenshots/` (gitignored).
