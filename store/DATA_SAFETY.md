# Data Safety form — Sleep Soundly

> Question-by-question answers for the Google Play Console **Data safety**
> section, sourced from
> [Data safety section in Google Play](https://support.google.com/googleplay/android-developer/answer/10787469).
> Each section restates the question Google asks, the answer to give, and
> *why* — anchored to the actual app behavior (`AndroidManifest.xml`,
> `build.gradle.kts`, and source code).

---

## Section 1 — Data collection and security

Google asks: *"Does your app collect or share any of the required user
data types?"*

> **Answer: No.**

Why this is correct:

- The app does not declare `android.permission.INTERNET` in
  `AndroidManifest.xml`. Without it, the app cannot make outbound network
  calls. (Sourced: see `app/src/main/AndroidManifest.xml`.)
- The Gradle dependency graph contains no analytics, crash-reporting,
  advertising, attribution, or networking library. (Sourced:
  `gradle/libs.versions.toml` lists only AndroidX core/lifecycle/activity
  /compose/material3.)
- A repo-wide search for `firebase|crashlytics|analytics|sentry|amplitude
  |mixpanel|posthog|telemetry|okhttp|retrofit|urlConnection` returns no
  third-party SDK references.

Google's own definition supports this answer: *"Collection refers to
transmitting data from your app off a user's device."* Sleep Soundly never
transmits anything.

Google asks: *"Is all of the user data collected by your app encrypted in
transit?"*

> **Answer: Not applicable** — the app collects no data, so there is no
> in-transit data to encrypt. Mark this only if the Console UI requires
> a selection; otherwise leave it as derived from the "no data" answer.

Google asks: *"Do you provide a way for users to request that their data
be deleted?"*

> **Answer: Not applicable / No data to delete.**
>
> Locally-stored preferences (selected sounds, per-sound volume,
> master volume) can be cleared by the user at any time via
> Android Settings → Apps → Sleep Soundly → Storage → Clear storage. This is
> a standard Android facility; we do not host any server-side data.

---

## Section 2 — Data types

For every category Google offers, the answer is **"Not collected, not
shared."** The categories (sourced from
[Data safety help](https://support.google.com/googleplay/android-developer/answer/10787469))
are:

| Category | Sub-types | Answer |
|---|---|---|
| **Location** | Approximate, Precise | Not collected, not shared |
| **Personal info** | Name, Email, User IDs, Address, Phone, Race & ethnicity, Political/religious beliefs, Sexual orientation, Other | Not collected, not shared |
| **Financial info** | Payment info, Purchase history, Credit score, Other financial info | Not collected, not shared. *(See note on Play Billing below if Pro tier ships in v1.)* |
| **Health & fitness** | Health info, Fitness info | Not collected, not shared |
| **Messages** | Emails, SMS/MMS, Other in-app messages | Not collected, not shared |
| **Photos and videos** | Photos, Videos | Not collected, not shared |
| **Audio files** | Voice/sound recordings, Music files, Other audio files | Not collected, not shared |
| **Files and docs** | Files and docs | Not collected, not shared |
| **Calendar** | Calendar events | Not collected, not shared |
| **Contacts** | Contacts | Not collected, not shared |
| **App activity** | App interactions, In-app search history, Installed apps | Not collected, not shared |
| **Other user-generated content** | Any UGC | Not collected, not shared |
| **Web browsing** | Web browsing history | Not collected, not shared |
| **App info and performance** | Crash logs, Diagnostics, Other app performance data | Not collected, not shared. **See note below.** |
| **Device or other IDs** | Device or other IDs | Not collected, not shared |

### Note: Google Play's automatic crash reporting

Crash reports collected automatically by the **Android operating system /
Google Play services** (the Vitals dashboard) are produced by Google,
sent to Google, and governed by
[Google's privacy policy](https://policies.google.com/privacy). Google's
own guidance is that this is not "collection by you, the developer," and
should *not* be declared in your Data Safety form. We have not integrated
Firebase Crashlytics or any other crash SDK that we would need to
disclose.

### Note: Play Billing (per-sound IAPs ship in v1.0)

The v1.0 release ships seven $0.99 one-time per-sound unlocks. Each
purchase is handled entirely by **Google Play Billing**. Payment info is
collected by Google, not by Sleep Soundly, and is governed by
[Google's privacy policy](https://policies.google.com/privacy). Per
Google's guidance, "data collected and processed by Google Play" does
not need to be declared in your Data Safety form. Mark **"Payment info"
as Not collected, not shared** by Sleep Soundly itself.

The only data Sleep Soundly itself stores about purchases is *which*
product IDs the user owns — kept in app-private SharedPreferences
(`entitlements.unlocked_sound_ids`) to avoid a Play Billing round-trip on
every launch. This is local-only state, not "collection" in Google's
sense, and is cleared by uninstalling the app or clearing storage.

---

## Section 3 — Privacy policy link

Google asks for a URL. Paste the published URL of `PRIVACY.md` here.

Requirements (sourced from
[Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/9888076)):

- *"Available on an active, publicly accessible and non-geofenced URL
  (no PDFs)"*
- *"Non-editable"*
- *"Clearly labeled as a privacy policy in the title"*
- *"The entity (for example, developer, company) named in the app's
  Google Play store listing must appear in the privacy policy or the
  app must be named in the privacy policy."*

Suggested hosting: GitHub Pages, Cloudflare Pages, or a static page on
your own domain. Set the HTML `<title>` to "Privacy policy — Sleep Soundly".

---

## Section 4 — Permissions disclosed in the listing

Google does not ask about permissions in the Data Safety form itself,
but reviewers cross-reference them. The permissions Sleep Soundly requests
(from `AndroidManifest.xml`), and the user-facing justification for each,
are:

| Permission | Purpose | User-facing justification |
|---|---|---|
| `FOREGROUND_SERVICE` | Required to host the audio service | Keeps the audio playing while the screen is off. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required on Android 14+ for media-playback FGS | Same — required by Android 14+ to declare the FGS as a media-playback one. |
| `POST_NOTIFICATIONS` | Show the persistent media-control notification | The Android system requires apps to ask for notification permission to display the playback control. |
| `WAKE_LOCK` | Prevent CPU sleep while audio is playing | So Doze does not silence the audio mid-night. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Opens the system dialog so the user can grant the app battery-optimization exemption | The user chooses whether to grant it; the system shows the dialog. |

None of these permissions cause data collection in the Google Play sense,
so none triggers a Data Safety disclosure. They are operational, not
data-collecting.

---

## Section 5 — Data Safety form: copy-paste answers

For each Data Safety form question that the Console UI presents, here
are the verbatim selections to make:

1. **"Does your app collect or share any of the required user data
   types?"** → **No**
2. **"Is all of the user data collected by your app encrypted in
   transit?"** → not applicable; the Console hides this when the prior
   answer is "No"
3. **"Do you provide a way for users to request that their data be
   deleted?"** → not applicable; same reason
4. **Privacy policy URL** → *paste the published `PRIVACY.md` URL*
5. **For each data type prompt that does appear**, select
   **"Not collected" and "Not shared"**
6. **"Independent security review"** (optional badge) — leave blank for
   v1.0 unless you've actually had one.

After saving the form, the Console will render a "Data safety" panel on
the public listing that reads roughly:
*"This app does not collect or share any of the listed data with
third parties. This app collects no data."* That panel is the marketing
moment for the positioning. Make sure the listing screenshots and copy
reinforce it.
