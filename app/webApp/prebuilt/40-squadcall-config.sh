#!/bin/sh
# Runs at container start: the nginx image executes /docker-entrypoint.d/*.sh before nginx. It writes
# config.js from the API_BASE_URL variable, so one image serves every environment. Set API_BASE_URL on the
# Railway web service (e.g. https://stg-club-api.squadcallapp.com/v1); a restart applies it, no rebuild.
# Without it, the address compiled into the build is used.
set -eu

out=/usr/share/nginx/html/config.js

if [ -z "${API_BASE_URL:-}" ]; then
  echo 'window.SQUADCALL_CONFIG = {};' > "$out"
  echo "config.js: API_BASE_URL not set, using the address built into the app"
  exit 0
fi

case "$API_BASE_URL" in
  https://*|http://*) ;;
  *) echo "config.js: API_BASE_URL must start with https:// -- got '$API_BASE_URL'" >&2; exit 1 ;;
esac

# The value goes into a JavaScript string, so quotes, backslashes and spaces are refused.
case "$API_BASE_URL" in
  *'"'*|*"'"*|*'\'*|*' '*|*'<'*|*'>'*)
    echo "config.js: API_BASE_URL has characters that aren't allowed" >&2; exit 1 ;;
esac

printf 'window.SQUADCALL_CONFIG = { apiBaseUrl: "%s" };\n' "$API_BASE_URL" > "$out"
echo "config.js: the app will call $API_BASE_URL"
