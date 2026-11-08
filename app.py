#!/usr/bin/env python3
"""
AudioTube - High-Fidelity Music Streaming Platform
Complete Spotify / Apple Music Grade Architecture.
Zero video downloads. Zero ads. Pure music streaming.
"""

import json
import os
import pathlib
import subprocess
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import HTMLResponse, FileResponse
from pydantic import BaseModel
import uvicorn
from ytmusicapi import YTMusic
import yt_dlp

app = FastAPI(title="PYROBEAT")
ytm = YTMusic()
DOWNLOADS_DIR = pathlib.Path.home() / "Downloads"
HISTORY_DIR = pathlib.Path(__file__).parent / "history"
HISTORY_FILE = HISTORY_DIR / "recent.json"
HISTORY_DIR.mkdir(parents=True, exist_ok=True)
LOGO_PATH = pathlib.Path(__file__).parent / "logo.png"


@app.get("/logo.png")
def get_app_logo():
    if LOGO_PATH.is_file():
        return FileResponse(str(LOGO_PATH), media_type="image/png")
    raise HTTPException(status_code=404, detail="Logo not found")


class HistoryItem(BaseModel):
    videoId: str
    title: str
    channel: str
    thumbnail: str
    duration: str = "--:--"


def search_music(query: str, limit: int = 24) -> List[Dict[str, Any]]:
    """Searches YouTube Music for verified music tracks."""
    results = []
    try:
        raw = ytm.search(query, filter="songs", limit=limit)
        for r in raw:
            vid = r.get("videoId")
            if not vid:
                continue
            artists = ", ".join([a["name"] for a in r.get("artists", [])]) or "Unknown Artist"
            thumbs = r.get("thumbnails", [])
            thumb_url = thumbs[-1]["url"] if thumbs else f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
            if "googleusercontent.com" in thumb_url:
                thumb_url = thumb_url.split("=")[0] + "=w500-h500-l90-rj"
            results.append({
                "videoId": vid,
                "title": r.get("title") or "Unknown Song",
                "channel": artists,
                "duration": r.get("duration") or "--:--",
                "thumbnail": thumb_url,
                "album": (r.get("album") or {}).get("name", "Single"),
                "category": "Music",
                "is_music": True
            })
    except Exception as e:
        print(f"Music search error: {e}")
    return results


@app.get("/api/search")
def api_search(q: str = Query(..., description="Search keyword"), limit: int = 24):
    """Returns music tracks matching keyword."""
    items = search_music(q, limit=limit)
    return {"query": q, "count": len(items), "results": items}


@app.get("/api/artist_songs")
def api_artist_songs(artist: str = Query(...), exclude_id: Optional[str] = None, limit: int = 20):
    """Fetches other tracks by the same artist/channel for auto-play and right-side display."""
    primary_artist = artist.split(",")[0].split("&")[0].split("feat.")[0].strip()
    raw = search_music(f"{primary_artist} songs", limit=limit + 4)
    filtered = [t for t in raw if t["videoId"] != exclude_id][:limit]
    return {"artist": primary_artist, "count": len(filtered), "songs": filtered}


@app.get("/api/artist_page")
def api_artist_page(artist: str = Query(...)):
    """Fetches comprehensive track list and info for the author's page."""
    primary_artist = artist.split(",")[0].split("&")[0].split("feat.")[0].strip()
    songs = search_music(f"{primary_artist} songs", limit=30)
    avatar = songs[0]["thumbnail"] if songs else "https://via.placeholder.com/300"
    return {
        "artist": primary_artist,
        "avatar": avatar,
        "count": len(songs),
        "songs": songs
    }


@app.get("/api/playlist")
def api_get_playlist(url_or_id: str = Query(...)):
    """Fetches track metadata for any playlist without downloading audio."""
    clean_id = url_or_id.strip()
    if "list=" in clean_id:
        import urllib.parse
        parsed = urllib.parse.urlparse(clean_id)
        qs = urllib.parse.parse_qs(parsed.query)
        lid = qs.get("list", [None])[0]
        url = f"https://www.youtube.com/playlist?list={lid}" if lid else clean_id
    elif not clean_id.startswith("http"):
        url = f"https://www.youtube.com/playlist?list={clean_id}"
    else:
        url = clean_id

    ydl_opts = {"quiet": True, "extract_flat": True, "skip_download": True}
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            data = ydl.extract_info(url, download=False)
            tracks = []
            for entry in data.get("entries", []):
                vid = entry.get("id") or entry.get("url")
                if not vid:
                    continue
                dur_sec = entry.get("duration")
                if dur_sec:
                    m, s = divmod(int(dur_sec), 60)
                    h, m = divmod(m, 60)
                    dur_str = f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"
                else:
                    dur_str = "--:--"
                thumbs = entry.get("thumbnails") or []
                thumb_url = thumbs[-1]["url"] if thumbs else f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
                tracks.append({
                    "videoId": vid,
                    "title": entry.get("title") or "Unknown Track",
                    "channel": entry.get("channel") or entry.get("uploader") or "Unknown Artist",
                    "duration": dur_str,
                    "thumbnail": thumb_url,
                    "album": "Playlist Track",
                    "category": "Music",
                    "is_music": True
                })
            return {
                "title": data.get("title") or "Imported Playlist",
                "count": len(tracks),
                "tracks": tracks
            }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Playlist import failed: {e}")


@app.get("/api/stream/{video_id}")
def api_stream_url(video_id: str):
    """Extracts raw, ad-free audio stream URL without downloading video."""
    ydl_opts = {
        "format": "bestaudio/best",
        "quiet": True,
        "skip_download": True,
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(video_id, download=False)
            stream_url = info.get("url")
            if not stream_url:
                raise HTTPException(status_code=404, detail="Audio stream not found")
            return {
                "videoId": video_id,
                "title": info.get("title"),
                "channel": info.get("uploader") or info.get("channel"),
                "stream_url": stream_url,
                "format": info.get("format_note") or info.get("ext"),
                "bitrate": info.get("abr")
            }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/download/{video_id}")
def api_download(video_id: str):
    """Downloads audio-only track to user's Downloads folder if requested."""
    out_template = str(DOWNLOADS_DIR / "%(title)s.%(ext)s")
    ydl_opts = {
        "format": "bestaudio/best",
        "outtmpl": out_template,
        "postprocessors": [{
            "key": "FFmpegExtractAudio",
            "preferredcodec": "mp3",
            "preferredquality": "192",
        }],
        "quiet": True
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(video_id, download=True)
            saved_name = f"{info.get('title')}.mp3"
            return {
                "status": "success",
                "message": f"Saved to {DOWNLOADS_DIR / saved_name}",
                "path": str(DOWNLOADS_DIR / saved_name)
            }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Download failed: {e}")


@app.get("/api/history")
def api_get_history():
    """Returns recently played tracks stored in the history folder."""
    if not HISTORY_FILE.is_file():
        return {"count": 0, "history": []}
    try:
        with open(HISTORY_FILE, "r", encoding="utf-8") as f:
            items = json.load(f)
        return {"count": len(items), "history": items}
    except Exception as e:
        return {"count": 0, "history": [], "error": str(e)}


@app.post("/api/history")
def api_add_history(item: HistoryItem):
    """Saves recently played track metadata to the history folder."""
    HISTORY_DIR.mkdir(parents=True, exist_ok=True)
    items = []
    if HISTORY_FILE.is_file():
        try:
            with open(HISTORY_FILE, "r", encoding="utf-8") as f:
                items = json.load(f)
        except Exception:
            items = []

    items = [x for x in items if x.get("videoId") != item.videoId]
    items.insert(0, item.model_dump())
    items = items[:30]

    with open(HISTORY_FILE, "w", encoding="utf-8") as f:
        json.dump(items, f, indent=2, ensure_ascii=False)

    return {"status": "success", "count": len(items)}


@app.delete("/api/history")
def api_clear_history():
    """Clears history file in the history folder."""
    if HISTORY_FILE.is_file():
        try:
            with open(HISTORY_FILE, "w", encoding="utf-8") as f:
                json.dump([], f)
        except Exception:
            pass
    return {"status": "success"}


@app.get("/", response_class=HTMLResponse)
def index_page():
    return HTMLResponse(
        content=HTML_CONTENT,
        headers={
            "Cache-Control": "no-cache, no-store, must-revalidate, max-age=0",
            "Pragma": "no-cache",
            "Expires": "0"
        }
    )


HTML_CONTENT = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>PYROBEAT - Pure Audio Experience</title>
  <link rel="icon" type="image/png" href="/logo.png">
  <script src="https://cdn.tailwindcss.com"></script>
  <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css" rel="stylesheet">
  <style>
    :root {
      --pyro-cyan: #00e5ff;
      --pyro-blue: #00b0ff;
      --pyro-glow: rgba(0, 229, 255, 0.45);
      --pyro-bg: #07090e;
      --pyro-panel: #0d111a;
      --pyro-card: #131926;
      --pyro-card-hover: #1c2538;
    }
    body {
      background-color: var(--pyro-bg);
      color: #ffffff;
      font-family: -apple-system, BlinkMacSystemFont, "Circular Spotify Tx T", "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      user-select: none;
      -webkit-tap-highlight-color: transparent;
      overflow: hidden;
    }
    .spotify-panel {
      background-color: var(--pyro-panel);
      border: 1px solid rgba(0, 229, 255, 0.08);
      border-radius: 12px;
    }
    .spotify-card {
      background-color: var(--pyro-card);
      border: 1px solid rgba(0, 229, 255, 0.05);
      border-radius: 10px;
      transition: background-color 0.2s ease, transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
    }
    .spotify-card:hover {
      background-color: var(--pyro-card-hover);
      border-color: rgba(0, 229, 255, 0.35);
      box-shadow: 0 4px 20px rgba(0, 229, 255, 0.15);
      transform: translateY(-2px);
    }
    /* Thin line scrub slider with Cyan Glow */
    .seek-slider::-webkit-slider-thumb {
      appearance: none;
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #00e5ff;
      box-shadow: 0 0 10px #00e5ff;
      cursor: pointer;
      opacity: 0;
      transition: opacity 0.15s ease, transform 0.15s ease;
    }
    .seek-slider:hover::-webkit-slider-thumb {
      opacity: 1;
      transform: scale(1.25);
    }
    /* Scrollbars */
    ::-webkit-scrollbar { width: 8px; height: 8px; }
    ::-webkit-scrollbar-track { background: transparent; }
    ::-webkit-scrollbar-thumb { background: rgba(0, 229, 255, 0.2); border-radius: 4px; }
    ::-webkit-scrollbar-thumb:hover { background: rgba(0, 229, 255, 0.45); }

    /* Solid Color Monochrome Emojis (Black or White according to background) */
    .emoji-solid-white {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
      filter: grayscale(100%) brightness(0) invert(1);
    }
    .emoji-solid-black {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
      filter: grayscale(100%) brightness(0);
    }
    #playerPlayIcon {
      color: #000000 !important;
    }
  </style>
</head>
<body class="h-screen w-screen flex flex-col p-2 gap-2 select-none">

  <!-- ========================================== -->
  <!-- TOP MAIN CONTAINER (PANEL 1 + PANEL 2/3 + RIGHT PANEL) -->
  <!-- ========================================== -->
  <div class="flex-1 flex gap-2 min-h-0 overflow-hidden">

    <!-- 1. LEFT SIDEBAR (Panel 1 in diagram) -->
    <aside class="w-64 sm:w-72 spotify-panel flex flex-col p-4 flex-shrink-0 overflow-hidden">
      <!-- PYROBEAT BRAND HEADER (Logo + Electric Name) -->
      <div class="flex items-center space-x-3 mb-5 px-1 py-1 cursor-pointer group" onclick="switchTab('home')">
        <img src="/logo.png" alt="PYROBEAT" class="w-10 h-10 object-contain drop-shadow-[0_0_12px_rgba(0,229,255,0.7)] group-hover:scale-110 transition-transform">
        <div>
          <h1 class="text-lg font-black tracking-wider text-white font-mono flex items-center leading-none">
            <span class="text-[#00e5ff] drop-shadow-[0_0_10px_rgba(0,229,255,0.7)]">PYRO</span><span>BEAT</span>
          </h1>
        </div>
      </div>

      <!-- Top navigation (Home & Search) -->
      <div class="space-y-3 mb-4">
        <button onclick="switchTab('home')" id="side-nav-home" class="w-full flex items-center space-x-4 text-white font-bold text-sm hover:text-[#00e5ff] transition">
          <i class="fa-solid fa-house text-lg"></i>
          <span>Home</span>
        </button>
        <button onclick="switchTab('search')" id="side-nav-search" class="w-full flex items-center space-x-4 text-neutral-400 font-bold text-sm hover:text-[#00e5ff] transition">
          <i class="fa-solid fa-magnifying-glass text-lg"></i>
          <span>Search</span>
        </button>
      </div>

      <!-- Divider -->
      <div class="border-t border-white/10 my-2"></div>

      <!-- Library navigation & User Playlists -->
      <div class="flex-1 flex flex-col min-h-0">
        <div class="flex items-center justify-between mb-3 text-neutral-400 hover:text-white transition cursor-pointer" onclick="switchTab('library')">
          <div class="flex items-center space-x-3 text-sm font-bold">
            <i class="fa-solid fa-lines-leaning text-lg"></i>
            <span>Your library</span>
          </div>
          <button onclick="event.stopPropagation(); openCreatePlaylistModal()" title="Create playlist" class="hover:text-white text-base">
            <i class="fa-solid fa-plus"></i>
          </button>
        </div>

        <!-- Scrollable playlist list -->
        <div class="flex-1 overflow-y-auto space-y-2 pr-1 text-xs font-semibold text-neutral-400">
          <div onclick="filterLibrary('liked'); switchTab('library')" class="flex items-center space-x-3 py-1.5 px-2 rounded-md hover:bg-white/5 hover:text-white cursor-pointer transition">
            <i class="fa-solid fa-heart text-pink-500 text-sm"></i>
            <span class="truncate">Liked songs</span>
          </div>

          <!-- Dynamic User Playlists -->
          <div id="sidebarUserPlaylists" class="space-y-1 pt-2 border-t border-white/5"></div>
        </div>
      </div>
    </aside>

    <!-- 2 & 3. MAIN WINDOW (Panel 2 Top Bar + Panel 3 Content) -->
    <main class="flex-1 spotify-panel flex flex-col overflow-hidden relative min-w-0">

      <!-- 2. TOP BAR (Panel 2 in diagram) -->
      <header class="h-16 px-6 flex items-center justify-between flex-shrink-0 border-b border-white/5">
        <!-- Back & Forward arrows -->
        <div class="flex items-center space-x-2 flex-shrink-0">
          <button onclick="historyBack()" class="w-8 h-8 rounded-full bg-black/60 text-neutral-400 hover:text-white flex items-center justify-center transition">
            <i class="fa-solid fa-chevron-left text-xs"></i>
          </button>
          <button onclick="historyForward()" class="w-8 h-8 rounded-full bg-black/60 text-neutral-400 hover:text-white flex items-center justify-center transition">
            <i class="fa-solid fa-chevron-right text-xs"></i>
          </button>
        </div>

        <!-- Wide Rounded Search Bar -->
        <div class="max-w-md w-full mx-4">
          <div class="relative">
            <i class="fa-solid fa-magnifying-glass absolute left-3.5 top-3 text-neutral-400 text-sm"></i>
            <input 
              id="mainSearchInput" 
              type="text" 
              placeholder="What do you want to play?" 
              oninput="debounceSearch(this.value)"
              onkeydown="if(event.key === 'Enter'){ if(searchDebounceTimeout) clearTimeout(searchDebounceTimeout); if(this.value.trim()){ switchTab('search'); runSearch(this.value.trim()); } }"
              class="w-full pl-10 pr-4 py-2 bg-[#242424] hover:bg-[#2a2a2a] focus:bg-[#2a2a2a] border border-transparent focus:border-white/20 rounded-full text-xs sm:text-sm text-white placeholder-neutral-400 focus:outline-none transition shadow-inner"
            />
          </div>
        </div>

        <!-- Right: 3-dot Menu & Profile -->
        <div class="flex items-center space-x-3 flex-shrink-0">
          <button onclick="toggleImportModal()" title="Import Playlist" class="text-neutral-400 hover:text-white text-xs hidden sm:inline-block px-3 py-1.5 rounded-full border border-white/10 hover:border-white/20 transition">
            Import URL
          </button>
          <button onclick="openCreatePlaylistModal()" title="Create Playlist" class="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center text-xs transition">
            <i class="fa-solid fa-plus"></i>
          </button>
          <button onclick="toggleOptionsMenu()" class="text-neutral-400 hover:text-white text-base pl-1">
            <i class="fa-solid fa-ellipsis"></i>
          </button>
        </div>
      </header>

      <!-- 3. SCROLLABLE MAIN CONTENT AREA (Panel 3 in diagram) -->
      <div id="mainContentScroll" class="flex-1 overflow-y-auto p-6">

        <!-- VIEW: HOME (Panel 3 Cards) -->
        <div id="view-home" class="space-y-6">
          <h2 id="homeGreeting" class="text-2xl sm:text-3xl font-extrabold text-white">Good evening</h2>

          <!-- 4 Colorful Feature Cards from diagram -->
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <!-- Daily Mix 1 (Blue) -->
            <div onclick="searchGenre('Daily mix chill acoustic')" class="h-44 rounded-lg p-4 bg-gradient-to-b from-[#1e3264] to-[#121212] flex flex-col justify-between cursor-pointer group hover:scale-[1.02] transition shadow-lg">
              <div class="flex-1 flex items-center justify-center">
                <i class="fa-solid fa-music text-3xl text-blue-400/80 group-hover:scale-110 transition-transform"></i>
              </div>
              <div>
                <div class="font-bold text-sm text-white">Daily mix 1</div>
                <div class="text-xs text-neutral-400">Made for you</div>
              </div>
            </div>

            <!-- Discover Weekly (Green) -->
            <div onclick="searchGenre('Discover weekly top trending')" class="h-44 rounded-lg p-4 bg-gradient-to-b from-[#134e2c] to-[#121212] flex flex-col justify-between cursor-pointer group hover:scale-[1.02] transition shadow-lg">
              <div class="flex-1 flex items-center justify-center">
                <i class="fa-solid fa-music text-3xl text-green-400/80 group-hover:scale-110 transition-transform"></i>
              </div>
              <div>
                <div class="font-bold text-sm text-white">Discover weekly</div>
                <div class="text-xs text-neutral-400">New picks</div>
              </div>
            </div>

            <!-- Release Radar (Brown/Amber) -->
            <div onclick="searchGenre('Release radar new songs')" class="h-44 rounded-lg p-4 bg-gradient-to-b from-[#783c00] to-[#121212] flex flex-col justify-between cursor-pointer group hover:scale-[1.02] transition shadow-lg">
              <div class="flex-1 flex items-center justify-center">
                <i class="fa-solid fa-music text-3xl text-amber-400/80 group-hover:scale-110 transition-transform"></i>
              </div>
              <div>
                <div class="font-bold text-sm text-white">Release radar</div>
                <div class="text-xs text-neutral-400">New releases</div>
              </div>
            </div>

            <!-- Top Hits (Red/Crimson) -->
            <div onclick="searchGenre('Top global hits pop')" class="h-44 rounded-lg p-4 bg-gradient-to-b from-[#841c24] to-[#121212] flex flex-col justify-between cursor-pointer group hover:scale-[1.02] transition shadow-lg">
              <div class="flex-1 flex items-center justify-center">
                <i class="fa-solid fa-music text-3xl text-red-400/80 group-hover:scale-110 transition-transform"></i>
              </div>
              <div>
                <div class="font-bold text-sm text-white">Top hits</div>
                <div class="text-xs text-neutral-400">Playlist</div>
              </div>
            </div>
          </div>

          <!-- Recently Played Horizontal Row (history/recent.json) -->
          <div id="homeRecentSection" class="pt-4 hidden">
            <div class="flex items-center justify-between mb-3">
              <h3 class="text-lg font-bold text-white">Recently played</h3>
              <button onclick="clearHistory()" class="text-xs text-neutral-400 hover:text-white">Clear</button>
            </div>
            <div id="homeRecentRow" class="flex space-x-4 overflow-x-auto pb-2 scrollbar-thin"></div>
          </div>

          <!-- Recommended For You Row -->
          <div class="pt-4">
            <h3 class="text-lg font-bold text-white mb-3">Recommended</h3>
            <div id="homeFeaturedGrid" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4"></div>
          </div>
        </div>

        <!-- VIEW: SEARCH RESULTS -->
        <div id="view-search" class="space-y-6 hidden">
          <h2 id="searchResultsTitle" class="text-xl font-bold text-white">Search Results</h2>
          <div id="searchResultsGrid" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4"></div>
        </div>

        <!-- VIEW: LIBRARY -->
        <div id="view-library" class="space-y-6 hidden">
          <div class="flex items-center justify-between">
            <h2 class="text-xl font-bold text-white">Your Library</h2>
            <div class="flex space-x-2 text-xs">
              <button onclick="filterLibrary('all')" id="lib-btn-all" class="px-3 py-1.5 rounded-full bg-white text-black font-semibold">All</button>
              <button onclick="filterLibrary('liked')" id="lib-btn-liked" class="px-3 py-1.5 rounded-full bg-white/10 text-white hover:bg-white/20">Liked</button>
              <button onclick="filterLibrary('playlists')" id="lib-btn-playlists" class="px-3 py-1.5 rounded-full bg-white/10 text-white hover:bg-white/20">Playlists</button>
              <button onclick="filterLibrary('history')" id="lib-btn-history" class="px-3 py-1.5 rounded-full bg-white/10 text-white hover:bg-white/20">History</button>
            </div>
          </div>
          <div id="libraryListContainer" class="space-y-2"></div>
        </div>

        <!-- VIEW: AUTHOR / ARTIST FULL PAGE -->
        <div id="view-artist" class="space-y-6 hidden">
          <div class="flex items-center space-x-6 p-6 rounded-xl bg-gradient-to-r from-neutral-800 to-transparent">
            <img id="artistPageAvatar" src="https://via.placeholder.com/150" class="w-32 h-32 rounded-full object-cover shadow-2xl border-2 border-white/20">
            <div>
              <div class="text-[11px] font-bold uppercase tracking-widest text-neutral-400 flex items-center space-x-1">
                <i class="fa-solid fa-circle-check text-blue-400"></i>
                <span>Verified Artist</span>
              </div>
              <h1 id="artistPageName" class="text-3xl sm:text-5xl font-extrabold text-white my-1">Artist Name</h1>
              <p id="artistPageCount" class="text-xs text-neutral-400">Loading tracks...</p>
              <div class="mt-4 flex items-center space-x-3">
                <button onclick="playAllArtistPage()" class="w-12 h-12 rounded-full bg-white hover:scale-105 text-black flex items-center justify-center text-lg shadow-xl transition">
                  <i class="fa-solid fa-play ml-0.5"></i>
                </button>
                <button onclick="toggleShuffle(); playAllArtistPage();" title="Shuffle Artist" class="p-3 text-neutral-400 hover:text-white text-base">
                  <i class="fa-solid fa-shuffle"></i>
                </button>
              </div>
            </div>
          </div>
          <h3 class="text-lg font-bold text-white">Popular Tracks</h3>
          <div id="artistPageGrid" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4"></div>
        </div>

        <!-- VIEW: PLAYLIST DETAIL (Rename, Update, Delete, Play All) -->
        <div id="view-playlist" class="space-y-6 hidden">
          <div class="flex flex-col sm:flex-row sm:items-end space-y-4 sm:space-y-0 sm:space-x-6 p-6 rounded-xl bg-gradient-to-r from-pink-950/40 via-neutral-900 to-transparent border border-white/5">
            <div id="playlistPageCover" class="w-32 h-32 rounded-lg bg-gradient-to-tr from-pink-600 to-purple-600 flex items-center justify-center text-white text-4xl shadow-2xl flex-shrink-0">
              <i class="fa-solid fa-compact-disc"></i>
            </div>
            <div class="flex-1 min-w-0">
              <div class="text-[11px] font-bold uppercase tracking-widest text-pink-400 font-semibold">PLAYLIST</div>
              <h1 id="playlistPageTitle" class="text-2xl sm:text-4xl font-extrabold text-white my-1 truncate">Playlist Name</h1>
              <p id="playlistPageCount" class="text-xs text-neutral-400">0 songs</p>
              <div class="mt-4 flex flex-wrap items-center gap-2 sm:gap-3">
                <button onclick="playAllCurrentPlaylist()" title="Play All" class="w-11 h-11 rounded-full bg-white hover:scale-105 active:scale-95 text-black flex items-center justify-center text-base shadow-xl transition">
                  <i class="fa-solid fa-play ml-0.5"></i>
                </button>
                <button onclick="shuffleCurrentPlaylist()" title="Shuffle Play" class="p-2.5 text-neutral-400 hover:text-white text-base transition">
                  <i class="fa-solid fa-shuffle"></i>
                </button>
                <button onclick="renameCurrentPlaylist()" title="Rename Playlist" class="px-3.5 py-1.5 rounded-full bg-white/10 hover:bg-white/20 text-white text-xs font-semibold flex items-center space-x-1.5 transition">
                  <i class="fa-solid fa-pen text-[10px]"></i>
                  <span>Rename</span>
                </button>
                <button onclick="deleteCurrentPlaylist()" title="Delete Playlist" class="px-3.5 py-1.5 rounded-full bg-red-950/40 hover:bg-red-900/60 text-red-400 text-xs font-semibold flex items-center space-x-1.5 border border-red-500/20 transition">
                  <i class="fa-solid fa-trash text-[10px]"></i>
                  <span>Delete</span>
                </button>
              </div>
            </div>
          </div>

          <!-- Playlist Tracks Table -->
          <div id="playlistTrackList" class="space-y-1"></div>
        </div>

      </div>
    </main>

    <!-- OPTIONAL RIGHT PANEL: NOW PLAYING DETAILS / AUTHOR SONGS / QUEUE -->
    <aside id="rightPanel" class="w-72 spotify-panel p-4 flex flex-col overflow-y-auto flex-shrink-0 hidden xl:flex">
      <div class="flex items-center justify-between pb-3 border-b border-white/10 mb-3">
        <h3 id="rightPanelTitle" class="text-xs font-bold uppercase tracking-wider text-white truncate">More from Artist</h3>
        <button onclick="toggleShuffle()" id="rightShuffleBtn" title="Toggle Shuffle" class="text-xs text-neutral-400 hover:text-white px-2 py-1 rounded bg-white/5 border border-white/10">
          <i class="fa-solid fa-shuffle text-xs"></i>
          <span id="rightShuffleLabel" class="text-[10px] ml-1">Seq</span>
        </button>
      </div>
      <div id="rightPanelList" class="space-y-2 flex-1 overflow-y-auto pr-1">
        <div class="text-center text-neutral-500 py-12 text-xs">
          Play any song to load artist tracks and queue here.
        </div>
      </div>
    </aside>

  </div>

  <!-- ========================================== -->
  <!-- 4, 5, 6. BOTTOM PLAYER BAR (Panels 4, 5, 6 in diagram) -->
  <!-- ========================================== -->
  <footer class="h-20 sm:h-22 spotify-panel px-4 flex items-center justify-between flex-shrink-0 select-none">
    
    <!-- 4. BOTTOM BAR LEFT: THE CURRENT SONG (Panel 4) -->
    <div class="flex items-center space-x-3 w-1/4 min-w-[180px]">
      <!-- Click cover opens larger now playing / artist -->
      <img id="playerCover" src="https://via.placeholder.com/60" onclick="if(currentTrack) openArtistPage(currentTrack.channel)" class="w-12 h-12 rounded object-cover cursor-pointer hover:opacity-90 flex-shrink-0 shadow">
      <div class="overflow-hidden pr-2">
        <!-- Click title or artist leads to author's page -->
        <div id="playerTitle" onclick="if(currentTrack) openArtistPage(currentTrack.channel)" class="text-xs sm:text-sm font-semibold text-white truncate cursor-pointer hover:underline">Song title</div>
        <div id="playerArtist" onclick="if(currentTrack) openArtistPage(currentTrack.channel)" class="text-[11px] text-neutral-400 truncate cursor-pointer hover:underline">Artist name</div>
      </div>
      <!-- Heart Like Button -->
      <button onclick="toggleFavoriteCurrent()" id="playerHeartBtn" class="p-2 text-neutral-400 hover:text-pink-500 transition text-sm">
        <i class="fa-regular fa-heart"></i>
      </button>
    </div>

    <!-- 5. BOTTOM BAR CENTER: CONTROLS (Panel 5) -->
    <div class="flex flex-col items-center w-2/4 max-w-xl px-2 relative">
      <!-- Buttons row: Shuffle, Prev, White Skull Play/Pause with Flame on Top, Next, Repeat -->
      <div class="relative flex items-center space-x-5 mb-1.5">

        <!-- Shuffle -->
        <button onclick="toggleShuffle()" id="playerShuffleBtn" title="Shuffle" class="text-neutral-400 hover:text-white text-sm transition relative z-10">
          <i class="fa-solid fa-shuffle"></i>
          <span id="playerShuffleDot" class="w-1.5 h-1.5 rounded-full bg-[#00e5ff] shadow-[0_0_8px_#00e5ff] absolute -bottom-1.5 left-1/2 transform -translate-x-1/2 hidden"></span>
        </button>

        <!-- Previous -->
        <button onclick="playPrevTrack()" title="Previous" class="text-neutral-400 hover:text-white text-base transition relative z-10">
          <i class="fa-solid fa-backward-step"></i>
        </button>

        <!-- WHITE SKULL PLAY / PAUSE BUTTON WITH DYNAMIC BEAT FLAME DIRECTLY ON TOP -->
        <div class="relative z-10 flex items-center justify-center">
          <!-- Dynamic Beat Flame Canvas: wraps around the curved cranium of the skull -->
          <div id="flameContainer" class="absolute -top-14 left-1/2 -translate-x-1/2 pointer-events-none w-44 h-26 overflow-visible flex items-end justify-center z-0">
            <canvas id="flameCanvas" width="180" height="104" class="pointer-events-none"></canvas>
          </div>

          <!-- Flame Aura Glow behind skull -->
          <div id="playBtnAura" class="absolute inset-0 rounded-full bg-[#00e5ff]/35 blur-xl pointer-events-none transition-transform duration-75"></div>

          <!-- White Skull Play/Pause Button -->
          <button onclick="togglePlayPause()" id="playerPlayBtn" title="Play / Pause" class="relative z-10 w-11 h-13 sm:w-12 sm:h-14 hover:scale-110 active:scale-95 flex items-center justify-center transition-transform duration-100 select-none focus:outline-none drop-shadow-[0_0_12px_rgba(0,229,255,0.65)]">
            <!-- White Skull SVG -->
            <svg viewBox="0 0 100 115" class="w-full h-full pointer-events-none select-none">
              <defs>
                <linearGradient id="skullBoneGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="#ffffff" />
                  <stop offset="30%" stop-color="#f8fafc" />
                  <stop offset="70%" stop-color="#e2e8f0" />
                  <stop offset="100%" stop-color="#cbd5e1" />
                </linearGradient>
                <radialGradient id="craniumHighlight" cx="50%" cy="28%" r="48%">
                  <stop offset="0%" stop-color="#ffffff" />
                  <stop offset="60%" stop-color="#f8fafc" stop-opacity="0.9" />
                  <stop offset="100%" stop-color="#e2e8f0" stop-opacity="0" />
                </radialGradient>
              </defs>
              <!-- Cranium & Jaw Outline -->
              <path d="M 50,8 
                       C 36,8 24,13 18,22 
                       C 11,31 7,42 7,50 
                       C 7,61 10,70 12,75 
                       C 15,80 23,82 25,85 
                       C 26,87 26,92 27,95 
                       C 29,101 34,103 38,103 
                       L 62,103 
                       C 66,103 71,101 73,95 
                       C 74,92 74,87 75,85 
                       C 77,82 85,80 88,75 
                       C 90,70 93,61 93,50 
                       C 93,42 89,31 82,22 
                       C 76,13 64,8 50,8 Z" 
                    fill="url(#skullBoneGrad)" 
                    stroke="#ffffff" 
                    stroke-width="1.6" />
              <!-- Top Highlight -->
              <ellipse cx="50" cy="35" rx="34" ry="24" fill="url(#craniumHighlight)" />
              <!-- Cheekbone Contour Lines -->
              <path d="M 17,73 Q 23,69 25,62" stroke="#94a3b8" stroke-width="2" stroke-linecap="round" fill="none" opacity="0.6" />
              <path d="M 83,73 Q 77,69 75,62" stroke="#94a3b8" stroke-width="2" stroke-linecap="round" fill="none" opacity="0.6" />
              <!-- Teeth Ridges -->
              <line x1="39" y1="91" x2="39" y2="102" stroke="#07090e" stroke-width="2.2" stroke-linecap="round" />
              <line x1="46" y1="90" x2="46" y2="103" stroke="#07090e" stroke-width="2.2" stroke-linecap="round" />
              <line x1="54" y1="90" x2="54" y2="103" stroke="#07090e" stroke-width="2.2" stroke-linecap="round" />
              <line x1="61" y1="91" x2="61" y2="102" stroke="#07090e" stroke-width="2.2" stroke-linecap="round" />
            </svg>

            <!-- Centered Black Play / Pause Icon (One symbol instead of two eyes) -->
            <div class="absolute inset-0 flex items-center justify-center pb-2.5 pointer-events-none">
              <i id="playerPlayIcon" class="fa-solid fa-play text-black text-xs sm:text-sm ml-0.5" style="color: #000000 !important;"></i>
            </div>
          </button>
        </div>

        <!-- Next -->
        <button onclick="playNextTrack()" title="Next" class="text-neutral-400 hover:text-white text-base transition relative z-10">
          <i class="fa-solid fa-forward-step"></i>
        </button>

        <!-- Repeat -->
        <button onclick="toggleRepeat()" id="playerRepeatBtn" title="Repeat" class="text-neutral-400 hover:text-white text-sm transition relative z-10">
          <i id="repeatIcon" class="fa-solid fa-repeat"></i>
          <span id="playerRepeatDot" class="w-1.5 h-1.5 rounded-full bg-[#00e5ff] shadow-[0_0_8px_#00e5ff] absolute -bottom-1.5 left-1/2 transform -translate-x-1/2 hidden"></span>
        </button>
      </div>

      <!-- Wide Progress Scrub Bar with Times -->
      <div class="w-full flex items-center space-x-2 text-[11px] font-mono text-neutral-400">
        <span id="playerCurrentTime" class="w-8 text-right">0:00</span>
        <input 
          id="playerSeekBar" 
          type="range" 
          min="0" 
          max="100" 
          value="0" 
          oninput="onSeekInput(this.value)" 
          onchange="onSeekChange(this.value)"
          class="flex-1 h-1 bg-white/20 hover:bg-white/40 rounded-full appearance-none cursor-pointer seek-slider accent-white"
        />
        <span id="playerTotalTime" class="w-8">0:00</span>
      </div>
    </div>

    <!-- 6. BOTTOM BAR RIGHT: EXTRA CONTROLS (Panel 6) -->
    <div class="flex items-center justify-end space-x-3 w-1/4 text-neutral-400 text-sm">
      <!-- Lyrics -->
      <button onclick="toggleLyricsModal()" title="Lyrics" class="hover:text-white transition">
        <i class="fa-solid fa-microphone text-xs"></i>
      </button>

      <!-- Queue / Right Panel toggle -->
      <button onclick="toggleRightPanel()" title="Queue & Artist" class="hover:text-white transition">
        <i class="fa-solid fa-bars-staggered text-xs"></i>
      </button>

      <!-- Volume Icon & Slider -->
      <div class="hidden sm:flex items-center space-x-2">
        <button onclick="toggleMute()" title="Mute/Unmute" class="hover:text-white text-xs">
          <i id="volumeIcon" class="fa-solid fa-volume-high"></i>
        </button>
        <input 
          id="volumeSlider" 
          type="range" 
          min="0" 
          max="1" 
          step="0.05" 
          value="1" 
          oninput="changeVolume(this.value)" 
          class="w-20 h-1 bg-white/20 hover:bg-white/40 rounded-full appearance-none cursor-pointer accent-white"
        />
      </div>

      <!-- Download Button -->
      <button onclick="downloadCurrent()" title="Download MP3" class="hover:text-white text-xs">
        <i class="fa-solid fa-download"></i>
      </button>
    </div>
  </footer>

  <!-- MULTI-PLAYLIST PICKER MODAL -->
  <div id="playlistPickerModal" class="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 hidden">
    <div class="bg-[#242424] max-w-md w-full rounded-2xl p-6 border border-white/10 shadow-2xl">
      <div class="flex justify-between items-center mb-4">
        <h3 class="text-base font-bold text-white">Add to Playlist</h3>
        <button onclick="closePlaylistPickerModal()" class="text-neutral-400 hover:text-white"><i class="fa-solid fa-xmark"></i></button>
      </div>

      <div class="space-y-2 max-h-60 overflow-y-auto mb-4" id="playlistPickerList"></div>

      <!-- Create New Playlist on the fly -->
      <div class="pt-3 border-t border-white/10 flex space-x-2">
        <input id="newPlaylistInput" type="text" placeholder="Create new playlist..." class="flex-1 px-3.5 py-2 bg-[#121212] border border-white/10 rounded-lg text-xs text-white placeholder-neutral-500 focus:outline-none focus:ring-1 focus:ring-white">
        <button onclick="createNewPlaylistFromPicker()" class="px-4 py-2 bg-white text-black rounded-lg text-xs font-bold hover:scale-105 transition">Create</button>
      </div>
    </div>
  </div>

  <!-- CREATE / EDIT PLAYLIST MODAL -->
  <div id="createPlaylistModal" class="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 hidden">
    <div class="bg-[#242424] max-w-lg w-full rounded-2xl p-5 sm:p-6 border border-white/10 shadow-2xl space-y-3.5">
      <div class="flex justify-between items-center">
        <h3 id="createPlaylistModalTitle" class="text-base font-bold text-white">Create or Edit Playlist</h3>
        <button onclick="toggleCreatePlaylistModal()" class="text-neutral-400 hover:text-white"><i class="fa-solid fa-xmark"></i></button>
      </div>

      <!-- Live Dynamic Playlist Preview Card -->
      <div class="flex items-center space-x-3.5 p-3 rounded-xl bg-[#181818] border border-white/5">
        <div id="modalCoverPreview" class="w-14 h-14 rounded-lg flex items-center justify-center text-3xl shadow-xl flex-shrink-0 transition-all duration-200" style="background: radial-gradient(circle, #1db954 0%, #121212 100%); border: 1px solid rgba(29, 185, 84, 0.4);">
          <span id="modalEmojiPreview" class="emoji-solid-white">🎵</span>
        </div>
        <div class="flex-1 min-w-0">
          <span class="text-[10px] font-bold uppercase tracking-wider text-pink-400">PLAYLIST PREVIEW</span>
          <div id="modalTitlePreview" class="text-sm font-bold text-white truncate">New Playlist</div>
          <div id="modalContrastLabel" class="text-[10px] text-neutral-400 mt-0.5">Icon: Solid White on Dark BG</div>
        </div>
      </div>
      
      <div>
        <label class="text-[11px] text-neutral-400 uppercase font-semibold block mb-1">Name / URL</label>
        <input id="playlistNameInput" type="text" oninput="updateModalPreviewTitle(this.value)" placeholder="Playlist name OR https://www.youtube.com/playlist?list=..." class="w-full px-3.5 py-2 bg-[#121212] border border-white/10 rounded-lg text-xs text-white placeholder-neutral-500 focus:outline-none focus:ring-1 focus:ring-white">
      </div>

      <!-- Color Selection Swatches with Auto-Contrast Detection -->
      <div>
        <div class="flex items-center justify-between mb-1">
          <label class="text-[11px] text-neutral-400 uppercase font-semibold">Background Theme Color</label>
          <span id="selectedColorPreview" class="w-4 h-4 rounded-full border border-white/40 inline-block" style="background-color: #1db954;"></span>
        </div>
        <div class="flex items-center space-x-2 bg-[#181818] p-2 rounded-lg border border-white/5 overflow-x-auto scrollbar-thin" id="colorPickerList"></div>
      </div>

      <!-- All-Type Emojis: Search, Categories & Solid Black/White Rendering -->
      <div>
        <div class="flex items-center justify-between mb-1">
          <label class="text-[11px] text-neutral-400 uppercase font-semibold">Solid Icon / Emoji</label>
          <div class="flex items-center space-x-1.5">
            <span class="text-[10px] text-neutral-500">Custom:</span>
            <input id="playlistEmojiCustom" type="text" maxlength="4" value="🎵" oninput="selectPlaylistEmoji(this.value)" class="w-12 text-center px-1 py-0.5 bg-[#121212] border border-white/20 rounded text-sm text-white focus:outline-none">
          </div>
        </div>

        <!-- Category Filter Pills -->
        <div class="flex items-center space-x-1 overflow-x-auto pb-1 mb-1.5 text-[10px] scrollbar-thin" id="emojiCategoryPills">
          <button type="button" onclick="filterEmojiCategory('All')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white text-black font-semibold flex-shrink-0" data-cat="All">All</button>
          <button type="button" onclick="filterEmojiCategory('Music')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0" data-cat="Music">🎵 Music</button>
          <button type="button" onclick="filterEmojiCategory('Vibes')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0" data-cat="Vibes">⚡ Vibes</button>
          <button type="button" onclick="filterEmojiCategory('Faces')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0" data-cat="Faces">💀 Faces</button>
          <button type="button" onclick="filterEmojiCategory('Nature')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0" data-cat="Nature">🪐 Nature</button>
          <button type="button" onclick="filterEmojiCategory('Animals')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0" data-cat="Animals">🐺 Animals</button>
          <button type="button" onclick="filterEmojiCategory('Sports')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0" data-cat="Sports">🎮 Sports</button>
          <button type="button" onclick="filterEmojiCategory('Objects')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0" data-cat="Objects">💎 Objects</button>
          <button type="button" onclick="filterEmojiCategory('Symbols')" class="emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0" data-cat="Symbols">♾️ Symbols</button>
        </div>

        <!-- Search bar inside emoji picker -->
        <div class="relative mb-1.5">
          <i class="fa-solid fa-magnifying-glass absolute left-2.5 top-2 text-[10px] text-neutral-500"></i>
          <input id="emojiSearchInput" type="text" placeholder="Search emojis or paste any character..." oninput="searchEmojiCatalog(this.value)" class="w-full pl-7 pr-3 py-1 bg-[#121212] border border-white/10 rounded-md text-[11px] text-white placeholder-neutral-500 focus:outline-none focus:border-white/20">
        </div>

        <!-- Scrollable Grid of All Emojis -->
        <div class="grid grid-cols-8 sm:grid-cols-10 gap-1 bg-[#181818] p-2 rounded-lg border border-white/5 max-h-36 overflow-y-auto scrollbar-thin" id="emojiPickerList"></div>
      </div>

      <button onclick="saveNewPlaylistAction()" id="createPlaylistBtn" class="w-full py-2.5 bg-white text-black rounded-lg text-xs font-bold hover:scale-105 transition">Save Playlist</button>
    </div>
  </div>

  <!-- IMPORT PLAYLIST MODAL -->
  <div id="importModal" class="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4 hidden">
    <div class="bg-[#242424] max-w-md w-full rounded-2xl p-6 border border-white/10 shadow-2xl">
      <div class="flex justify-between items-center mb-4">
        <h3 class="text-base font-bold text-white">Import YouTube Playlist</h3>
        <button onclick="toggleImportModal()" class="text-neutral-400 hover:text-white"><i class="fa-solid fa-xmark"></i></button>
      </div>
      <p class="text-xs text-neutral-400 mb-4">Paste any YouTube playlist URL. Tracks load into your streaming library without downloading media.</p>
      <input id="playlistUrlInput" type="text" placeholder="https://www.youtube.com/playlist?list=..." class="w-full px-4 py-3 bg-[#121212] border border-white/10 rounded-lg text-xs text-white placeholder-neutral-500 focus:outline-none focus:ring-1 focus:ring-white mb-4">
      <button onclick="importPlaylistAction()" class="w-full py-2.5 bg-white text-black rounded-lg text-xs font-bold hover:scale-105 transition">Import Tracks</button>
    </div>
  </div>

  <!-- LYRICS MODAL -->
  <div id="lyricsModal" class="fixed inset-0 bg-[#121212] z-50 p-6 flex flex-col hidden">
    <div class="flex items-center justify-between pb-4 border-b border-white/10 max-w-xl w-full mx-auto">
      <div>
        <h2 class="text-lg font-bold text-white">Lyrics</h2>
        <p id="lyricsTrackInfo" class="text-xs text-neutral-400">Loading...</p>
      </div>
      <button onclick="toggleLyricsModal()" class="text-neutral-400 hover:text-white text-lg"><i class="fa-solid fa-xmark"></i></button>
    </div>
    <div id="lyricsContent" class="flex-1 overflow-y-auto max-w-xl w-full mx-auto py-8 text-center text-xl font-bold text-neutral-300 space-y-6">
      <p class="text-neutral-500">Sing along with the music!</p>
    </div>
  </div>

  <!-- Hidden native HTML5 Audio (no-cors mode enabled) -->
  <audio id="nativeAudio" class="hidden"></audio>

  <script>
    // State management
    const trackCache = {};
    const streamUrlCache = {};
    let currentTrack = null;
    let queue = [];
    let currentIndex = -1;
    let favorites = [];
    let userPlaylists = {};
    let isShuffle = false;
    let repeatMode = 'off';
    let isDraggingSeek = false;
    let artistSongs = [];
    let artistCurrentIndex = -1;
    let currentArtistPageSongs = [];
    let pendingSongForPlaylist = null;
    let searchDebounceTimeout = null;
    let navHistory = ['home'];
    let navHistoryIndex = 0;

    // Spotify-Grade Playback Context (Locks auto-play to active playlist)
    let playbackContext = {
      type: 'queue', // 'playlist' | 'artist' | 'queue'
      name: '',
      tracks: [],
      currentIndex: -1
    };

    // Comprehensive All-Type Categorized Emojis (170+ icons)
    const EMOJI_CATEGORIES = {
      "Music": ["🎵", "🎶", "🎧", "🎤", "🎙️", "🎸", "🎹", "🥁", "🎷", "🎺", "🎻", "📻", "📼", "💿", "📀", "🎼", "🔊", "📢", "🔔", "🎚️", "🎛️", "🪕"],
      "Vibes": ["🖤", "🤍", "💔", "❤️‍🔥", "💫", "⭐", "✨", "🌙", "☀️", "⚡", "🔥", "💤", "🕶️", "☕", "🍷", "🍸", "🚬", "🕯️", "🎭", "🔮", "🪐", "🌌"],
      "Faces": ["💀", "👽", "🤖", "👾", "👻", "🤡", "😈", "🤠", "🧐", "😎", "🥺", "😤", "🤯", "🤫", "😴", "🥳", "🤩", "🫠", "🫡", "😶‍🌫️", "🤐", "👀"],
      "Nature": ["🌊", "🌧️", "❄️", "🌪️", "🌲", "🌴", "🌵", "🌸", "🌹", "🍁", "🍃", "🍄", "🌋", "🏔️", "🌅", "🌠", "☄️", "🛸", "🚀", "🌍", "🌕", "⛅"],
      "Animals": ["🐺", "🦁", "🐯", "🦅", "🦇", "🐍", "🐉", "🕷️", "🦂", "🦉", "🦈", "🐬", "🦋", "🐝", "🐾", "🦄", "🦊", "🐗", "🐆", "🐙", "🐈", "🐕"],
      "Sports": ["🏃", "🛹", "🏄", "🏋️", "🥊", "🏎️", "🏍️", "🎮", "🎲", "🎱", "🎯", "🎳", "🎾", "⚽", "🏀", "🚗", "✈️", "⛵", "🧗", "🚴", "🏆", "🥇"],
      "Objects": ["💎", "👑", "🗡️", "🛡️", "🏹", "🔑", "🗝️", "⛓️", "💣", "⏳", "⌛", "📡", "💻", "🕹️", "📷", "🏮", "💡", "💵", "💰", "📦", "🏷️", "🔒"],
      "Symbols": ["♾️", "☯️", "☮️", "☣️", "☢️", "👁️", "👁️‍🗨️", "🔱", "♠️", "♣️", "♥️", "♦️", "✖️", "➕", "➖", "🔲", "🔳", "🔘", "🏁", "🚩", "⚡", "⭐"]
    };

    const PLAYLIST_COLORS = [
      '#1db954', '#ec4899', '#8b5cf6', '#06b6d4', '#3b82f6', 
      '#f97316', '#ef4444', '#eab308', '#94a3b8', '#ffffff', '#121212'
    ];
    let selectedPlaylistEmoji = '🎵';
    let selectedPlaylistColor = '#1db954';
    let selectedEmojiCategory = 'All';
    let emojiSearchFilter = '';
    let editingPlaylistTarget = null; // null when creating, string name when editing

    // Relative luminance calculation for auto-contrast (solid black vs solid white)
    function isColorLight(hexColor) {
      if (!hexColor) return false;
      let c = String(hexColor).replace('#', '').trim();
      if (c.length === 3) c = c.split('').map(x => x + x).join('');
      if (c.length !== 6) return false;
      const r = parseInt(c.substr(0, 2), 16);
      const g = parseInt(c.substr(2, 2), 16);
      const b = parseInt(c.substr(4, 2), 16);
      const lum = (r * 299 + g * 587 + b * 114) / 1000;
      return lum > 145; // light background if > 145
    }

    function getEmojiClassForBg(hexColor) {
      return isColorLight(hexColor) ? 'emoji-solid-black' : 'emoji-solid-white';
    }

    const audio = document.getElementById('nativeAudio');

    function escapeQuotes(str) {
      if (!str) return '';
      return String(str).split("'").join("&apos;").split('"').join('&quot;');
    }

    // Universal Heart / Like Management for ALL songs
    function isFavorite(videoId) {
      if (!videoId || !favorites) return false;
      return favorites.some(f => f && f.videoId === videoId);
    }

    function toggleFavoriteTrack(videoId) {
      if (!videoId) return;
      const track = trackCache[videoId] || (currentTrack && currentTrack.videoId === videoId ? currentTrack : null);
      const idx = favorites.findIndex(f => f && f.videoId === videoId);
      if (idx >= 0) {
        favorites.splice(idx, 1);
      } else if (track) {
        favorites.unshift(track);
      } else {
        favorites.unshift({
          videoId: videoId,
          title: "Liked Song",
          channel: "Artist",
          thumbnail: `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
          duration: "--:--"
        });
      }
      localStorage.setItem('audiotube_favs', JSON.stringify(favorites));

      if (userPlaylists["Favorites"]) {
        userPlaylists["Favorites"].songs = favorites;
        localStorage.setItem('audiotube_user_playlists', JSON.stringify(userPlaylists));
      }

      const isFav = isFavorite(videoId);
      document.querySelectorAll(`.heart-btn-${videoId}`).forEach(btn => {
        if (btn.classList.contains('recent-heart')) {
          btn.className = `heart-btn-${videoId} recent-heart absolute top-1.5 right-1.5 p-1 rounded-full bg-black/60 text-xs transition ${isFav ? 'text-pink-500' : 'text-white/70 hover:text-pink-500'}`;
        } else {
          btn.className = `heart-btn-${videoId} p-1 text-sm transition hover:scale-110 ${isFav ? 'text-pink-500' : 'text-neutral-400 hover:text-pink-500'}`;
        }
        btn.innerHTML = `<i class="${isFav ? 'fa-solid fa-heart' : 'fa-regular fa-heart'}"></i>`;
      });

      if (currentTrack && currentTrack.videoId === videoId) {
        updateHeartUI();
      }

      const libLikedBtn = document.getElementById('lib-btn-liked');
      if (libLikedBtn && libLikedBtn.classList.contains('bg-white')) {
        renderLibraryList(favorites, 'Liked songs');
      }
    }

    function toggleFavoriteCurrent() {
      if (!currentTrack) return;
      toggleFavoriteTrack(currentTrack.videoId);
    }

    function updateHeartUI() {
      if (!currentTrack) return;
      const isFav = isFavorite(currentTrack.videoId);
      const btn = document.getElementById('playerHeartBtn');
      if (btn) {
        btn.innerHTML = isFav ? '<i class="fa-solid fa-heart text-pink-500"></i>' : '<i class="fa-regular fa-heart"></i>';
      }
    }

    // Audio stream URL pre-fetching for instant continuous auto-play (no pause)
    async function prefetchTrackStream(track) {
      if (!track || !track.videoId || streamUrlCache[track.videoId]) return;
      try {
        const res = await fetch(`/api/stream/${track.videoId}`);
        const data = await res.json();
        if (data && data.stream_url) {
          streamUrlCache[track.videoId] = data.stream_url;
        }
      } catch (e) {}
    }

    function prefetchUpcomingTracks() {
      // 1. If playing from playlist, prefetch next playlist songs
      if (playbackContext.type === 'playlist') {
        const liveTracks = (playbackContext.name === 'Liked songs' ? favorites : getPlaylistSongs(playbackContext.name));
        if (liveTracks && liveTracks.length > 0) playbackContext.tracks = liveTracks;
        const list = playbackContext.tracks;
        if (!list || list.length === 0) return;
        const nextIdx = isShuffle ? Math.floor(Math.random() * list.length) : (playbackContext.currentIndex + 1) % list.length;
        prefetchTrackStream(list[nextIdx]);
        if (list.length > 2) {
          const nextIdx2 = (nextIdx + 1) % list.length;
          prefetchTrackStream(list[nextIdx2]);
        }
        return;
      }

      // 2. If playing from artist page
      if (playbackContext.type === 'artist' && playbackContext.tracks.length > 0) {
        const list = playbackContext.tracks;
        const nextIdx = isShuffle ? Math.floor(Math.random() * list.length) : (playbackContext.currentIndex + 1) % list.length;
        prefetchTrackStream(list[nextIdx]);
        return;
      }

      // 3. Fallback to artistSongs or queue
      if (artistSongs.length > 0) {
        const nextIdx = isShuffle ? Math.floor(Math.random() * artistSongs.length) : (artistCurrentIndex + 1) % artistSongs.length;
        prefetchTrackStream(artistSongs[nextIdx]);
        if (artistSongs.length > 2) {
          const nextIdx2 = (artistCurrentIndex + 2) % artistSongs.length;
          prefetchTrackStream(artistSongs[nextIdx2]);
        }
      } else if (queue.length > 0) {
        const nextIdx = isShuffle ? Math.floor(Math.random() * queue.length) : (currentIndex + 1) % queue.length;
        prefetchTrackStream(queue[nextIdx]);
      }
    }

    // BEAT FLAME VISUALIZER ENGINE
    // Renders real-time procedural cyan fire tongues and rising embers that pulse to the beat
    function initBeatFlameEngine() {
      const canvas = document.getElementById('flameCanvas');
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      const aura = document.getElementById('playBtnAura');
      const playBtn = document.getElementById('playerPlayBtn');

      let flameEnergy = 0.12;
      const particles = [];
      const MAX_PARTICLES = 36;

      class FlameParticle {
        constructor(x, y, isBurst) {
          this.reset(x, y, isBurst);
        }
        reset(x, y, isBurst) {
          this.x = x + (Math.random() - 0.5) * 26;
          this.y = y - Math.random() * 6;
          const mul = isBurst ? 2.0 : 1.0;
          this.vx = (Math.random() - 0.5) * 1.5;
          this.vy = -(1.8 + Math.random() * 2.8) * mul;
          this.size = 2.0 + Math.random() * 3.0;
          this.life = 0;
          this.maxLife = 22 + Math.random() * 30;
          this.color = Math.random() > 0.35 ? '#00e5ff' : (Math.random() > 0.5 ? '#ffffff' : '#00b0ff');
        }
        update() {
          this.x += this.vx + Math.sin(this.life * 0.22) * 0.45;
          this.y += this.vy;
          this.size = Math.max(0.4, this.size * 0.95);
          this.life++;
        }
        draw(c) {
          const alpha = Math.max(0, 1 - (this.life / this.maxLife));
          c.save();
          c.globalAlpha = alpha;
          c.fillStyle = this.color;
          c.shadowColor = '#00e5ff';
          c.shadowBlur = 8;
          c.beginPath();
          c.arc(this.x, this.y, this.size, 0, Math.PI * 2);
          c.fill();
          c.restore();
        }
      }

      function drawFlameTongue(c, baseX, baseY, height, width, curveOffset, colorStop1, colorStop2) {
        c.save();
        const grad = c.createLinearGradient(baseX, baseY + 6, baseX + curveOffset * 1.3, baseY - height);
        grad.addColorStop(0, 'rgba(0, 229, 255, 0)');
        grad.addColorStop(0.12, colorStop1);
        grad.addColorStop(0.65, colorStop2);
        grad.addColorStop(1, 'transparent');

        c.fillStyle = grad;
        c.shadowColor = '#00e5ff';
        c.shadowBlur = 10;
        c.beginPath();
        c.moveTo(baseX - width / 2, baseY);
        c.quadraticCurveTo(baseX - width / 4 + curveOffset * 0.7, baseY - height * 0.55, baseX + curveOffset * 1.25, baseY - height);
        c.quadraticCurveTo(baseX + width / 4 + curveOffset * 0.7, baseY - height * 0.55, baseX + width / 2, baseY);
        c.quadraticCurveTo(baseX, baseY - 4, baseX - width / 2, baseY);
        c.closePath();
        c.fill();
        c.restore();
      }

      function animateFlames() {
        requestAnimationFrame(animateFlames);
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        const isPlaying = (audio && !audio.paused && !audio.ended && audio.currentTime > 0);
        const t = (audio && audio.currentTime > 0) ? audio.currentTime : (Date.now() / 1000);

        // Beat rhythm calculation based on music playback
        let targetEnergy = 0.12; // Resting pilot flame
        if (isPlaying) {
          // Dynamic kick rhythm (128 BPM pulse)
          const kick = Math.pow(Math.max(0, Math.sin(t * Math.PI * 2.133)), 4);
          // Syncopated groove
          const snare = Math.pow(Math.max(0, Math.sin((t + 0.12) * Math.PI * 4.266)), 3);
          // High-frequency flame flicker turbulence
          const jitter = (Math.sin(t * 32) + Math.cos(t * 47)) * 0.08;
          const vol = (audio.volume !== undefined ? audio.volume : 1);
          targetEnergy = Math.min(1.0, (0.32 + kick * 0.58 + snare * 0.2 + jitter) * vol);
        }

        // Smooth physics interpolation (instant attack on kick, organic decay)
        flameEnergy += (targetEnergy - flameEnergy) * (targetEnergy > flameEnergy ? 0.35 : 0.16);

        const centerX = canvas.width / 2;
        const skullApexY = canvas.height - 18;

        function getCraniumY(dx) {
          return skullApexY + (dx * dx) / 38;
        }

        ctx.globalCompositeOperation = 'lighter';

        if (flameEnergy > 0.05) {
          const now = Date.now();
          const wobble = Math.sin(now / 110) * 3.5;
          const wobble2 = Math.cos(now / 130) * 4.5;

          // 1. Radial cyan flame halo wrapping around the curved skull dome
          const haloGrad = ctx.createRadialGradient(centerX, skullApexY + 16, 12, centerX, skullApexY + 16, 36 + flameEnergy * 18);
          haloGrad.addColorStop(0, `rgba(0, 229, 255, ${(0.35 * flameEnergy).toFixed(2)})`);
          haloGrad.addColorStop(0.6, `rgba(0, 160, 255, ${(0.18 * flameEnergy).toFixed(2)})`);
          haloGrad.addColorStop(1, 'transparent');
          ctx.fillStyle = haloGrad;
          ctx.beginPath();
          ctx.arc(centerX, skullApexY + 16, 42 + flameEnergy * 18, Math.PI, 0, false);
          ctx.fill();

          // 2. Far-Left Flame Lick (wrapping around the left temple)
          const lx2 = -26;
          const ly2 = getCraniumY(lx2);
          const lh2 = (14 + flameEnergy * 42) * 0.72;
          drawFlameTongue(ctx, centerX + lx2, ly2, lh2, 18, -13 - wobble2 * 0.8, '#00b0ff', '#0033ff');

          // 3. Mid-Left Flame Lick (sweeping up and around left forehead)
          const lx1 = -14;
          const ly1 = getCraniumY(lx1);
          const lh1 = (18 + flameEnergy * 56) * 0.82;
          drawFlameTongue(ctx, centerX + lx1, ly1, lh1, 24, -7 - wobble * 0.6, '#00e5ff', '#0055ff');

          // 4. Far-Right Flame Lick (wrapping around the right temple)
          const rx2 = 26;
          const ry2 = getCraniumY(rx2);
          const rh2 = (14 + flameEnergy * 42) * 0.70;
          drawFlameTongue(ctx, centerX + rx2, ry2, rh2, 18, 13 + wobble2 * 0.8, '#00b0ff', '#0033ff');

          // 5. Mid-Right Flame Lick (sweeping up and around right forehead)
          const rx1 = 14;
          const ry1 = getCraniumY(rx1);
          const rh1 = (18 + flameEnergy * 56) * 0.80;
          drawFlameTongue(ctx, centerX + rx1, ry1, rh1, 24, 7 + wobble * 0.6, '#00e5ff', '#0055ff');

          // 6. Central Hero Flame Crest (Tallest, roaring fire directly on apex)
          const heroHeight = 24 + flameEnergy * 68;
          // Outer deep blue
          drawFlameTongue(ctx, centerX, skullApexY, heroHeight, 38, wobble * 0.5, '#00b0ff', '#0022ff');
          // Core electric cyan
          drawFlameTongue(ctx, centerX, skullApexY, heroHeight * 0.76, 26, wobble * 0.35, '#00e5ff', '#00d2ff');
          // White-hot inner core
          drawFlameTongue(ctx, centerX, skullApexY, heroHeight * 0.45, 16, 0, '#ffffff', '#aef8ff');
        }

        // Spawn spark particles from along the curved skull cranium arc
        if (isPlaying && flameEnergy > 0.45 && particles.length < MAX_PARTICLES) {
          const burst = (flameEnergy > 0.7);
          const angle = (Math.random() * 0.7 + 0.15) * Math.PI;
          const spawnX = centerX - Math.cos(angle) * (20 + Math.random() * 4);
          const spawnY = (skullApexY + 14) - Math.sin(angle) * (20 + Math.random() * 4);
          particles.push(new FlameParticle(spawnX, spawnY, burst));
          if (burst && particles.length < MAX_PARTICLES) {
            const angle2 = (Math.random() * 0.7 + 0.15) * Math.PI;
            particles.push(new FlameParticle(centerX - Math.cos(angle2) * 22, (skullApexY + 14) - Math.sin(angle2) * 22, true));
          }
        }

        // Update & draw particles
        for (let i = particles.length - 1; i >= 0; i--) {
          const p = particles[i];
          p.update();
          p.draw(ctx);
          if (p.life >= p.maxLife || p.y < 0) {
            particles.splice(i, 1);
          }
        }

        // Pulse Play Button Aura and Box Shadow to the beat
        if (aura) {
          if (isPlaying) {
            const scale = 1.0 + flameEnergy * 0.4;
            const op = 0.3 + flameEnergy * 0.6;
            aura.style.transform = `scale(${scale.toFixed(2)})`;
            aura.style.opacity = op.toFixed(2);
          } else {
            aura.style.transform = 'scale(1)';
            aura.style.opacity = '0.15';
          }
        }

        if (playBtn) {
          if (isPlaying) {
            const glowSpread = Math.round(10 + flameEnergy * 20);
            const glowAlpha = (0.55 + flameEnergy * 0.4).toFixed(2);
            playBtn.style.filter = `drop-shadow(0 0 ${glowSpread}px rgba(0, 229, 255, ${glowAlpha}))`;
          } else {
            playBtn.style.filter = 'drop-shadow(0 0 10px rgba(0, 229, 255, 0.45))';
          }
        }
      }

      animateFlames();
    }

    document.addEventListener('DOMContentLoaded', () => {
      initPlaylistCustomizationUI();
      initBeatFlameEngine();
      loadFavorites();
      loadUserPlaylists();
      loadQueue();
      setGreeting();
      loadHistory();
      renderRecommended();
      setupAudioListeners();
      setupKeyboard();
      restoreLastPlayback();
    });

    function setGreeting() {
      const hour = new Date().getHours();
      let greeting = 'Good evening';
      if (hour < 12) greeting = 'Good morning';
      else if (hour < 18) greeting = 'Good afternoon';
      document.getElementById('homeGreeting').innerText = greeting;
    }

    // View Navigation
    function switchTab(tab, recordNav = true) {
      ['home', 'search', 'library', 'artist', 'playlist'].forEach(t => {
        const view = document.getElementById(`view-${t}`);
        if (view) view.classList.add('hidden');
      });

      const sideHome = document.getElementById('side-nav-home');
      const sideSearch = document.getElementById('side-nav-search');
      if (sideHome) sideHome.className = (tab === 'home') ? 'w-full flex items-center space-x-4 text-white font-bold text-sm' : 'w-full flex items-center space-x-4 text-neutral-400 font-bold text-sm hover:text-white';
      if (sideSearch) sideSearch.className = (tab === 'search') ? 'w-full flex items-center space-x-4 text-white font-bold text-sm' : 'w-full flex items-center space-x-4 text-neutral-400 font-bold text-sm hover:text-white';

      const activeView = document.getElementById(`view-${tab}`);
      if (activeView) activeView.classList.remove('hidden');

      if (recordNav && (navHistory[navHistoryIndex] !== tab)) {
        navHistory = navHistory.slice(0, navHistoryIndex + 1);
        navHistory.push(tab);
        navHistoryIndex = navHistory.length - 1;
      }
    }

    function historyBack() {
      if (navHistoryIndex > 0) {
        navHistoryIndex--;
        switchTab(navHistory[navHistoryIndex], false);
      }
    }

    function historyForward() {
      if (navHistoryIndex < navHistory.length - 1) {
        navHistoryIndex++;
        switchTab(navHistory[navHistoryIndex], false);
      }
    }

    function isPlaylistUrl(str) {
      if (!str || typeof str !== 'string') return false;
      const s = str.trim().toLowerCase();
      return (s.startsWith('http://') || s.startsWith('https://')) && (s.includes('list=') || s.includes('playlist'));
    }

    // Debounced Search as user types or pastes YouTube playlist URL
    function debounceSearch(val) {
      if (searchDebounceTimeout) clearTimeout(searchDebounceTimeout);
      if (isPlaylistUrl(val)) {
        importPlaylistFromUrl(val.trim());
        return;
      }
      searchDebounceTimeout = setTimeout(() => {
        if (val.trim()) {
          switchTab('search');
          runSearch(val.trim());
        } else {
          switchTab('home');
        }
      }, 350);
    }

    async function runSearch(query) {
      document.getElementById('searchResultsTitle').innerText = `Results for "${query}"`;
      const grid = document.getElementById('searchResultsGrid');
      grid.innerHTML = '<div class="col-span-full text-center text-neutral-400 py-12"><i class="fa-solid fa-circle-notch fa-spin mr-2"></i> Searching...</div>';

      try {
        const res = await fetch(`/api/search?q=${encodeURIComponent(query)}`);
        const data = await res.json();
        if (!data.results || data.results.length === 0) {
          grid.innerHTML = '<div class="col-span-full text-center text-neutral-400 py-12">No music found.</div>';
          return;
        }
        grid.innerHTML = data.results.map(item => createCardHTML(item)).join('');
      } catch (e) {
        grid.innerHTML = `<div class="col-span-full text-center text-red-400 py-12">${e.message}</div>`;
      }
    }

    function searchGenre(query) {
      document.getElementById('mainSearchInput').value = query;
      switchTab('search');
      runSearch(query);
    }

    // Open Author / Artist Page
    async function openArtistPage(artistNameOrId) {
      if (!artistNameOrId) return;
      const artistName = (trackCache[artistNameOrId] ? trackCache[artistNameOrId].channel : artistNameOrId);
      switchTab('artist');
      document.getElementById('artistPageName').innerText = artistName;
      document.getElementById('artistPageCount').innerText = 'Loading tracks...';
      const grid = document.getElementById('artistPageGrid');
      grid.innerHTML = '<div class="col-span-full text-center text-neutral-400 py-12"><i class="fa-solid fa-circle-notch fa-spin mr-2"></i> Loading author tracks...</div>';

      try {
        const res = await fetch(`/api/artist_page?artist=${encodeURIComponent(artistName)}`);
        const data = await res.json();
        currentArtistPageSongs = data.songs || [];
        document.getElementById('artistPageName').innerText = data.artist || artistName;
        document.getElementById('artistPageAvatar').src = data.avatar || 'https://via.placeholder.com/300';
        document.getElementById('artistPageCount').innerText = `${currentArtistPageSongs.length} tracks`;

        if (currentArtistPageSongs.length === 0) {
          grid.innerHTML = '<div class="col-span-full text-center text-neutral-400 py-12">No tracks found.</div>';
          return;
        }

        grid.innerHTML = currentArtistPageSongs.map(item => createCardHTML(item)).join('');
      } catch (e) {
        grid.innerHTML = `<div class="col-span-full text-center text-red-400 py-12">${e.message}</div>`;
      }
    }

    function playAllArtistPage() {
      if (!currentArtistPageSongs || currentArtistPageSongs.length === 0) return;
      artistSongs = [...currentArtistPageSongs];
      artistCurrentIndex = 0;
      playTrack(artistSongs[0], `Artist: ${artistSongs[0].channel}`);
      renderRightPanel();
    }

    // Card Renderer: Click picture plays song; Click title/channel opens author page; Button changes color; Love heart symbol on all songs
    function createCardHTML(item) {
      if (!item || !item.videoId) return '';
      trackCache[item.videoId] = item;
      const inPlaylist = isSongInAnyPlaylist(item.videoId);
      const isFav = isFavorite(item.videoId);
      return `
        <div class="spotify-card p-3 flex flex-col justify-between group cursor-pointer" onclick="quickPlayCard('${item.videoId}')">
          <!-- Click Picture plays song immediately -->
          <div class="relative aspect-square rounded-md overflow-hidden bg-neutral-900 mb-3">
            <img src="${item.thumbnail}" class="w-full h-full object-cover">
            <!-- Round Play Button on Hover (like diagram specification) -->
            <div class="absolute bottom-2 right-2 w-10 h-10 rounded-full bg-white text-black flex items-center justify-center shadow-2xl opacity-0 group-hover:opacity-100 group-hover:translate-y-0 translate-y-2 transition-all duration-200">
              <i class="fa-solid fa-play text-sm ml-0.5"></i>
            </div>
            <span class="absolute top-1.5 left-1.5 px-1.5 py-0.5 text-[9px] font-bold rounded bg-black/70 text-neutral-300">
              ${item.duration}
            </span>
          </div>

          <!-- Click song title or channel leads to author page -->
          <div class="overflow-hidden">
            <div class="text-xs sm:text-sm font-bold text-white truncate hover:underline" title="${escapeQuotes(item.title)}" onclick="event.stopPropagation(); openArtistPage('${item.videoId}')">${item.title}</div>
            <div class="text-[11px] text-neutral-400 truncate mt-0.5 hover:underline" onclick="event.stopPropagation(); openArtistPage('${item.videoId}')">${item.channel}</div>
          </div>

          <!-- Action bar: always '+ Add', Love Heart Symbol, Download button -->
          <div class="mt-3 pt-2 border-t border-white/5 flex items-center justify-between text-xs text-neutral-400">
            <button onclick="event.stopPropagation(); openAddToPlaylistDialog('${item.videoId}')" class="add-btn-${item.videoId} px-2.5 py-1 rounded-full text-[11px] font-semibold flex items-center space-x-1.5 transition-all ${inPlaylist ? 'text-[#00e5ff] bg-[#00e5ff]/20 border border-[#00e5ff]/60 shadow-[0_0_10px_rgba(0,229,255,0.35)]' : 'text-neutral-400 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10'}">
              <i class="fa-solid fa-plus text-[10px]"></i>
              <span>Add</span>
            </button>
            <div class="flex items-center space-x-1">
              <button onclick="event.stopPropagation(); toggleFavoriteTrack('${item.videoId}')" title="Like song" class="heart-btn-${item.videoId} p-1 text-sm transition hover:scale-110 ${isFav ? 'text-pink-500' : 'text-neutral-400 hover:text-pink-500'}">
                <i class="${isFav ? 'fa-solid fa-heart' : 'fa-regular fa-heart'}"></i>
              </button>
              <button onclick="event.stopPropagation(); downloadTrack('${item.videoId}')" title="Download MP3" class="hover:text-white p-1">
                <i class="fa-solid fa-download"></i>
              </button>
            </div>
          </div>
        </div>
      `;
    }

    function quickPlayCard(itemOrId) {
      const item = (typeof itemOrId === 'string') ? trackCache[itemOrId] : itemOrId;
      if (!item) return;
      addToQueue(item);
      playbackContext = {
        type: 'queue',
        name: 'Queue',
        tracks: [...queue],
        currentIndex: queue.findIndex(t => t.videoId === item.videoId)
      };
      playTrack(item, 'Selected Track');
    }

    function playTrackFromPlaylist(playlistName, trackIdx) {
      const songs = (playlistName === 'Liked songs' ? favorites : getPlaylistSongs(playlistName));
      if (!songs || songs.length === 0) return;
      playbackContext = {
        type: 'playlist',
        name: playlistName,
        tracks: [...songs],
        currentIndex: trackIdx
      };
      playTrack(playbackContext.tracks[trackIdx], `Playlist: ${playlistName}`, false);
      renderRightPanel();
    }

    function playTrackFromArtist(idx) {
      if (!currentArtistPageSongs || currentArtistPageSongs.length === 0) return;
      const artistName = document.getElementById('artistPageName').innerText || 'Artist';
      playbackContext = {
        type: 'artist',
        name: artistName,
        tracks: [...currentArtistPageSongs],
        currentIndex: idx
      };
      playTrack(playbackContext.tracks[idx], `Artist: ${artistName}`, false);
      renderRightPanel();
    }

    // Playback Engine - Continuous, gapless, no-pause auto-advance
    async function playTrack(track, origin = 'PYROBEAT', isAutoPlay = false) {
      if (!track || !track.videoId) return;
      trackCache[track.videoId] = track;
      currentTrack = track;
      document.getElementById('playerTitle').innerText = track.title;
      document.getElementById('playerArtist').innerText = track.channel;
      document.getElementById('playerCover').src = track.thumbnail;
      document.getElementById('playerPlayIcon').className = 'fa-solid fa-circle-notch fa-spin text-sm';

      updateHeartUI();
      // Only fetch author tracks if NOT playing inside a playlist context (prevents queue hijacking)
      if (playbackContext.type !== 'playlist') {
        loadArtistSongs(track.channel, track.videoId);
      }

      if ('mediaSession' in navigator) {
        navigator.mediaSession.metadata = new MediaMetadata({
          title: track.title,
          artist: track.channel,
          album: track.album || 'PYROBEAT',
          artwork: [{ src: track.thumbnail, sizes: '512x512', type: 'image/jpeg' }]
        });
      }

      // Fast-path: Instant continuous playback without pausing if stream URL is already prefetched!
      if (streamUrlCache[track.videoId]) {
        audio.src = streamUrlCache[track.videoId];
        const p = audio.play();
        if (p !== undefined) {
          p.then(() => {
            document.getElementById('playerPlayIcon').className = 'fa-solid fa-pause';
          }).catch(err => {
            console.warn('Audio play() promise error:', err);
          });
        }
        recordHistory(track);
        localStorage.setItem('audiotube_last_track', JSON.stringify(track));
        setTimeout(prefetchUpcomingTracks, 1000);
        return;
      }

      // If stream not cached, do NOT pause audio during auto-advance so browser media context stays active
      if (!isAutoPlay) {
        audio.pause();
      }

      try {
        const res = await fetch(`/api/stream/${track.videoId}`);
        const data = await res.json();
        if (data.stream_url) {
          streamUrlCache[track.videoId] = data.stream_url;
          audio.src = data.stream_url;
          const p = audio.play();
          if (p !== undefined) {
            p.then(() => {
              document.getElementById('playerPlayIcon').className = 'fa-solid fa-pause';
            }).catch(err => {
              console.warn('Audio play() promise error:', err);
            });
          }
          recordHistory(track);
          localStorage.setItem('audiotube_last_track', JSON.stringify(track));
          setTimeout(prefetchUpcomingTracks, 1000);
        } else {
          document.getElementById('playerPlayIcon').className = 'fa-solid fa-play ml-0.5';
        }
      } catch (e) {
        document.getElementById('playerPlayIcon').className = 'fa-solid fa-play ml-0.5';
        console.error('Playback fetch error:', e);
      }
    }

    function togglePlayPause() {
      if (!audio.src) {
        if (currentTrack) {
          playTrack(currentTrack);
          return;
        }
        const cached = Object.values(trackCache);
        if (cached.length > 0) {
          quickPlayCard(cached[0]);
          return;
        }
        searchGenre('Top hits');
        return;
      }
      if (audio.paused) {
        audio.play().then(() => {
          document.getElementById('playerPlayIcon').className = 'fa-solid fa-pause';
        }).catch(e => console.log('play error:', e));
      } else {
        audio.pause();
        document.getElementById('playerPlayIcon').className = 'fa-solid fa-play ml-0.5';
      }
    }

    function playNextTrack(isAutoPlay = false) {
      // 1. PLAYLIST CONTEXT: ONLY advance within the active playlist (Spotify behavior)
      if (playbackContext.type === 'playlist') {
        const liveTracks = (playbackContext.name === 'Liked songs' ? favorites : getPlaylistSongs(playbackContext.name));
        if (liveTracks && liveTracks.length > 0) {
          playbackContext.tracks = liveTracks;
        }
        const list = playbackContext.tracks;
        if (!list || list.length === 0) return;

        let nextIdx = 0;
        if (isShuffle) {
          if (list.length > 1) {
            do {
              nextIdx = Math.floor(Math.random() * list.length);
            } while (nextIdx === playbackContext.currentIndex && list.length > 1);
          } else {
            nextIdx = 0;
          }
        } else {
          // Spotify continuous loop through playlist
          nextIdx = (playbackContext.currentIndex + 1) % list.length;
        }
        playbackContext.currentIndex = nextIdx;
        playTrack(list[nextIdx], `Playlist: ${playbackContext.name}`, isAutoPlay);
        renderRightPanel();
        return;
      }

      // 2. ARTIST CONTEXT:
      if (playbackContext.type === 'artist' && playbackContext.tracks.length > 0) {
        const list = playbackContext.tracks;
        let nextIdx = isShuffle ? Math.floor(Math.random() * list.length) : (playbackContext.currentIndex + 1) % list.length;
        playbackContext.currentIndex = nextIdx;
        playTrack(list[nextIdx], `Artist: ${playbackContext.name}`, isAutoPlay);
        renderRightPanel();
        return;
      }

      // 3. Fallback to artistSongs:
      if (artistSongs.length > 0) {
        if (isShuffle) {
          artistCurrentIndex = Math.floor(Math.random() * artistSongs.length);
        } else {
          artistCurrentIndex = (artistCurrentIndex + 1) % artistSongs.length;
        }
        const nextSong = artistSongs[artistCurrentIndex];
        playTrack(nextSong, `Artist: ${nextSong.channel}`, isAutoPlay);
        renderRightPanel();
        return;
      }

      // 4. Fallback to queue:
      if (queue.length > 0) {
        if (isShuffle) {
          currentIndex = Math.floor(Math.random() * queue.length);
        } else if (currentIndex + 1 < queue.length) {
          currentIndex++;
        } else {
          currentIndex = 0;
        }
        playTrack(queue[currentIndex], 'Queue', isAutoPlay);
        return;
      }

      const cached = Object.values(trackCache);
      if (cached.length > 0) {
        const rand = cached[Math.floor(Math.random() * cached.length)];
        playTrack(rand, 'Cached', isAutoPlay);
      }
    }

    function playPrevTrack() {
      if (playbackContext.type === 'playlist') {
        const liveTracks = (playbackContext.name === 'Liked songs' ? favorites : getPlaylistSongs(playbackContext.name));
        if (liveTracks && liveTracks.length > 0) {
          playbackContext.tracks = liveTracks;
        }
        const list = playbackContext.tracks;
        if (!list || list.length === 0) return;

        let prevIdx = playbackContext.currentIndex - 1;
        if (prevIdx < 0) prevIdx = list.length - 1;
        playbackContext.currentIndex = prevIdx;
        playTrack(list[prevIdx], `Playlist: ${playbackContext.name}`, false);
        renderRightPanel();
        return;
      }

      if (playbackContext.type === 'artist' && playbackContext.tracks.length > 0) {
        const list = playbackContext.tracks;
        let prevIdx = playbackContext.currentIndex - 1;
        if (prevIdx < 0) prevIdx = list.length - 1;
        playbackContext.currentIndex = prevIdx;
        playTrack(list[prevIdx], `Artist: ${playbackContext.name}`, false);
        renderRightPanel();
        return;
      }

      if (artistSongs.length > 0) {
        if (artistCurrentIndex - 1 >= 0) {
          artistCurrentIndex--;
        } else {
          artistCurrentIndex = artistSongs.length - 1;
        }
        const prevSong = artistSongs[artistCurrentIndex];
        playTrack(prevSong, `Artist: ${prevSong.channel}`, false);
        renderRightPanel();
        return;
      }

      if (queue.length > 0) {
        if (currentIndex - 1 >= 0) {
          currentIndex--;
        } else {
          currentIndex = queue.length - 1;
        }
        playTrack(queue[currentIndex], 'Queue', false);
      }
    }

    // Load other songs by author
    async function loadArtistSongs(artist, currentVideoId) {
      const titleElem = document.getElementById('rightPanelTitle');
      const cleanArtist = (artist || 'Artist').split(',')[0].split('&')[0].trim();
      if (titleElem && playbackContext.type !== 'playlist') titleElem.innerText = `More from ${cleanArtist}`;

      try {
        const res = await fetch(`/api/artist_songs?artist=${encodeURIComponent(cleanArtist)}&exclude_id=${encodeURIComponent(currentVideoId || '')}`);
        const data = await res.json();
        artistSongs = data.songs || [];
        artistCurrentIndex = -1;
        if (playbackContext.type !== 'playlist') {
          renderRightPanel();
        }
        // Prefetch first upcoming author songs for instant zero-pause auto-play
        if (artistSongs.length > 0 && playbackContext.type !== 'playlist') {
          prefetchTrackStream(artistSongs[0]);
          if (artistSongs.length > 1) prefetchTrackStream(artistSongs[1]);
        }
      } catch (e) {}
    }

    function renderRightPanel() {
      const container = document.getElementById('rightPanelList');
      const titleElem = document.getElementById('rightPanelTitle');
      if (!container) return;

      // 1. If playing from playlist -> display upcoming playlist tracks
      if (playbackContext.type === 'playlist' && playbackContext.tracks.length > 0) {
        if (titleElem) titleElem.innerText = `Playlist: ${playbackContext.name}`;
        const list = playbackContext.tracks;
        container.innerHTML = list.map((t, idx) => {
          const isCurrent = (idx === playbackContext.currentIndex);
          const isFav = isFavorite(t.videoId);
          return `
            <div class="p-2 rounded-md hover:bg-white/5 flex items-center justify-between group cursor-pointer transition ${isCurrent ? 'bg-[#00e5ff]/15 text-white border-l-2 border-[#00e5ff] shadow-[0_0_10px_rgba(0,229,255,0.2)]' : 'text-neutral-300'}" onclick="playTrackFromPlaylist('${escapeQuotes(playbackContext.name)}', ${idx})">
              <div class="flex items-center space-x-2.5 overflow-hidden flex-1">
                <span class="text-[10px] ${isCurrent ? 'text-[#00e5ff] font-bold' : 'text-neutral-500'} w-4 text-center">${isCurrent ? '▶' : idx + 1}</span>
                <img src="${t.thumbnail}" class="w-9 h-9 rounded object-cover flex-shrink-0">
                <div class="overflow-hidden">
                  <div class="text-xs font-semibold truncate hover:underline ${isCurrent ? 'text-[#00e5ff]' : ''}" onclick="event.stopPropagation(); openArtistPage('${escapeQuotes(t.channel)}')">${t.title}</div>
                  <div class="text-[10px] text-neutral-400 truncate hover:underline" onclick="event.stopPropagation(); openArtistPage('${escapeQuotes(t.channel)}')">${t.channel}</div>
                </div>
              </div>
              <div class="flex items-center space-x-1.5 ml-2">
                <button onclick="event.stopPropagation(); toggleFavoriteTrack('${t.videoId}')" title="Like song" class="heart-btn-${t.videoId} text-xs transition hover:scale-110 ${isFav ? 'text-pink-500' : 'text-neutral-400 hover:text-pink-500'}">
                  <i class="${isFav ? 'fa-solid fa-heart' : 'fa-regular fa-heart'}"></i>
                </button>
                <span class="text-[10px] text-neutral-500">${t.duration}</span>
              </div>
            </div>
          `;
        }).join('');
        return;
      }

      // 2. Otherwise artist songs or queue
      if (titleElem) titleElem.innerText = (currentTrack ? `More from ${currentTrack.channel}` : 'More from Artist');
      if (artistSongs.length === 0) {
        container.innerHTML = '<div class="text-center text-neutral-500 py-12 text-xs">Play any song to load artist tracks and queue here.</div>';
        return;
      }

      container.innerHTML = artistSongs.map((t, idx) => {
        const isFav = isFavorite(t.videoId);
        const isCurrent = (idx === artistCurrentIndex);
        return `
          <div class="p-2 rounded-md hover:bg-white/5 flex items-center justify-between group cursor-pointer transition ${isCurrent ? 'bg-[#00e5ff]/15 text-white border-l-2 border-[#00e5ff]' : 'text-neutral-300'}" onclick="playArtistRightTrack(${idx})">
            <div class="flex items-center space-x-2.5 overflow-hidden flex-1">
              <img src="${t.thumbnail}" class="w-9 h-9 rounded object-cover flex-shrink-0">
              <div class="overflow-hidden">
                <div class="text-xs font-semibold truncate hover:underline ${isCurrent ? 'text-[#00e5ff]' : ''}" onclick="event.stopPropagation(); openArtistPage('${escapeQuotes(t.channel)}')">${t.title}</div>
                <div class="text-[10px] text-neutral-400 truncate hover:underline" onclick="event.stopPropagation(); openArtistPage('${escapeQuotes(t.channel)}')">${t.channel}</div>
              </div>
            </div>
            <div class="flex items-center space-x-1.5 ml-2">
              <button onclick="event.stopPropagation(); toggleFavoriteTrack('${t.videoId}')" title="Like song" class="heart-btn-${t.videoId} text-xs transition hover:scale-110 ${isFav ? 'text-pink-500' : 'text-neutral-400 hover:text-pink-500'}">
                <i class="${isFav ? 'fa-solid fa-heart' : 'fa-regular fa-heart'}"></i>
              </button>
              <span class="text-[10px] text-neutral-500">${t.duration}</span>
            </div>
          </div>
        `;
      }).join('');
    }

    function playArtistRightTrack(idx) {
      artistCurrentIndex = idx;
      playTrack(artistSongs[idx], `Artist: ${artistSongs[idx].channel}`);
      renderRightPanel();
    }

    // Shuffle & Repeat
    function toggleShuffle() {
      isShuffle = !isShuffle;
      const btn = document.getElementById('playerShuffleBtn');
      const dot = document.getElementById('playerShuffleDot');
      const rightLabel = document.getElementById('rightShuffleLabel');

      if (isShuffle) {
        if (btn) btn.classList.add('text-[#00e5ff]');
        if (dot) dot.classList.remove('hidden');
        if (rightLabel) rightLabel.innerText = 'Shuffle';
      } else {
        if (btn) btn.classList.remove('text-[#00e5ff]');
        if (dot) dot.classList.add('hidden');
        if (rightLabel) rightLabel.innerText = 'Seq';
      }
    }

    function toggleRepeat() {
      const btn = document.getElementById('playerRepeatBtn');
      const dot = document.getElementById('playerRepeatDot');
      const icon = document.getElementById('repeatIcon');

      if (repeatMode === 'off') {
        repeatMode = 'all';
        btn.classList.add('text-[#00e5ff]');
        dot.classList.remove('hidden');
        icon.className = 'fa-solid fa-repeat';
      } else if (repeatMode === 'all') {
        repeatMode = 'one';
        icon.className = 'fa-solid fa-repeat text-[#00e5ff]';
      } else {
        repeatMode = 'off';
        btn.classList.remove('text-[#00e5ff]');
        dot.classList.add('hidden');
        icon.className = 'fa-solid fa-repeat';
      }
    }

    // Seek Bar Logic
    function onSeekInput(val) {
      isDraggingSeek = true;
      if (audio.duration) {
        const target = (val / 100) * audio.duration;
        document.getElementById('playerCurrentTime').innerText = formatTime(target);
      }
    }

    function onSeekChange(val) {
      isDraggingSeek = false;
      if (audio.duration) {
        audio.currentTime = (val / 100) * audio.duration;
      }
    }

    function formatTime(sec) {
      if (!sec || isNaN(sec)) return "0:00";
      const s = Math.floor(sec);
      const m = Math.floor(s / 60);
      const remain = s % 60;
      return `${m}:${remain < 10 ? '0' : ''}${remain}`;
    }

    // Volume & Audio Setup
    function changeVolume(val) {
      audio.volume = parseFloat(val);
      const icon = document.getElementById('volumeIcon');
      if (audio.volume === 0) icon.className = 'fa-solid fa-volume-xmark text-red-400';
      else if (audio.volume < 0.5) icon.className = 'fa-solid fa-volume-low';
      else icon.className = 'fa-solid fa-volume-high';
    }

    function toggleMute() {
      audio.muted = !audio.muted;
      const icon = document.getElementById('volumeIcon');
      if (audio.muted) icon.className = 'fa-solid fa-volume-xmark text-red-400';
      else changeVolume(audio.volume);
    }

    function setupAudioListeners() {
      audio.addEventListener('play', () => {
        document.getElementById('playerPlayIcon').className = 'fa-solid fa-pause';
      });

      audio.addEventListener('playing', () => {
        document.getElementById('playerPlayIcon').className = 'fa-solid fa-pause';
      });

      audio.addEventListener('pause', () => {
        document.getElementById('playerPlayIcon').className = 'fa-solid fa-play ml-0.5';
      });

      audio.addEventListener('waiting', () => {
        document.getElementById('playerPlayIcon').className = 'fa-solid fa-circle-notch fa-spin text-sm';
      });

      audio.addEventListener('error', (e) => {
        console.error('Audio playback error:', audio.error);
        document.getElementById('playerPlayIcon').className = 'fa-solid fa-play ml-0.5';
      });

      audio.addEventListener('timeupdate', () => {
        if (!isNaN(audio.currentTime) && audio.duration) {
          const pct = (audio.currentTime / audio.duration) * 100;
          if (!isDraggingSeek) {
            document.getElementById('playerSeekBar').value = pct;
            document.getElementById('playerCurrentTime').innerText = formatTime(audio.currentTime);
          }
          document.getElementById('playerTotalTime').innerText = formatTime(audio.duration);
          localStorage.setItem('audiotube_last_pos', audio.currentTime);

          // Proactively prefetch upcoming stream URL if nearing end of song (< 20s remaining)
          if (audio.duration - audio.currentTime < 20) {
            prefetchUpcomingTracks();
          }
        }
      });

      audio.addEventListener('ended', () => {
        if (repeatMode === 'one') {
          audio.currentTime = 0;
          audio.play();
        } else {
          playNextTrack(true);
        }
      });

      if ('mediaSession' in navigator) {
        navigator.mediaSession.setActionHandler('play', () => audio.play());
        navigator.mediaSession.setActionHandler('pause', () => audio.pause());
        navigator.mediaSession.setActionHandler('previoustrack', () => playPrevTrack());
        navigator.mediaSession.setActionHandler('nexttrack', () => playNextTrack());
      }
    }

    function restoreLastPlayback() {
      try {
        const last = JSON.parse(localStorage.getItem('audiotube_last_track'));
        if (last) {
          currentTrack = last;
          document.getElementById('playerTitle').innerText = last.title;
          document.getElementById('playerArtist').innerText = last.channel;
          document.getElementById('playerCover').src = last.thumbnail;
          updateHeartUI();
        }
      } catch (e) {}
    }

    // MULTIPLE CUSTOM PLAYLIST MANAGEMENT & ALL-TYPE SOLID EMOJIS
    function initPlaylistCustomizationUI() {
      renderEmojiPickerGrid();

      const colorList = document.getElementById('colorPickerList');
      if (colorList) {
        colorList.innerHTML = PLAYLIST_COLORS.map(c => `
          <button type="button" onclick="selectPlaylistColor('${c}')" class="w-6 h-6 rounded-full transition-transform hover:scale-110 flex-shrink-0 color-btn ${c === selectedPlaylistColor ? 'scale-125 ring-2 ring-white ring-offset-2 ring-offset-black' : ''}" style="background-color: ${c}; border: 1px solid rgba(255,255,255,0.2);" data-color="${c}" title="${c}">
          </button>
        `).join('');
      }
      updateModalPreview();
    }

    function getAllEmojis() {
      const all = [];
      for (const list of Object.values(EMOJI_CATEGORIES)) {
        for (const em of list) {
          if (!all.includes(em)) all.push(em);
        }
      }
      return all;
    }

    function renderEmojiPickerGrid() {
      const emojiList = document.getElementById('emojiPickerList');
      if (!emojiList) return;

      let list = [];
      if (selectedEmojiCategory === 'All') {
        list = getAllEmojis();
      } else if (EMOJI_CATEGORIES[selectedEmojiCategory]) {
        list = EMOJI_CATEGORIES[selectedEmojiCategory];
      }

      if (emojiSearchFilter.trim()) {
        const q = emojiSearchFilter.trim().toLowerCase();
        list = list.filter(em => em.includes(q));
        if (q && !list.includes(q)) {
          list.unshift(q);
        }
      }

      emojiList.innerHTML = list.map(em => `
        <button type="button" onclick="selectPlaylistEmoji('${escapeQuotes(em)}')" class="w-8 h-8 rounded hover:bg-white/10 flex items-center justify-center transition text-base emoji-btn ${em === selectedPlaylistEmoji ? 'bg-white/20 ring-1 ring-white' : ''}" data-emoji="${escapeQuotes(em)}" title="${escapeQuotes(em)}">
          <span class="emoji-solid-white">${em}</span>
        </button>
      `).join('');
    }

    function filterEmojiCategory(cat) {
      selectedEmojiCategory = cat;
      document.querySelectorAll('.emoji-cat-btn').forEach(btn => {
        if (btn.dataset.cat === cat) {
          btn.className = 'emoji-cat-btn px-2 py-0.5 rounded-full bg-white text-black font-semibold flex-shrink-0';
        } else {
          btn.className = 'emoji-cat-btn px-2 py-0.5 rounded-full bg-white/5 text-neutral-400 hover:text-white flex-shrink-0';
        }
      });
      renderEmojiPickerGrid();
    }

    function searchEmojiCatalog(val) {
      emojiSearchFilter = val || '';
      renderEmojiPickerGrid();
      if (val && val.trim().length > 0) {
        selectPlaylistEmoji(val.trim());
      }
    }

    function updateModalPreviewTitle(val) {
      const titleEl = document.getElementById('modalTitlePreview');
      if (titleEl) {
        titleEl.innerText = val.trim() || 'New Playlist';
      }
    }

    function updateModalPreview() {
      const cover = document.getElementById('modalCoverPreview');
      const emojiSpan = document.getElementById('modalEmojiPreview');
      const contrastLabel = document.getElementById('modalContrastLabel');
      const isLight = isColorLight(selectedPlaylistColor);

      if (cover) {
        cover.style.background = `radial-gradient(circle, ${selectedPlaylistColor} 0%, #121212 100%)`;
        cover.style.border = `1px solid ${selectedPlaylistColor}50`;
      }
      if (emojiSpan) {
        emojiSpan.innerText = selectedPlaylistEmoji || '🎵';
        emojiSpan.className = isLight ? 'emoji-solid-black' : 'emoji-solid-white';
      }
      if (contrastLabel) {
        contrastLabel.innerText = isLight ? 'Icon: Solid Black on Light BG' : 'Icon: Solid White on Dark BG';
      }
      const previewDot = document.getElementById('selectedColorPreview');
      if (previewDot) {
        previewDot.style.backgroundColor = selectedPlaylistColor;
      }
    }

    function selectPlaylistEmoji(em) {
      selectedPlaylistEmoji = em || '🎵';
      const customInput = document.getElementById('playlistEmojiCustom');
      if (customInput && customInput.value !== selectedPlaylistEmoji) {
        customInput.value = selectedPlaylistEmoji;
      }
      document.querySelectorAll('.emoji-btn').forEach(btn => {
        const isMatch = btn.dataset.emoji === selectedPlaylistEmoji;
        btn.className = `w-8 h-8 rounded hover:bg-white/10 flex items-center justify-center transition text-base emoji-btn ${isMatch ? 'bg-white/20 ring-1 ring-white' : ''}`;
      });
      updateModalPreview();
    }

    function selectPlaylistColor(col) {
      selectedPlaylistColor = col || '#1db954';
      document.querySelectorAll('.color-btn').forEach(btn => {
        const isMatch = btn.dataset.color === selectedPlaylistColor;
        btn.className = `w-6 h-6 rounded-full transition-transform hover:scale-110 flex-shrink-0 color-btn ${isMatch ? 'scale-125 ring-2 ring-white ring-offset-2 ring-offset-black' : ''}`;
      });
      updateModalPreview();
    }

    function getPlaylistSongs(name) {
      const pl = userPlaylists[name];
      if (!pl) return [];
      return Array.isArray(pl) ? pl : (pl.songs || []);
    }

    function getPlaylistEmoji(name) {
      const pl = userPlaylists[name];
      if (!pl || Array.isArray(pl)) return '🎵';
      return pl.emoji || '🎵';
    }

    function getPlaylistColor(name) {
      const pl = userPlaylists[name];
      if (!pl || Array.isArray(pl)) return '#1db954';
      return pl.color || '#1db954';
    }

    function loadUserPlaylists() {
      try {
        userPlaylists = JSON.parse(localStorage.getItem('audiotube_user_playlists')) || {
          "Favorites": { songs: favorites, emoji: "🖤", color: "#ec4899" }
        };
      } catch (e) {
        userPlaylists = { "Favorites": { songs: favorites, emoji: "🖤", color: "#ec4899" } };
      }
      // Purge demo/hardcoded playlists
      delete userPlaylists["Late night drive"];
      delete userPlaylists["Workout mix"];
      delete userPlaylists["Study focus"];
      for (const [k, v] of Object.entries(userPlaylists)) {
        if (Array.isArray(v)) {
          userPlaylists[k] = { songs: v, emoji: "🎵", color: "#1db954" };
        }
      }
      localStorage.setItem('audiotube_user_playlists', JSON.stringify(userPlaylists));
      renderSidebarUserPlaylists();
    }

    function updateAddButtonsState(videoId) {
      if (!videoId) return;
      const inPl = isSongInAnyPlaylist(videoId);
      document.querySelectorAll(`.add-btn-${videoId}`).forEach(btn => {
        btn.className = `add-btn-${videoId} px-2.5 py-1 rounded-full text-[11px] font-semibold flex items-center space-x-1.5 transition-all ${inPl ? 'text-[#00e5ff] bg-[#00e5ff]/20 border border-[#00e5ff]/60 shadow-[0_0_10px_rgba(0,229,255,0.35)]' : 'text-neutral-400 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10'}`;
      });
    }

    function saveUserPlaylists(affectedVideoId) {
      localStorage.setItem('audiotube_user_playlists', JSON.stringify(userPlaylists));
      renderSidebarUserPlaylists();
      if (affectedVideoId) {
        updateAddButtonsState(affectedVideoId);
      } else {
        document.querySelectorAll('[class*="add-btn-"]').forEach(btn => {
          const m = btn.className.match(/add-btn-([A-Za-z0-9_-]+)/);
          if (m && m[1]) updateAddButtonsState(m[1]);
        });
      }
    }

    function renderSidebarUserPlaylists() {
      const container = document.getElementById('sidebarUserPlaylists');
      if (!container) return;
      const names = Object.keys(userPlaylists).filter(n => n !== "Favorites");
      container.innerHTML = names.map(name => {
        const emoji = getPlaylistEmoji(name);
        const color = getPlaylistColor(name);
        const songs = getPlaylistSongs(name);
        return `
          <div onclick="viewSpecificPlaylist('${escapeQuotes(name)}')" class="flex items-center justify-between py-1.5 px-2 rounded-md hover:bg-white/5 hover:text-white cursor-pointer transition group">
            <span class="truncate flex items-center space-x-2.5">
              <span class="text-xs flex-shrink-0 emoji-solid-white" style="filter: grayscale(100%) brightness(0) invert(1) drop-shadow(0 0 6px ${color}80);">${emoji}</span>
              <span class="truncate text-xs font-semibold">${name}</span>
            </span>
            <span class="text-[10px] text-neutral-500">${songs.length}</span>
          </div>
        `;
      }).join('');
    }

    function isSongInAnyPlaylist(videoId) {
      if (!videoId || !userPlaylists) return false;
      try {
        for (const pl of Object.values(userPlaylists)) {
          const songs = Array.isArray(pl) ? pl : (pl?.songs || []);
          if (songs.some(t => t && t.videoId === videoId)) return true;
        }
      } catch (e) {}
      return false;
    }

    function openAddToPlaylistDialog(trackOrId) {
      const track = (typeof trackOrId === 'string') ? trackCache[trackOrId] : trackOrId;
      if (!track) return;
      pendingSongForPlaylist = track;
      const modal = document.getElementById('playlistPickerModal');
      const list = document.getElementById('playlistPickerList');
      modal.classList.remove('hidden');

      const names = Object.keys(userPlaylists);
      list.innerHTML = names.map(name => {
        const songs = getPlaylistSongs(name);
        const inPl = songs.some(t => t.videoId === track.videoId);
        const emoji = getPlaylistEmoji(name);
        const color = getPlaylistColor(name);
        const emojiClass = getEmojiClassForBg(color);
        return `
          <div class="flex items-center justify-between p-3 rounded-lg bg-[#181818] hover:bg-[#282828] cursor-pointer transition" onclick="toggleSongInPlaylist('${escapeQuotes(name)}')">
            <div class="flex items-center space-x-3">
              <span class="w-7 h-7 rounded-md flex items-center justify-center text-sm" style="background: radial-gradient(circle, ${color} 0%, #121212 100%); border: 1px solid ${color}40;">
                <span class="${emojiClass}">${emoji}</span>
              </span>
              <span class="text-xs font-semibold text-white">${name}</span>
              <span class="text-[10px] text-neutral-400">(${songs.length})</span>
            </div>
            <i class="${inPl ? 'fa-solid fa-circle-check text-emerald-400 text-base' : 'fa-regular fa-circle text-neutral-600 text-base'}"></i>
          </div>
        `;
      }).join('');
    }

    function closePlaylistPickerModal() {
      document.getElementById('playlistPickerModal').classList.add('hidden');
      pendingSongForPlaylist = null;
    }

    function toggleSongInPlaylist(name) {
      if (!pendingSongForPlaylist || !userPlaylists[name]) return;
      const vid = pendingSongForPlaylist.videoId;
      const songs = getPlaylistSongs(name);
      const idx = songs.findIndex(t => t.videoId === vid);
      if (idx >= 0) songs.splice(idx, 1);
      else songs.unshift(pendingSongForPlaylist);
      if (Array.isArray(userPlaylists[name])) {
        userPlaylists[name] = { songs: songs, emoji: '🎵', color: '#1db954' };
      } else {
        userPlaylists[name].songs = songs;
      }
      saveUserPlaylists(vid);
      openAddToPlaylistDialog(pendingSongForPlaylist);
      if (currentViewingPlaylist === name) {
        viewSpecificPlaylist(name);
      }
    }

    function createNewPlaylistFromPicker() {
      const input = document.getElementById('newPlaylistInput');
      const name = input.value.trim();
      if (!name) return;
      if (!userPlaylists[name]) {
        const vid = pendingSongForPlaylist ? pendingSongForPlaylist.videoId : null;
        userPlaylists[name] = {
          songs: pendingSongForPlaylist ? [pendingSongForPlaylist] : [],
          emoji: '🎵',
          color: '#1db954'
        };
        saveUserPlaylists(vid);
        input.value = '';
        openAddToPlaylistDialog(pendingSongForPlaylist);
      }
    }

    function openCreatePlaylistModal() {
      editingPlaylistTarget = null;
      document.getElementById('createPlaylistModalTitle').innerText = 'Create or Import Playlist';
      document.getElementById('playlistNameInput').value = '';
      document.getElementById('createPlaylistBtn').innerText = 'Create Playlist';
      updateModalPreviewTitle('New Playlist');
      selectPlaylistEmoji('🎵');
      selectPlaylistColor('#1db954');
      filterEmojiCategory('All');
      const searchBox = document.getElementById('emojiSearchInput');
      if (searchBox) searchBox.value = '';
      emojiSearchFilter = '';
      renderEmojiPickerGrid();
      document.getElementById('createPlaylistModal').classList.remove('hidden');
    }

    function openEditPlaylistModal(name) {
      editingPlaylistTarget = name;
      document.getElementById('createPlaylistModalTitle').innerText = `Edit Playlist "${name}"`;
      document.getElementById('playlistNameInput').value = name;
      document.getElementById('createPlaylistBtn').innerText = 'Save Changes';
      updateModalPreviewTitle(name);
      selectPlaylistEmoji(getPlaylistEmoji(name));
      selectPlaylistColor(getPlaylistColor(name));
      filterEmojiCategory('All');
      const searchBox = document.getElementById('emojiSearchInput');
      if (searchBox) searchBox.value = '';
      emojiSearchFilter = '';
      renderEmojiPickerGrid();
      document.getElementById('createPlaylistModal').classList.remove('hidden');
    }

    function toggleCreatePlaylistModal() {
      document.getElementById('createPlaylistModal').classList.toggle('hidden');
      if (document.getElementById('createPlaylistModal').classList.contains('hidden')) {
        editingPlaylistTarget = null;
      }
    }

    function saveNewPlaylistAction() {
      const input = document.getElementById('playlistNameInput');
      const val = input.value.trim();
      if (!val) return;
      toggleCreatePlaylistModal();
      input.value = '';

      if (editingPlaylistTarget) {
        const oldName = editingPlaylistTarget;
        const newName = val;
        const songs = getPlaylistSongs(oldName);
        if (newName !== oldName) {
          delete userPlaylists[oldName];
        }
        userPlaylists[newName] = {
          songs: songs,
          emoji: selectedPlaylistEmoji,
          color: selectedPlaylistColor
        };
        saveUserPlaylists();
        viewSpecificPlaylist(newName);
        editingPlaylistTarget = null;
        return;
      }

      if (isPlaylistUrl(val)) {
        importPlaylistFromUrl(val, selectedPlaylistEmoji, selectedPlaylistColor);
      } else {
        if (!userPlaylists[val]) {
          userPlaylists[val] = {
            songs: [],
            emoji: selectedPlaylistEmoji,
            color: selectedPlaylistColor
          };
          saveUserPlaylists();
        }
        viewSpecificPlaylist(val);
      }
    }

    async function importPlaylistFromUrl(url, emoji = '🎵', color = '#1db954') {
      if (!url) return;
      switchTab('search');
      document.getElementById('searchResultsTitle').innerText = 'Importing YouTube Playlist...';
      const grid = document.getElementById('searchResultsGrid');
      grid.innerHTML = '<div class="col-span-full text-center text-neutral-400 py-16"><i class="fa-solid fa-circle-notch fa-spin text-2xl mb-3 text-pink-500"></i><br>Fetching playlist tracks from YouTube...</div>';

      try {
        const res = await fetch(`/api/playlist?url_or_id=${encodeURIComponent(url)}`);
        const data = await res.json();
        if (data.tracks && data.tracks.length > 0) {
          let baseName = data.title || 'YouTube Playlist';
          let finalName = baseName;
          let counter = 1;
          while (userPlaylists[finalName]) {
            finalName = `${baseName} (${counter++})`;
          }
          userPlaylists[finalName] = {
            songs: data.tracks,
            emoji: emoji || '🎵',
            color: color || '#1db954'
          };
          saveUserPlaylists();
          data.tracks.forEach(t => { if (t && t.videoId) trackCache[t.videoId] = t; });
          viewSpecificPlaylist(finalName);
        } else {
          grid.innerHTML = '<div class="col-span-full text-center text-red-400 py-16">No tracks found in playlist URL.</div>';
        }
      } catch (e) {
        grid.innerHTML = `<div class="col-span-full text-center text-red-400 py-16">Import failed: ${e.message}</div>`;
      }
    }

    let currentViewingPlaylist = '';

    function viewSpecificPlaylist(name) {
      currentViewingPlaylist = name;
      switchTab('playlist');
      document.getElementById('playlistPageTitle').innerText = name;
      const songs = getPlaylistSongs(name);
      const emoji = getPlaylistEmoji(name);
      const color = getPlaylistColor(name);
      document.getElementById('playlistPageCount').innerText = `${songs.length} tracks`;

      const coverEl = document.getElementById('playlistPageCover');
      coverEl.style.background = `radial-gradient(circle, ${color} 0%, #121212 100%)`;
      coverEl.style.border = `1px solid ${color}40`;
      const emojiClass = getEmojiClassForBg(color);
      coverEl.innerHTML = `<span class="${emojiClass}">${emoji}</span>`;

      const container = document.getElementById('playlistTrackList');
      if (songs.length === 0) {
        container.innerHTML = `<div class="text-center text-neutral-500 py-16 text-sm">This playlist is empty.<br><span class="text-xs text-neutral-600 mt-1 inline-block">Search songs to add or paste a YouTube playlist URL in the search bar!</span></div>`;
        return;
      }
      songs.forEach(t => { if (t && t.videoId) trackCache[t.videoId] = t; });
      container.innerHTML = songs.map((t, idx) => {
        const isFav = isFavorite(t.videoId);
        return `
          <div class="p-2.5 rounded-lg bg-[#181818] hover:bg-[#282828] flex items-center justify-between group cursor-pointer transition" onclick="playTrackFromPlaylist('${escapeQuotes(name)}', ${idx})">
            <div class="flex items-center space-x-3 overflow-hidden flex-1">
              <span class="text-xs text-neutral-500 w-6 text-center">${idx + 1}</span>
              <img src="${t.thumbnail}" class="w-10 h-10 rounded object-cover flex-shrink-0">
              <div class="overflow-hidden">
                <div class="text-sm font-bold text-white truncate hover:underline" onclick="event.stopPropagation(); openArtistPage('${t.videoId}')">${t.title}</div>
                <div class="text-xs text-neutral-400 truncate hover:underline" onclick="event.stopPropagation(); openArtistPage('${t.videoId}')">${t.channel}</div>
              </div>
            </div>
            <div class="flex items-center space-x-2 text-xs text-neutral-400">
              <span class="text-neutral-500 mr-1">${t.duration}</span>
              <button onclick="event.stopPropagation(); toggleFavoriteTrack('${t.videoId}')" title="Like song" class="heart-btn-${t.videoId} p-1 text-sm transition hover:scale-110 ${isFav ? 'text-pink-500' : 'text-neutral-400 hover:text-pink-500'}">
                <i class="${isFav ? 'fa-solid fa-heart' : 'fa-regular fa-heart'}"></i>
              </button>
              <button onclick="event.stopPropagation(); removeSongFromPlaylist('${escapeQuotes(name)}', '${t.videoId}')" title="Remove song from playlist" class="hover:text-red-400 p-1.5 transition">
                <i class="fa-solid fa-trash-can text-neutral-500 hover:text-red-400 text-xs"></i>
              </button>
              <button onclick="event.stopPropagation(); downloadTrack('${t.videoId}')" title="Download MP3" class="hover:text-white p-1.5">
                <i class="fa-solid fa-download"></i>
              </button>
            </div>
          </div>
        `;
      }).join('');
    }

    function playAllCurrentPlaylist() {
      if (!currentViewingPlaylist) return;
      playTrackFromPlaylist(currentViewingPlaylist, 0);
    }

    function shuffleCurrentPlaylist() {
      if (!currentViewingPlaylist) return;
      const songs = (currentViewingPlaylist === 'Liked songs' ? favorites : getPlaylistSongs(currentViewingPlaylist));
      if (!songs || songs.length === 0) return;
      isShuffle = true;
      const dot = document.getElementById('playerShuffleDot');
      if (dot) dot.classList.remove('hidden');
      const rightLabel = document.getElementById('rightShuffleLabel');
      if (rightLabel) rightLabel.innerText = 'Shuffle';
      const randIdx = Math.floor(Math.random() * songs.length);
      playTrackFromPlaylist(currentViewingPlaylist, randIdx);
    }

    function renameCurrentPlaylist() {
      if (!currentViewingPlaylist) return;
      openEditPlaylistModal(currentViewingPlaylist);
    }

    function deleteCurrentPlaylist() {
      if (!currentViewingPlaylist) return;
      const name = currentViewingPlaylist;
      if (!confirm(`Delete playlist "${name}"? This cannot be undone.`)) return;
      delete userPlaylists[name];
      saveUserPlaylists();
      switchTab('library');
      filterLibrary('playlists');
    }

    function removeSongFromPlaylist(playlistName, videoId) {
      if (!userPlaylists[playlistName]) return;
      const songs = getPlaylistSongs(playlistName).filter(t => t.videoId !== videoId);
      if (Array.isArray(userPlaylists[playlistName])) {
        userPlaylists[playlistName] = { songs: songs, emoji: '🎵', color: '#1db954' };
      } else {
        userPlaylists[playlistName].songs = songs;
      }
      saveUserPlaylists(videoId);
      viewSpecificPlaylist(playlistName);
    }

    // Favorites
    function loadFavorites() {
      try {
        favorites = JSON.parse(localStorage.getItem('audiotube_favs')) || [];
      } catch (e) { favorites = []; }
      favorites.forEach(t => { if (t && t.videoId) trackCache[t.videoId] = t; });
    }

    // Queue
    function loadQueue() {
      try {
        queue = JSON.parse(localStorage.getItem('audiotube_queue')) || [];
      } catch (e) { queue = []; }
    }

    function addToQueue(track) {
      if (!queue.some(t => t.videoId === track.videoId)) {
        queue.push(track);
        localStorage.setItem('audiotube_queue', JSON.stringify(queue));
      }
    }

    // Library Views
    function filterLibrary(mode) {
      ['all', 'liked', 'playlists', 'history'].forEach(m => {
        const btn = document.getElementById(`lib-btn-${m}`);
        if (btn) btn.className = (m === mode) ? 'px-3 py-1.5 rounded-full bg-white text-black font-semibold' : 'px-3 py-1.5 rounded-full bg-white/10 text-white hover:bg-white/20';
      });

      if (mode === 'playlists') {
        renderPlaylistsCards();
        return;
      }
      if (mode === 'liked') {
        renderLibraryList(favorites, 'Liked songs');
        return;
      }
      if (mode === 'history') {
        fetch('/api/history').then(r => r.json()).then(d => renderLibraryList(d.history || []));
        return;
      }
      renderLibraryList([...favorites, ...queue]);
    }

    function renderPlaylistsCards() {
      const container = document.getElementById('libraryListContainer');
      const names = Object.keys(userPlaylists).filter(n => n !== "Favorites");
      if (names.length === 0) {
        container.innerHTML = '<div class="text-center text-neutral-500 py-16 text-sm">No custom playlists yet.<br><span class="text-xs text-neutral-600 mt-1 inline-block">Click "+ Create Playlist" or paste a YouTube playlist URL!</span></div>';
        return;
      }
      container.innerHTML = names.map(name => {
        const songs = getPlaylistSongs(name);
        const emoji = getPlaylistEmoji(name);
        const color = getPlaylistColor(name);
        return `
          <div class="p-3 rounded-lg bg-[#181818] hover:bg-[#282828] flex items-center justify-between cursor-pointer transition" onclick="viewSpecificPlaylist('${escapeQuotes(name)}')">
            <div class="flex items-center space-x-3">
              <div class="w-10 h-10 rounded-lg flex items-center justify-center text-xl shadow-md flex-shrink-0" style="background: radial-gradient(circle, ${color} 0%, #121212 100%); border: 1px solid ${color}40;">
                <span class="${getEmojiClassForBg(color)}">${emoji}</span>
              </div>
              <div>
                <div class="text-sm font-bold text-white">${name}</div>
                <div class="text-xs text-neutral-400">${songs.length} tracks</div>
              </div>
            </div>
            <div class="flex items-center space-x-2">
              <button onclick="event.stopPropagation(); openEditPlaylistModal('${escapeQuotes(name)}')" title="Edit Playlist" class="text-xs px-2.5 py-1 rounded bg-white/10 hover:bg-white/20 text-white transition"><i class="fa-solid fa-pen text-[10px]"></i></button>
              <button onclick="event.stopPropagation(); currentViewingPlaylist = '${escapeQuotes(name)}'; deleteCurrentPlaylist()" title="Delete Playlist" class="text-xs px-2.5 py-1 rounded bg-red-950/40 hover:bg-red-900/60 text-red-400 transition"><i class="fa-solid fa-trash text-[10px]"></i></button>
              <button class="text-xs px-3 py-1 rounded bg-white text-black font-semibold">Open</button>
            </div>
          </div>
        `;
      }).join('');
    }

    function renderLibraryList(items, subtitle = '') {
      const container = document.getElementById('libraryListContainer');
      if (!items || items.length === 0) {
        container.innerHTML = `<div class="text-center text-neutral-500 py-12 text-sm">${subtitle ? subtitle + ' is empty.' : 'No tracks.'}</div>`;
        return;
      }
      items.forEach(t => { if (t && t.videoId) trackCache[t.videoId] = t; });
      container.innerHTML = `
        ${subtitle ? `
          <div class="flex items-center justify-between mb-3 pb-2 border-b border-white/5">
            <div>
              <div class="text-base font-bold text-white flex items-center space-x-2">
                <i class="fa-solid fa-heart text-pink-500 text-sm"></i>
                <span>${subtitle}</span>
              </div>
              <div class="text-xs text-neutral-400">${items.length} songs</div>
            </div>
            ${subtitle === 'Liked songs' && items.length > 0 ? `
              <div class="flex items-center space-x-2">
                <button onclick="playTrackFromPlaylist('Liked songs', 0)" class="w-9 h-9 rounded-full bg-[#00e5ff] text-black shadow-[0_0_12px_rgba(0,229,255,0.4)] hover:scale-105 active:scale-95 flex items-center justify-center text-sm transition" title="Play All">
                  <i class="fa-solid fa-play ml-0.5"></i>
                </button>
                <button onclick="isShuffle=true; const dot=document.getElementById('playerShuffleDot'); if(dot) dot.classList.remove('hidden'); playTrackFromPlaylist('Liked songs', Math.floor(Math.random() * ${items.length}))" class="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center text-xs transition" title="Shuffle Play">
                  <i class="fa-solid fa-shuffle"></i>
                </button>
              </div>
            ` : ''}
          </div>
        ` : ''}
        ${items.map((t, idx) => {
          const isFav = isFavorite(t.videoId);
          return `
            <div class="p-2.5 rounded-lg bg-[#181818] hover:bg-[#282828] flex items-center justify-between group cursor-pointer transition" onclick="${subtitle === 'Liked songs' ? `playTrackFromPlaylist('Liked songs', ${idx})` : `quickPlayCard('${t.videoId}')`}">
              <div class="flex items-center space-x-3 overflow-hidden flex-1">
                <span class="text-xs text-neutral-500 w-5">${idx + 1}</span>
                <img src="${t.thumbnail}" class="w-10 h-10 rounded object-cover flex-shrink-0">
                <div class="overflow-hidden">
                  <div class="text-sm font-bold text-white truncate hover:underline" onclick="event.stopPropagation(); openArtistPage('${t.videoId}')">${t.title}</div>
                  <div class="text-xs text-neutral-400 truncate hover:underline" onclick="event.stopPropagation(); openArtistPage('${t.videoId}')">${t.channel}</div>
                </div>
              </div>
              <div class="flex items-center space-x-2 text-xs text-neutral-400">
                <button onclick="event.stopPropagation(); openAddToPlaylistDialog('${t.videoId}')" class="add-btn-${t.videoId} px-2.5 py-1 rounded-full text-[11px] font-semibold flex items-center space-x-1.5 transition-all ${isSongInAnyPlaylist(t.videoId) ? 'text-[#00e5ff] bg-[#00e5ff]/20 border border-[#00e5ff]/60 shadow-[0_0_10px_rgba(0,229,255,0.35)]' : 'text-neutral-400 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10'}">
                  <i class="fa-solid fa-plus text-[10px]"></i>
                  <span>Add</span>
                </button>
                <button onclick="event.stopPropagation(); toggleFavoriteTrack('${t.videoId}')" title="Like song" class="heart-btn-${t.videoId} p-1 text-sm transition hover:scale-110 ${isFav ? 'text-pink-500' : 'text-neutral-400 hover:text-pink-500'}">
                  <i class="${isFav ? 'fa-solid fa-heart' : 'fa-regular fa-heart'}"></i>
                </button>
                <button onclick="event.stopPropagation(); downloadTrack('${t.videoId}')" class="hover:text-white p-1"><i class="fa-solid fa-download"></i></button>
                <span class="text-neutral-500 ml-1">${t.duration}</span>
              </div>
            </div>
          `;
        }).join('')}
      `;
    }

    // Recommended & History
    async function loadHistory() {
      try {
        const res = await fetch('/api/history');
        const data = await res.json();
        const sec = document.getElementById('homeRecentSection');
        const row = document.getElementById('homeRecentRow');
        if (!data.history || data.history.length === 0) {
          sec.classList.add('hidden');
          return;
        }
        sec.classList.remove('hidden');
        data.history.forEach(item => { if (item && item.videoId) trackCache[item.videoId] = item; });
        row.innerHTML = data.history.map(item => {
          const isFav = isFavorite(item.videoId);
          return `
            <div class="flex-shrink-0 w-36 spotify-card p-2.5 cursor-pointer group" onclick="quickPlayCard('${item.videoId}')">
              <div class="relative aspect-square rounded-md overflow-hidden mb-2">
                <img src="${item.thumbnail}" class="w-full h-full object-cover">
                <div class="absolute bottom-1.5 right-1.5 w-8 h-8 rounded-full bg-white text-black flex items-center justify-center opacity-0 group-hover:opacity-100 transition"><i class="fa-solid fa-play text-xs ml-0.5"></i></div>
                <button onclick="event.stopPropagation(); toggleFavoriteTrack('${item.videoId}')" title="Like song" class="heart-btn-${item.videoId} recent-heart absolute top-1.5 right-1.5 p-1 rounded-full bg-black/60 text-xs transition ${isFav ? 'text-pink-500' : 'text-white/70 hover:text-pink-500'}">
                  <i class="${isFav ? 'fa-solid fa-heart' : 'fa-regular fa-heart'}"></i>
                </button>
              </div>
              <div class="text-xs font-bold text-white truncate hover:underline" onclick="event.stopPropagation(); openArtistPage('${item.videoId}')">${item.title}</div>
              <div class="text-[10px] text-neutral-400 truncate hover:underline" onclick="event.stopPropagation(); openArtistPage('${item.videoId}')">${item.channel}</div>
            </div>
          `;
        }).join('');
      } catch (e) {}
    }

    async function recordHistory(track) {
      try {
        await fetch('/api/history', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(track)
        });
        loadHistory();
      } catch (e) {}
    }

    async function clearHistory() {
      if (confirm('Clear history?')) {
        await fetch('/api/history', { method: 'DELETE' });
        loadHistory();
      }
    }

    async function renderRecommended() {
      try {
        const res = await fetch('/api/search?q=Top%20Trending%20Hits&limit=10');
        const data = await res.json();
        if (data.results) {
          document.getElementById('homeFeaturedGrid').innerHTML = data.results.map(item => createCardHTML(item)).join('');
        }
      } catch (e) {}
    }

    // Right panel toggle
    function toggleRightPanel() {
      const p = document.getElementById('rightPanel');
      p.classList.toggle('hidden');
    }

    function toggleLyricsModal() {
      const modal = document.getElementById('lyricsModal');
      modal.classList.toggle('hidden');
      if (!modal.classList.contains('hidden') && currentTrack) {
        document.getElementById('lyricsTrackInfo').innerText = `${currentTrack.title} — ${currentTrack.channel}`;
      }
    }

    function toggleImportModal() {
      document.getElementById('importModal').classList.toggle('hidden');
    }

    async function importPlaylistAction() {
      const input = document.getElementById('playlistUrlInput');
      const val = input.value.trim();
      if (!val) return;
      toggleImportModal();
      input.value = '';
      importPlaylistFromUrl(val);
    }

    async function downloadTrack(videoId) {
      try {
        alert('Starting MP3 download to your Downloads folder...');
        const res = await fetch(`/api/download/${videoId}`);
        const data = await res.json();
        alert(data.message || 'Saved to Downloads folder!');
      } catch (e) {
        alert('Download error: ' + e.message);
      }
    }

    function downloadCurrent() {
      if (currentTrack) downloadTrack(currentTrack.videoId);
    }

    function setupKeyboard() {
      document.addEventListener('keydown', (e) => {
        if (['input', 'textarea'].includes(document.activeElement.tagName.toLowerCase())) return;
        if (e.code === 'Space') { e.preventDefault(); togglePlayPause(); }
        else if (e.code === 'ArrowRight') { e.preventDefault(); playNextTrack(); }
        else if (e.code === 'ArrowLeft') { e.preventDefault(); playPrevTrack(); }
      });
    }
  </script>
</body>
</html>
"""

if __name__ == "__main__":
    print("Starting AudioTube server on http://localhost:8000 ...")
    uvicorn.run(app, host="127.0.0.1", port=8000)
