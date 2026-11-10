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
from fastapi.responses import HTMLResponse, FileResponse, RedirectResponse
from pydantic import BaseModel
import uvicorn
from ytmusicapi import YTMusic
import yt_dlp

app = FastAPI(title="SKULLBEAT")
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


class CsvImportRequest(BaseModel):
    filename: Optional[str] = "Imported Playlist"
    content: str
    playlist_name: Optional[str] = None
    emoji: Optional[str] = "🎵"
    color: Optional[str] = "#3D8BFF"


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


def extract_spotify_metadata(url_or_uri: str) -> Dict[str, Any]:
    """Extracts Spotify playlist, album, or track metadata and resolves tracks to pure audio streams."""
    import re, urllib.request, urllib.parse, json, concurrent.futures

    raw = url_or_uri.strip()
    if "collection/tracks" in raw.lower():
        raise HTTPException(
            status_code=400,
            detail="Spotify Liked Songs (collection/tracks) is private to your Spotify account. To import: in Spotify desktop, press Ctrl+A on Liked Songs -> Right Click -> Add to Playlist -> New Playlist -> Share public link here!"
        )
    m = re.search(r"(playlist|album|track)[/:]([a-zA-Z0-9]+)", raw)
    if not m:
        raise HTTPException(status_code=400, detail="Invalid Spotify link format. Provide open.spotify.com/playlist/... or album/...")

    entity_type = m.group(1)
    entity_id = m.group(2)
    embed_target = f"https://open.spotify.com/embed/{entity_type}/{entity_id}"

    html = ""
    # 1. Try direct with short timeout
    try:
        req = urllib.request.Request(embed_target, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            html = resp.read().decode("utf-8", errors="ignore")
    except Exception:
        html = ""

    # 2. Proxy fallback via Google Translate proxy
    if not html or ("trackList" not in html and "__NEXT_DATA__" not in html):
        try:
            proxy_url = f"https://translate.google.com/translate?sl=auto&tl=en&u={urllib.parse.quote(embed_target)}"
            req2 = urllib.request.Request(proxy_url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
            with urllib.request.urlopen(req2, timeout=10) as resp2:
                html = resp2.read().decode("utf-8", errors="ignore")
        except Exception as pe:
            raise HTTPException(status_code=502, detail=f"Failed to fetch Spotify embed data: {pe}")

    title = "Spotify Playlist" if entity_type == "playlist" else ("Spotify Album" if entity_type == "album" else "Spotify Track")
    items = []

    m_data = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', html)
    if m_data:
        try:
            data = json.loads(m_data.group(1))
            entity = data.get("props", {}).get("pageProps", {}).get("state", {}).get("data", {}).get("entity", {})
            title = entity.get("name") or entity.get("title") or title
            track_list = entity.get("trackList", [])
            for t in track_list:
                t_name = t.get("title") or ""
                t_sub = t.get("subtitle") or ""
                if t_name:
                    items.append({"title": t_name, "artist": t_sub})
            if not items and entity_type == "track":
                t_name = entity.get("name") or entity.get("title")
                artists_list = entity.get("artists") or []
                if isinstance(artists_list, list) and artists_list:
                    a_name = artists_list[0].get("name", "")
                else:
                    a_name = str(entity.get("subtitle", ""))
                if t_name:
                    items.append({"title": t_name, "artist": a_name})
        except Exception:
            pass

    if not items:
        m_regex = re.search(r'"trackList":\s*(\[.*?\])\s*,\s*"', html)
        if m_regex:
            try:
                for t in json.loads(m_regex.group(1)):
                    items.append({"title": t.get("title", ""), "artist": t.get("subtitle", "")})
            except Exception:
                pass

    if not items:
        raise HTTPException(status_code=404, detail="No tracks found in Spotify link. Ensure the playlist is public.")

    def match_song(item):
        q = f"{item['title']} {item['artist']}".strip()
        found = search_music(q, limit=1)
        if found:
            return found[0]
        return None

    target_items = items[:80]
    resolved_tracks = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        results = list(executor.map(match_song, target_items))
        for r in results:
            if r:
                resolved_tracks.append(r)

    if not resolved_tracks:
        raise HTTPException(status_code=404, detail="Could not match Spotify tracks to audio streams.")

    return {
        "title": title,
        "count": len(resolved_tracks),
        "tracks": resolved_tracks,
        "source": "spotify"
    }


def extract_youtube_playlist(url_or_id: str) -> Dict[str, Any]:
    """Fetches track metadata for YouTube or YouTube Music playlist."""
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

    ydl_opts = {"quiet": True, "extract_flat": True, "skip_download": True, "js_runtimes": {"node": {}}}
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
                "title": data.get("title") or "Imported YouTube Playlist",
                "count": len(tracks),
                "tracks": tracks,
                "source": "youtube"
            }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Playlist import failed: {e}")


@app.get("/api/playlist")
def api_get_playlist(url_or_id: str = Query(..., description="Spotify or YouTube playlist link")):
    """Fetches track metadata for any Spotify or YouTube playlist without downloading audio."""
    clean = url_or_id.strip()
    if "spotify.com" in clean or clean.startswith("spotify:"):
        return extract_spotify_metadata(clean)
    else:
        return extract_youtube_playlist(clean)


STREAM_URL_CACHE: Dict[str, str] = {}


@app.get("/api/stream/{video_id}")
def api_stream_url(video_id: str, q: Optional[str] = None, redirect: bool = False):
    """Extracts raw, ad-free audio stream URL without downloading video."""
    target_vid = (video_id or "").strip()
    
    # Auto-resolve if pending or placeholder
    if not target_vid or target_vid.startswith("pending") or target_vid in ("undefined", "null", "none"):
        if q and q.strip():
            try:
                results = search_music(q.strip(), limit=1)
                if results and results[0].get("videoId"):
                    target_vid = results[0]["videoId"]
                else:
                    raise HTTPException(status_code=404, detail="Track audio not found")
            except HTTPException:
                raise
            except Exception as e:
                raise HTTPException(status_code=500, detail=f"Resolve failed: {e}")
        else:
            raise HTTPException(status_code=400, detail="Missing video ID or search query")

    # Fast in-memory cache
    stream_url = STREAM_URL_CACHE.get(target_vid)
    title = target_vid
    channel = ""
    abr = None
    fmt_note = "webm"

    if not stream_url:
        ydl_opts = {
            "format": "bestaudio/best",
            "quiet": True,
            "skip_download": True,
            "js_runtimes": {"node": {}},
        }
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(target_vid, download=False)
                stream_url = info.get("url")
                if not stream_url:
                    raise HTTPException(status_code=404, detail="Audio stream not found")
                title = info.get("title") or target_vid
                channel = info.get("uploader") or info.get("channel") or ""
                fmt_note = info.get("format_note") or info.get("ext") or "webm"
                abr = info.get("abr")
                STREAM_URL_CACHE[target_vid] = stream_url
        except HTTPException:
            raise
        except Exception as e:
            raise HTTPException(status_code=500, detail=str(e))

    if redirect:
        return RedirectResponse(url=stream_url, status_code=307)

    return {
        "videoId": target_vid,
        "title": title,
        "channel": channel,
        "stream_url": stream_url,
        "format": fmt_note,
        "bitrate": abr
    }


@app.get("/api/download/{video_id}")
def api_download(video_id: str, q: Optional[str] = None):
    """Downloads audio-only track as MP3 and streams to browser as attachment."""
    target_vid = (video_id or "").strip()
    if not target_vid or target_vid.startswith("pending") or target_vid in ("undefined", "null"):
        if q and q.strip():
            try:
                results = search_music(q.strip(), limit=1)
                if results and results[0].get("videoId"):
                    target_vid = results[0]["videoId"]
                else:
                    raise HTTPException(status_code=404, detail="Track audio not found")
            except HTTPException:
                raise
            except Exception as e:
                raise HTTPException(status_code=500, detail=f"Resolve failed: {e}")
        else:
            raise HTTPException(status_code=400, detail="Missing video ID or search query")

    DOWNLOADS_DIR.mkdir(parents=True, exist_ok=True)

    # Check if file is already downloaded
    existing = list(DOWNLOADS_DIR.glob(f"*{target_vid}*.mp3"))
    if existing and existing[0].is_file():
        mp3_file = existing[0]
        safe_name = mp3_file.name
        return FileResponse(
            str(mp3_file),
            media_type="audio/mpeg",
            filename=safe_name,
            headers={"Content-Disposition": f'attachment; filename="{safe_name}"'}
        )

    out_template = str(DOWNLOADS_DIR / f"%(title)s [{target_vid}].%(ext)s")
    ydl_opts = {
        "format": "bestaudio/best",
        "outtmpl": out_template,
        "postprocessors": [{
            "key": "FFmpegExtractAudio",
            "preferredcodec": "mp3",
            "preferredquality": "192",
        }],
        "quiet": True,
        "js_runtimes": {"node": {}},
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(target_vid, download=True)
            raw_title = info.get("title") or "SkullBeat Track"
            safe_title = re.sub(r'[\\/*?:"<>|]', "", raw_title)
            candidates = list(DOWNLOADS_DIR.glob(f"*{target_vid}*.mp3"))
            if candidates and candidates[0].is_file():
                mp3_file = candidates[0]
            else:
                mp3_file = DOWNLOADS_DIR / f"{safe_title}.mp3"

            if mp3_file.is_file():
                return FileResponse(
                    str(mp3_file),
                    media_type="audio/mpeg",
                    filename=f"{safe_title}.mp3",
                    headers={"Content-Disposition": f'attachment; filename="{safe_title}.mp3"'}
                )

            return {
                "status": "success",
                "message": f"Saved to {DOWNLOADS_DIR}",
                "title": raw_title
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


LIBRARY_FILE = HISTORY_DIR / "user_library.json"


@app.get("/api/user_library")
def api_get_user_library():
    """Returns persistent user playlists and favorites (strictly stored locally, gitignored)."""
    if not LIBRARY_FILE.is_file():
        return {"playlists": {}, "favorites": []}
    try:
        with open(LIBRARY_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        return {"playlists": {}, "favorites": [], "error": str(e)}


@app.post("/api/user_library")
def api_save_user_library(data: Dict[str, Any]):
    """Saves user playlists and favorites locally to history/user_library.json."""
    HISTORY_DIR.mkdir(parents=True, exist_ok=True)
    try:
        with open(LIBRARY_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/resolve_track")
def api_resolve_track(q: str = Query(..., description="Song title and artist query")):
    """Resolves a pending track to a real YouTube video audio stream."""
    try:
        results = search_music(q.strip(), limit=1)
        if results:
            return results[0]
        raise HTTPException(status_code=404, detail="Track audio not found")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/import_csv")
def api_import_csv(req: CsvImportRequest):
    """Imports songs from a Spotify Exportify CSV into user_library.json."""
    import csv, io, hashlib, concurrent.futures
    try:
        reader = csv.DictReader(io.StringIO(req.content))
        tracks = []
        for idx, row in enumerate(reader):
            title = ""
            artist = ""
            album = ""
            duration_ms = 0
            for k, v in row.items():
                if not k:
                    continue
                k_clean = k.strip().lower()
                if "track name" in k_clean:
                    title = v.strip()
                elif "artist name" in k_clean:
                    artist = v.strip().replace(";", ", ")
                elif "album name" in k_clean:
                    album = v.strip()
                elif "duration (ms)" in k_clean:
                    try:
                        duration_ms = int(v.strip())
                    except Exception:
                        duration_ms = 0

            if not title:
                continue

            mins = duration_ms // 60000
            secs = (duration_ms % 60000) // 1000
            duration_str = f"{mins}:{secs:02d}" if duration_ms > 0 else "3:30"
            fake_hash = hashlib.md5(f"{title}_{artist}_{idx}".encode()).hexdigest()[:11]

            tracks.append({
                "videoId": f"pending_{fake_hash}",
                "title": title,
                "channel": artist or "Unknown Artist",
                "artist": artist or "Unknown Artist",
                "album": album or "Imported",
                "duration": duration_str,
                "thumbnail": "/logo.png"
            })

        if not tracks:
            raise HTTPException(status_code=400, detail="No valid tracks found in CSV.")

        name = req.playlist_name
        if not name and req.filename:
            name = req.filename.replace(".csv", "").replace("_", " ").title()
        if not name:
            name = "Imported Playlist"

        # Pre-resolve top 5 tracks concurrently for immediate playback
        def _res(t):
            try:
                r = search_music(f"{t['title']} {t['artist']}".strip(), limit=1)
                if r:
                    t["videoId"] = r[0]["videoId"]
                    t["thumbnail"] = r[0].get("thumbnail") or t["thumbnail"]
                    t["channel"] = r[0].get("channel") or t["channel"]
            except Exception:
                pass

        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as ex:
            list(ex.map(_res, tracks[:5]))

        # Read existing user library
        lib = {"playlists": {}, "favorites": []}
        if LIBRARY_FILE.is_file():
            try:
                with open(LIBRARY_FILE, "r", encoding="utf-8") as f:
                    lib = json.load(f)
            except Exception:
                pass

        lib.setdefault("playlists", {})
        lib["playlists"][name] = {
            "songs": tracks,
            "emoji": req.emoji or "🎵",
            "color": req.color or "#3D8BFF"
        }
        if "liked" in name.lower() or "favorite" in name.lower():
            lib["favorites"] = tracks

        with open(LIBRARY_FILE, "w", encoding="utf-8") as f:
            json.dump(lib, f, ensure_ascii=False, indent=2)

        return {
            "status": "success",
            "playlist_name": name,
            "count": len(tracks),
            "playlist": lib["playlists"][name]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))



@app.get("/api/lyrics")
def api_get_lyrics(title: str = Query(...), artist: str = Query("")):
    """Fetches synced or plain lyrics from lrclib."""
    import urllib.request, urllib.parse
    try:
        q = urllib.parse.urlencode({"track_name": title, "artist_name": artist})
        url = f"https://lrclib.net/api/get?{q}"
        req = urllib.request.Request(url, headers={"User-Agent": "SkullBeat/2.0"})
        with urllib.request.urlopen(req, timeout=3.5) as resp:
            if resp.status == 200:
                data = json.loads(resp.read().decode("utf-8"))
                return {"synced": data.get("syncedLyrics"), "plain": data.get("plainLyrics")}
    except Exception:
        pass
    return {"synced": None, "plain": None}


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


HTML_CONTENT = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>SkullBeat Desktop</title>
<meta name="color-scheme" content="dark">
<link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@800&display=swap" rel="stylesheet">
<style>
:root{color-scheme:dark;--bg:#05070F;--s1:#0E1426;--s2:#172044;--tx:#F2F5FF;--mu:#8E99BC;--blue:#3D8BFF;--cyan:#00D4FF;--vio:#8B5CFF;--red:#FF3B5C;--grad:linear-gradient(135deg,#3D8BFF,#8B5CFF);--grad2:linear-gradient(135deg,#3D8BFF,#00D4FF)}
*{box-sizing:border-box;margin:0;padding:0}
html,body{height:100%}
body{background:var(--bg);color:var(--tx);font:14px/1.4 system-ui,-apple-system,"Segoe UI",sans-serif;overflow:hidden}
button{font:inherit;color:inherit;background:none;border:0;cursor:pointer}
button:focus-visible,input:focus-visible{outline:2px solid var(--cyan);outline-offset:2px}
.ic{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;flex:none}
.app{height:100%;display:grid;grid-template-columns:232px 1fr 320px;grid-template-rows:1fr 88px;gap:8px;padding:8px;padding-top:calc(8px + env(safe-area-inset-top,0px));padding-bottom:calc(8px + env(safe-area-inset-bottom,0px))}
.card{background:var(--s1);border-radius:16px;overflow:hidden}
/* sidebar */
.side{padding:20px 12px;display:flex;flex-direction:column;gap:4px;overflow-y:auto}
.logo{position:relative;display:flex;align-items:center;height:44px;margin:0 0 20px 6px;flex:none;cursor:pointer}
.logo img{width:38px;height:38px;border-radius:10px;object-fit:cover;box-shadow:0 0 16px rgba(0,212,255,.5);margin-right:10px;border:1px solid rgba(0,212,255,.3)}
.logo span{font:800 18px Orbitron,system-ui,sans-serif;letter-spacing:2px;color:#fff}
.nb{display:flex;align-items:center;gap:14px;padding:12px 14px;border-radius:14px;color:var(--mu);font-weight:600;min-height:44px;text-align:left;transition:color .15s,background .15s}
.nb:hover{color:var(--tx);background:var(--s2)}
.nb.on{color:var(--tx);background:var(--grad)}
.side h3{font-size:12px;color:var(--mu);font-weight:600;letter-spacing:.08em;margin:20px 14px 8px;display:flex;justify-content:space-between;align-items:center}
.side h3 button{color:var(--cyan);font-size:11px}
.pl-list{display:flex;flex-direction:column;gap:2px;overflow-y:auto}
.pl{display:flex;align-items:center;gap:10px;padding:8px 10px;border-radius:10px;width:100%;text-align:left;color:var(--mu);transition:background .15s,color .15s}
.pl:hover{background:var(--s2);color:var(--tx)}
.pl.on{background:rgba(61,139,255,.18);color:var(--tx)}
.pl i{width:36px;height:36px;border-radius:8px;flex:none;background-size:cover;background-position:center}
.pl span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
/* main */
.main{overflow-y:auto;padding:28px 32px 32px;background:linear-gradient(180deg,#121b3a 0,var(--s1) 260px)}
.main::-webkit-scrollbar,.right::-webkit-scrollbar,.side::-webkit-scrollbar,.pl-list::-webkit-scrollbar{width:8px}
.main::-webkit-scrollbar-thumb,.right::-webkit-scrollbar-thumb,.side::-webkit-scrollbar-thumb,.pl-list::-webkit-scrollbar-thumb{background:var(--s2);border-radius:4px}
.view{display:none}.view.on{display:block}
h1{font-size:32px;margin-bottom:20px;font-weight:700}
h2{font-size:20px;margin:32px 0 14px;font-weight:600}
.t{font-size:14px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.a{font-size:12px;color:var(--mu);margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.quick{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:12px}
.q{display:flex;align-items:center;gap:14px;background:rgba(23,32,68,.6);border-radius:10px;overflow:hidden;text-align:left;transition:background .15s;padding-right:12px}
.q:hover{background:var(--s2)}
.q i{width:64px;height:64px;flex:none;background-size:cover;background-position:center}
.row{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:20px}
.al{text-align:left;padding:12px;border-radius:14px;transition:background .15s}
.al:hover{background:var(--s2)}
.al i{display:block;aspect-ratio:1;border-radius:12px;margin-bottom:10px;box-shadow:0 8px 24px rgba(0,0,0,.4);background-size:cover;background-position:center}
/* chips + search */
.chips{display:flex;gap:8px;flex-wrap:wrap}
.chip{padding:8px 16px;border-radius:999px;background:var(--s2);font-size:13px;min-height:36px;transition:background .15s;color:var(--mu)}
.chip:hover{filter:brightness(1.2);color:var(--tx)}
.chip.on{background:var(--grad);color:var(--tx);font-weight:600}
.find{display:flex;align-items:center;gap:10px;background:var(--bg);border-radius:12px;padding:0 14px;color:var(--mu);max-width:540px;margin:16px 0 12px;border:1px solid rgba(255,255,255,.05)}
.find:focus-within{border-color:var(--blue)}
.find input{flex:1;height:46px;background:none;border:0;outline:0;color:var(--tx);font-size:14px}
.range{display:flex;align-items:center;gap:12px;max-width:540px;color:var(--mu);font-size:12px;margin:16px 0}
.range input{flex:1;accent-color:var(--blue)}
.bar{display:flex;justify-content:space-between;align-items:center;margin:16px 0 8px}
.bar-actions{display:flex;gap:12px;align-items:center}
.bar button{color:var(--cyan);font-size:13px;min-height:36px}
/* track table */

/* Queue & Repeat Badges, Context Menu, Mobile Swipe */
.rp{position:relative}
.rep-badge{position:absolute;top:2px;right:2px;font-size:10px;font-weight:900;background:var(--cyan);color:#05070F;width:15px;height:15px;border-radius:50%;display:none;align-items:center;justify-content:center;line-height:1;box-shadow:0 0 6px rgba(0,212,255,.8)}
.rep-badge.on{display:flex}
.badge{display:inline-flex;padding:1px 6px;border-radius:10px;font-size:11px;background:var(--cyan);color:#05070F;font-weight:700;margin-left:5px}

.th,.tr{display:grid;grid-template-columns:36px minmax(0,3fr) minmax(0,2fr) 64px 74px;gap:12px;align-items:center;padding:8px 14px;border-radius:10px;position:relative}
.tr{touch-action:pan-y}
.tr-acts{display:flex;align-items:center;justify-content:flex-end;gap:4px}
.q-add-btn{color:var(--mu);display:flex;align-items:center;justify-content:center;padding:4px;border-radius:6px;transition:color .15s,opacity .15s;opacity:.5}
.tr:hover .q-add-btn,.tr:focus-within .q-add-btn{opacity:1;color:var(--cyan)}
.q-add-btn:hover{color:#fff;background:rgba(0,212,255,.2)}

.tr.swiping{transition:none;background:rgba(0,212,255,.1)}
.tr.swipe-ready{background:rgba(0,212,255,.22);border-left:3px solid var(--cyan)}

.ctx-menu{position:fixed;background:#0E1426;border:1px solid rgba(61,139,255,.3);border-radius:10px;padding:6px;min-width:170px;z-index:9999;box-shadow:0 10px 30px rgba(0,0,0,.8);display:none;flex-direction:column;gap:2px}
.ctx-menu.on{display:flex}
.ctx-item{display:flex;align-items:center;gap:10px;padding:8px 12px;font-size:13px;color:var(--tx);border-radius:6px;cursor:pointer;background:transparent;border:0;width:100%;text-align:left;transition:background .15s}
.ctx-item:hover{background:rgba(61,139,255,.25);color:#fff}

.popover{position:fixed;bottom:80px;left:50%;transform:translateX(50px);background:#0E1426;border:1px solid rgba(61,139,255,.3);border-radius:10px;padding:10px;z-index:9999;display:none;flex-direction:column;gap:6px;box-shadow:0 10px 30px rgba(0,0,0,.8)}
.popover.on{display:flex}

.toast{position:fixed;bottom:100px;left:50%;transform:translateX(-50%);background:#172044;border:1px solid var(--cyan);color:#fff;padding:8px 20px;border-radius:20px;font-size:13px;font-weight:600;z-index:99999;box-shadow:0 8px 25px rgba(0,0,0,.7);opacity:0;pointer-events:none;transition:opacity .2s,transform .2s}
.toast.on{opacity:1;transform:translateX(-50%) translateY(-6px)}

.th{font-size:12px;color:var(--mu);border-bottom:1px solid var(--s2);border-radius:0;padding-bottom:10px}
.tr{width:100%;text-align:left;transition:background .15s;cursor:pointer}
.tr:hover{background:var(--s2)}
.tr.cur .t{color:var(--cyan)}
.tr .n{color:var(--mu);text-align:center}
.tr .nm{display:flex;align-items:center;gap:12px;min-width:0}
.tr .nm i{width:44px;height:44px;border-radius:8px;flex:none;background-size:cover;background-position:center}
.tr .nm div,.tr .al2{min-width:0}
.tr p{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.al2,.d{color:var(--mu);font-size:13px}
.heart{color:var(--mu);display:flex;justify-content:center}
.heart.on{color:var(--red)}.heart.on svg{fill:var(--red)}
.empty{color:var(--mu);padding:40px 16px;text-align:center}
/* right panel */
.right{padding:20px;overflow-y:auto;display:flex;flex-direction:column;gap:16px}
.right .lab{font-size:11px;letter-spacing:.1em;color:var(--mu);text-align:center;text-transform:uppercase}
.art{width:100%;aspect-ratio:1;border-radius:20px;background:var(--grad);box-shadow:0 0 40px rgba(61,139,255,.4);background-size:cover;background-position:center;transition:background .3s}
.meta{display:flex;align-items:center;gap:10px}.meta>div{flex:1;min-width:0}.meta h3{font-size:20px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tabs{display:flex;gap:8px}
.q2{display:flex;align-items:center;gap:10px;padding:6px 8px;border-radius:10px;width:100%;text-align:left;transition:background .15s}
.q2:hover{background:var(--s2)}
.q2 i{width:40px;height:40px;border-radius:8px;flex:none;background-size:cover;background-position:center}
.q2 div{min-width:0}
.ly{overflow-y:auto;max-height:360px;padding:4px 0}
.ly p{color:var(--mu);font-size:15px;font-weight:600;margin:10px 0;transition:color .2s}
.ly p.hi{color:var(--cyan);transform:scale(1.02)}
/* player */
.player{grid-column:1/-1;display:grid;grid-template-columns:1fr minmax(320px,560px) 1fr;align-items:center;gap:16px;padding:0 20px;background:var(--s2)}
.np{display:flex;align-items:center;gap:12px;min-width:0}.np i{width:56px;height:56px;border-radius:10px;flex:none;background-size:cover;background-position:center}
.np div{min-width:0}.np p{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ctr{display:flex;flex-direction:column;gap:6px}
.btns{display:flex;justify-content:center;align-items:center;gap:10px}
.ib{width:44px;height:44px;display:flex;align-items:center;justify-content:center;border-radius:50%;color:var(--mu);transition:color .15s}
.ib:hover{color:var(--tx)}.ib.act{color:var(--cyan)}
.play{width:52px;height:52px;border-radius:50%;background:var(--grad2);color:var(--tx);display:flex;align-items:center;justify-content:center;box-shadow:0 0 24px rgba(61,139,255,.5);transition:transform .15s}
.play:hover{transform:scale(1.06)}
.prog{display:flex;align-items:center;gap:10px;font-size:12px;color:var(--mu)}
input[type=range]{-webkit-appearance:none;appearance:none;height:4px;border-radius:2px;background:linear-gradient(to right,var(--cyan) var(--p,0%),var(--bg) var(--p,0%));cursor:pointer;flex:1}
input[type=range]::-webkit-slider-thumb{-webkit-appearance:none;width:14px;height:14px;border-radius:50%;background:var(--tx);opacity:0;transition:opacity .15s}
input[type=range]:hover::-webkit-slider-thumb,input[type=range]:focus-visible::-webkit-slider-thumb{opacity:1}
input[type=range]::-moz-range-thumb{width:14px;height:14px;border:0;border-radius:50%;background:var(--tx)}
.vol{display:flex;align-items:center;justify-content:flex-end;gap:8px;color:var(--mu)}.vol input{max-width:110px}

/* modal */
.modal-overlay{position:fixed;inset:0;background:rgba(5,7,15,.75);backdrop-filter:blur(6px);display:flex;align-items:center;justify-content:center;z-index:99;opacity:0;pointer-events:none;transition:opacity .2s}
.modal-overlay.on{opacity:1;pointer-events:auto}
.modal{background:var(--s1);border:1px solid var(--s2);border-radius:18px;padding:24px;width:90%;max-width:440px;display:flex;flex-direction:column;gap:14px;box-shadow:0 12px 40px rgba(0,0,0,.6)}
.modal h3{font-size:18px}
.modal input{width:100%;height:44px;background:var(--bg);border:1px solid var(--s2);border-radius:10px;padding:0 14px;color:var(--tx);font-size:14px}
.modal-btns{display:flex;gap:10px;justify-content:flex-end}
.btn-p{background:var(--grad);padding:10px 18px;border-radius:10px;font-weight:600}
.btn-s{padding:10px 16px;border-radius:10px;color:var(--mu)}

/* spin */
@keyframes spin{to{transform:rotate(360deg)}}
.spin{animation:spin 1s linear infinite}

/* responsive */
@media (max-width:1180px){.app{grid-template-columns:232px 1fr}.right{display:none}}
@media (max-width:820px){
 .app{grid-template-columns:1fr;grid-template-rows:1fr 76px 64px}
 .side{order:3;flex-direction:row;padding:6px;gap:6px}.side .logo,.side h3,.side .pl-list{display:none}.nb{flex:1;flex-direction:column;gap:2px;font-size:11px;padding:6px 0;justify-content:center}
 .main{padding:20px 16px}.player{order:2;grid-template-columns:1fr auto;padding:0 12px}.ctr .prog,.vol,.ctr .sh,.ctr .rp{display:none}
 .th,.tr{grid-template-columns:minmax(0,1fr) 48px}.th span:nth-child(1),.th span:nth-child(3),.th span:nth-child(4),.tr .n,.tr .al2,.tr .d{display:none}
}
</style>
</head>
<body>
<div class="app">

<aside class="card side">
  <div class="logo" aria-label="SkullBeat" onclick="show('home')"><img src="/logo.png" alt="SkullBeat"><span>SKULLBEAT</span></div>
  <button class="nb on" data-v="home"><svg class="ic" viewBox="0 0 24 24"><path d="M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/></svg>Home</button>
  <button class="nb" data-v="search"><svg class="ic" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.5-4.5"/></svg>Search</button>
  <button class="nb" data-v="library"><svg class="ic" viewBox="0 0 24 24"><path d="M5 4v16M10 4v16M15 5l4 15"/></svg>Library</button>
  <h3>PLAYLISTS <button id="add-pl" title="Import / Add Playlist">+ Import</button></h3>
  <div class="pl-list" id="side-pl"></div>
</aside>

<main class="card main" id="main">
  <section class="view on" id="v-home">
    <h1 id="greet">Good evening</h1>
    <div class="quick" id="quick"></div>
    <h2>Recently played</h2><div class="row" id="recent"></div>
    <h2>Made for you</h2><div class="row" id="made"></div>
  </section>

  <section class="view" id="v-search">
    <h1>Search</h1>
    <label class="find"><svg class="ic" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.5-4.5"/></svg><input id="sq" placeholder="What do you want to play?" autocomplete="off"></label>
    <div id="sres"></div>
  </section>

  <section class="view" id="v-library">
    <h1>Library</h1>
    <div class="chips" id="tabs"><button class="chip on" data-t="all">All</button><button class="chip" data-t="liked">Liked</button><button class="chip" data-t="playlists">Playlists</button><button class="chip" data-t="history">History</button></div>
    <label class="find"><svg class="ic" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.5-4.5"/></svg><input id="lq" placeholder="Filter by title, artist or album" autocomplete="off"></label>
    <div class="chips" id="by"><button class="chip on" data-k="all">Any</button><button class="chip" data-k="artist">Artist</button><button class="chip" data-k="title">Title</button><button class="chip" data-k="album">Album</button></div>
    <div class="range"><span>Duration</span><input type="range" id="dmin" min="0" max="600" value="0" aria-label="Min duration"><input type="range" id="dmax" min="0" max="600" value="600" aria-label="Max duration"><span id="dlab">0:00 – 10:00</span></div>
    <div class="bar"><span class="a" id="cnt">0 tracks</span><div class="bar-actions"><button id="exp">Export M3U · CSV · JSON</button></div></div>
    <div id="lib"></div>
  </section>
</main>

<aside class="card right" id="right">
  <p class="lab" id="from">PLAYING FROM PLAYLIST</p>
  <div class="art" id="rart"></div>
  <div class="meta"><div><h3 id="rt">Song title</h3><p class="a" id="ra" style="font-size:15px">Artist</p></div><button class="ib heart" id="rlike" aria-label="Like"><svg class="ic" viewBox="0 0 24 24"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/></svg></button></div>
  <div class="tabs"><button class="chip on" data-p="queue">Queue <span id="q-cnt" class="badge"></span></button><button class="chip" data-p="lyrics">Lyrics</button></div>
  <div id="pq"></div>
</aside>

<footer class="card player">
  <div class="np"><i id="pa"></i><div><p class="t" id="pt"></p><p class="a" id="pr"></p></div></div>
  <div class="ctr">
    <div class="btns">
      <button class="ib sh" id="shuf" aria-label="Shuffle"><svg class="ic" viewBox="0 0 24 24"><path d="M3 6h4l10 12h4M18 3l3 3-3 3M3 18h4l3-3.5M14 9.5L17 6h4M18 15l3 3-3 3"/></svg></button>
      <button class="ib" id="prev" aria-label="Previous"><svg class="ic" viewBox="0 0 24 24"><path d="M19 5v14L8 12zM5 5v14"/></svg></button>
      <button class="play" id="pp" aria-label="Play"></button>
      <button class="ib" id="next" aria-label="Next"><svg class="ic" viewBox="0 0 24 24"><path d="M5 5v14l11-7zM19 5v14"/></svg></button>
      <button class="ib rp" id="rep" aria-label="Repeat" title="Repeat (1-tap: OFF, 2-tap: 1x, 3-tap: 2x)"><svg class="ic" viewBox="0 0 24 24"><path d="M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3"/></svg><span class="rep-badge" id="rep-badge"></span></button>
    </div>
    <div class="prog"><span id="tc">0:00</span><input type="range" id="seek" min="0" max="100" value="0" aria-label="Seek"><span id="td">0:00</span></div>
  </div>
  <div class="vol"><button class="ib" id="dl" aria-label="Download"><svg class="ic" viewBox="0 0 24 24"><path d="M12 3v12M7 11l5 5 5-5M4 20h16"/></svg></button><button class="ib" id="mute-btn"><svg class="ic" id="vol-ic" viewBox="0 0 24 24"><path d="M11 5L6 9H2v6h4l5 4zM15.5 8.5a5 5 0 0 1 0 7"/></svg></button><input type="range" id="vol" min="0" max="100" value="80" aria-label="Volume"></div>
</footer>
</div>

<!-- Import Modal -->
<div class="modal-overlay" id="import-modal">
  <div class="modal">
    <h3>Import Playlist / CSV</h3>
    <input id="import-url" placeholder="Paste Spotify or YouTube playlist URL..." autocomplete="off">
    <div style="font-size:12px;color:var(--mu);text-align:center">OR</div>
    <button class="chip" onclick="document.getElementById('csv-in').click()" style="text-align:center">Select Spotify Export CSV file...</button>
    <input type="file" id="csv-in" accept=".csv" style="display:none">
    <div class="modal-btns">
      <button class="btn-s" onclick="closeImport()">Cancel</button>
      <button class="btn-p" onclick="doImportUrl()">Import URL</button>
    </div>
  </div>
</div>


<!-- Context Menu -->
<div class="ctx-menu" id="ctx-menu">
  <button class="ctx-item" id="ctx-play"><svg class="ic" style="width:16px;height:16px" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>Play Now</button>
  <button class="ctx-item" id="ctx-queue"><svg class="ic" style="width:16px;height:16px" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>Add to Queue (FIFO)</button>
  <button class="ctx-item" id="ctx-like"><svg class="ic" style="width:16px;height:16px" viewBox="0 0 24 24"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/></svg>Like / Favorite</button>
  <button class="ctx-item" id="ctx-dl"><svg class="ic" style="width:16px;height:16px" viewBox="0 0 24 24"><path d="M12 3v13m0 0l4-4m-4 4l-4-4M4 17v4h16v-4"/></svg>Download MP3</button>
</div>

<!-- Repeat Popover -->
<div class="popover" id="rep-pop">
  <p style="font-size:11px;font-weight:700;color:var(--mu);margin:0 0 4px">REPEAT MODE</p>
  <button class="chip" data-set-rep="0">OFF</button>
  <button class="chip" data-set-rep="1">REPEAT ONE (1 additional play)</button>
  <button class="chip" data-set-rep="2">REPEAT TWO (2 additional plays)</button>
</div>

<!-- Toast -->
<div class="toast" id="toast"></div>

<audio id="audio" preload="auto"></audio>


<script>
var G=['#3D8BFF,#8B5CFF','#8B5CFF,#FF3B5C','#3D8BFF,#00D4FF','#172044,#3D8BFF'].map(function(g){return 'linear-gradient(135deg,'+g+')'});
var $=function(s){return document.querySelector(s)};
var fmt=function(s){s=Math.round(s||0);return Math.floor(s/60)+':'+('0'+(s%60)).slice(-2)};
var esc=function(s){return String(s||'').replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})};
var parseSec=function(d){if(typeof d==='number')return Math.round(d);if(!d)return 180;var s=String(d).trim();if(s.indexOf(':')>-1){var p=s.split(':').map(Number);if(p.length===2)return p[0]*60+p[1];if(p.length===3)return p[0]*3600+p[1]*60+p[2]}var n=Number(s);return isNaN(n)?180:Math.round(n)};

var PLAY='<svg viewBox="0 0 24 24" width="30" height="30"><path d="M12 3a8 8 0 0 0-8 8c0 2.6 1.2 4.3 3 5.4V20h2v-2h2v2h2v-2h2v2h2v-3.6c1.8-1.1 3-2.8 3-5.4a8 8 0 0 0-8-8z" fill="#F2F5FF"/><circle cx="9" cy="11" r="2" fill="#05070F"/><circle cx="15" cy="11" r="2" fill="#05070F"/></svg>';
var PAUSE='<svg class="ic" viewBox="0 0 24 24" style="width:26px;height:26px"><path d="M7 5v14M17 5v14" stroke-width="3"/></svg>';
var SPIN='<svg class="ic spin" viewBox="0 0 24 24" style="width:26px;height:26px"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-dasharray="28" stroke-dashoffset="10" stroke-width="2.5" fill="none"/></svg>';
var HEART='<svg class="ic" viewBox="0 0 24 24"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/></svg>';

var audio=document.getElementById('audio');
document.addEventListener('pointerdown',function unlock(){
  if(audio&&!audio.unlocked){
    audio.play().then(function(){audio.pause();audio.currentTime=0;audio.unlocked=true}).catch(function(){});
    document.removeEventListener('pointerdown',unlock);
  }
},{once:true});

/* Playback State */
var S={
  cur:null,
  playing:false,
  loading:false,
  pos:0,
  shuf:false,
  repeatMode:0,       // 0: OFF, 1: REPEAT ONE, 2: REPEAT TWO
  repeatRemaining:0,  // additional plays remaining for active track
  repeatTotalPlayed:0,
  panel:'queue',
  tab:'all',
  by:'all',
  selectedPlaylist:null,
  queue:[],           // FIFO user queue
  context:[],         // Active backing track list (playlist or library)
  contextIdx:-1,      // Current index in active context
  contextName:'Library',
  isFromQueue:false,
  playedHistory:[],   // Shuffle history to prevent repeated songs
  history:[],
  playlists:{},
  likedIds:new Set(),
  allTracks:[],
  searchTracks:[],
  streamCache:{},
  advancing:false
};

/* Toast Notification */
var toastTimer=null;
function showToast(msg){
  var t=$('#toast');
  if(!t)return;
  t.textContent=msg;
  t.classList.add('on');
  clearTimeout(toastTimer);
  toastTimer=setTimeout(function(){t.classList.remove('on')},2200);
}

function show(v){
  document.querySelectorAll('.view').forEach(function(e){e.classList.toggle('on',e.id==='v-'+v)});
  document.querySelectorAll('.nb').forEach(function(b){b.classList.toggle('on',b.dataset.v===v)});
  $('#main').scrollTop=0;
  if(location.hash!=='#'+v)history.replaceState(null,'','#'+v);
  if(v==='search')setTimeout(function(){$('#sq').focus()},50);
}
document.querySelectorAll('.nb').forEach(function(b){b.onclick=function(){show(b.dataset.v)}});

function isLiked(id){return S.likedIds.has(id)}
function trackBg(t,i){return t&&t.thumbnail&&t.thumbnail!=='/logo.png'?'url("'+t.thumbnail+'") center/cover no-repeat, '+(t.g||G[(i||0)%G.length]):(t&&t.g?t.g:G[(i||0)%G.length])}

function rows(list,empty){
  if(!list||!list.length)return'<p class="empty">'+empty+'</p>';
  return'<div class="th"><span>#</span><span>Title</span><span>Album</span><span>Time</span><span style="text-align:right"></span></div>'+list.map(function(t,i){
    var cur=S.cur&&((S.cur.videoId&&S.cur.videoId===t.videoId)||(S.cur.id===t.id)||(S.cur.t&&S.cur.t===t.t));
    var bg=trackBg(t,i);
    var liked=isLiked(t.videoId||t.id);
    var tid=esc(t.videoId||t.id);
    return'<div class="tr'+(cur?' cur':'')+'" role="button" tabindex="0" data-tid="'+tid+'">'
         +'<span class="n">'+(i+1)+'</span>'
         +'<div class="nm"><i style="background:'+bg+'"></i><div><p class="t">'+esc(t.t)+'</p><p class="a">'+esc(t.a)+'</p></div></div>'
         +'<p class="al2">'+esc(t.al||'Single')+'</p>'
         +'<span class="d">'+fmt(t.d)+'</span>'
         +'<div class="tr-acts">'
         +'<button class="ib q-add-btn" data-act="queue-track" data-tid="'+tid+'" title="Add to Queue (FIFO)" aria-label="Add to Queue"><svg viewBox="0 0 24 24" class="ic" style="width:16px;height:16px"><path d="M12 5v14M5 12h14"/></svg></button>'
         +'<button class="heart'+(liked?' on':'')+'" data-like="'+tid+'" aria-label="Like">'+HEART+'</button>'
         +'</div>'
         +'</div>';
  }).join('');
}

function match(t,q,k){
  if(!q)return true;
  q=q.toLowerCase();
  var f={artist:t.a||'',title:t.t||'',album:t.al||'',all:(t.t+' '+(t.a||'')+' '+(t.al||''))}[k]||'';
  return f.toLowerCase().indexOf(q)>-1;
}

function renderLib(){
  var lo=+$('#dmin').value,hi=+$('#dmax').value;
  if(lo>hi){var x=lo;lo=hi;hi=x}
  $('#dlab').textContent=fmt(lo)+' – '+fmt(hi);
  
  if(S.tab==='playlist'&&S.selectedPlaylist){
    var val=S.playlists[S.selectedPlaylist];
    var list=Array.isArray(val)?val:(val&&val.songs?val.songs:[]);
    var plTracks=list.map(function(item,i){
      return{
        id:item.videoId||item.title||('p_'+i),
        videoId:item.videoId,
        t:item.title,
        a:item.channel||item.artist||'Unknown',
        al:item.album||S.selectedPlaylist||'Single',
        d:parseSec(item.duration),
        thumbnail:item.thumbnail,
        g:G[i%G.length]
      };
    });
    var q=$('#lq').value.trim();
    var filtered=plTracks.filter(function(t){
      return match(t,q,S.by)&&t.d>=lo&&t.d<=hi;
    });
    $('#cnt').textContent=filtered.length+' tracks';
    $('#lib').innerHTML='<div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:14px;flex-wrap:wrap;gap:10px">'
                       +'<div><h2 style="margin:0 0 4px 0">'+esc(S.selectedPlaylist)+'</h2><p class="a">'+list.length+' tracks in this playlist</p></div>'
                       +'<div style="display:flex;gap:8px">'
                       +'<button class="chip on" data-act="play-all" data-pl="'+esc(S.selectedPlaylist)+'">▶ Play All</button>'
                       +'<button class="chip" data-act="shuf-pl" data-pl="'+esc(S.selectedPlaylist)+'">🔀 Shuffle</button>'
                       +'<button class="chip" data-act="back-playlists">← Back</button>'
                       +'</div></div>'
                       +rows(filtered,'No tracks match your filters');
    return;
  }

  if(S.tab==='playlists'){
    var plKeys=Object.keys(S.playlists);
    if(!plKeys.length){$('#cnt').textContent='0 playlists';$('#lib').innerHTML='<p class="empty">No playlists yet. Click "+ Import" to add one.</p>';return}
    $('#cnt').textContent=plKeys.length+' playlists';
    $('#lib').innerHTML='<div class="quick">'+plKeys.map(function(k,i){
      var val=S.playlists[k];
      var list=Array.isArray(val)?val:(val&&val.songs?val.songs:[]);
      var count=list.length;
      return'<button class="q" data-pl="'+esc(k)+'"><i style="background:'+G[i%G.length]+'"></i><div><p class="t">'+esc(k)+'</p><p class="a">'+count+' tracks</p></div></button>';
    }).join('')+'</div>';
    return;
  }

  var list=S.allTracks;
  if(S.tab==='liked')list=S.allTracks.filter(function(t){return isLiked(t.videoId||t.id)});
  if(S.tab==='history')list=S.history;

  var q=$('#lq').value.trim();
  var filtered=list.filter(function(t){
    return match(t,q,S.by)&&t.d>=lo&&t.d<=hi;
  });
  $('#cnt').textContent=filtered.length+' tracks';
  $('#lib').innerHTML=rows(filtered,'No tracks match your filters');
}

$('#tabs').onclick=function(e){
  var b=e.target.closest('.chip');
  if(!b)return;
  S.tab=b.dataset.t;
  S.selectedPlaylist=null;
  this.querySelectorAll('.chip').forEach(function(c){c.classList.toggle('on',c===b)});
  renderLib();
};
$('#by').onclick=function(e){
  var b=e.target.closest('.chip');
  if(!b)return;
  S.by=b.dataset.k;
  this.querySelectorAll('.chip').forEach(function(c){c.classList.toggle('on',c===b)});
  renderLib();
};
['#lq','#dmin','#dmax'].forEach(function(s){$(s).oninput=renderLib});

/* Search */
var searchTimer=null;
function renderSearch(){
  var q=$('#sq').value.trim();
  if(!q){$('#sres').innerHTML='<p class="empty">Search songs, artists and albums</p>';return}
  clearTimeout(searchTimer);
  $('#sres').innerHTML='<p class="empty">Searching...</p>';
  searchTimer=setTimeout(async function(){
    try{
      var res=await fetch('/api/search?q='+encodeURIComponent(q));
      if(!res.ok)throw new Error('Search failed');
      var data=await res.json();
      S.searchTracks=data.map(function(item,i){
        return{
          id:'s_'+(item.videoId||i),
          videoId:item.videoId,
          t:item.title,
          a:item.channel,
          al:'YouTube',
          d:parseSec(item.duration),
          thumbnail:item.thumbnail,
          g:G[i%G.length]
        };
      });
      $('#sres').innerHTML=rows(S.searchTracks,'No results for “'+esc(q)+'”');
    }catch(err){
      $('#sres').innerHTML='<p class="empty">Search error. Try again.</p>';
    }
  },300);
}
$('#sq').oninput=renderSearch;

/* Track Finder across all sets */
function findTrackById(id){
  if(!id)return null;
  var all=[].concat(S.allTracks,S.searchTracks,S.history,S.queue);
  if(S.cur)all.push(S.cur);
  if(S.context&&S.context.length)all=all.concat(S.context);
  if(S.selectedPlaylist&&S.playlists[S.selectedPlaylist]){
    var pSongs=S.playlists[S.selectedPlaylist].songs||[];
    all=all.concat(pSongs.map(function(item,i){
      return{
        id:item.videoId||item.title||('p_'+i),
        videoId:item.videoId,
        t:item.title,
        a:item.channel||item.artist||'Unknown',
        al:item.album||S.selectedPlaylist||'Single',
        d:parseSec(item.duration),
        thumbnail:item.thumbnail,
        g:G[i%G.length]
      };
    }));
  }
  return all.find(function(x){
    return(x.videoId&&x.videoId===id)||(x.id&&x.id===id)||(x.t&&x.t===id);
  })||null;
}

/* Queue Operations (FIFO) */
function addToQueue(track){
  if(!track)return;
  // Make independent copy
  var copy=Object.assign({},track);
  S.queue.push(copy);
  paintPanel();
  showToast('Added to Queue (#'+S.queue.length+'): '+track.t);
}

function removeFromQueue(idx){
  if(idx>=0&&idx<S.queue.length){
    var rm=S.queue.splice(idx,1)[0];
    paintPanel();
    showToast('Removed from Queue: '+rm.t);
  }
}

function clearQueue(){
  S.queue=[];
  paintPanel();
  showToast('Queue cleared');
}

/* Player & Right Panel Paint */
function paintPlayer(){
  var t=S.cur;
  if(!t){
    $('#pt').textContent='SkullBeat';$('#pr').textContent='Select a track';
    $('#rt').textContent='No track playing';$('#ra').textContent='-';
    $('#rart').style.background='var(--grad)';$('#pa').style.background='var(--grad)';
    $('#pp').innerHTML=PLAY;
    return;
  }
  var bg=trackBg(t,0);
  $('#pa').style.background=bg;$('#pt').textContent=t.t;$('#pr').textContent=t.a;
  $('#rart').style.background=bg;$('#rt').textContent=t.t;$('#ra').textContent=t.a;
  var liked=isLiked(t.videoId||t.id);
  $('#rlike').classList.toggle('on',liked);
  $('#td').textContent=fmt(t.d);
  $('#pp').innerHTML=S.loading?SPIN:(S.playing?PAUSE:PLAY);
  
  // Shuffle indicator
  $('#shuf').classList.toggle('act',S.shuf);

  // Repeat indicator & Counter badge
  var repBtn=$('#rep');
  var badge=$('#rep-badge');
  repBtn.classList.toggle('act',S.repeatMode>0);
  if(badge){
    if(S.repeatMode===1){
      badge.textContent='1';
      badge.className='rep-badge on';
      badge.title='Repeat One ('+S.repeatRemaining+' replay left)';
    }else if(S.repeatMode===2){
      badge.textContent=S.repeatRemaining>0?S.repeatRemaining:'2';
      badge.className='rep-badge on';
      badge.title='Repeat Two ('+S.repeatRemaining+' replays left)';
    }else{
      badge.textContent='';
      badge.className='rep-badge';
      badge.title='Repeat OFF';
    }
  }

  paintProg();
  paintPanel();
  document.querySelectorAll('.tr').forEach(function(r){
    var match=S.cur&&r.dataset.tid===(S.cur.videoId||S.cur.id);
    r.classList.toggle('cur',match);
  });
}

function paintProg(){
  var d=(audio.duration&&!isNaN(audio.duration))?audio.duration:(S.cur?S.cur.d:0);
  var p=d>0?(S.pos/d*100):0;
  $('#seek').max=d||100;$('#seek').value=S.pos;
  $('#seek').style.setProperty('--p',p+'%');
  $('#tc').textContent=fmt(S.pos);
  if(d>0)$('#td').textContent=fmt(d);
  
  if(S.panel==='lyrics'){
    var ps=document.querySelectorAll('.ly p');
    if(ps.length){
      var idx=Math.floor(S.pos/Math.max(1,d)*ps.length);
      ps.forEach(function(p,j){p.classList.toggle('hi',j===idx)});
    }
  }
}

function paintPanel(){
  document.querySelectorAll('.tabs .chip').forEach(function(c){c.classList.toggle('on',c.dataset.p===S.panel)});
  
  // Update Queue Count Badge in Tab
  var qBadge=$('#q-cnt');
  if(qBadge)qBadge.textContent=S.queue.length?'('+S.queue.length+')':'';

  if(S.panel==='queue'){
    var html='';
    // 1. User Queue (FIFO)
    if(S.queue.length){
      html+='<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">'
          +'<p class="a" style="margin:0;font-weight:700;color:var(--cyan)">USER QUEUE ('+S.queue.length+')</p>'
          +'<button class="chip" data-act="clear-queue" style="font-size:11px;padding:3px 10px;min-height:auto">Clear</button>'
          +'</div>';
      html+=S.queue.map(function(t,i){
        var bg=trackBg(t,i);
        return'<div class="q2" style="position:relative;display:flex;align-items:center;padding-right:32px">'
             +'<button class="q2-main" data-tid="'+esc(t.videoId||t.id)+'" style="display:flex;align-items:center;gap:10px;flex:1;background:none;border:0;color:inherit;text-align:left;cursor:pointer">'
             +'<span style="font-size:11px;color:var(--cyan);width:16px">'+(i+1)+'</span>'
             +'<i style="background:'+bg+'"></i>'
             +'<div style="overflow:hidden"><p class="t">'+esc(t.t)+'</p><p class="a">'+esc(t.a)+'</p></div>'
             +'</button>'
             +'<button class="ib q-rm-btn" data-act="remove-q" data-idx="'+i+'" title="Remove from queue" style="position:absolute;right:4px;color:var(--mu);padding:4px"><svg class="ic" style="width:14px;height:14px" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12"/></svg></button>'
             +'</div>';
      }).join('');
    }

    // 2. Up Next from Active Context / Playlist
    var ctx=(S.context&&S.context.length)?S.context:S.allTracks;
    if(ctx&&ctx.length){
      html+='<p class="a" style="margin:'+(S.queue.length?'16px':'0')+' 0 8px;font-weight:700">NEXT FROM '+esc((S.contextName||'LIBRARY').toUpperCase())+'</p>';
      var upcoming=[];
      var start=S.contextIdx>=0?S.contextIdx+1:0;
      for(var k=0;k<Math.min(6,ctx.length);k++){
        var idx=(start+k)%ctx.length;
        if(ctx[idx]!==S.cur)upcoming.push(ctx[idx]);
      }
      html+=upcoming.map(function(t,i){
        var bg=trackBg(t,i);
        return'<button class="q2" data-tid="'+esc(t.videoId||t.id)+'"><i style="background:'+bg+'"></i><div><p class="t">'+esc(t.t)+'</p><p class="a">'+esc(t.a)+'</p></div></button>';
      }).join('');
    }else if(!S.queue.length){
      html='<p class="a" style="padding:16px;text-align:center">Queue is empty</p>';
    }
    $('#pq').className='';
    $('#pq').innerHTML=html;
  }else{
    $('#pq').className='ly';
    if(S.lyricsList&&S.lyricsList.length){
      $('#pq').innerHTML=S.lyricsList.map(function(l){return'<p>'+esc(l)+'</p>'}).join('');
    }else{
      var L=['Neon on the bone, we drift','Through the static and the haze','Hearts keep time in the dark','Pulse of a thousand days','Skull beat, skull beat','Don’t let the signal fade'];
      $('#pq').innerHTML=L.map(function(l){return'<p>'+l+'</p>'}).join('');
    }
  }
}

/* Audio Playback Engine */
function playTrack(track,origin){
  if(!track)return;
  S.cur=track;
  S.pos=0;
  S.playing=true;
  S.loading=true;
  
  // Reset repeat counter for newly started track
  S.repeatRemaining=S.repeatMode;
  S.repeatTotalPlayed=0;

  if(origin)$('#from').textContent='PLAYING FROM '+(origin.toUpperCase());
  paintPlayer();

  var vid=track.videoId||track.id||'';
  var q=(track.t+' '+(track.a||'')).trim();
  var directUrl='/api/stream/'+encodeURIComponent(vid)+'?redirect=1&q='+encodeURIComponent(q);

  audio.src=directUrl;
  var p=audio.play();
  if(p!==undefined){
    p.then(function(){
      S.playing=true;
      S.loading=false;
      paintPlayer();
    }).catch(function(err){
      console.warn('Playback error:',err);
      S.playing=false;
      S.loading=false;
      paintPlayer();
    });
  }

  if('mediaSession' in navigator){
    try{
      navigator.mediaSession.metadata=new MediaMetadata({
        title:track.t,artist:track.a,album:track.al||'SKULLBEAT',
        artwork:[{src:track.thumbnail||'/logo.png',sizes:'512x512',type:'image/jpeg'}]
      });
    }catch(e){}
  }

  try{
    fetch('/api/history',{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({
        videoId:vid,
        title:track.t,
        channel:track.a,
        thumbnail:track.thumbnail,
        duration:fmt(track.d)
      })
    });
  }catch(e){}

  fetchLyrics(track);
}

async function fetchLyrics(track){
  S.lyricsList=null;
  try{
    var res=await fetch('/api/lyrics?title='+encodeURIComponent(track.t)+'&artist='+encodeURIComponent(track.a||''));
    if(res.ok){
      var data=await res.json();
      if(data.synced){
        S.lyricsList=data.synced.split('\n').map(function(l){return l.replace(/^\[.*?\]/,'').trim()}).filter(Boolean);
      }else if(data.plain){
        S.lyricsList=data.plain.split('\n').map(function(l){return l.trim()}).filter(Boolean);
      }
    }
  }catch(e){}
  if(S.panel==='lyrics')paintPanel();
}

/* Audio Events */
audio.onloadstart=function(){S.loading=true;paintPlayer()};
audio.onwaiting=function(){S.loading=true;paintPlayer()};
audio.onplaying=function(){S.loading=false;S.playing=true;paintPlayer()};
audio.onplay=function(){S.loading=false;S.playing=true;paintPlayer()};
audio.onpause=function(){S.playing=false;paintPlayer()};
audio.onerror=function(){
  console.error('Audio load error:',audio.error);
  S.loading=false;S.playing=false;paintPlayer();
};
audio.ontimeupdate=function(){
  S.pos=Math.round(audio.currentTime);
  paintProg();
};

/* Audio Finished: Spotify-Grade Priority Engine */
audio.onended=function(){
  // Priority 1: Finish active song and its repeat count
  if(S.repeatRemaining>0){
    S.repeatRemaining--;
    S.repeatTotalPlayed++;
    audio.currentTime=0;
    audio.play().catch(function(){});
    paintPlayer();
    showToast('Replaying track ('+S.repeatRemaining+' replay remaining)');
    return;
  }
  
  // Repeat cycle complete: advance to next song (FIFO Queue -> Context)
  step(1);
};

/* Master Next / Prev Step Function */
function step(d){
  if(S.advancing)return;
  S.advancing=true;
  setTimeout(function(){S.advancing=false},400);

  // Priority 2: Play queued songs in FIFO order
  if(d>0&&S.queue.length>0){
    var nextFromQueue=S.queue.shift(); // FIFO: First In, First Out
    S.isFromQueue=true;
    playTrack(nextFromQueue,'Queue');
    paintPanel();
    return;
  }

  // Priority 3: When queue is empty, automatically resume playlist playback
  var ctx=(S.context&&S.context.length)?S.context:S.allTracks;
  if(!ctx||!ctx.length)return;

  var nextIdx=0;
  if(S.shuf){
    // Shuffle ON: Pick random song avoiding same song and recent songs
    var pool=ctx.map(function(t,i){return i}).filter(function(i){return i!==S.contextIdx});
    var unplayed=pool.filter(function(i){return S.playedHistory.indexOf(i)===-1});
    if(!unplayed.length){
      S.playedHistory=[];
      unplayed=pool;
    }
    var choices=unplayed.length?unplayed:pool;
    nextIdx=choices[Math.floor(Math.random()*choices.length)];
    S.playedHistory.push(nextIdx);
    if(S.playedHistory.length>Math.min(25,Math.floor(ctx.length/2)))S.playedHistory.shift();
  }else{
    // Shuffle OFF: Play next song in playlist order
    if(S.contextIdx===-1)nextIdx=0;
    else nextIdx=(S.contextIdx+d+ctx.length)%ctx.length;
  }

  S.contextIdx=nextIdx;
  S.isFromQueue=false;
  var track=ctx[nextIdx];
  playTrack(track,S.contextName||'Playlist');
  paintPanel();
}

/* Repeat Setting & Multi-Tap Controller */
function setRepeatMode(mode){
  S.repeatMode=mode;
  S.repeatRemaining=mode;
  S.repeatTotalPlayed=0;
  paintPlayer();
  var labels=['Repeat: OFF','Repeat: ONE (1 additional play)','Repeat: TWO (2 additional plays)'];
  showToast(labels[mode]);
}

var repTapTimer=null;
var repTapCount=0;

$('#rep').onclick=function(e){
  e.preventDefault();
  repTapCount++;
  clearTimeout(repTapTimer);
  repTapTimer=setTimeout(function(){
    if(repTapCount===1){
      // Single tap: Turn repeat OFF (or cycle to 1 if already OFF)
      if(S.repeatMode!==0)setRepeatMode(0);
      else setRepeatMode(1);
    }else if(repTapCount===2){
      // Double tap: Enable REPEAT ONE
      setRepeatMode(1);
    }else if(repTapCount>=3){
      // Triple tap: Enable REPEAT TWO
      setRepeatMode(2);
    }
    repTapCount=0;
  },320);
};

// Desktop accessible repeat controls: Right-click / contextmenu on repeat button
$('#rep').oncontextmenu=function(e){
  e.preventDefault();
  var pop=$('#rep-pop');
  pop.classList.toggle('on');
};

document.querySelectorAll('#rep-pop button').forEach(function(btn){
  btn.onclick=function(){
    var m=+btn.dataset.setRep;
    setRepeatMode(m);
    $('#rep-pop').classList.remove('on');
  };
});

/* Shuffle Toggle */
$('#shuf').onclick=function(){
  S.shuf=!S.shuf;
  paintPlayer();
  showToast(S.shuf?'Shuffle: ON':'Shuffle: OFF');
};

/* Play / Pause */
$('#pp').onclick=function(){
  if(!S.cur){
    var ctx=(S.context&&S.context.length)?S.context:S.allTracks;
    if(ctx.length)playTrack(ctx[0],S.contextName||'Library');
    return;
  }
  if(audio.paused){
    if(!audio.src){
      playTrack(S.cur);
    }else{
      audio.play().catch(function(e){
        console.warn('Play error:',e);
        playTrack(S.cur);
      });
    }
  }else{
    audio.pause();
  }
};

$('#next').onclick=function(){step(1)};
$('#prev').onclick=function(){
  if(audio.currentTime>4){
    audio.currentTime=0;
  }else{
    step(-1);
  }
};

$('#seek').oninput=function(){
  var val=+this.value;
  S.pos=val;
  if(audio.duration&&!isNaN(audio.duration)&&audio.readyState>=1){
    audio.currentTime=val;
  }
  paintProg();
};

$('#vol').oninput=function(){
  var v=+this.value/100;
  audio.volume=v;
  this.style.setProperty('--p',this.value+'%');
};
$('#vol').style.setProperty('--p','80%');

$('#mute-btn').onclick=function(){
  audio.muted=!audio.muted;
  $('#vol-ic').style.opacity=audio.muted?'.3':'1';
};

$('#dl').onclick=function(){
  if(!S.cur)return;
  var vid=S.cur.videoId||'';
  var q=encodeURIComponent((S.cur.title||'')+' '+(S.cur.artist||''));
  window.open('/api/download/'+(vid?encodeURIComponent(vid):'pending')+'?q='+q,'_blank');
};

/* Playlist Navigation & Context Setting */
function openPlaylist(name){
  S.selectedPlaylist=name;
  S.tab='playlist';
  document.querySelectorAll('#tabs .chip').forEach(function(c){c.classList.toggle('on',false)});
  show('library');
  renderLib();
}

function playPlaylist(name,startIdx){
  var val=S.playlists[name];
  var list=Array.isArray(val)?val:(val&&val.songs?val.songs:[]);
  if(!list||!list.length)return;
  var mapped=list.map(function(item,i){
    return{
      id:item.videoId||item.title||('p_'+i),
      videoId:item.videoId,
      t:item.title,
      a:item.channel||item.artist||'Unknown',
      al:item.album||name||'Single',
      d:parseSec(item.duration),
      thumbnail:item.thumbnail,
      g:G[i%G.length]
    };
  });
  
  // Set as backing context
  S.context=mapped;
  S.contextName=name;
  var s=startIdx||0;
  S.contextIdx=s;
  S.isFromQueue=false;
  
  playTrack(mapped[s],name);
}

/* Desktop Context Menu Manager */
var ctxTargetTrack=null;
var ctxMenu=$('#ctx-menu');

document.addEventListener('contextmenu',function(e){
  var r=e.target.closest('[data-tid]');
  if(r){
    e.preventDefault();
    var tid=r.dataset.tid;
    ctxTargetTrack=findTrackById(tid);
    if(ctxTargetTrack){
      ctxMenu.style.left=Math.min(e.clientX,window.innerWidth-180)+'px';
      ctxMenu.style.top=Math.min(e.clientY,window.innerHeight-130)+'px';
      ctxMenu.classList.add('on');
    }
  }else if(!e.target.closest('#rep')){
    ctxMenu.classList.remove('on');
    $('#rep-pop').classList.remove('on');
  }
});

$('#ctx-play').onclick=function(){
  ctxMenu.classList.remove('on');
  if(ctxTargetTrack)playTrack(ctxTargetTrack,'Selection');
};
$('#ctx-queue').onclick=function(){
  ctxMenu.classList.remove('on');
  if(ctxTargetTrack)addToQueue(ctxTargetTrack);
};
$('#ctx-like').onclick=function(){
  ctxMenu.classList.remove('on');
  if(ctxTargetTrack){
    var id=ctxTargetTrack.videoId||ctxTargetTrack.id;
    if(S.likedIds.has(id))S.likedIds.delete(id);else S.likedIds.add(id);
    paintPlayer();
  }
};
$('#ctx-dl').onclick=function(){
  ctxMenu.classList.remove('on');
  if(ctxTargetTrack){
    var vid=ctxTargetTrack.videoId||'';
    var q=encodeURIComponent((ctxTargetTrack.title||'')+' '+(ctxTargetTrack.artist||''));
    window.open('/api/download/'+(vid?encodeURIComponent(vid):'pending')+'?q='+q,'_blank');
  }
};

/* Mobile Touch Swipe: Left-to-Right adds to Queue */
var touchRow=null,touchStartX=0,touchStartY=0,touchDx=0,isSwiping=false;

document.addEventListener('touchstart',function(e){
  var r=e.target.closest('.tr');
  if(!r)return;
  touchRow=r;
  touchStartX=e.touches[0].clientX;
  touchStartY=e.touches[0].clientY;
  touchDx=0;
  isSwiping=false;
},{passive:true});

document.addEventListener('touchmove',function(e){
  if(!touchRow)return;
  var curX=e.touches[0].clientX;
  var curY=e.touches[0].clientY;
  var dx=curX-touchStartX;
  var dy=curY-touchStartY;

  // Horizontal swipe left-to-right
  if(dx>10&&Math.abs(dx)>Math.abs(dy)*1.4){
    isSwiping=true;
    touchDx=dx;
    var moveX=Math.min(dx,100);
    touchRow.style.transform='translateX('+moveX+'px)';
    touchRow.style.transition='none';
    touchRow.classList.add('swiping');
    touchRow.classList.toggle('swipe-ready',dx>65);
  }
},{passive:true});

document.addEventListener('touchend',function(){
  if(!touchRow)return;
  var r=touchRow;
  var dx=touchDx;
  touchRow=null;
  isSwiping=false;

  r.style.transition='transform .2s ease';
  r.style.transform='translateX(0)';
  r.classList.remove('swiping');
  r.classList.remove('swipe-ready');

  if(dx>65){
    var tid=r.dataset.tid;
    var t=findTrackById(tid);
    if(t){
      addToQueue(t);
      if(navigator.vibrate)try{navigator.vibrate(35)}catch(err){}
    }
  }
});

/* Master Click Router */
document.addEventListener('click',function(e){
  // Close popovers if clicked outside
  if(!e.target.closest('#ctx-menu'))ctxMenu.classList.remove('on');
  if(!e.target.closest('#rep')&&!e.target.closest('#rep-pop'))$('#rep-pop').classList.remove('on');

  // Queue actions in right panel
  if(e.target.closest('[data-act="remove-q"]')){
    var btn=e.target.closest('[data-act="remove-q"]');
    removeFromQueue(+btn.dataset.idx);
    return;
  }
  if(e.target.closest('[data-act="clear-queue"]')){
    clearQueue();
    return;
  }

  // Direct "+ Queue" button on track row
  if(e.target.closest('[data-act="queue-track"]')){
    e.stopPropagation();
    var qBtn=e.target.closest('[data-act="queue-track"]');
    var qTrack=findTrackById(qBtn.dataset.tid);
    if(qTrack)addToQueue(qTrack);
    return;
  }

  // Back to playlists button
  var backBtn=e.target.closest('[data-act="back-playlists"]');
  if(backBtn){
    S.tab='playlists';
    document.querySelectorAll('#tabs .chip').forEach(function(c){
      c.classList.toggle('on',c.dataset.t==='playlists');
    });
    renderLib();
    return;
  }

  // Play All playlist button
  var playAllBtn=e.target.closest('[data-act="play-all"]');
  if(playAllBtn){
    var plName=playAllBtn.dataset.pl||S.selectedPlaylist;
    if(plName)playPlaylist(plName,0);
    return;
  }

  // Shuffle playlist button
  var shufBtn=e.target.closest('[data-act="shuf-pl"]');
  if(shufBtn){
    var plName=shufBtn.dataset.pl||S.selectedPlaylist;
    var val=S.playlists[plName];
    var list=Array.isArray(val)?val:(val&&val.songs?val.songs:[]);
    if(list.length){
      var rIdx=Math.floor(Math.random()*list.length);
      S.shuf=true;
      playPlaylist(plName,rIdx);
    }
    return;
  }

  // Like heart
  var l=e.target.closest('[data-like]');
  if(l){
    e.stopPropagation();
    var id=l.dataset.like;
    if(S.likedIds.has(id))S.likedIds.delete(id);else S.likedIds.add(id);
    l.classList.toggle('on',S.likedIds.has(id));
    paintPlayer();
    return;
  }

  // Track row / card click
  var r=e.target.closest('[data-tid]');
  if(r){
    var id=r.dataset.tid;
    var t=findTrackById(id);
    if(t){
      // Set context depending on current view
      if(S.tab==='playlist'&&S.selectedPlaylist){
        var val=S.playlists[S.selectedPlaylist];
        var sList=Array.isArray(val)?val:(val&&val.songs?val.songs:[]);
        S.context=sList.map(function(item,i){
          return{
            id:item.videoId||item.title||('p_'+i),
            videoId:item.videoId,
            t:item.title,
            a:item.channel||item.artist||'Unknown',
            al:item.album||S.selectedPlaylist||'Single',
            d:parseSec(item.duration),
            thumbnail:item.thumbnail,
            g:G[i%G.length]
          };
        });
        S.contextName=S.selectedPlaylist;
        S.contextIdx=S.context.findIndex(function(x){return(x.videoId&&x.videoId===id)||(x.id&&x.id===id)});
      }else if(S.tab==='all'){
        S.context=S.allTracks;
        S.contextName='Library';
        S.contextIdx=S.allTracks.findIndex(function(x){return(x.videoId&&x.videoId===id)||(x.id&&x.id===id)});
      }else if(S.searchTracks&&S.searchTracks.length){
        S.context=S.searchTracks;
        S.contextName='Search';
        S.contextIdx=S.searchTracks.findIndex(function(x){return(x.videoId&&x.videoId===id)||(x.id&&x.id===id)});
      }
      S.isFromQueue=false;
      playTrack(t,S.contextName||'Selection');
    }
    return;
  }

  // Playlist card or sidebar playlist button
  var qb=e.target.closest('[data-pl]');
  if(qb){
    var plName=qb.dataset.pl;
    if(qb.classList.contains('pl')||qb.closest('#lib')){
      openPlaylist(plName);
    }else{
      playPlaylist(plName,0);
    }
    return;
  }

  // Logo click
  var logo=e.target.closest('.logo');
  if(logo){
    show('home');
    return;
  }
});

/* Keyboard shortcuts */
document.addEventListener('keydown',function(e){
  if(e.target.matches('input'))return;
  if(e.code==='Space'){e.preventDefault();$('#pp').click()}
  if(e.key==='ArrowRight'&&e.shiftKey)step(1);
  if(e.key==='ArrowLeft'&&e.shiftKey)step(-1);
});

$('#rlike').onclick=function(){
  if(!S.cur)return;
  var id=S.cur.videoId||S.cur.id;
  if(S.likedIds.has(id))S.likedIds.delete(id);else S.likedIds.add(id);
  paintPlayer();
};

document.querySelector('.tabs').onclick=function(e){
  var b=e.target.closest('[data-p]');
  if(b){S.panel=b.dataset.p;paintPanel()}
};

/* Export */
$('#exp').onclick=function(){
  var list=S.allTracks.filter(function(t){return S.tab==='liked'?isLiked(t.videoId||t.id):true});
  var csv='title,artist,album,duration,videoId\n'+list.map(function(t){return[t.t,t.a,t.al||'',t.d,t.videoId||''].map(function(v){return'"'+String(v).replace(/"/g,'""')+'"'}).join(',')}).join('\n');
  var a=document.createElement('a');a.href='data:text/csv;charset=utf-8,'+encodeURIComponent(csv);a.download='skullbeat-library.csv';a.click();
};

/* Import Modal */
function openImport(){$('#import-modal').classList.add('on')}
function closeImport(){$('#import-modal').classList.remove('on')}
$('#add-pl').onclick=openImport;

async function doImportUrl(){
  var url=$('#import-url').value.trim();
  if(!url)return;
  closeImport();
  alert('Importing '+url+' ...');
  try{
    var res=await fetch('/api/playlist?url='+encodeURIComponent(url));
    var data=await res.json();
    if(data.success){
      alert('Imported '+data.total+' tracks successfully!');
      loadLibrary();
    }else{
      alert('Import failed: '+(data.detail||data.message||'Unknown error'));
    }
  }catch(e){
    alert('Import error: '+e.message);
  }
}

$('#csv-in').onchange=async function(e){
  var file=e.target.files[0];
  if(!file)return;
  var name=file.name.replace(/\.[^/.]+$/,'');
  var content=await file.text();
  closeImport();
  alert('Importing '+file.name+' ...');
  try{
    var res=await fetch('/api/import_csv',{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({name:name,content:content})
    });
    var data=await res.json();
    if(data.success){
      alert('Imported '+data.count+' tracks into "'+data.playlist+'"!');
      loadLibrary();
    }else{
      alert('Import error: '+(data.detail||'Failed'));
    }
  }catch(err){
    alert('Import failed: '+err.message);
  }
};

/* Init / Data Loading */
async function loadLibrary(){
  try{
    var res=await fetch('/api/user_library');
    if(res.ok){
      var data=await res.json();
      S.playlists=data.playlists||{};
      var favs=data.favorites||[];
      favs.forEach(function(f){if(f.videoId)S.likedIds.add(f.videoId)});

      // Side playlists
      var plKeys=Object.keys(S.playlists);
      $('#side-pl').innerHTML=plKeys.map(function(p,i){
        return'<button class="pl" data-pl="'+esc(p)+'"><i style="background:'+G[i%G.length]+'"></i><span>'+esc(p)+'</span></button>';
      }).join('');

      // Quick play cards
      $('#quick').innerHTML=plKeys.slice(0,6).map(function(p,i){
        var bg=G[i%G.length];
        return'<button class="q" data-pl="'+esc(p)+'"><i style="background:'+bg+'"></i><span class="t">'+esc(p)+'</span></button>';
      }).join('');

      // Flatten tracks
      var map={};
      plKeys.forEach(function(k){
        var val=S.playlists[k];
        var list=Array.isArray(val)?val:(val&&val.songs?val.songs:[]);
        list.forEach(function(item){
          var key=item.videoId||item.title;
          if(!map[key]){
            map[key]={
              id:item.videoId||key,
              videoId:item.videoId,
              t:item.title,
              a:item.channel||item.artist||'Unknown',
              al:item.album||k||'Single',
              d:parseSec(item.duration),
              thumbnail:item.thumbnail,
              g:G[Object.keys(map).length%G.length]
            };
          }
        });
      });
      S.allTracks=Object.values(map);
      S.context=S.allTracks;
      S.contextName='Library';
      
      // Made for you
      $('#made').innerHTML=S.allTracks.slice(0,10).map(function(t){
        return'<button class="al" data-tid="'+esc(t.videoId||t.id)+'"><i style="background:'+trackBg(t,0)+'"></i><p class="t">'+esc(t.t)+'</p><p class="a">'+esc(t.a)+'</p></button>';
      }).join('');

      renderLib();
    }
  }catch(e){console.warn('Library load error:',e)}
}

async function loadHistory(){
  try{
    var res=await fetch('/api/history');
    if(res.ok){
      var data=await res.json();
      var list=Array.isArray(data)?data:(data.history||[]);
      S.history=list.map(function(item,i){
        return{
          id:item.videoId||i,
          videoId:item.videoId,
          t:item.title,
          a:item.channel||'Artist',
          al:'Recent',
          d:parseSec(item.duration),
          thumbnail:item.thumbnail,
          g:G[i%G.length]
        };
      });
      $('#recent').innerHTML=S.history.slice(0,6).map(function(t){
        return'<button class="al" data-tid="'+esc(t.videoId||t.id)+'"><i style="background:'+trackBg(t,0)+'"></i><p class="t">'+esc(t.t)+'</p><p class="a">'+esc(t.a)+'</p></button>';
      }).join('');
    }
  }catch(e){console.warn('History load error:',e)}
}

(function initGreeting(){
  var h=new Date().getHours();
  $('#greet').textContent=h<12?'Good morning':h<18?'Good afternoon':'Good evening';
})();

paintPlayer();
loadLibrary().then(loadHistory);

var h=location.hash.slice(1);
if(['home','search','library'].indexOf(h)>-1)show(h);
</script>
</body>
</html>"""

if __name__ == "__main__":
    print("Starting AudioTube server on http://localhost:8000 ...")
    uvicorn.run(app, host="127.0.0.1", port=8000)
