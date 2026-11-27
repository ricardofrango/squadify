# The web client's image when the bundle is built outside Railway (TECHNICAL_DECISIONS §13/§17):
# GitHub Actions runs composeCompatibilityBrowserDistribution, then `railway up` uploads only this
# Dockerfile, nginx.conf, the container start script and the built files (dist/). Railway's build is then
# a copy into nginx, seconds instead of the full Kotlin/Wasm compile that ran out of time there.
#
# The API address is set per service with the API_BASE_URL variable: the start script writes config.js
# from it (40-squadcall-config.sh), so changing the server needs a restart, not a rebuild.
#
# The upload has this file both at its root and at app/webApp/Dockerfile, so it's picked up whichever
# Dockerfile path the Railway service is set to. app/webApp/Dockerfile (the full build) stays for a
# self-contained `docker build` from the repo root.
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
COPY 40-squadcall-config.sh /docker-entrypoint.d/40-squadcall-config.sh
RUN chmod 755 /docker-entrypoint.d/40-squadcall-config.sh

EXPOSE 8080
