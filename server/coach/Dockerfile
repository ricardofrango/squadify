# Builds and runs the Squadify for Coaches Ktor server (:server:coach, TECHNICAL_DECISIONS.md §72). Same
# shape as server/clubs/Dockerfile: the build context is the REPO ROOT
# (`docker build -f server/coach/Dockerfile .`), and --configure-on-demand only configures what
# :server:coach:buildFatJar needs (:common, :server:core, :server:data, :server:web, :server:coach) -- no
# Android SDK, no Xcode, no Node.js. Runtime env vars: APP_ENV, JWT_SECRET (its own, never the clubs
# server's), DATABASE_* (its own database), SERVER_PORT.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle wrapper + version catalog first, for Docker layer caching.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# settings.gradle.kts names every module's directory, so they must all exist, even the ones not built.
COPY common ./common
COPY core ./core
COPY server ./server
COPY app ./app

RUN ./gradlew :server:coach:buildFatJar --configure-on-demand --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/server/coach/build/libs/coach-all.jar app.jar

EXPOSE 8080
ENV SERVER_PORT=8080
ENTRYPOINT ["java", "-jar", "app.jar"]
