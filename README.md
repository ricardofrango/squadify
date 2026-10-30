This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM), Server.

* [/app/iosApp](./app/iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/app/clubs](./app/clubs/src) is Squadify for Clubs: its navigation, Koin setup, iOS entry and the screens only clubs have (TECHNICAL_DECISIONS.md §72). Every clubs entry point (Android, iOS, web, desktop) starts it.

* [/app/coach](./app/coach/src) is Squadify for Coaches: its navigation, Koin setup, strings and the screens only the coach app has (the home with every age group, setting up their own space, joining with a code, inviting staff). [/app/coachAndroidApp](./app/coachAndroidApp) is its Android app.

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
- Coach Android app (Squadify for Coaches): `./gradlew :app:coachAndroidApp:assembleDevDebug`, or its run configuration. The dev flavor talks to the coach server on this machine (port 8090), so start it first (below). It installs next to the clubs app.
- Desktop app:
  - Hot reload: `./gradlew :app:desktopApp:hotRun --auto`
  - Standard run: `./gradlew :app:desktopApp:run`
- Server, in two terminals:
  - `./gradlew :server:clubs:run` runs the server (port 8080, or `SERVER_PORT`). Stop it with Ctrl+C.
  - `./gradlew -t :server:clubs:classes` compiles on every save; the running server reloads the new code by itself (see `TECHNICAL_DECISIONS.md` §59).
  - Don't use `./gradlew -t :server:clubs:run`: it never reloads, and it can leave a server holding port 8080 after Ctrl+C. To free the port: `lsof -ti tcp:8080 | xargs kill`.
- Coach server (Squadify for Coaches): `./gradlew :server:coach:run` (port 8090, or `SERVER_PORT`). It creates its own local database, `squadify_coach_dev`, on the same Postgres the first time.
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :app:webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/app/iosApp](./app/iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Coach app tests: `./gradlew :app:coach:testAndroidHostTest`
- Clubs app tests: `./gradlew :app:clubs:jvmTest`
- Desktop tests: `./gradlew :app:shared:jvmTest`
- Server tests: `./gradlew :server:core:test :server:data:test :server:web:test :server:clubs:test :server:coach:test`
  - The coach server's tests run on their own database, `squadify_coach_test`, dropped when they finish, so `squadify_coach_dev` keeps only what you create.
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