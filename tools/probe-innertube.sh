#!/usr/bin/env bash
#
# Probes YouTube's InnerTube player endpoint to see what it would take to use it.
#
# This is a capability probe, not part of the app. The question it answers: can this project resolve
# a playable audio URL from InnerTube without porting SimpMusic's stream resolution, which needs a
# JavaScript engine to run YouTube's signature cipher plus NewPipe extractor forks? Non-web clients
# are widely reported to hand back plain `url` fields with no `signatureCipher`, which would remove
# the cipher dependency entirely. The second question is the codec: this player decodes MP3, and
# YouTube serves Opus and AAC, so which itags are on offer decides whether a decoder is needed too.
#
# Run it from a CI runner, or anywhere with network; it reports through annotations so the result
# survives the job log. It was wired into the build once and has since been taken out again: it
# answered the question it was written for, and a step that always prints a failed InnerTube call
# would only add noise. The measured answer, on 2026-09-21:
#
#   WEB_REMIX 7.20.1     HTTP 404 from music.youtube.com/youtubei/v1/player
#   ANDROID_MUSIC 7.03.52 playability=LOGIN_REQUIRED "Please sign in"     0 formats
#   TVHTML5 7.20240101    playability=UNPLAYABLE "The page needs to be reloaded" 0 formats
#
# No client returned a single playable format, which is the point: this is not an endpoint you can
# call with a guessed client version. SimpMusic tracks a maintained player-config table and runs a
# three-tier extractor cascade for exactly that reason.
set -euo pipefail

fail() {
  echo "FAIL: $1" >&2
  echo "::error::$1"
  exit 1
}

command -v curl >/dev/null 2>&1 || fail "no curl on PATH"
command -v python3 >/dev/null 2>&1 || fail "no python3 on PATH"

# The public WEB_REMIX key that ships in every music.youtube.com page load.
KEY="AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
# A well-known, permanently available video, so the probe is not at the mercy of a trending track.
VIDEO_ID="dQw4w9WgXcQ"
ENDPOINT="https://music.youtube.com/youtubei/v1/player?key=$KEY"

probe() {
  local client="$1" version="$2"
  local body
  body="$(python3 -c '
import json, sys
print(json.dumps({
    "context": {"client": {"clientName": sys.argv[1], "clientVersion": sys.argv[2]}},
    "videoId": sys.argv[3],
    "contentCheckOk": True,
    "racyCheckOk": True,
}))' "$client" "$version" "$VIDEO_ID")"

  local response
  if ! response="$(curl -fsS -X POST "$ENDPOINT" -H 'Content-Type: application/json' -d "$body" 2>&1)"; then
    echo "RESULT client=$client version=$version request-failed: $(echo "$response" | head -c 200 | tr '\n' ' ')"
    return 0
  fi

  echo "$response" | python3 -c '
import json, sys
client = sys.argv[1]
try:
    data = json.load(sys.stdin)
except Exception as exc:
    print("RESULT client=%s unreadable-response: %s" % (client, exc))
    raise SystemExit(0)

status = data.get("playabilityStatus", {})
formats = (data.get("streamingData") or {}).get("adaptiveFormats", [])
formats += (data.get("streamingData") or {}).get("formats", [])
plain = [f for f in formats if f.get("url")]
ciphered = [f for f in formats if f.get("signatureCipher")]
audio = [f for f in formats if "audio" in (f.get("mimeType") or "")]

print("RESULT client=%s playability=%s formats=%d plain=%d ciphered=%d audioFormats=%d"
      % (client, status.get("status"), len(formats), len(plain), len(ciphered), len(audio)))
print("  reason=%s" % (status.get("reason") or "-"))
for f in audio[:8]:
    print("  audio itag=%-4s %-42s %s plain=%s"
          % (f.get("itag"), (f.get("mimeType") or "")[:42],
             f.get("audioQuality") or "-", bool(f.get("url"))))
' "$client"
}

out="$(
  probe WEB_REMIX 7.20.1
  probe ANDROID_MUSIC 7.03.52
  probe TVHTML5 7.20240101.00.00
)"
echo "$out"
echo "::notice::InnerTube probe: $(echo "$out" | tr '\n' ' ' | head -c 900)"
