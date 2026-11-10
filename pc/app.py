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


@app.get("/api/stream/{video_id}")
def api_stream_url(video_id: str):
    """Extracts raw, ad-free audio stream URL without downloading video."""
    ydl_opts = {
        "format": "bestaudio/best",
        "quiet": True,
        "skip_download": True,
        "js_runtimes": {"node": {}},
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
.logo{position:relative;width:176px;height:40px;border-radius:8px;overflow:hidden;margin:0 0 20px 8px;background:var(--bg);flex:none}
.logo svg{position:absolute;inset:0;width:176px;height:40px}
.logo span{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font:800 20px Orbitron,system-ui,sans-serif;letter-spacing:2px;color:#fff;mix-blend-mode:difference}
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
.th,.tr{display:grid;grid-template-columns:36px minmax(0,3fr) minmax(0,2fr) 64px 48px;gap:14px;align-items:center;padding:8px 14px;border-radius:10px}
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
  <div class="logo" aria-label="SkullBeat"><svg viewBox="0 0 176 40" aria-hidden="true"><path d="M0 40V22Q11 14 22 22T44 22T66 22T88 22T110 22T132 22T154 22T176 22V40z" fill="#fff"/></svg><span>SKULLBEAT</span></div>
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
  <div class="tabs"><button class="chip on" data-p="queue">Queue</button><button class="chip" data-p="lyrics">Lyrics</button></div>
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
      <button class="ib rp" id="rep" aria-label="Repeat"><svg class="ic" viewBox="0 0 24 24"><path d="M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3"/></svg></button>
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
var S={cur:null,playing:false,pos:0,shuf:false,rep:false,panel:'queue',tab:'all',by:'all',queue:[],history:[],playlists:{},likedIds:new Set(),allTracks:[],searchTracks:[],streamCache:{}};

function show(v){
  document.querySelectorAll('.view').forEach(function(e){e.classList.toggle('on',e.id==='v-'+v)});
  document.querySelectorAll('.nb').forEach(function(b){b.classList.toggle('on',b.dataset.v===v)});
  $('#main').scrollTop=0; if(location.hash!=='#'+v)history.replaceState(null,'','#'+v);
  if(v==='search')setTimeout(function(){$('#sq').focus()},0);
}
document.querySelectorAll('.nb').forEach(function(b){b.onclick=function(){show(b.dataset.v)}});

function isLiked(id){return S.likedIds.has(id)}
function trackBg(t,i){return t&&t.thumbnail?'url("'+t.thumbnail+'") center/cover no-repeat':(t&&t.g?t.g:G[(i||0)%G.length])}

function rows(list,empty){
  if(!list||!list.length)return'<p class="empty">'+empty+'</p>';
  return'<div class="th"><span>#</span><span>Title</span><span>Album</span><span>Time</span><span></span></div>'+list.map(function(t,i){
    var cur=S.cur&&((S.cur.videoId&&S.cur.videoId===t.videoId)||(S.cur.id===t.id));
    var bg=trackBg(t,i);
    var liked=isLiked(t.videoId||t.id);
    return'<div class="tr'+(cur?' cur':'')+'" role="button" tabindex="0" data-tid="'+esc(t.videoId||t.id)+'"><span class="n">'+(i+1)+'</span><div class="nm"><i style="background:'+bg+'"></i><div><p class="t">'+esc(t.t)+'</p><p class="a">'+esc(t.a)+'</p></div></div><p class="al2">'+esc(t.al||'Single')+'</p><span class="d">'+fmt(t.d)+'</span><button class="heart'+(liked?' on':'')+'" data-like="'+esc(t.videoId||t.id)+'" aria-label="Like">'+HEART+'</button></div>'}).join('');
}

function match(t,q,k){
  if(!q)return true;q=q.toLowerCase();
  var f={artist:t.a,title:t.t,album:t.al,all:(t.t+' '+t.a+' '+(t.al||''))}[k];
  return f.toLowerCase().indexOf(q)>-1;
}

function renderLib(){
  var lo=+$('#dmin').value,hi=+$('#dmax').value;if(lo>hi){var x=lo;lo=hi;hi=x}
  $('#dlab').textContent=fmt(lo)+' – '+fmt(hi);
  
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

$('#tabs').onclick=function(e){var b=e.target.closest('.chip');if(!b)return;S.tab=b.dataset.t;this.querySelectorAll('.chip').forEach(function(c){c.classList.toggle('on',c===b)});renderLib()};
$('#by').onclick=function(e){var b=e.target.closest('.chip');if(!b)return;S.by=b.dataset.k;this.querySelectorAll('.chip').forEach(function(c){c.classList.toggle('on',c===b)});renderLib()};
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

/* Player & Right Panel */
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
  $('#shuf').classList.toggle('act',S.shuf);$('#rep').classList.toggle('act',S.rep);
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
  if(S.panel==='queue'){
    var q=S.queue.slice(0,8);
    if(!q.length)$('#pq').innerHTML='<p class="a" style="padding:16px;text-align:center">Queue is empty</p>';
    else $('#pq').innerHTML='<p class="a" style="margin-bottom:6px">NEXT UP</p>'+q.map(function(t,i){
      var bg=trackBg(t,i);
      return'<button class="q2" data-tid="'+esc(t.videoId||t.id)+'"><i style="background:'+bg+'"></i><div><p class="t">'+esc(t.t)+'</p><p class="a">'+esc(t.a)+'</p></div></button>';
    }).join('');
  }else{
    $('#pq').className='ly';
    if(S.lyricsList&&S.lyricsList.length){
      $('#pq').innerHTML=S.lyricsList.map(function(l){return'<p>'+esc(l)+'</p>'}).join('');
    }else{
      var L=['Neon on the bone, we drift','Through the static and the haze','Hearts keep time in the dark','Pulse of a thousand days','Skull beat, skull beat','Don’t let the signal fade'];
      $('#pq').innerHTML=L.map(function(l){return'<p>'+l+'</p>'}).join('');
    }
    return;
  }
  $('#pq').className='';
}

/* Audio Playback Engine */
async function playTrack(track,origin){
  if(!track)return;
  S.cur=track;S.pos=0;S.playing=false;S.loading=true;
  if(origin)$('#from').textContent='PLAYING FROM '+(origin.toUpperCase());
  paintPlayer();

  if(!track.videoId||track.videoId.startsWith('pending_')){
    try{
      var q=(track.t+' '+(track.a||'')).trim();
      var rRes=await fetch('/api/resolve_track?q='+encodeURIComponent(q));
      if(rRes.ok){
        var matched=await rRes.json();
        if(matched&&matched.videoId){
          track.videoId=matched.videoId;
          track.thumbnail=matched.thumbnail||track.thumbnail;
          track.d=parseSec(matched.duration)||track.d;
        }
      }
    }catch(e){console.warn('Resolve error:',e)}
  }

  if(!track.videoId||track.videoId.startsWith('pending_')){
    S.loading=false;paintPlayer();
    alert('Could not find stream for: '+track.t);
    return;
  }

  if('mediaSession' in navigator){
    navigator.mediaSession.metadata=new MediaMetadata({
      title:track.t,artist:track.a,album:track.al||'SKULLBEAT',
      artwork:[{src:track.thumbnail||'/logo.png',sizes:'512x512',type:'image/jpeg'}]
    });
  }

  try{fetch('/api/history',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({videoId:track.videoId,title:track.t,channel:track.a,thumbnail:track.thumbnail,duration:fmt(track.d)})})}catch(e){}

  try{
    var streamUrl=S.streamCache[track.videoId];
    if(!streamUrl){
      var res=await fetch('/api/stream/'+track.videoId);
      var data=await res.json();
      streamUrl=data.stream_url;
      if(streamUrl)S.streamCache[track.videoId]=streamUrl;
    }
    if(streamUrl){
      audio.src=streamUrl;
      audio.play().then(function(){
        S.playing=true;S.loading=false;paintPlayer();
      }).catch(function(err){
        console.warn('Play error:',err);
        S.playing=false;S.loading=false;paintPlayer();
      });
    }else{
      S.loading=false;paintPlayer();
    }
  }catch(err){
    console.error('Stream fetch error:',err);
    S.loading=false;paintPlayer();
  }

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

audio.ontimeupdate=function(){
  S.pos=Math.round(audio.currentTime);
  paintProg();
};
audio.onplay=function(){S.playing=true;S.loading=false;paintPlayer()};
audio.onpause=function(){S.playing=false;paintPlayer()};
audio.onended=function(){if(S.rep){audio.currentTime=0;audio.play()}else{step(1)}};

function step(d){
  if(!S.queue.length&&S.allTracks.length){S.queue=S.allTracks.slice()}
  if(!S.queue.length)return;
  var nextIdx=0;
  if(S.shuf){nextIdx=Math.floor(Math.random()*S.queue.length)}
  var t=S.queue.splice(nextIdx,1)[0];
  playTrack(t,'Queue');
}

/* Controls */
$('#pp').onclick=function(){
  if(!S.cur){if(S.allTracks.length)playTrack(S.allTracks[0],'Library');return}
  if(audio.paused)audio.play();else audio.pause();
};
$('#next').onclick=function(){step(1)};
$('#prev').onclick=function(){if(audio.currentTime>4){audio.currentTime=0}else step(-1)};
$('#shuf').onclick=function(){S.shuf=!S.shuf;paintPlayer()};
$('#rep').onclick=function(){S.rep=!S.rep;paintPlayer()};
$('#seek').oninput=function(){audio.currentTime=+this.value;S.pos=+this.value;paintProg()};
$('#vol').oninput=function(){
  var v=+this.value/100;audio.volume=v;
  this.style.setProperty('--p',this.value+'%');
};
$('#vol').style.setProperty('--p','80%');
$('#mute-btn').onclick=function(){
  audio.muted=!audio.muted;
  $('#vol-ic').style.opacity=audio.muted?'.3':'1';
};
$('#dl').onclick=function(){
  if(!S.cur||!S.cur.videoId)return;
  window.open('/api/download/'+S.cur.videoId,'_blank');
};

/* Clicks */
document.addEventListener('click',function(e){
  var l=e.target.closest('[data-like]');
  if(l){
    e.stopPropagation();
    var id=l.dataset.like;
    if(S.likedIds.has(id))S.likedIds.delete(id);else S.likedIds.add(id);
    l.classList.toggle('on',S.likedIds.has(id));
    paintPlayer();
    return;
  }
  var r=e.target.closest('[data-tid]');
  if(r){
    var id=r.dataset.tid;
    var t=[].concat(S.allTracks,S.searchTracks,S.history).find(function(x){return(x.videoId||x.id)===id});
    if(t)playTrack(t,'Selection');
    return;
  }
  var qb=e.target.closest('[data-pl]');
  if(qb){
    var plName=qb.dataset.pl;
    playPlaylist(plName);
  }
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
  alert('Importing playlist...');
  try{
    var res=await fetch('/api/playlist?url='+encodeURIComponent(url));
    if(res.ok){
      var data=await res.json();
      await loadLibrary();
      alert('Imported playlist with '+(data.tracks?data.tracks.length:0)+' songs!');
    }
  }catch(e){alert('Import failed: '+e)}
}

$('#csv-in').onchange=function(e){
  var file=e.target.files[0];
  if(!file)return;
  closeImport();
  var reader=new FileReader();
  reader.onload=async function(evt){
    try{
      var res=await fetch('/api/import_csv',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          filename:file.name,
          content:evt.target.result,
          playlist_name:file.name.replace(/\.csv$/i,'').replace(/_/g,' ')
        })
      });
      if(res.ok){
        var data=await res.json();
        await loadLibrary();
        alert('Imported '+data.count+' tracks into '+data.playlist_name);
      }
    }catch(err){alert('CSV import error: '+err)}
  };
  reader.readAsText(file);
};

function playPlaylist(name){
  var val=S.playlists[name];
  var list=Array.isArray(val)?val:(val&&val.songs?val.songs:[]);
  if(!list||!list.length)return;
  var mapped=list.map(function(item,i){
    return{
      id:item.videoId||('pl_'+i),
      videoId:item.videoId,
      t:item.title,
      a:item.channel||item.artist||'Unknown',
      al:item.album||name,
      d:parseSec(item.duration),
      thumbnail:item.thumbnail,
      g:G[i%G.length]
    };
  });
  S.queue=mapped.slice(1);
  playTrack(mapped[0],name);
}

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
