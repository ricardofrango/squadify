# Builds and serves the Squadify web client -- Compose Multiplatform for Web, built as a Wasm-with-JS-
# fallback bundle (composeCompatibilityBrowserDistribution) so it runs in any modern browser.
#
# Railway no longer runs this: its build ran out of time there. Deploys build the bundle in GitHub
# Actions and upload it with app/webApp/prebuilt/Dockerfile instead (.github/workflows/deploy-web.yml,
# TECHNICAL_DECISIONS §13). This stays for a self-contained `docker build` of the web client.
#
# Build context is the REPO ROOT (docker build -f app/webApp/Dockerfile .) -- settings.gradle.kts's
# `include(...)` requires every listed project directory to physically exist even with
# --configure-on-demand (verified: omitting `server` fails with "Configuring project ':server'
# without an existing directory is not allowed"), so all of common/core/server/app must be present in
# the build context regardless of what's actually compiled.
#
# Unlike :server (which depends on the plain-JVM :common), webApp depends on :app:shared -> :core, and
# :core still declares an Android target -- so this build stage needs the Android SDK too, purely to
# let Gradle configure that dependency, even though only
# :app:webApp:composeCompatibilityBrowserDistribution is invoked.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=$ANDROID_SDK_ROOT

# libatomic1 -- the Wasm target's own Node.js (v25) fails at startup without it ("error while loading
# shared libraries: libatomic.so.1"); not needed by the JS target's older Node, which is why this only
# surfaces on :kotlinWasmNpmInstall.
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip libatomic1 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    curl -sSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q /tmp/cmdline-tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

# Versions must match gradle/libs.versions.toml's android-compileSdk -- see server/Dockerfile's note
# on the "platforms;android-37.0" (not "android-37") package-naming gotcha.
RUN yes | sdkmanager --licenses > /dev/null && \
    sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"

# Gradle wrapper + version catalog first for better Docker layer caching.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew

COPY common ./common
COPY core ./core
COPY server ./server
COPY app ./app

# Which backend this build points at (dev/staging/production) -- see app/webApp/build.gradle.kts and
# TECHNICAL_DECISIONS.md's "Environment-Based App Config" section. Defaults to production since that's
# what gets deployed most; override with `docker build --build-arg SQUADIFY_ENV=staging` for a staging
# deploy.
ARG SQUADIFY_ENV=production

# --configure-on-demand is deliberately NOT used here -- Gradle itself warns that the js/wasmJs
# targets don't support it ("may lead to unpredictable and inconsistent behaviour").
# --max-workers=1 avoids running the js and wasmJs Kotlin compile daemons concurrently, each of which
# can claim up to kotlin.daemon.jvmargs' -Xmx3072M -- in parallel that's enough to exceed a
# memory-constrained build host (hit locally: "Not enough memory to run compilation").
RUN ./gradlew :app:webApp:composeCompatibilityBrowserDistribution -PsquadifyEnv=$SQUADIFY_ENV --no-daemon --max-workers=1

# ---- Runtime stage: nginx serving the static bundle -- no JDK, no Gradle, no Android SDK, no Node ----
FROM nginx:alpine
COPY app/webApp/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/app/webApp/build/dist/composeWebCompatibility/productionExecutable /usr/share/nginx/html

EXPOSE 8080
