#!/usr/bin/env bash
# Checks a running app's browser hardening: security headers on the page and the API, a script-src hash for
# every inline script the page really serves, no public API map, no Server-Timing for strangers.
#   tools/verify/headers.sh https://api.140.245.56.102.nip.io     (prod, after a deploy)
#   tools/verify/headers.sh http://127.0.0.1:18181                (the verify copy; HSTS is still sent over http)
set -euo pipefail
BASE=${1:?usage: headers.sh <base-url>}
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

curl -fsS -D "$TMP/page.h" -o "$TMP/page.html" "$BASE/"
curl -sS -D "$TMP/api.h" -o /dev/null "$BASE/api/me"

python3 -I - "$TMP" <<'PY'
import base64, hashlib, re, sys, pathlib
d = pathlib.Path(sys.argv[1])
def headers(name):
    h = {}
    for line in (d / name).read_text().splitlines()[1:]:
        if ":" in line:
            k, v = line.split(":", 1)
            h[k.strip().lower()] = v.strip()
    return h
fail = []
for name in ("page.h", "api.h"):
    h = headers(name)
    for k, want in (("x-content-type-options", "nosniff"), ("x-frame-options", "DENY"),
                    ("referrer-policy", "strict-origin-when-cross-origin")):
        if h.get(k) != want: fail.append(f"{name}: {k} is {h.get(k)!r}, want {want!r}")
    if not h.get("strict-transport-security", "").startswith("max-age="): fail.append(f"{name}: no HSTS")
    csp = h.get("content-security-policy", "")
    for part in ("frame-ancestors 'none'", "object-src 'none'"):
        if part not in csp: fail.append(f"{name}: CSP lacks {part}")
    if "server-timing" in h: fail.append(f"{name}: Server-Timing sent to a stranger")
csp = headers("page.h").get("content-security-policy", "")
script_src = next((p.strip() for p in csp.split(";") if p.strip().startswith("script-src ")), "")
if "'unsafe-inline'" in script_src or "'unsafe-eval'" in script_src: fail.append("script-src allows unsafe-inline/eval")
html = (d / "page.html").read_text()
inline = [m.group(2) for m in re.finditer(r"<script(\s[^>]*)?>([\s\S]*?)</script>", html, re.I)
          if not re.search(r"\ssrc\s*=", m.group(1) or "") and m.group(2)]
for body in inline:
    h = "'sha256-" + base64.b64encode(hashlib.sha256(body.encode()).digest()).decode() + "'"
    if h not in script_src: fail.append(f"inline script {body.strip()[:40]!r} has no hash in script-src")
print(f"inline scripts: {len(inline)}, all hashed: {not any('inline script' in f for f in fail)}")
if fail:
    print("\n".join("FAIL " + f for f in fail)); sys.exit(1)
PY

for p in /swagger /openapi; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE$p")
  [ "$code" = 404 ] || { echo "FAIL $p answers $code, want 404"; exit 1; }
done
echo "headers ok: $BASE"
