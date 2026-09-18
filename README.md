# OmniMusic

A music app in the spirit of [SimpMusic](https://github.com/maxrave-dev/SimpMusic) — browse a
catalog, search it, build playlists, and play — written entirely in **Kotlin** with
**Compose Multiplatform**, targeting **Android** and **desktop (Windows, macOS, Linux)**.

There is no Python and no HTML anywhere in this repository.

```
┌─────────────────────────────────────────────────────────────────────────┐
│  composeApp  (Compose Multiplatform UI, shared 100% between targets)    │
│    commonMain: App.kt · AppModel.kt · screens · theme                   │
│    androidMain: MainActivity, MediaPlayer output, SharedPreferences     │
│    desktopMain: main(), Java Sound output, config-dir store             │
├─────────────────────────────────────────────────────────────────────────┤
│  shared  (pure Kotlin, zero third-party dependencies)                   │
│    model · json · provider(Deezer) · repo · player · audio · store      │
└─────────────────────────────────────────────────────────────────────────┘
```

## What it does

| Area | Behaviour |
| --- | --- |
| Browse | Trending tracks, albums and genres on a home feed (`/chart`, `/genre`) |
| Search | Tracks, albums and artists in one call; each facet fails independently; tracks page server-side, with "load more" |
| Detail | Album with track list (falls back to `/album/{id}/tracks`), artist top tracks |
| Radio | "Start a radio" from any seed track |
| Playback | Queue with per-track removal, next/previous, shuffle (a real play order, so *previous* works), repeat off/all/one, seek, volume |
| Playlists | Create (from the library screen), rename, delete, add/dedupe, reorder — persisted as JSON; "add to playlist" from the player |
| History | A recents tab with play counts, backed by on-device data. Nothing leaves the device |
| Lyrics | Synced lyrics from LRCLIB, highlighted line by line, with an unsynced fallback |
| Resume | The queue, cursor, position, shuffle/repeat and volume survive a restart |
| Offline filter | Instant filtering of the queue and saved playlists over what is already in memory |
| Caching | 5-minute TTL cache in the repository, so navigating back does not re-fetch |
| Back | System back (Android) and Escape (desktop) close the now-playing panel, then walk back through album/artist pages |
| Keyboard | Space play/pause, ←/→ seek 10 s, N/P next/previous, media keys, Escape to close; the desktop window title shows the playing track |

The catalog backend is **Deezer's public REST API**: no API key, no OAuth, and a 30-second MP3
preview per track that the player streams. Lyrics come from **LRCLIB**, also keyless. Swapping in
another service means implementing `MusicProvider` or `LyricsProvider` (one interface each) and
nothing else.

## Layout

```
shared/src/commonMain/kotlin/co/omnimusic/core/
  json/        Json.kt JsonParser.kt JsonWriter.kt     dependency-free JSON model, parser, writer
  model/       Track Album Artist Genre Playlist Page Load
  net/         HttpFetcher                              the only seam to the network
  provider/    MusicProvider, deezer/DeezerProvider, deezer/DeezerJson
  repo/        MusicRepository, MemoryCache
  player/      PlaybackEngine, PlayState, RepeatMode    queue + transport state machine
  audio/       AudioOutput, WavCodec                    platform audio seam + RIFF/WAVE codec
  lyrics/      LrcParser, Lyrics, LrcLibProvider, LyricsLibrary
  store/       KeyValueStore, PlaylistStore, PlaylistJson, TrackJson, PlaybackStateStore,
               ListeningHistory
  util/        Strings.kt (formatting, URL building, search folding), LocalFilter, Guard
shared/src/desktopMain/   JdkHttpFetcher, JvmKeyValueStore, JavaSoundAudioOutput
shared/src/desktopTest/   tests against recorded API responses + fixtures/
shared/src/commonTest/    tests for everything platform-independent
composeApp/src/commonMain/  App.kt AppModel.kt ui/ theme/
composeApp/src/androidMain/ MainActivity.kt AndroidPlatform.kt AndroidManifest.xml
composeApp/src/desktopMain/ Main.kt
composeApp/src/desktopTest/ AppModelTest.kt (the state holder, driven on a plain JVM)
tools/           Gradle-free build: setup-toolchain.sh, run-tests.sh, check-ui.sh, run-demo.sh
                 stubs/       compile-only Compose/Android declarations for check-ui.sh
```

Two design rules make the core testable on a machine with no sound card and no network:

1. **No third-party dependencies in `shared`.** The JSON parser, the WAV codec and the persistence
   codec are all hand-written, so the module compiles with a bare `kotlinc`.
2. **No platform APIs and no threads in `shared`.** Networking is an injected `HttpFetcher`, locking
   is an injected `Guard`, and the playback engine has no timer — its host ticks it with
   `advance(deltaMillis)` once per frame, which makes the whole transport deterministic.

## Building and running

### Desktop

```bash
./gradlew :composeApp:run                             # debug window
./gradlew :composeApp:packageDistributionForCurrentOS # .deb / .dmg / .msi
```

### Android

```bash
./gradlew :composeApp:assembleDebug
```

### Everything, without Gradle

The core, the UI state holder and every test can be built and run with nothing but a JRE and
`kotlinc` — no Android SDK, no Maven Central. This is what CI runs first:

```bash
./tools/setup-toolchain.sh   # JRE from PyPI (jdk4py) + kotlinc from the npm registry
./tools/run-tests.sh         # compile core + UI + tests, run 212 tests
./tools/check-ui.sh          # type-check composeApp/ against compile-only Compose stubs
./tools/run-demo.sh session  # CLI front end: search, playlist, playback, history
```

`check-ui.sh` deserves a caveat of its own, spelled out in [`tools/stubs/README.md`](tools/stubs/README.md):
it compiles `commonMain` + `desktopMain` + `androidMain` of `composeApp` against hand-written
declarations of the Compose API surface the app touches, because the real artifacts cannot be
downloaded here. It catches real mistakes — the first run found four — but it proves nothing about
rendering or behaviour, since the stub bodies are empty.

The demo defaults to the live API; add `--fixtures shared/src/desktopTest/fixtures` to replay
recorded responses instead, which works offline.

```bash
./tools/run-demo.sh --fixtures shared/src/desktopTest/fixtures --silent session
```

## Verification status

Be precise about what has actually been executed, because it is not everything:

**Verified in this repository** — `./tools/run-tests.sh` compiles `shared/src/commonMain`,
`shared/src/desktopMain`, `composeApp/src/commonMain`, every test source set and the Compose stubs
with kotlinc (Kotlin 2.4.20, Temurin JRE 25.0.2) and runs **212 tests, all passing**. They cover the
JSON parser and writer, the WAV codec (including 24-bit and float PCM and malformed containers), the
playback engine (shuffle order, repeat modes, seek clamping, dead-stream skipping, queue mutation,
session restore), the LRC parser (centisecond and millisecond fractions, `[offset:]`, multi-timestamp
lines, metadata tags), the playlist, history and playback-state stores (including corrupt data on
disk), the TTL cache, the local filter, the Deezer mappers, and the repository. The provider, repository and lyrics tests run against
**recorded Deezer and LRCLIB responses** captured from the live APIs, not invented shapes.
`AppModelTest` (57 tests) drives the UI's state holder itself — navigation, search, playlists,
transport, lyrics, session restore — against faked collaborators, on a plain JVM; it is what caught
the two session bugs described below. `./tools/run-demo.sh` runs the
same core end to end and writes real playlist and history JSON to disk.

**Type-checked, but never run** — the Compose UI. `./tools/check-ui.sh` compiles
`composeApp/src/{commonMain,desktopMain,androidMain}` with kotlinc against the compile-only
declarations in `tools/stubs/` (see that folder's README for exactly what that does and does not
prove). Its first run found four genuine bugs, all now fixed: `Modifier.align(Alignment.BottomCenter)`
on the snackbar outside a `BoxScope`; `Modifier.clickable(onClick, onLongClick)`, where `clickable`
has no `onLongClick` and `combinedClickable` was meant; a smart cast on the delegated `model.screen`
property; and a named `selector` argument passed to `LocalFilter.filter`'s `vararg selectors`. No
composable in this project has ever been composed or drawn.

Two of the bugs found were behavioural, not typographical, and both broke session resume:

- `AppModel.currentTimeMillis` was declared *below* the `init` block that restores the previous
  session. Property initializers run in declaration order, so the field was still `null` when the
  engine reported the restored track as started; the NPE landed inside the engine's
  `startCurrent`, which treats a listener exception as an unplayable track and skips forward. Every
  cold start with a saved session therefore opened on the **wrong song at 0:00** with a bogus
  "Could not play" notice.
- Restoring a session fired the same `onTrackStarted` path as pressing play, so reopening the app
  counted as a play (inflating play counts and reordering "recently played") and re-saved the
  position as 0:00 before the seek landed. A restore now records neither.

**Not verified here** — the Gradle build and the Android layer at runtime. This sandbox has no
Android SDK and cannot reach `repo1.maven.org` or `services.gradle.org`, so `./gradlew` cannot run
at all. `composeApp/` and the Gradle scripts are written against Compose Multiplatform 1.8.2 /
Kotlin 2.1.20 / AGP 8.7.3 (pinned in `gradle/libs.versions.toml`). Expect to adjust those versions
on a first build, and expect the stub check to have missed anything the stubs mis-model. There is
also no Gradle wrapper jar committed — run `gradle wrapper` once, as CI does.

## Known limitations

- **Desktop MP3 playback depends on an SPI that this sandbox cannot fetch.** The JDK decodes
  WAV/AIFF/AU only and Deezer previews are MP3, so the desktop target depends on
  `com.googlecode.soundlibs:mp3spi:1.9.5.4` + `jlayer:1.0.1.4` (both verified to exist on Maven
  Central) and `PcmConversion` converts whatever the SPI returns to PCM before the line is opened —
  that conversion is the step usually missed, and the reason "I added the MP3 library" still fails.
  The conversion *decision* is unit-tested here; actually decoding an MP3 has never been run in
  this environment, because it needs both the dependency and a sound card.
- **No cover art yet.** `Artwork` paints a deterministic gradient from the entity id. Adding Coil
  means replacing the body of that one composable.
- **Previews are 30 seconds.** That is what a keyless API gives you.
- **Lyrics are line-level, not word-level**, and LRCLIB's coverage is community-driven, so plenty of
  tracks simply have none. Translations and romanization are not implemented.
- **Playback is not a foreground service on Android**, so it stops when the activity is destroyed.
