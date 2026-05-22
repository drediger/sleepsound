# Privacy policy — Sleep Soundly

> **Canonical source: [`docs/privacy/index.html`](../docs/privacy/index.html)**,
> hosted at <https://drediger.github.io/sleepsound/privacy/>. That HTML is
> what Play Console links to. This markdown remains in the repo as a
> requirements checklist (the bracketed `[Required: ...]` tags below trace
> each section back to a clause in
> [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/9888076)).
> If you edit policy text, edit the HTML first; sync back here only as
> needed for future audits.

**Effective date:** 2026-05-22
**Last updated:** 2026-05-22

This is the privacy policy for **Sleep Soundly**, an Android sleep-sound app
published on Google Play by **David Rediger** ("we", "us", or "the developer").
[Required: entity in the listing must appear in the privacy policy.]

## Summary

Sleep Soundly is a fully offline app. It does **not** collect, transmit,
share, or sell any personal or sensitive user data. It has no analytics,
no advertising identifiers, no crash-reporting service, and no network
calls of its own.

## Data we access or collect

We access or collect **no personal or sensitive user data**. Specifically:

- We do not collect your name, email address, phone number, mailing
  address, or any other identifier.
- We do not access your location, contacts, calendar, camera, microphone,
  files, photos, SMS, or call logs.
- We do not use any Advertising ID, ad-attribution SDK, or analytics SDK.
- We do not maintain a server. The Sleep Soundly application binary does not
  declare the Android `INTERNET` permission and is incapable of making
  outbound network connections from within the app.
- Crash reports collected automatically by Google Play (via the
  pre-installed Google Play system) are governed by
  [Google's privacy policy](https://policies.google.com/privacy), not
  this one. We do not receive personally identifying information from
  those reports.

[Required: disclose personal and sensitive data types accessed or
collected.]

## Information stored on your device

The following is stored only in app-private storage on your device. It
never leaves your device:

- The set of sounds you've selected and your per-sound volume settings,
  so the app remembers your last mix between launches.
- Your master volume.
- Whether you've opted into "resume on reboot."

You can delete all of this at any time by clearing the app's storage
(Android Settings → Apps → Sleep Soundly → Storage → Clear storage) or by
uninstalling the app.

## Data sharing

We share **no** user data with any third party. We do not have any
server, partner, vendor, or advertiser to share data with.

[Required: parties with whom personal or sensitive user data is shared.]

## Links to third-party sites

The app contains links you can choose to open. These open in your default
browser and are governed by the privacy policy of the destination:

- The "OEM settings" link in the in-app settings opens
  [dontkillmyapp.com](https://dontkillmyapp.com), which is an independent
  community resource not affiliated with us.
- The "Privacy policy" link in the in-app settings opens this document.

When you tap one of these links, your browser may send standard request
information (IP address, User-Agent) to the destination server. We have
no control over and receive no copy of that information.

## Security

Because Sleep Soundly does not transmit user data, there is no in-transit
data to protect. Locally stored preferences use Android's standard
app-private storage, which Android isolates from other apps by default.

[Required: secure data handling procedures.]

## Data retention and deletion

We retain no user data because we collect none. Local on-device
preferences are retained until you clear the app's storage or uninstall
the app, both of which immediately and permanently remove them.

[Required: data retention and deletion policy.]

## Children's privacy

Sleep Soundly is intended for adults. It does not knowingly collect any
information from children under 13 (or the applicable age in your
jurisdiction). If you believe a child has provided information to us,
please contact us using the contact details below and we will delete any
such information (in practice, none can exist because we do not collect
any).

## Changes to this policy

If we change this policy, we will update the "Last updated" date at the
top of this page and, where the change is material, post a notice in
the app's "What's new" section on Google Play before the new version
takes effect.

## Contact

If you have a question or wish to submit an inquiry about this policy:

- Email: see hosted policy at <https://drediger.github.io/sleepsound/privacy/>
  (the markdown copy intentionally does not duplicate the contact details
  to avoid drift; the HTML is the publishable source)
- Or open an issue at <https://github.com/drediger/sleepsound/issues>.

[Required: developer information and a contact or inquiry mechanism.]

---

## Implementation notes — delete before publication

This policy is drafted to satisfy the requirements documented in
[Google Play's User Data policy](https://support.google.com/googleplay/android-developer/answer/9888076),
which mandates:

1. *"All apps must post a privacy policy link in the designated field
   within Play Console, and a privacy policy link or text within the app
   itself."* — Status: **app already exposes a Privacy Policy link in
   `SettingsBottomSheet`**, pointing at `R.string.privacy_policy_url`.
   Update that string to the published URL before submission.

2. *"Developer information and a privacy point of contact or a mechanism
   to submit inquiries."* — Status: **placeholder in the Contact section
   above.** Replace `<Developer / Company name>`, `<contact email>`, and
   (optional but recommended) postal address before publication.

3. *"Information about personal and sensitive data types accessed or
   collected."* — Status: **answered as "none"**, accurate per the source
   audit (`grep -rE "INTERNET|firebase|crashlytics|analytics|sentry" ...`
   returns no hits; manifest has no `INTERNET` permission).

4. *"Any parties with which any personal or sensitive user data is
   shared."* — Status: **answered as "none"**, correct.

5. *"Secure data handling procedures for personal and sensitive user
   data."* — Status: **addressed in the Security section.**

6. *"The developer's data retention and deletion policy."* — Status:
   **addressed in the Data retention and deletion section.**

Per Google's publication requirements, the URL hosting this document
must be:

- **publicly accessible and non-geofenced** — host it on something
  always-on (GitHub Pages, Cloudflare Pages, your own static site).
  *Do not* host as a PDF.
- **non-editable** — i.e. a static page, not a wiki anyone can change.
- **clearly labeled as a privacy policy in the title** — the `<title>`
  HTML tag should include "Privacy policy."

Once published, update `app/src/main/res/values/strings.xml` —
`privacy_policy_url` — and replace `<PRIVACY_POLICY_URL>` in
`STORE_LISTING.md`.
