package com.skullbeat.app.domain.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.skullbeat.app.domain.models.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object TrackExporter {

    fun toM3u(tracks: List<Track>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        for (t in tracks) {
            sb.appendLine("#EXTINF:${t.durationSec},${t.artist} - ${t.title}")
            sb.appendLine("https://www.youtube.com/watch?v=${t.videoId}")
        }
        return sb.toString()
    }

    fun toCsv(tracks: List<Track>): String {
        val sb = StringBuilder()
        sb.appendLine("Title,Artist,Album,Duration,URL")
        for (t in tracks) {
            val title = escapeCsv(t.title)
            val artist = escapeCsv(t.artist)
            val album = escapeCsv(t.album)
            val url = "https://www.youtube.com/watch?v=${t.videoId}"
            sb.appendLine("\"$title\",\"$artist\",\"$album\",${t.durationSec},\"$url\"")
        }
        return sb.toString()
    }

    fun toJson(tracks: List<Track>): String {
        val array = JSONArray()
        for (t in tracks) {
            val obj = JSONObject()
            obj.put("videoId", t.videoId)
            obj.put("title", t.title)
            obj.put("artist", t.artist)
            obj.put("album", t.album)
            obj.put("durationSec", t.durationSec)
            obj.put("durationFormatted", t.durationFormatted)
            obj.put("url", "https://www.youtube.com/watch?v=${t.videoId}")
            array.put(obj)
        }
        return array.toString(2)
    }

    fun shareExport(context: Context, formatName: String, content: String, mimeType: String) {
        val fileName = "skullbeat_export_${System.currentTimeMillis()}.$formatName"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export SkullBeat Playlist"))
    }

    private fun escapeCsv(str: String): String {
        return str.replace("\"", "\"\"")
    }
}
