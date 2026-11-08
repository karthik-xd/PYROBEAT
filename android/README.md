# SkullBeat for Android

Native Android music player for SkullBeat. 100% on-device extraction, zero server requirements, zero audio transcoding latency, pure background audio streaming with Spotify and YouTube playlist imports.

---

## 1. Tech Stack & Architecture

- **Language & Framework:** Kotlin 1.9.24, Jetpack Compose with Material 3, Single Activity architecture, Navigation Compose.
- **Audio Engine:** AndroidX Media3 (ExoPlayer + `MediaSessionService` + `MediaSession`).
- **Stream Resolver:** `ResolvingDataSource` with expiring stream URL cache (auto refreshes on 403/410 errors).
- **Extraction:** Native Java `NewPipeExtractor` (`v0.24.4`) with custom `OkHttpDownloader`.
- **Database & Storage:** Room (SQLite) with reactive Flows, Jetpack Preferences DataStore.
- **Dependency Injection:** Dagger Hilt with KSP.
- **Network & Images:** OkHttp 4.12, Coil Compose.
- **Background Jobs:** WorkManager (`DownloadWorker`) with MediaStore tagging and foreground progress notifications.

---

## 2. Directory Structure

```text
android/
├── gradle/
│   ├── libs.versions.toml             # Version catalog
│   └── wrapper/                       # Gradle wrapper (8.7)
├── app/
│   ├── build.gradle.kts               # Module build setup
│   ├── proguard-rules.pro             # Proguard keep rules for NewPipeExtractor & Rhino
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml    # Permissions, Service, Activity & FileProvider
│       │   ├── java/com/skullbeat/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── SkullBeatApp.kt
│       │   │   ├── data/
│       │   │   │   ├── db/            # Room database & DAOs (Tracks, Favorites, Playlists, History, Queue)
│       │   │   │   ├── extractor/     # YouTubeExtractor & StreamCache
│       │   │   │   ├── network/       # OkHttpDownloader & SpotifyScraper
│       │   │   │   ├── repository/    # MusicRepositoryImpl
│       │   │   │   └── storage/       # UserPreferences (DataStore)
│       │   │   ├── domain/
│       │   │   │   ├── models/        # Track, Playlist, Favorite, HistoryItem, QueueItem
│       │   │   │   ├── repository/    # MusicRepository interface
│       │   │   │   └── util/          # TrackFilter, TrackExporter, TimeUtils
│       │   │   ├── player/
│       │   │   │   ├── manager/       # PlayerManager, PlaybackState, QueueHelper
│       │   │   │   └── service/       # PlaybackService (MediaSessionService)
│       │   │   ├── download/          # DownloadWorker (WorkManager + MediaStore)
│       │   │   ├── di/                # Hilt DI modules (AppModule, DatabaseModule)
│       │   │   └── ui/
│       │   │       ├── theme/         # Colors, Typography, Dark Theme
│       │   │       ├── navigation/    # Screen routes & SkullBeatNavHost
│       │   │       ├── components/    # MiniPlayer, BeatWaveView, SkullPlayButton, TrackListItem, FilterBar
│       │   │       ├── screens/       # Home, Search, Library, Artist, Playlist, NowPlaying
│       │   │       └── dialogs/       # AddToPlaylist, CreateEditPlaylist, ImportDialog, LyricsDialog
│       │   └── res/
│       │       ├── drawable/          # logo.png, icons
│       │       ├── font/              # orbitron_extrabold.ttf
│       │       └── values/            # strings.xml, themes.xml
│       └── test/java/com/skullbeat/app/
│           ├── TimeUtilsTest.kt       # Duration parsing & formatting tests
│           ├── TrackFilterTest.kt     # Filter logic tests (Title, Artist, Album, Duration)
│           ├── QueueHelperTest.kt     # Fisher-Yates shuffle order tests
│           ├── HistoryRuleTest.kt     # History 30-track limit & deduplication tests
│           └── SpotifyParserTest.kt   # Spotify embed regex & NEXT_DATA JSON parser tests
```

---

## 3. Core Features Implemented

1. **Beat Wave Engine Canvas:**
   - 60 fps Compose canvas rendering the white audio wave with harmonic waterline surging and licking flame tongues along the wave crest.
   - Monochrome XOR invert blend rendering the Orbitron wordmark `SKULLBEAT`.

2. **Custom Skull Play Button:**
   - Optically centered bone-white skull icon with hollow eye sockets and nose cavity. Smooth state transitions between pause and skull play.

3. **Background Audio & Stream Expiration Fix:**
   - Foreground service (`mediaPlayback`) with lock-screen, headphone buttons, Bluetooth, and notification seek bar controls.
   - `ResolvingDataSource` dynamically resolves the direct audio URL with expiration handling. Re-resolves and retries automatically on 403 or 410.

4. **Multi-Source Library Filter & Exporter:**
   - Port of `filter_music.py`: filters library, liked tracks, or history across free-text, artist, title, album, and duration range sliders.
   - Instant export to M3U, CSV, or JSON via Android system share sheet.

5. **Spotify & YouTube Playlist Importer:**
   - Detects playlist links pasted into Search.
   - Scrapes Spotify embed JSON metadata and parallel-matches tracks to YouTube Music streams (semaphore limit 8).
   - Clear warning for private Spotify `collection/tracks` Liked Songs.

6. **Playlists, Favorites & History:**
   - Full playlist management with custom emoji picker and color selection.
   - Last 30 played tracks tracked in Room with instant deduplication and top-insertion.
   - DataStore persists last track, seek position, shuffle state, repeat mode, and volume.

---

## 4. How to Build & Run

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 34)

### Build Debug APK:
```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"
.\gradlew assembleDebug
```
The output APK is generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests:
```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"
.\gradlew test
```

### Install onto Device / Emulator:
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
