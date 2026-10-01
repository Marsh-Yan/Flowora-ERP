#!/usr/bin/env bash
set -euo pipefail
base=http://localhost:8080
check_security() {
  local headers
  headers=$(curl --silent --show-error -D - -o /dev/null "$base$1" | tr -d '\r')
  printf '%s\n' "$headers" | grep -iq '^X-Content-Type-Options: nosniff$'
  printf '%s\n' "$headers" | grep -iq '^X-Frame-Options: DENY$'
  printf '%s\n' "$headers" | grep -iq '^Referrer-Policy: strict-origin-when-cross-origin$'
  if [[ $# -gt 1 ]]; then printf '%s\n' "$headers" | grep -iq "$2"; fi
}
asset=$(curl --fail --silent "$base/" | sed -n 's/.*src="\(\/assets\/[^" ]*\.js\)".*/\1/p' | head -n 1)
test -n "$asset"
check_security / '^Cache-Control: no-store$'
check_security /dashboard '^Cache-Control: no-store$'
check_security "$asset" '^Cache-Control: public, max-age=31536000, immutable$'
check_security /assets/r4-missing.js
test "$(curl --silent -o /dev/null -w '%{http_code}' "$base/assets/r4-missing.js")" = 404
check_security /api/v2/session/me
echo 'PASS: HTML, SPA, fingerprint asset, 404 and API security headers'
