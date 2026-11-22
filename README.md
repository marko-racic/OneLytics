# OneLytics

Project page: <https://marko.racic.rs/OneLytics/>

A 1×1 Android home-screen widget that shows **today's GA4 sessions** for one Google Analytics
property. No account, no server, no tracking of the user — the app talks straight to Google.

**No ads, no analytics, no tracking, no accounts, no background services — just a 43 KB app
that does what it says.**

- Sign-in: Google OAuth (PKCE, no client secret), scope `analytics.readonly`
- Data: GA4 Data API `runReport`, metric `sessions`, today only
- Refresh: `JobScheduler` on your chosen interval (15 min … 1 day) + tap the widget to refresh now
- Multiple widgets, each with its own property and colour palette
- No third-party libraries, `minSdk 26`

## Build

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
gradle assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The OAuth client ID lives in `gradle.properties` (`gaClientId=...`); the redirect scheme is
derived from it automatically.

### Signing

The keystore and its passwords are **not** in this repository. Put them in your local
`~/.gradle/gradle.properties`:

```properties
KEYSTORE_FILE=/path/to/onelytics.keystore
KEYSTORE_PASSWORD=...
KEY_PASSWORD=...
```

Without those three properties the release build still works, it is just unsigned.

## Google Cloud setup (once)

1. console.cloud.google.com → new project (e.g. `onelytics`)
2. APIs & Services → Library → enable **Google Analytics Data API** **and** Google Analytics Admin API (property list)
3. OAuth consent screen → External → app name "OneLytics", test user = your Gmail
4. Credentials → Create → **OAuth client ID** → type **Android**
   - Package: `rs.racic.onelytics`
   - SHA-1: your release key's SHA-1
5. Put the client ID into `gradle.properties` → rebuild
6. No extra user needs to be added in GA4 — you sign in with your own Google account that already
   has access to the property.

## Notes

- `analytics.readonly` is a Google **restricted** scope: a public Play release requires OAuth
  verification of the app, internal/external testing tracks do not.
- Nothing leaves the phone except the requests to Google APIs; tokens are stored only in the
  app's private storage.

## Links

- Project page: <https://marko.racic.rs/OneLytics/>
