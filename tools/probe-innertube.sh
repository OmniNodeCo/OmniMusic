#!/usr/bin/env bash
#
# Probes YouTube's InnerTube player endpoint to see whether this player could use it.
#
# The first version of this probe hardcoded an API key and reported HTTP 404 from every attempt.
# That was the probe's fault, not YouTube's: InnerTube answers an unrecognised key with 404, so the
# result said nothing about whether streams are obtainable. This version reads the key out of a real
# music.youtube.com page load, which is where every client gets it, and then tries the client
# variants that are known to differ in whether they hand back a plain `url` or a `signatureCipher` -
# the difference decides whether a JavaScript engine is needed to decipher signatures at all.
#
# Run from a CI runner, or anywhere with network. Results go into an annotation, because job logs are
# awkward to reach after the fact.
set -euo pipefail

fail() {
  echo "FAIL: $1" >&2
  echo "::error::$1"
  exit 1
}

command -v curl >/dev/null 2>&1 || fail "no curl on PATH"
command -v python3 >/dev/null 2>&1 || fail "no python3 on PATH"

UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

# --------------------------------------------------------------------------------------------
# Step 1: the key and client version, taken from the page rather than remembered.
# --------------------------------------------------------------------------------------------
PAGE="$(curl -fsS -H "User-Agent: $UA" https://music.youtube.com/ || true)"
KEY="$(printf '%s' "$PAGE" | grep -o '"INNERTUBE_API_KEY":"[^"]*"' | head -1 | cut -d'"' -f4 || true)"
WEB_VERSION="$(printf '%s' "$PAGE" | grep -o '"INNERTUBE_CLIENT_VERSION":"[^"]*"' | head -1 | cut -d'"' -f4 || true)"

if [ -z "$KEY" ]; then
  fail "could not read INNERTUBE_API_KEY from music.youtube.com (page bytes: ${#PAGE})"
fi
echo "key: ${KEY:0:12}...  web client version: ${WEB_VERSION:-unknown}"

probe() {
  local host="$1" client="$2" version="$3"
  local body response
  body="$(python3 -c '
import json, sys
ctx = {"client": {"clientName": sys.argv[1], "clientVersion": sys.argv[2], "hl": "en", "gl": "US"}}
if sys.argv[1] == "TVHTML5_SIMPLY_EMBEDDED_PLAYER":
    ctx["thirdParty"] = {"embedUrl": "https://www.youtube.com/"}
print(json.dumps({"context": ctx, "videoId": sys.argv[3],
                  "contentCheckOk": True, "racyCheckOk": True}))' "$client" "$version" "$VIDEO_ID")"

  if ! response="$(curl -fsS -X POST "$host/youtubei/v1/player?key=$KEY" \
      -H 'Content-Type: application/json' -H "User-Agent: $UA" -d "$body" 2>&1)"; then
    echo "  $client @ ${host##*://} -> HTTP error: $(printf '%s' "$response" | head -c 120 | tr '\n' ' ')"
    return 0
  fi

  printf '%s' "$response" | python3 -c '
import json, sys
client = sys.argv[1]
try:
    data = json.load(sys.stdin)
except Exception as exc:
    print("  %s -> unreadable response: %s" % (client, exc)); raise SystemExit(0)

status = data.get("playabilityStatus", {})
sd = data.get("streamingData") or {}
fmts = sd.get("adaptiveFormats", []) + sd.get("formats", [])
audio = [f for f in fmts if "audio" in (f.get("mimeType") or "")]
plain = [f for f in audio if f.get("url")]
ciphered = [f for f in audio if f.get("signatureCipher")]

print("  %-30s %-14s audio=%-2d plain=%-2d ciphered=%-2d %s"
      % (client, status.get("status"), len(audio), len(plain), len(ciphered),
         ("reason=" + status["reason"]) if status.get("reason") else ""))
for f in plain[:4]:
    print("      playable itag=%-4s %s" % (f.get("itag"), (f.get("mimeType") or "")[:52]))
' "$client"
}

report="$(
  # --------------------------------------------------------------------------------------------
# Step 2: find a real music videoId through InnerTube search.
#
# The first version asked YouTube Music about a plain YouTube video and got "Video unavailable" from
# WEB_REMIX, which says as much about the video as about the endpoint. Searching first also answers
# the metadata half of the question on its own - search is far less gated than playback.
# --------------------------------------------------------------------------------------------
SEARCH_BODY="$(python3 -c '
import json, sys
print(json.dumps({
    "context": {"client": {"clientName": "WEB_REMIX", "clientVersion": sys.argv[1], "hl": "en", "gl": "US"}},
    "query": sys.argv[2],
}))' "${WEB_VERSION:-7.20.1}" "daft punk one more time")"

SEARCH="$(curl -fsS -X POST "https://music.youtube.com/youtubei/v1/search?key=$KEY" \
  -H 'Content-Type: application/json' -H "User-Agent: $UA" -d "$SEARCH_BODY" || true)"

VIDEO_ID="$(printf '%s' "$SEARCH" | python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
except Exception as exc:
    print(""); raise SystemExit(0)

found = []
def walk(node):
    if isinstance(node, dict):
        # A song row carries its id in the menu/overlay; the renderer name is what identifies it.
        if "musicResponsiveListItemRenderer" in node:
            row = node["musicResponsiveListItemRenderer"]
            for target in ("playlistItemData",):
                pid = row.get(target, {}).get("videoId")
                if pid:
                    found.append(pid)
        for value in node.values():
            walk(value)
    elif isinstance(node, list):
        for value in node:
            walk(value)

walk(data)
print(found[0] if found else "")
')"

if [ -z "$VIDEO_ID" ]; then
  echo "search: no videoId in the response (body bytes: ${#SEARCH})"
  echo "::warning::InnerTube search returned no videoId, so the player probe had nothing to ask about"
  VIDEO_ID="dQw4w9WgXcQ"
else
  echo "search: WEB_REMIX returned videoId $VIDEO_ID for 'daft punk one more time'"
fi
echo "::notice::InnerTube search works: videoId=$VIDEO_ID"

# --------------------------------------------------------------------------------------------
# Step 3: ask each client for that video and report what came back.
# --------------------------------------------------------------------------------------------
echo "clients:"
  for host in https://music.youtube.com https://www.youtube.com; do
    probe "$host" WEB_REMIX "${WEB_VERSION:-7.20.1}"
    probe "$host" ANDROID 19.09.37
    probe "$host" ANDROID_MUSIC 7.03.52
    probe "$host" MWEB 2.20240101.00.00
    probe "$host" TVHTML5_SIMPLY_EMBEDDED_PLAYER 2.0
    probe "$host" IOS 19.09.3
  done
)"
echo "$report"
# Annotations survive the job log, so the verdict goes there too. The first version of this filtered
# the report down to lines mentioning a playable format or an error, which threw away the status line
# for every client that answered with a parseable body - exactly the interesting case. Send it all.
echo "::notice::InnerTube probe (key read from the page): $(echo "$report" | tr '\n' '|' | head -c 2400)"
