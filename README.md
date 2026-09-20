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
| Playback | Queue with per-track removal and reordering, next/previous, shuffle (a real play order, so *previous* works), repeat off/all/one, seek, volume |
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
./gradlew :composeApp:packageDistributionForCurrentOS # .deb / .dmg / .exe
```

### Android

```bash
./gradlew :composeApp:assembleDebug
```

### Cutting a release

The four installers are built by CI on every push, but they only become something downloadable
when a tag is pushed:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

That runs `.github/workflows/release.yml`, which builds `assembleRelease` plus the three native
packages and publishes them as a GitHub release — [`v0.1.0`](https://github.com/OmniNodeCo/OmniMusic/releases/tag/v0.1.0)
is the tag that has been through it. Packaging the Windows `.exe` needs Inno Setup on the build
machine, because that is what jpackage drives for `--type exe`; `tools/ensure-innosetup.ps1` installs
it in CI. The APK is signed with the debug key — the release
build type points at `signingConfigs.debug` so the artifact installs out of the box — so replace
that with a real signing config before publishing anywhere public. Running the workflow manually
(`workflow_dispatch`) is meant to produce a prerelease named after the commit rather than a
version; that branch of the script has not been executed.

### Everything, without Gradle

The core, the UI state holder and every test can be built and run with nothing but a JRE and
`kotlinc` — no Android SDK, no Maven Central. This is what CI runs first:

```bash
./tools/setup-toolchain.sh   # JRE from PyPI (jdk4py) + kotlinc from the npm registry
./tools/run-tests.sh         # compile core + UI + tests, run 294 tests
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
with kotlinc (Kotlin 2.4.20, Temurin JRE 25.0.2) and runs **294 tests, all passing**. They cover the
JSON parser and writer, the WAV codec (including 24-bit and float PCM and malformed containers), the
playback engine (shuffle order, repeat modes, seek clamping, dead-stream skipping, queue mutation,
session restore), the LRC parser (centisecond and millisecond fractions, `[offset:]`, multi-timestamp
lines, metadata tags), the playlist, history and playback-state stores (including corrupt data on
disk), the TTL cache, the local filter, the Deezer mappers, and the repository. The provider, repository and lyrics tests run against
**recorded Deezer and LRCLIB responses** captured from the live APIs, not invented shapes.
`AppModelTest` (59 tests) drives the UI's state holder itself — navigation, search, playlists,
transport, lyrics, session restore — against faked collaborators, on a plain JVM; it is what caught
the two session bugs described below. The two desktop platform classes are covered for real rather
than through fakes: `JvmKeyValueStoreTest` (15) writes to a temporary directory, and
`JdkHttpFetcherTest` (9) talks to a `com.sun.net.httpserver` instance on the loopback interface,
which ships with the JRE and needs no network access. `PlaybackEngineConcurrencyTest` (5) runs the
engine under the same kind of reentrant lock the platforms supply, and asserts both that a listener
may call back into the engine from inside a callback and that no public entry point touches state
outside the guard. `./tools/run-demo.sh` runs the
same core end to end and writes real playlist and history JSON to disk.

**Compiled, but never rendered** — the Compose UI. `./tools/check-ui.sh` compiles
`composeApp/src/{commonMain,desktopMain,androidMain}` with kotlinc against the compile-only
declarations in `tools/stubs/` (see that folder's README for exactly what that does and does not
prove). Its first run found four genuine bugs, all now fixed: `Modifier.align(Alignment.BottomCenter)`
on the snackbar outside a `BoxScope`; `Modifier.clickable(onClick, onLongClick)`, where `clickable`
has no `onLongClick` and `combinedClickable` was meant; a smart cast on the delegated `model.screen`
property; and a named `selector` argument passed to `LocalFilter.filter`'s `vararg selectors`. CI
then compiled the same sources against **real Compose Multiplatform 1.8.2** for both targets and
found a fifth, which the stubs had hidden: `KeyEvent` is a value class and `key`/`type` are
*extension properties*, so importing `KeyEvent` alone leaves them unresolved. The stub now mirrors
the real declaration, and deleting those two imports makes `check-ui.sh` fail at the same two
positions CI reported. A sixth came later and is a boundary rather than a stub gap: `remember { }`
inside a `LaunchedEffect` block. `@Composable` is only enforced by the Compose compiler plugin,
which `check-ui.sh` does not run, so composable-context violations cannot be caught locally at all.
No composable in this project has ever been composed or drawn.

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

A third was reported by a user and is the only one of the three found outside this repository:

- **Seeking a remote track failed with `cannot seek this stream`.** `openRemote` handed
  `AudioSystem` the URL itself, and `seekTo` rewinds with `reset()`. Java Sound's own reader marks
  **200 bytes** to parse the header, and the JDK wraps a URL stream in an **8 KB** buffer — enough
  for that header reset, so the stream opens and plays. But once playback has read past the buffer
  the mark is gone, and `reset()` throws `Resetting to invalid mark`. 8 KB of 16-bit mono is about a
  fifth of a second, so every seek after the first instant failed: the arrow keys, the seek bar,
  resuming a saved position, and looping a track with repeat-one. The fix is
  `RemoteAudioBuffer.open`, which decodes from a buffered copy of the body instead of the
  connection, so the mark a seek returns to always exists. `RemoteAudioBufferTest` proves the old
  code path fails and the new one does not — reverting `open` to `getAudioInputStream(url)` makes
  `testAStreamOpenedFromTheNetworkCanBeRewoundPastTheBuffer` fail with exactly the reported error.
  Two supporting fixes came out of the same report: `PlaybackEngine.seekTo` no longer moves the
  reported position when the output refuses the seek (it used to, so the UI showed a time the audio
  was not at), and `AppModel` routes seek failures into the notice channel instead of letting an
  exception escape `onPreviewKeyEvent` into composition.

And a fourth came from the same user a release later, which turned out to be the more interesting one:

- **`could not read https://cdnt-preview.dzcdn.net/...mp3?hdnea=exp=...` — a stream link that had
  already expired.** A track stores the stream URL it was fetched with, and Deezer signs those URLs
  for about **15 minutes**: the same track id, requested twice 262 seconds apart, came back with the
  same file path and a different `exp`/`hmac`. The app keeps tracks in saved playlists, listening
  history and the session it restores on start, so nearly everything played later than a few minutes
  after it was found had a dead link, and the CDN answered 403. `StreamLink` reads the expiry out of
  the token, and `AppModel` now resolves a track's audio at play time: a link still inside its window
  is played as it is, anything else is re-fetched from the provider by track id first and remembered
  for the rest of the session. A refused link now reports `HTTP 403` instead of pasting 300
  characters of signature into a snackbar.
  `fixtures/track_detail.json` is a recorded `/track/{id}` response that deliberately keeps its
  signed query, because a stripped URL cannot test the parser; `StreamLinkTest`, `DeezerProviderTest`
  and four `AppModelTest` cases cover it. Disconnecting the resolver in `AppModel` makes the two
  refresh tests fail, so they are load-bearing rather than decorative.

**Built in CI, never launched** — the Gradle build, the Android APK and the desktop packages.
This sandbox has no Android SDK and cannot reach `repo1.maven.org` or `services.gradle.org`, so
`./gradlew` cannot run *here*; GitHub Actions runs it on every push instead. The `build` workflow's
five jobs are green: core tests, `:composeApp:assembleDebug`, and
`:composeApp:packageDistributionForCurrentOS` on ubuntu, macOS and windows. That produced real
artifacts — a 9.1 MB debug APK and desktop distributions of 52.9 MB (Linux), 55.9 MB (Windows) and
66.5 MB (macOS) — which is what turned up the `desktopTest` source-set and key-event bugs above.
The pinned versions in `gradle/libs.versions.toml` (Compose Multiplatform 1.8.2 / Kotlin 2.1.20 /
AGP 8.7.3) do resolve and compile. There is still no Gradle wrapper jar committed — CI runs
`gradle wrapper` first, and you should too.

The `release` workflow has also run end to end once: tag [`v0.1.0`](https://github.com/OmniNodeCo/OmniMusic/releases/tag/v0.1.0)
published `composeApp-release.apk` (7.7 MB), `omnimusic_1.0.0-1_amd64.deb` (54.5 MB),
`OmniMusic-1.0.0.msi` (58.3 MB) and `OmniMusic-1.0.0.dmg` (68.4 MB). Each artifact's SHA-256 matched
on download into the publishing job, so those four files are exactly what the build jobs produced.
Its first attempt failed with a glob bug — `actions/download-artifact`'s `pattern` is a minimatch
glob, not a prefix, so the wildcard-less `omnimusic-` matched none of the four artifacts and the job
aborted on an empty `dist/` rather than cutting an empty release. Only the tag path has been
exercised; the `workflow_dispatch` prerelease branch has never run. The Windows asset in that
release is an MSI; from `v0.1.1` on it is an Inno Setup `.exe`.

**Launched in CI, never on a real machine** — the packaged Windows app. The `v0.1.0` MSI installed
for a user and then failed with the launcher's *Failed to launch JVM*, which is what jpackage reports
for everything that goes wrong before the first frame. Three things changed:

- `nativeDistributions { includeAllModules = true }`. The bundled runtime is a jlink image, and the
  module set jdeps infers misses anything reached reflectively or through `ServiceLoader` — which is
  how Java Sound finds the MP3 SPI and how the JDK finds its TLS providers. This is the documented
  remedy for exactly this symptom and costs tens of megabytes. **The cause is inferred, not
  reproduced**: no Windows install has been run here, so this is the most likely explanation rather
  than a confirmed one.
- Windows ships as an **Inno Setup `.exe`** rather than an MSI (`TargetFormat.Exe`), and
  `tools/ensure-innosetup.ps1` installs Inno Setup when the runner image does not ship it.
- `Main.kt` catches what escapes `main()` and writes the stack trace to
  `<config dir>/startup-error.log` — `%APPDATA%\OmniMusic` on Windows — plus a dialog, so the next
  broken install reports a cause instead of one opaque line.

`tools/smoke-windows-launch.ps1` is what makes any of this checkable, and it runs in both workflows
after packaging. It makes two claims, and it is worth being exact about which is which:

- **The bundled runtime carries the modules the app needs.** Read from the runtime's own `release`
  file: **71 modules**, including `java.desktop`, `jdk.crypto.ec`, `jdk.unsupported`, `java.sql` and
  `jdk.zipfs`, all of which the script asserts by name. This is the half of the symptom jdeps cannot
  see, so it is the half worth pinning down.
- **The packaged classpath resolves and starts the app.** The script parses `OmniMusic.cfg` the way
  the launcher does — `app.classpath` repeats once per jar, 48 entries here, `app.mainclass`, and
  `$APPDIR` expanded in the JVM options too — and runs it. The process was **still alive after 30
  seconds**, so the main class resolved and Compose initialised rather than dying on a missing class.

What that second check deliberately does *not* use is the shipped JVM: the bundled runtime's `bin`
holds only DLLs, because jpackage's launcher enters the JVM through `jli.dll` and never spawns
`java.exe`. The script falls back to the build JDK for that run and says so in the log. So the
module set is verified against the real artifact and the classpath is verified against the real jars
— but no shipped `OmniMusic.exe` has been double-clicked, and no window has been drawn.

What no amount of building proves: nobody has opened a window. Rendering, the real network calls to
Deezer and LRCLIB, and audio output have never been exercised end to end.

## Known limitations

- **Desktop MP3 playback depends on an SPI that this sandbox cannot fetch.** The JDK decodes
  WAV/AIFF/AU only and Deezer previews are MP3, so the desktop target depends on
  `com.googlecode.soundlibs:mp3spi:1.9.5.4` + `jlayer:1.0.1.4` (both verified to exist on Maven
  Central) and `PcmConversion` converts whatever the SPI returns to PCM before the line is opened —
  that conversion is the step usually missed, and the reason "I added the MP3 library" still fails.
  The conversion *decision* is unit-tested here; actually decoding an MP3 has never been run in
  this environment, because it needs both the dependency and a sound card.
- **Cover art is loaded but has never been seen load.** `Artwork` renders a Coil `AsyncImage` over a
  deterministic gradient derived from the entity id; the gradient stays underneath as the
  placeholder, the no-artwork case and the offline case. The catalog only sometimes has an image —
  an artist nested in a track carries no picture — so the fallback is a normal state, not an error.
  Both coordinates are verified to exist on Maven Central and CI compiles against them, but no
  window has ever been opened here, so the one failure mode that matters has not been observed:
  drop `coil-network-okhttp` and `AsyncImage` renders the placeholder forever without reporting
  anything.
- **Previews are 30 seconds.** That is what a keyless API gives you.
- **Lyrics are line-level, not word-level**, and LRCLIB's coverage is community-driven, so plenty of
  tracks simply have none. Translations and romanization are not implemented.
- **Playback is not a foreground service on Android**, so it stops when the activity is destroyed.
