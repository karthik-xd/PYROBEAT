# SKULLBEAT Architecture & Code Structure Tree

This document defines the complete parent-child file hierarchy and component mappings for **SkullBeat**.
No more whole-codebase scanning is needed: every file, class, function, and relationship is mapped below.

---

## Table of Contents
1. [PC Architecture Tree](#1-pc-architecture-tree)
2. [Android Architecture Tree](#2-android-architecture-tree)
3. [Component & Layer Data Flow](#3-component--layer-data-flow)
4. [Cross-Platform Feature Parity Matrix](#4-cross-platform-feature-parity-matrix)

---

# 1. PC Architecture Tree

The PC suite is an audio-only desktop streaming server and terminal suite running Python 3.10+.

```text
pc/
├── app.py                    # [PARENT: Web Server & Core API]
│   ├── index_page            # Web UI single-page dashboard (HTML5 + Tailwind CSS + Orbitron)
│   ├── search_music          # YouTube Music audio search via yt-dlp
│   ├── api_search            # GET /api/search?q={query}
│   ├── api_artist_songs      # GET /api/artist/songs?artist={artist}&current_id={id}
│   ├── api_artist_page       # GET /api/artist/page?artist={artist}
│   ├── api_stream_url        # GET /api/stream?id={video_id} (Extracts opus/m4a audio direct URL)
│   ├── api_download          # GET /api/download?id={video_id}&title={title}&artist={artist}
│   ├── api_get_playlist      # GET /api/playlist?url={url} (Spotify embed scrape or YouTube playlist)
│   ├── api_get_lyrics        # GET /api/lyrics?artist={artist}&title={title}
│   ├── api_import_csv        # POST /api/import/csv (Parses CSV tracks, maps via yt-dlp)
│   ├── api_get_history       # GET /api/history (Reads history/recent.json, max 30 items)
│   ├── api_add_history       # POST /api/history (Appends track to history/recent.json)
│   ├── api_clear_history     # DELETE /api/history (Clears history/recent.json)
│   ├── api_get_user_library  # GET /api/library (Reads history/user_library.json)
│   └── api_save_user_library # POST /api/library (Persists history/user_library.json)
│
├── filter_music.py           # [PARENT: Terminal CLI Suite]
│   ├── get_auth_file         # Checks headers_auth.json for authenticated YT Music
│   ├── run_setup             # Interactive OAuth/header setup wizard
│   ├── get_ytmusic_client    # Initializes ytmusicapi client
│   ├── normalize_track       # Standardizes track dict (title, artist, album, duration)
│   ├── fetch_tracks          # Retrieves liked songs, history, or playlist tracks
│   ├── apply_filters         # Dynamic in-memory filters (Title, Artist, Album, Min/Max duration)
│   ├── display_tracks        # Formatted ANSI terminal table view
│   ├── export_tracks         # Exports filtered list to M3U, CSV, or JSON
│   ├── get_audio_stream_url  # Resolves direct audio URL via yt-dlp
│   └── play_track            # CLI player streaming directly via mpv or ffplay
│
├── requirements.txt          # [Dependencies: fastapi, uvicorn, yt-dlp, ytmusicapi, requests]
├── run_audiotube.bat         # [One-Click Desktop Launcher for Windows]
├── logo.png                  # [Brand emblem]
├── skull_transparent.png     # [Transparent skull glyph for desktop UI]
└── history/                  # [PARENT: Persistent JSON Storage]
    ├── recent.json           # Array of recent played tracks (max 30 items)
    └── user_library.json     # Saved user library cache
```

---

# 2. Android Architecture Tree

The Android application is a native Kotlin project using Jetpack Compose (Material 3), AndroidX Media3 (ExoPlayer + MediaSessionService), Room SQLite, and Dagger Hilt.

```text
android/app/src/main/
├── AndroidManifest.xml       # App declarations, permissions (FOREGROUND_SERVICE, WAKE_LOCK), PlaybackService
│
├── java/com/skullbeat/app/
│   │
│   ├── SkullBeatApp.kt       # [PARENT: Application Entrypoint]
│   │   └── @HiltAndroidApp   # Initializes Dagger Hilt dependency container
│   │
│   ├── MainActivity.kt       # [PARENT: Single Activity Host]
│   │   ├── setContent        # Renders SkullBeatTheme and SkullBeatNavHost
│   │   └── enableEdgeToEdge  # Android 14+ gesture navigation & status bar transparent inset
│   │
│   ├── di/                   # [PARENT: Dependency Injection Modules]
│   │   ├── AppModule.kt      # Provides OkHttpClient, YouTubeExtractor, SpotifyScraper, MusicRepository
│   │   └── DatabaseModule.kt # Provides AppDatabase, TrackDao, FavoriteDao, PlaylistDao, HistoryDao, QueueDao
│   │
│   ├── domain/               # [PARENT: Clean Architecture Domain Layer]
│   │   ├── models/           # Core Entities & Data Transfer Objects
│   │   │   ├── Track.kt          # Track model (videoId, title, artist, album, durationSec, thumbnailUrl, streamUrl)
│   │   │   ├── Playlist.kt       # Custom playlist model (id, name, emoji, colorHex, createdAt)
│   │   │   ├── PlaylistTrack.kt  # Join table entity (playlistId, videoId, position)
│   │   │   ├── Favorite.kt       # Favorite track entity (videoId, addedAt)
│   │   │   ├── HistoryItem.kt    # History item entity (id, videoId, playedAt)
│   │   │   └── QueueItem.kt      # Saved persistent queue entity (id, videoId, position)
│   │   │
│   │   ├── repository/
│   │   │   └── MusicRepository.kt # Domain interface contract for all music data operations
│   │   │
│   │   └── util/             # Pure Domain Utility Classes
│   │       ├── TrackFilter.kt    # In-memory filter logic matching filter_music.py (title, artist, album, duration range)
│   │       ├── TrackExporter.kt  # Generates M3U playlist, CSV, and JSON strings for sharing
│   │       ├── TrackMatcher.kt   # Levenshtein & token-matching logic for Spotify to YouTube mapping
│   │       ├── CsvTrackParser.kt # Parser for user-uploaded CSV song lists
│   │       └── TimeUtils.kt      # Formats mm:ss, parses ISO durations, and computes display timestamps
│   │
│   ├── data/                 # [PARENT: Concrete Data Sources & Persistence]
│   │   ├── db/               # Room SQLite Database & DAOs
│   │   │   ├── AppDatabase.kt    # Room database declaration (entities, version, migrations)
│   │   │   ├── TrackDao.kt       # Track caching queries (insertTrack, getTrack, getAllTracksFlow)
│   │   │   ├── FavoriteDao.kt    # Liked songs queries (addFavorite, removeFavorite, getFavoriteTracksFlow)
│   │   │   ├── PlaylistDao.kt    # Playlist queries (insert, update, delete, addTrackToPlaylist, getTracksFlow)
│   │   │   ├── HistoryDao.kt     # History queries (recordPlay, getHistoryTracksFlow, clearHistory, limit 30)
│   │   │   └── QueueDao.kt       # Persistent playback queue queries (setQueue, getQueueTracksFlow)
│   │   │
│   │   ├── extractor/        # Audio Stream Extraction
│   │   │   ├── YouTubeExtractor.kt # NewPipeExtractor wrapper resolving audio streams, search, and artist discographies
│   │   │   └── StreamCache.kt      # In-memory expiration cache for direct audio CDN URLs (avoids 403s)
│   │   │
│   │   ├── network/          # Networking Clients
│   │   │   ├── OkHttpDownloader.kt # Custom Downloader implementation bridging NewPipeExtractor to OkHttp
│   │   │   └── SpotifyScraper.kt   # Scrapes Spotify public embed HTML for track metadata without API tokens
│   │   │
│   │   ├── repository/
│   │   │   └── MusicRepositoryImpl.kt # Concrete MusicRepository coordinating Room DB, Extractor, & DataStore
│   │   │
│   │   └── storage/
│   │       └── UserPreferences.kt     # Jetpack DataStore saving lastTrackId, positionMs, shuffle, repeat, volume
│   │
│   ├── player/               # [PARENT: Audio Engine & Playback Service]
│   │   ├── audio/
│   │   │   └── AudioNormalizer.kt     # Broadcast DynamicsProcessing 1-band MBC & peak limiter + LoudnessEnhancer fallback
│   │   │
│   │   ├── manager/
│   │   │   ├── PlayerManager.kt       # Master ExoPlayer controller, session manager, loop logic, user queue
│   │   │   ├── PlaybackState.kt       # Immutable UI state (currentTrack, isPlaying, isLoading, loopMode, userQueue)
│   │   │   └── QueueHelper.kt         # Shuffle generation (Fisher-Yates) and track sequence management
│   │   │
│   │   └── service/
│   │       └── PlaybackService.kt     # MediaSessionService, notification provider with 2-color palette shading
│   │
│   ├── download/             # [PARENT: Offline Download Engine]
│   │   └── DownloadWorker.kt # WorkManager coroutine worker downloading audio into Android MediaStore/Music
│   │
│   └── ui/                   # [PARENT: Jetpack Compose Presentation Layer]
│       │
│       ├── theme/            # Theme, Palette & Typography
│       │   ├── Color.kt          # Dark palette tokens (#05070F, #0E1426, #172044, ElectricBlue, CyanAccent)
│       │   ├── Type.kt           # Fredoka typography scale & Orbitron ExtraBold wordmark definition
│       │   └── Theme.kt          # SkullBeatTheme Compose wrapper
│       │
│       ├── navigation/       # Screen Routing & Destinations
│       │   ├── Screen.kt         # Sealed class: Home, Search, Library, PlaylistDetail, Artist
│       │   └── SkullBeatNavHost.kt # NavHost routing, bottom navigation bar, mini-player dock, and root dialogs
│       │
│       ├── components/       # Reusable Design System Widgets
│       │   ├── BeatWaveView.kt    # 60fps kinetic audio wave with XOR difference invert SKULLBEAT wordmark
│       │   ├── SkullPlayButton.kt # Optically centered skull button (play glyph, pause bars, spinning buffer state)
│       │   ├── SwipeToQueueBox.kt # 120fps left-to-right gesture box triggering immediate user queue addition
│       │   ├── TrackListItem.kt   # Track item with 1-touch play, artist click, favorite heart, and swipe-to-queue
│       │   ├── MiniPlayer.kt      # Floating mini-player bar docked above bottom nav with 2dp progress highlight
│       │   └── FilterBar.kt       # Chips (Artist, Title, Album) & duration range slider for library filtering
│       │
│       ├── dialogs/          # Modal Dialogs & Sheets
│       │   ├── AddToPlaylistDialog.kt     # Dialog for choosing or creating a playlist to store a track
│       │   ├── CreateEditPlaylistDialog.kt# Dialog for custom playlist name, emoji picker, and color swatches
│       │   ├── ImportDialog.kt            # Bottom sheet for Spotify/YouTube URL import and CSV song file upload
│       │   ├── LyricsDialog.kt            # Bottom sheet displaying synced or plain lyrics
│       │   └── AboutDialog.kt             # What's New dialog displaying version, changelog, and feature release notes
│       │
│       └── screens/          # Top-Level Screens & ViewModels
│           ├── splash/
│           │   └── SplashScreen.kt            # Brand splash screen with pulsing skull and animated equalizer
│           │
│           ├── home/
│           │   ├── HomeScreen.kt              # Home feed with 2x2 quick playlist grid, Recently Played, Made for You
│           │   └── HomeViewModel.kt           # Exposes recent items, favorites, and user playlists
│           │
│           ├── search/
│           │   ├── SearchScreen.kt            # Instant YouTube search, link detection banner, and swipe-to-queue
│           │   └── SearchViewModel.kt         # Manages debounced search query, results, and Spotify/YouTube link detection
│           │
│           ├── library/
│           │   ├── LibraryScreen.kt           # Tabs (All, Liked, Playlists, History), advanced filter bar, export actions
│           │   └── LibraryViewModel.kt        # In-memory track filtering, M3U/CSV export, and queue additions
│           │
│           ├── playlist/
│           │   ├── PlaylistDetailScreen.kt    # Playlist view with emoji cover, Play/Shuffle, and Add Songs search sheet
│           │   └── PlaylistViewModel.kt       # Coordinates playlist track list, ordering, and live track additions
│           │
│           ├── artist/
│           │   ├── ArtistScreen.kt            # Artist header with avatar, top songs list, and swipe-to-queue
│           │   └── ArtistViewModel.kt         # Queries artist tracks via YouTubeExtractor and provides playback
│           │
│           └── nowplaying/
│               ├── NowPlayingSheet.kt         # Full-screen player, rotating landscape view, queue sheet, loop button
│               └── NowPlayingViewModel.kt     # Coordinates active playback, favorites, seeking, loop mode, and queue
│
└── res/                      # [PARENT: Android System Resources]
    ├── drawable/
    │   ├── logo.png                   # High-res SkullBeat logo
    │   ├── ic_notification.xml        # Monochrome notification skull icon
    │   └── ic_shuffle.xml             # Custom shuffle vector icon
    ├── font/
    │   ├── fredoka.ttf                # Fredoka Variable font for UI headers
    │   └── orbitron_extrabold.ttf     # Orbitron ExtraBold font for SKULLBEAT wordmark
    ├── values/
    │   ├── strings.xml                # App name and localized strings
    │   └── themes.xml                 # Window background and system bar styles
    └── xml/
        └── file_paths.xml             # FileProvider paths for exporting M3U, CSV, and JSON
```

---

# 3. Component & Layer Data Flow

```text
User Tap Track
      │
      ▼
TrackListItem (Instant 1-Touch Play)
      │
      ▼
PlayerManager.playTrack()
      │
      ├─► ResolvingDataSource / YouTubeExtractor (Resolves opus/m4a stream)
      ├─► AudioNormalizer.attach() (Applies hardware DynamicsProcessing MBC / Limiter)
      ├─► Room DB (Saves play to HistoryDao, updates DataStore state)
      └─► PlaybackService (Updates foreground notification with 2-color artwork shading)
```

### Swipe-to-Queue Flow
```text
User Swipes Track Left-to-Right
      │
      ▼
SwipeToQueueBox (Consumes horizontal drag, threshold: 40dp, fires LongPress Haptic)
      │
      ▼
onSwipeToQueue() -> ViewModel.addToQueue(track)
      │
      ▼
PlayerManager.addToUserQueue(track)
      │
      ├─► In-memory PlaybackState.userQueue updated
      ├─► Pre-fetches next track stream URL in background
      └─► Played in exact priority order before resuming shuffle or playlist sequence
```

---

# 4. Cross-Platform Feature Parity Matrix

| Feature | PC File (`pc/`) | Android File (`android/`) |
| :--- | :--- | :--- |
| **Audio Stream Extraction** | `pc/app.py` (`api_stream_url`) | `YouTubeExtractor.kt` + `StreamCache.kt` |
| **Terminal / Filter Engine** | `pc/filter_music.py` (`apply_filters`) | `TrackFilter.kt` + `FilterBar.kt` |
| **Playlist Export (M3U/CSV)**| `pc/filter_music.py` (`export_tracks`) | `TrackExporter.kt` (`LibraryScreen.kt`) |
| **Spotify Link Import** | `pc/app.py` (`extract_spotify_metadata`) | `SpotifyScraper.kt` + `ImportDialog.kt` |
| **History Tracking (Max 30)**| `pc/history/recent.json` | `HistoryDao.kt` (Room SQLite) |
| **Volume Normalization** | `pc/app.py` (Gain normalization) | `AudioNormalizer.kt` (`DynamicsProcessing`) |
| **Instant 1-Touch Playback** | `pc/app.py` (Direct player call) | `PlayerManager.kt` + `TrackListItem.kt` |
| **Swipe-to-Queue** | N/A (Desktop click queue) | `SwipeToQueueBox.kt` (120fps Compose gesture) |
| **Multi-Touch Loop Mode** | `pc/app.py` (Loop toggle) | `NowPlayingSheet.kt` (1x, 2x, Infinite modes) |
| **Dynamic Theming** | `pc/app.py` (Palette extraction) | `NowPlayingSheet.kt` + `PlaybackService.kt` |
