# SKULLBEAT (AudioTube) 🎵

> High-fidelity, ad-free YouTube Music streamer and library filter with 80% bandwidth savings.

SKULLBEAT lets you filter your YouTube Music library, export playlists, and stream pure audio without downloading video streams or listening to advertisements. It includes both a modern FastAPI web interface and a comprehensive command-line tool.

---

## ✨ Features

- **Ad-Free Pure Audio Streaming**: Direct audio extraction via `yt-dlp` saves up to 80% bandwidth.
- **FastAPI Web Interface**: Spotify/Apple Music-grade UI for searching, streaming, artist discographies, and playback history.
- **Library Filtering**: Search and filter liked songs, playlists, or library tracks by artist, title, or duration.
- **Playlist Exporting**: Export filtered music lists to `M3U`, `CSV`, or `JSON`.
- **Zero Video Overhead**: Instant playback without video rendering or video data overhead.

---

## 🚀 Quick Start

### 1. Installation

Clone repository and install dependencies:

```bash
git clone https://github.com/karthik-xd/PYROBEAT.git
cd PYROBEAT
pip install -r requirements.txt
```

### 2. Run the Web Player

Launch the local web server:

```bash
python app.py
```
Or on Windows, double-click `run_audiotube.bat`. Open your browser at [http://localhost:8000](http://localhost:8000).

---

## 🛠️ CLI Usage & Account Setup

SKULLBEAT includes a CLI tool (`filter_music.py`) for library filtering and terminal audio streaming.

### Authentication Setup

To access your personal YouTube Music library or liked songs:

```bash
python filter_music.py --setup
```

#### Option 1: Browser Headers (Recommended)
1. Open `https://music.youtube.com` in Brave, Chrome, or Edge.
2. Open DevTools (`F12`) -> **Network** tab.
3. Click any song or playlist.
4. Right-click any `music.youtube.com` network request -> **Copy** -> **Copy request headers** (or **Copy as cURL**).
5. Paste into setup prompt and press `Enter`, then `Ctrl+Z` (Windows) followed by `Enter`.
6. Saved to `headers_auth.json` (auto-ignored in `.gitignore`).

#### Option 2: Google Cloud OAuth
1. Create a project in Google Cloud Console.
2. Enable **YouTube Data API v3**.
3. Create OAuth Client ID with type **TVs and Limited Input devices**.
4. Enter Client ID and Client Secret in setup wizard.
5. Saved to `oauth.json` (auto-ignored in `.gitignore`).

---

### CLI Examples

#### View Liked Music
```bash
python filter_music.py --source liked
```

#### Filter by Artist or Song Title
```bash
python filter_music.py --source library --artist "Coldplay"
python filter_music.py --source liked --title "Yellow"
python filter_music.py --query "synthwave"
```

#### Filter by Duration (Skip shorts / long sets)
```bash
# Only songs between 2 minutes (120s) and 6 minutes (360s)
python filter_music.py --min-duration 120 --max-duration 360
```

#### Export Filtered Tracks
```bash
python filter_music.py --artist "Coldplay" --export coldplay_songs.csv
python filter_music.py --source liked --export my_liked_music.m3u
```

#### Direct Terminal Streaming
```bash
# Stream song #1 directly via ffplay:
python filter_music.py --artist "Eminem" --play 1

# Print direct stream URL:
python filter_music.py --artist "Eminem" --stream-url 1
```

---

## 📜 License

Distributed under the [MIT License](LICENSE).
