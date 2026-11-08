#!/usr/bin/env python3
"""
YouTube Music Account Filter & Ad-Free Streamer
Filters songs, albums, and artists from your YouTube Music account.
"""

import argparse
import csv
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

try:
    from ytmusicapi import YTMusic, setup_oauth
    from ytmusicapi.setup import setup_browser
except ImportError:
    print("ytmusicapi not found. Install it with: pip install ytmusicapi")
    sys.exit(1)

AUTH_OAUTH_FILE = "oauth.json"
AUTH_HEADERS_FILE = "headers_auth.json"


def get_auth_file() -> Optional[str]:
    """Finds available auth configuration."""
    if Path(AUTH_OAUTH_FILE).is_file():
        return AUTH_OAUTH_FILE
    if Path(AUTH_HEADERS_FILE).is_file():
        return AUTH_HEADERS_FILE
    return None


def run_setup():
    """Runs interactive authentication setup."""
    print("=" * 60)
    print("YouTube Music Authentication Setup")
    print("=" * 60)
    print("Choose an authentication method:")
    print("1. Browser Headers (Recommended - easiest, no Google Cloud needed)")
    print("2. Google OAuth (Requires Google Cloud OAuth Client ID & Secret)")
    choice = input("\nEnter choice [1 or 2]: ").strip()

    if choice == "2":
        print("\n--- Google Cloud OAuth Setup ---")
        client_id = input("Enter OAuth Client ID: ").strip()
        client_secret = input("Enter OAuth Client Secret: ").strip()
        if not client_id or not client_secret:
            print("Client ID and Client Secret are required.")
            return False
        try:
            print("\nStarting OAuth device flow. Open your browser when prompted...")
            setup_oauth(
                client_id=client_id,
                client_secret=client_secret,
                filepath=AUTH_OAUTH_FILE,
                open_browser=True,
            )
            print(f"OAuth credentials successfully saved to '{AUTH_OAUTH_FILE}'!")
            return True
        except Exception as e:
            print(f"OAuth setup failed: {e}")
            return False
    else:
        print("\n--- Browser Headers Setup ---")
        print("1. Open Brave / Chrome and go to: https://music.youtube.com")
        print("2. Open Developer Tools (Press F12 or Ctrl+Shift+I).")
        print("3. Click the 'Network' tab.")
        print("4. Filter by 'browse' or click on any song/playlist on the page.")
        print("5. Right-click any request to 'music.youtube.com' -> Copy -> Copy as cURL (bash) OR Copy request headers.")
        print("6. Paste the copied text below, then press Enter and Ctrl+Z (or Ctrl+D) to finish:\n")
        
        try:
            raw_headers = sys.stdin.read()
            if not raw_headers.strip():
                print("No headers received.")
                return False
            setup_browser(filepath=AUTH_HEADERS_FILE, headers_raw=raw_headers)
            print(f"\nBrowser headers saved to '{AUTH_HEADERS_FILE}'!")
            return True
        except Exception as e:
            print(f"Header setup failed: {e}")
            return False


def get_ytmusic_client() -> YTMusic:
    """Initializes and returns YTMusic instance."""
    auth_file = get_auth_file()
    if not auth_file:
        print("No authentication file found (oauth.json or headers_auth.json).")
        print("Run setup first: python filter_music.py --setup")
        sys.exit(1)
    try:
        return YTMusic(auth_file)
    except Exception as e:
        print(f"Error authenticating with '{auth_file}': {e}")
        print("Please rerun setup: python filter_music.py --setup")
        sys.exit(1)


def normalize_track(item: Dict[str, Any]) -> Dict[str, Any]:
    """Normalizes various YTMusic item formats into a standard track dict."""
    video_id = item.get("videoId") or ""
    title = item.get("title") or "Unknown"

    artists_list = item.get("artists") or []
    if isinstance(artists_list, list):
        artist_names = [a.get("name", "") for a in artists_list if isinstance(a, dict)]
        artist_str = ", ".join(filter(None, artist_names))
    elif isinstance(artists_list, str):
        artist_str = artists_list
    else:
        artist_str = "Unknown"

    album_obj = item.get("album") or {}
    if isinstance(album_obj, dict):
        album_name = album_obj.get("name", "")
    elif isinstance(album_obj, str):
        album_name = album_obj
    else:
        album_name = ""

    duration = item.get("duration") or ""
    duration_seconds = item.get("duration_seconds") or 0

    if not duration_seconds and duration and ":" in duration:
        parts = duration.split(":")
        try:
            if len(parts) == 2:
                duration_seconds = int(parts[0]) * 60 + int(parts[1])
            elif len(parts) == 3:
                duration_seconds = int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
        except ValueError:
            pass

    return {
        "videoId": video_id,
        "title": title,
        "artist": artist_str,
        "album": album_name,
        "duration": duration,
        "duration_seconds": duration_seconds,
        "url": f"https://music.youtube.com/watch?v={video_id}" if video_id else "",
    }


def fetch_tracks(yt: YTMusic, source: str = "liked", limit: int = 100) -> List[Dict[str, Any]]:
    """Fetches tracks based on selected account source."""
    print(f"Fetching from '{source}' (limit: {limit})...")
    raw_tracks = []

    if source == "liked":
        liked = yt.get_liked_songs(limit=limit)
        raw_tracks = liked.get("tracks", [])
    elif source == "library":
        raw_tracks = yt.get_library_songs(limit=limit)
    elif source == "history":
        raw_tracks = yt.get_history()
    elif source == "playlists":
        playlists = yt.get_library_playlists(limit=25)
        if not playlists:
            print("No library playlists found.")
            return []
        print("\nAvailable Playlists:")
        for idx, pl in enumerate(playlists):
            print(f"  [{idx + 1}] {pl.get('title')} ({pl.get('count', 0)} tracks)")
        sel = input("Select playlist number: ").strip()
        try:
            pl_idx = int(sel) - 1
            pl_id = playlists[pl_idx]["playlistId"]
            pl_data = yt.get_playlist(pl_id, limit=limit)
            raw_tracks = pl_data.get("tracks", [])
        except (ValueError, IndexError):
            print("Invalid playlist selection.")
            return []
    else:
        print(f"Unknown source: {source}")
        return []

    return [normalize_track(t) for t in raw_tracks if t.get("videoId")]


def apply_filters(
    tracks: List[Dict[str, Any]],
    artist: Optional[str] = None,
    title: Optional[str] = None,
    album: Optional[str] = None,
    query: Optional[str] = None,
    min_duration: Optional[int] = None,
    max_duration: Optional[int] = None,
) -> List[Dict[str, Any]]:
    """Filters track list based on user criteria."""
    filtered = []

    for t in tracks:
        if artist and artist.lower() not in t["artist"].lower():
            continue
        if title and title.lower() not in t["title"].lower():
            continue
        if album and album.lower() not in t["album"].lower():
            continue
        if query:
            q = query.lower()
            if q not in t["title"].lower() and q not in t["artist"].lower() and q not in t["album"].lower():
                continue
        if min_duration is not None and t["duration_seconds"] < min_duration:
            continue
        if max_duration is not None and t["duration_seconds"] > max_duration:
            continue
        filtered.append(t)

    return filtered


def display_tracks(tracks: List[Dict[str, Any]]):
    """Prints a clean ASCII table of tracks."""
    if not tracks:
        print("No tracks match criteria.")
        return

    print(f"\nFound {len(tracks)} matching track(s):\n")
    header = f"{'#':<4} {'Title':<35} {'Artist':<25} {'Duration':<10} {'Video ID':<15}"
    print(header)
    print("-" * len(header))

    for idx, t in enumerate(tracks, 1):
        title = (t["title"][:32] + "..") if len(t["title"]) > 34 else t["title"]
        artist = (t["artist"][:22] + "..") if len(t["artist"]) > 24 else t["artist"]
        dur = t["duration"] or f"{t['duration_seconds']}s"
        vid = t["videoId"]
        print(f"{idx:<4} {title:<35} {artist:<25} {dur:<10} {vid:<15}")


def export_tracks(tracks: List[Dict[str, Any]], filename: str):
    """Exports filtered tracks to JSON, CSV, or M3U."""
    ext = Path(filename).suffix.lower()
    if ext == ".json":
        with open(filename, "w", encoding="utf-8") as f:
            json.dump(tracks, f, indent=2, ensure_ascii=False)
    elif ext == ".csv":
        with open(filename, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(
                f,
                fieldnames=["videoId", "title", "artist", "album", "duration", "duration_seconds", "url"],
            )
            writer.writeheader()
            writer.writerows(tracks)
    elif ext == ".m3u":
        with open(filename, "w", encoding="utf-8") as f:
            f.write("#EXTM3U\n")
            for t in tracks:
                dur = t["duration_seconds"] or -1
                f.write(f"#EXTINF:{dur},{t['artist']} - {t['title']}\n")
                f.write(f"{t['url']}\n")
    else:
        print(f"Unsupported format '{ext}'. Use .json, .csv, or .m3u")
        return

    print(f"Exported {len(tracks)} track(s) to '{filename}'.")


def get_audio_stream_url(video_id: str) -> Optional[str]:
    """Uses yt-dlp to extract the raw, direct ad-free audio stream URL."""
    url = f"https://www.youtube.com/watch?v={video_id}"
    cmd = ["yt-dlp", "-g", "-f", "bestaudio", url]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return proc.stdout.strip().split("\n")[-1]
    except Exception as e:
        print(f"Failed to fetch audio stream for {video_id}: {e}")
        return None


def play_track(video_id: str, title: str):
    """Plays audio stream directly with ffplay (no download, no ads)."""
    print(f"\nFetching direct ad-free audio stream for '{title}'...")
    stream_url = get_audio_stream_url(video_id)
    if not stream_url:
        print("Could not retrieve audio stream.")
        return

    print(f"Streaming now via ffplay (Press 'q' in ffplay window or Ctrl+C to stop)...")
    try:
        subprocess.run(["ffplay", "-nodisp", "-autoexit", "-loglevel", "error", stream_url])
    except KeyboardInterrupt:
        print("\nPlayback stopped.")


def main():
    parser = argparse.ArgumentParser(
        description="Filter and stream ad-free music from your YouTube Music account."
    )
    parser.add_argument("--setup", action="store_true", help="Run interactive authentication setup")
    parser.add_argument(
        "--source",
        choices=["liked", "library", "history", "playlists"],
        default="liked",
        help="Account music source (default: liked)",
    )
    parser.add_argument("--limit", type=int, default=100, help="Maximum tracks to fetch (default: 100)")
    parser.add_argument("--artist", help="Filter by artist name")
    parser.add_argument("--title", help="Filter by song title")
    parser.add_argument("--album", help="Filter by album name")
    parser.add_argument("--query", "-q", help="Search in title, artist, or album")
    parser.add_argument("--min-duration", type=int, help="Minimum duration in seconds")
    parser.add_argument("--max-duration", type=int, help="Maximum duration in seconds")
    parser.add_argument("--export", help="Export filtered tracks to file (.json, .csv, .m3u)")
    parser.add_argument("--stream-url", type=int, help="Print direct audio stream URL for track # from list")
    parser.add_argument("--play", type=int, help="Stream ad-free audio for track # from list using ffplay")

    args = parser.parse_args()

    if args.setup:
        run_setup()
        return

    auth_file = get_auth_file()
    if not auth_file:
        print("No YouTube credentials found. Launching setup wizard...\n")
        if not run_setup():
            return

    yt = get_ytmusic_client()
    tracks = fetch_tracks(yt, source=args.source, limit=args.limit)

    filtered = apply_filters(
        tracks,
        artist=args.artist,
        title=args.title,
        album=args.album,
        query=args.query,
        min_duration=args.min_duration,
        max_duration=args.max_duration,
    )

    display_tracks(filtered)

    if args.export:
        export_tracks(filtered, args.export)

    if args.stream_url:
        idx = args.stream_url - 1
        if 0 <= idx < len(filtered):
            t = filtered[idx]
            url = get_audio_stream_url(t["videoId"])
            print(f"\nDirect Audio Stream URL for '{t['title']}':\n{url}")
        else:
            print("Invalid track number for --stream-url.")

    if args.play:
        idx = args.play - 1
        if 0 <= idx < len(filtered):
            t = filtered[idx]
            play_track(t["videoId"], t["title"])
        else:
            print("Invalid track number for --play.")


if __name__ == "__main__":
    main()
