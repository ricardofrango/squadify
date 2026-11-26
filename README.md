This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM), Server.

* [/app/iosApp](./app/iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/app/clubs](./app/clubs/src) is Squadcall for Clubs: its navigation, Koin setup, iOS entry and the screens only clubs have (TECHNICAL_DECISIONS.md §72). Every clubs entry point (Android, iOS, web, desktop) starts it.

* [/app/coach](./app/coach/src) is Squadcall for Coaches: its navigation, Koin setup, strings and the screens only the coach app has (the home with every age group, setting up their own space, joining with a code, inviting staff). [/app/coachAndroidApp](./app/coachAndroidApp) is its Android app, [/app/coachIosApp](./app/coachIosApp) its iOS app.

* [/app/shared](./app/shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./app/shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./app/shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./app/shared/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/clubs/src/main/kotlin) is for the Ktor server application.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Coach Android app (Squadcall for Coaches): `./gradlew :app:coachAndroidApp:assembleDevDebug`, or its run configuration. The dev flavor talks to the coach server on this machine (port 8090), so start it first (below). It installs next to the clubs app.
- Desktop app:
  - Hot reload: `./gradlew :app:desktopApp:hotRun --auto`
  - Standard run: `./gradlew :app:desktopApp:run`
- Server, in two terminals:
  - `./gradlew :server:clubs:run` runs the server (port 8080, or `SERVER_PORT`). Stop it with Ctrl+C.
  - `./gradlew -t :server:clubs:classes` compiles on every save; the running server reloads the new code by itself (see `TECHNICAL_DECISIONS.md` §59).
  - Don't use `./gradlew -t :server:clubs:run`: it never reloads, and it can leave a server holding port 8080 after Ctrl+C. To free the port: `lsof -ti tcp:8080 | xargs kill`.
- Coach server (Squadcall for Coaches): `./gradlew :server:coach:run` (port 8090, or `SERVER_PORT`). It creates its own local database, `squadcall_coach_dev`, on the same Postgres the first time.
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :app:webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/app/iosApp](./app/iosApp) directory in Xcode and run it from there.
- Coach iOS app: open [/app/coachIosApp](./app/coachIosApp) in Xcode and run the `coachIosApp` scheme. Debug talks to the coach server on this machine (port 8090); pick the Staging configuration in the scheme for coach-server on Railway staging.

### Server configuration

Each server reads its settings from environment variables (on Railway: the service's Variables tab; a change needs a restart). Locally every one has a default that works with `docker compose up -d`. The table says where a value is required when the server is deployed: the coach server when `APP_ENV` is `staging` or `production`, the clubs server when it's `production`. The reasons are in `TECHNICAL_DECISIONS.md` (§3, §57, §72, §81 to §85).

| Variable | Servers | What it does |
|---|---|---|
| `APP_ENV` | both | `production` or `staging`, on Railway; unset is local development. The clubs server runs with the development settings unless it is `production`, but `staging` counts as deployed for the seed account. |
| `SERVER_PORT` | both | The port; 8080 (clubs) or 8090 (coach) by default. |
| `DATABASE_HOST`, `DATABASE_PASSWORD` | both | Required when deployed. Locally `localhost` and `postgres`. |
| `DATABASE_PORT`, `DATABASE_USER`, `DATABASE_NAME` | both | 5432, `postgres`, and `squadcall_dev` / `squadcall_production` (clubs) or `squadcall_coach_dev` / `squadcall_coach_production` (coach). |
| `JWT_SECRET` | both | Required when deployed. Each server has its own: a token from one is refused by the other. |
| `RESEND_API_KEY`, `RESEND_FROM_ADDRESS` | both | Emails. Optional: without a key the server logs and skips each email. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | both | The raw JSON of the service account for pushes (the coach server's own Firebase project). Optional: without it each push is logged and skipped. |
| `STORAGE_BUCKET`, `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY` | both | The S3-compatible bucket for images (a Railway Storage Bucket). Without a bucket, images go to `LOCAL_IMAGE_DIR` (`uploads`). |
| `PUBLIC_BASE_URL` | both | The server's own public address, for a push's picture; else Railway's public domain (`RAILWAY_PUBLIC_DOMAIN`). |
| `FEATURE_AGE_GROUP_IMPORT` | both | `true` turns on copying an age group between the apps (§81, §82). Off by default. |
| `IMPORT_SOURCES` | both | The other server's address (origin only, no `/v1`, no port on `https`) that an age group may be imported from. Comma-separated. |
| `IMPORT_SOURCES_CROSS_ENVIRONMENT` | both | Of those, the ones in another environment (e.g. staging into production): platform admins only. |
| `CLIENT_CACHE_SECONDS`, `CLIENT_MATCH_CACHE_SECONDS` | both | How long the apps keep a read, and a game's status and results (§83). 0, the default, turns it off. |
| `MIN_ANDROID_BUILD`, `MIN_IOS_BUILD` | both | The oldest app build each platform may use; older builds get 426 and the update screen (§85). 0, the default, is none. For Android it is the CI run number of the Firebase workflow, which counts separately for each app. |
| `PLANS_ENABLED` | coach | `true` turns plans and ads on. Off by default: every space is Pro (§84). |
| `PLATFORM_ADMIN_EMAILS` | coach | Accounts that are platform admins, comma-separated, besides any marked so in the database. |
| `GOOGLE_PLAY_PACKAGE_NAME`, `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `GOOGLE_PLAY_NOTIFICATIONS_TOKEN` | coach | Checking Google Play subscriptions (§72). Without the account, plans change by request or at once locally. |
| `WEBAPP_BASE_URL` | clubs | The web client's origin, for CORS and the links in emails. |
| `SEED_ADMIN_ENABLED`, `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | clubs | A platform-admin account created at startup. On by default for local development only, with labelled fallback credentials from the code. On a deployed server (`APP_ENV` `staging` or `production`) it is off unless `SEED_ADMIN_ENABLED=true`, and then the email and password are required. |

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Coach app tests: `./gradlew :app:coach:testAndroidHostTest`
- Clubs app tests: `./gradlew :app:clubs:testAndroidHostTest` (or `:app:clubs:jvmTest`)
- Desktop tests: `./gradlew :app:shared:jvmTest`
- Server tests: `./gradlew :server:core:test :server:data:test :server:web:test :server:clubs:test :server:coach:test`
  - The coach server's tests run on their own database, `squadcall_coach_test`, dropped when they finish, so `squadcall_coach_dev` keeps only what you create.
- Web tests:
  - Wasm target: `./gradlew :app:shared:wasmJsTest`
  - JS target: `./gradlew :app:shared:jsTest`
- iOS tests: `./gradlew :app:shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).