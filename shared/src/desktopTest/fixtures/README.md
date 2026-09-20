# Fixtures

Recorded API responses, replayed by `FixtureHttpFetcher` in tests and by
`tools/run-demo.sh --fixtures`.

Provenance:

| File | Source |
| --- | --- |
| `search_tracks.json`, `search_tracks_page2.json` | live `api.deezer.com/search?q=daft+punk` |
| `search_albums.json`, `search_artists.json` | live `api.deezer.com/search/album`, `/search/artist` |
| `artist_top.json`, `artist_detail.json` | live `api.deezer.com/artist/27/top`, `/artist/27` |
| `album_detail.json`, `album_no_tracks.json`, `album_tracks.json` | live `api.deezer.com/album/302127` |
| `chart_tracks.json`, `chart_albums.json`, `genres.json`, `radio.json`, `playlist_detail.json` | live Deezer endpoints |
| `track_detail.json` | live `api.deezer.com/track/2868828162` (`available_countries` and `track_token` dropped) |
| `lrclib_synced.json` | live `lrclib.net/api/get?artist_name=Daft+Punk&track_name=One+More+Time` (lyrics trimmed to the first section, `lyricsfile` dropped) |
| `lrclib_plain_only.json`, `lrclib_not_found.json` | constructed, shape matches LRCLIB |
| `error_parameter.json` | live Deezer error body |
| `malformed.json`, `unplayable.json` | constructed edge cases |

Track/album/artist objects are verbatim apart from signed CDN query strings, which expire and are
therefore stripped from the preview URLs.

`track_detail.json` is the one exception: it keeps its `hdnea=exp=...~acl=...~hmac=...` token,
because that shape is what `StreamLink` parses and a stripped URL cannot test it. The token expired
on 2026-09-20, so it cannot fetch anything.
