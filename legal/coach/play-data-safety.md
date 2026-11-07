# Squadify Coach — Google Play data safety answers

What to fill in Play Console → App content for the coach app (`com.ricardofrango.squadify.coach`), as the app
is built (2026-11-07). Keep it in step with `privacy-policy.en.md`; a change to what the app collects changes
both.

## App content declarations

| Declaration | Answer |
|---|---|
| Privacy policy | The public URL of `privacy-policy.en.md` / `.pt.md` (to host; see the PR) |
| Ads | **Yes, the app contains ads** (AdMob banner, Free plan only) |
| Advertising ID | **Yes**, used for **Advertising or marketing** (AdMob). The ads SDK adds `com.google.android.gms.permission.AD_ID` to the manifest |
| Target audience | **18 and over** only. Including under-13s brings Google's Families policy (stricter ad rules, no advertising ID for children) |
| App access | Sign-in required: give the reviewers a test account (a Free space with an age group, a player and a game) |
| Content rating | Fill the questionnaire: no violence, no user-to-user chat, ads present |
| Account deletion | In the app (Account → Delete account) **and** a web URL where people can ask for it without the app: required. A page with the steps and `{{CONTACT_EMAIL}}` is enough |

## Data safety form

**Overview questions**

- Does the app collect or share any of the required user data types? **Yes**
- Is all user data collected by the app encrypted in transit? **Yes** (HTTPS everywhere)
- Do you provide a way for users to request that their data is deleted? **Yes** (in the app, and by email)

**Data types.** "Collected" = sent off the device. "Shared" = given to a third party other than a service
provider acting for us (Railway, Firebase, Resend and Google Play as payment processor count as service
providers, so they aren't "shared"; AdMob's own use of ad data is "shared").

| Category → type | Collected | Shared | Optional? | Purposes | Where it comes from |
|---|---|---|---|---|---|
| Personal info → **Name** | Yes | No | Required | App functionality, Account management | The coach's name; players' names |
| Personal info → **Email address** | Yes | No | Required | App functionality, Account management | Sign-up; staff invites sent by email |
| Personal info → **Phone number** | Yes | No | Optional | App functionality, Account management | Sign-up and Account |
| Personal info → **Other info** | Yes | No | Optional | App functionality | Players' date of birth, shirt number, position, height, weight, emergency contact |
| Financial info → **Purchase history** | Yes | No | Optional | App functionality | Google Play subscriptions (plan, period, purchase token) |
| Photos and videos → **Photos** | Yes | No | Optional | App functionality | The coach's and players' photos, opponents' and the space's logos |
| App activity → **App interactions** | Yes | **Yes** | Optional | Advertising or marketing, Analytics, Fraud prevention | AdMob (Free plan) |
| App activity → **Other user-generated content** | Yes | No | Required | App functionality | Games' events, ratings, training, notes |
| Location → **Approximate location** | Yes | **Yes** | Optional | Advertising or marketing, Fraud prevention | AdMob, from the IP address (Free plan) |
| App info and performance → **Crash logs** | Yes | No | Required | App functionality, Analytics | Firebase Crashlytics |
| App info and performance → **Diagnostics** | Yes | **Yes** | Required | App functionality, Analytics, Advertising | Crashlytics (device model, Android version); AdMob's SDK diagnostics |
| Device or other IDs → **Device or other IDs** | Yes | **Yes** | Required | App functionality, Advertising or marketing, Fraud prevention | The push token (Firebase Cloud Messaging); the advertising ID (AdMob) |

**Is data processed ephemerally?** No for all of the above.

**Not collected:** Health and fitness, Messages, Contacts, Calendar, Files and docs, Audio, Web browsing,
Precise location, Race/ethnicity, Political or religious beliefs, Sexual orientation, Financial info other
than purchase history (no card details: Google Play handles payment).

## To check before submitting

- **Height and weight** are listed under Personal info → Other info. If Play's reviewers read them as health
  data, move them to Health and fitness → Health info (Collected, Optional, App functionality).
- **AdMob's own declaration:** Google publishes what the Google Mobile Ads SDK collects
  (https://developers.google.com/admob/android/privacy/play-data-disclosure). Compare with the AdMob rows above
  when the SDK's version changes.
- **Crashlytics** keeps crash reports for 90 days (Firebase's default).
