# Builds and runs the Squadify for Clubs Ktor server (:server:clubs). See TECHNICAL_DECISIONS.md / ISSUES
# TO FIX.md for the app config (client base URL) and env vars (APP_ENV, JWT_SECRET, DATABASE_*) this
# expects at runtime.
#
# Build context is the REPO ROOT (`docker build -f server/clubs/Dockerfile .`) -- settings.gradle.kts
# includes every module (app:androidApp, app:desktopApp, app:shared, app:webApp, common, core,
# server:clubs, server:core, server:data), but :server:clubs depends only on :common, a plain-JVM module holding the
# shared models (:core re-sources those same files for its own multiplatform targets, so nothing is
# duplicated -- see common/build.gradle.kts and core/build.gradle.kts), and :server:core (server/core,
# the game code shared with the coach server -- TECHNICAL_DECISIONS.md §72) and :server:data (server/data,
# the shared Exposed tables and repositories).
# --configure-on-demand below skips configuring every project except the ones :server:clubs:buildFatJar
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
# means only :common, :server:core, :server:data and :server:clubs are actually evaluated/compiled.
COPY common ./common
COPY core ./core
COPY server ./server
COPY app ./app

RUN ./gradlew :server:clubs:buildFatJar --configure-on-demand --no-daemon

# ---- Runtime stage: no JDK, no Gradle -- just the JRE and the fat jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/server/clubs/build/libs/clubs-all.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
