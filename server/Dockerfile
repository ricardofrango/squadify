# Builds and runs the Squadify Ktor server. See TECHNICAL_DECISIONS.md / ISSUES TO FIX.md for the
# app config (client base URL) and env vars (APP_ENV, JWT_SECRET, DATABASE_*) this expects at runtime.
#
# Build context is the REPO ROOT (see the build command in README/deploy notes: `docker build -f
# server/Dockerfile .`) -- settings.gradle.kts includes every module (app:androidApp, app:desktopApp,
# app:shared, app:webApp, common, core, server, server:core), but :server depends only on :common, a
# plain-JVM module holding the shared models, and :server:core (server/core, the game code shared with
# the coach server -- TECHNICAL_DECISIONS.md §72; copied with server/ below) (:core re-sources those same files for its own
# multiplatform targets, so nothing is duplicated -- see common/build.gradle.kts and core/build.gradle.kts).
# --configure-on-demand below skips configuring every project except the ones :server:buildFatJar
# actually needs, so this build never touches :core's Android/iOS/JS/Wasm targets or the client app
# modules -- no Android SDK, no Xcode, no Node.js required.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle wrapper + version catalog first for better Docker layer caching (only re-downloads
# dependencies when these actually change, not on every source edit).
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# The whole repo -- settings.gradle.kts references every module's directory, but --configure-on-demand
# means only :common, :server:core and :server are actually evaluated/compiled.
COPY common ./common
COPY core ./core
COPY server ./server
COPY app ./app

RUN ./gradlew :server:buildFatJar --configure-on-demand --no-daemon

# ---- Runtime stage: no JDK, no Gradle -- just the JRE and the fat jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/server/build/libs/server-all.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
