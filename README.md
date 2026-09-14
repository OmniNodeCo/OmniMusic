<div align="center">
<h1>OmniMusic</h1>
A FOSS YouTube Music client for Android and Desktop with many features from<br>Spotify, SponsorBlock, ReturnYouTubeDislike using Compose Multiplatform to develop.
<br>
<br>
<a href="https://github.com/OmniNodeCo/OmniMusic/releases"><img src="https://img.shields.io/github/v/release/OmniNodeCo/OmniMusic"></a>
<a href="https://github.com/OmniNodeCo/OmniMusic/releases"><img src="https://img.shields.io/github/downloads/OmniNodeCo/OmniMusic/total"></a>
<br>
<br>
<h4>Download</h4>
<a href="https://github.com/OmniNodeCo/OmniMusic/releases"><img src="https://raw.githubusercontent.com/NeoApplications/Neo-Backup/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" width="200"></a>
<h4>Nightly Build</h4>
<a href="https://github.com/OmniNodeCo/OmniMusic/actions/workflows/build.yml"><img src="https://github.com/OmniNodeCo/OmniMusic/actions/workflows/build.yml/badge.svg"></a>
</div>

> OmniMusic is available on Desktop now!
>
> [kotlin-footguns](https://github.com/maxrave-dev/kotlin-footguns) — the Kotlin, Compose Multiplatform and desktop JVM traps this project ran into the hard way. Star it if it saves you a night.

## Features ✨️
- Play music from YouTube Music or YouTube for free, without ads and in the background
- Three Now Playing styles: Classic, Material 3 Expressive and Apple Music (NEW)
- Ten-band equalizer with presets and AutoEq headphone profiles, plus Delay and Reverb effects (NEW)
- OmniMusic Wrapped: your year in music, plus monthly recap playlists (NEW)
- On-device listening analytics: charts, period history, listening clock (NEW)
- Word-by-word Apple Music-style lyrics, romanization for 12 languages, share lyrics as an image (NEW)
- Home screen widgets: turntable, playlists and listening insights (NEW)
- High quality up-to 256kbps stream (Opus or AAC) for YouTube Music Premium users
- Browsing Home, Charts, Podcast, Moods & Genre with YouTube Music data at high speed
- Search everything on YouTube
- Spotify Canvas and Animated Album Art supported (NEW)
- Play 1080p video option with subtitle
- AI song suggestions
- Import playlists converted from Spotify and other apps
- Customize your playlist, synced with YouTube Music
- Notifications from followed artists
- Caching and offline playback support
- Crossfade with DJ-style like Apple Music
- Customizing THEME (Light, Dark, Color, etc)
- Synced lyrics from OmniMusic Lyrics, LRCLIB, Spotify (require login) and YouTube Transcript - AI lyrics translation (BETA) (\*)
- Personalize data (\**) and multi-YouTube-account support
- Last.fm scrobbling (Full version)
- Supports SponsorBlock and Return YouTube Dislike
- Sleep Timer
- Android Auto with online content, feature rich UI/UX
- Discord Rich Presence support
- Listen Together: shared rooms that play in sync with friends, compatible with Metrolist (NEW)
- And many more!

> (\*) Use your OpenAI or Gemini API key
> (\**) For users who chose "Send back to Google" feature

> **Warning**
> This app is in the beta stage, so it may have many bugs and make it crash. If you find any bugs,
> please create an issue on the [issue tracker](https://github.com/OmniNodeCo/OmniMusic/issues).
> Because of depending on YouTube Music, the player error will happen and it's normally, please don't ask me about the stable state of this app.

## Screenshots
 <p align="center">
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/01.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/02.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/03.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/04.png?raw=true" width="200" /> </p> <p align="center">
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/05.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/06.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/07.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/08.png?raw=true" width="200" /> </p> <p align="center">
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/09.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/10.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/11.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/12.png?raw=true" width="200" /> </p> <p align="center">
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/13.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/14.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/15.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/16.png?raw=true" width="200" /> </p> <p align="center">
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/17.png?raw=true" width="200" />
 <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/18.png?raw=true" width="200" /> </p> <p align="center">
   <img src="https://github.com/OmniNodeCo/OmniMusic/blob/dev/asset/screenshot/19.png?raw=true" width="800" />
</p>

 ## Data
- This app uses hidden API from YouTube Music with some tricks to get data from YouTube Music.
- Use Spotify Web API and some tricks to get Spotify Canvas and Lyrics
- Thanks to [InnerTune](https://github.com/z-huang/InnerTune/) for the idea to get data from YouTube Music. This repo is my inspiration to create this app.
- Special thanks to [SmartTube](https://github.com/yuliskov/SmartTube). This repo help me to extract the streaming URL of YouTube Music.
- My app is using [SponsorBlock](https://sponsor.ajay.app/) to skip sponsor in YouTube videos.
- ReturnYouTubeDislike for getting information on votes
- Main lyrics data from OmniMusic Lyrics
- Alternative lyrics data from LRCLIB. More information [LRCLIB](https://lrclib.net/)

 ## Privacy
 OmniMusic doesn't have any tracker or third-party server for collecting user data in FOSS version. If YouTube
logged-in users enable "Send back to Google" feature, OmniMusic only uses YouTube Music Tracking API to send listening history and listening record of video to Google for better recommendations and
supporting artist or YouTube Creator (see the `kotlinYtmusicScraper` module under `core/service/`).

We collect crash data in the Full version to improve the app.

## Full or FOSS version
I use [Sentry](http://sentry.io) crashlytics to catch all crashes in the Full version. [Sentry](https://github.com/getsentry/sentry) is the open-source project.
 If you don't want to be collected crash data, you must use FOSS version.

## Desktop app

### Which file should I download?
- For Windows: Download the `.msix` package and run `install.bat` to install.
- For macOS: Download the file with extension `.dmg`.
- For Linux: Download the file with extension `.AppImage` (all Linux distributions)

### Some limitations on Desktop app:
- Some Linux distributions may have stability issues (upstream JetBrains issue).
- ARM64 on Windows and Linux: use the x64 build.

Please report issues on the [issue tracker](https://github.com/OmniNodeCo/OmniMusic/issues) if you find any bugs.

## FAQ

#### 1. Wrong Lyrics?
 Lyrics are provided by LRCLIB and other sources. Sometimes lyrics may not match perfectly with the YouTube `videoId` parameter. So I need to use some "String Matcher" and "Duration" to search lyrics. So sometimes, some songs or videos get the wrong lyrics.

#### 2. Why the name or brand is "OmniMusic"?
 "Omni" means *all* or *every* — OmniMusic aims to be the one app for everything you want from a music streaming client: every source, every platform, every playback style.

## Contributing

Contributions are welcome — the full guide lives in [CONTRIBUTING.md](CONTRIBUTING.md). The short version:

1. **Start from an issue.** Every PR needs an accepted issue behind it — open one first so the change is agreed before the code exists.
2. **Fork and branch from `dev`** (`main` tracks releases), and fill in the whole PR template — one checkbox is machine-checked.
3. **AI policy.** AI-*assisted* work is welcome; AI-*driven* work is not:
   - A human must have written or personally reviewed **every line** and be able to answer review comments about it.
   - Unattended agent submissions (Jules, Devin, and friends) are **closed automatically** by the triage bot, on sight.
   - Commits carrying AI co-author trailers (`Co-Authored-By: Claude/Copilot/…`) or "Generated with …" markers are rejected the same way — squash them out first.
   - Repeat offenders are blocked.
4. **Translations** are not accepted as PRs editing the string files directly.

## Legal Disclaimer & Terms of Use

### 1. 100% Free, Open-Source & Strictly Non-Commercial
OmniMusic is a fully open-source project (FOSS) created purely for educational purposes and personal use. **We do not sell this application, nor do we monetize it in any way.** There are no advertisements, no premium features, no subscriptions, and no hidden fees within the app. This project has absolutely no commercial value or financial intent.

### 2. A Custom Browser with Content Filtering
OmniMusic acts strictly as a specialized, third-party web browser and client. It simply parses the publicly available website content and APIs of YouTube and YouTube Music, rendering them in a custom user interface. The ad-free experience it provides is fundamentally no different from using a standard web browser (like Chrome, Firefox, or Brave) equipped with a common ad-blocking extension (such as uBlock Origin).

### 3. Support Content Creators
We deeply respect the hard work of artists, musicians, and content creators. **We strongly encourage all users to subscribe to [YouTube Premium](https://www.youtube.com/premium).** Purchasing a Premium subscription is the best way to financially support the creators you listen to and ensure the continued growth of the platform. OmniMusic is built as a proof-of-concept for developers and enthusiasts, not to harm creators' revenues.

### 4. No Hosting of Copyrighted Material
We do not host, upload, distribute, or store any audio, video, or copyrighted media files on our own servers. All content accessed through this application is stored entirely on Google's/YouTube's servers and remains the property of their respective copyright owners. The app merely acts as a conduit to stream publicly accessible links.

### 5. User Responsibility & Legal Contact
The software is provided "AS IS", without warranty of any kind. The developers of OmniMusic do not encourage or condone piracy. Users are solely responsible for ensuring their usage of this app complies with their local copyright laws and the Terms of Service of the platforms they access.

Because we do not host any media files, we cannot process DMCA takedown requests for audio or video content. However, if you represent a copyright holder or have legal concerns regarding the open-source code itself, please open an issue at **https://github.com/OmniNodeCo/OmniMusic/issues**.

  ## Contribute
We're looking for more contributors, all contributions are welcome!
See our [CODE OF CONDUCT](CODE_OF_CONDUCT.md)

Thanks for all my contributors:

<a href="https://github.com/OmniNodeCo/OmniMusic/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=OmniNodeCo/OmniMusic" />
</a>

 ## Showcase
This project is following clean architecture and MVVM pattern (in UI, app module).

 ### Dependencies graph
  <p float="left">
  <img src="https://github.com/OmniNodeCo/OmniMusic/blob/main/asset/dependencies_graph.svg?raw=true" width="800">
  </p>
