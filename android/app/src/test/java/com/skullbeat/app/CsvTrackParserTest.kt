package com.skullbeat.app

import com.skullbeat.app.domain.util.CsvTrackParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CsvTrackParserTest {

    @Test
    fun parseCsvWithStandardHeaders() {
        val csv = """
            Title,Artist
            "Starboy",The Weeknd
            Midnight City,M83
        """.trimIndent()

        val result = CsvTrackParser.parse(csv)
        assertEquals(2, result.size)
        assertEquals("Starboy", result[0].title)
        assertEquals("The Weeknd", result[0].artist)
        assertEquals("Midnight City", result[1].title)
        assertEquals("M83", result[1].artist)
    }

    @Test
    fun parseCsvWithSpotifyExportHeaders() {
        val csv = """
            Track Name,Artist Name(s),Album Name
            Resonance,HOME,Odyssey
            Memory Reboot,VOJ; Narvent,Memory Reboot
        """.trimIndent()

        val result = CsvTrackParser.parse(csv)
        assertEquals(2, result.size)
        assertEquals("Resonance", result[0].title)
        assertEquals("HOME", result[0].artist)
        assertEquals("Memory Reboot", result[1].title)
        assertEquals("VOJ, Narvent", result[1].artist)
    }

    @Test
    fun parseCsvWithRepeatedHeaders() {
        val csv = """
            Track URI,ISRC,Track Name,Album Name,Artist Name(s)
            spotify:track:1,US1,"Song A","Album A","Artist A"
            Track URI,ISRC,Track Name,Album Name,Artist Name(s)
            spotify:track:2,US2,"Song B","Album B","Artist B;Artist C"
        """.trimIndent()

        val result = CsvTrackParser.parse(csv)
        assertEquals(2, result.size)
        assertEquals("Song A", result[0].title)
        assertEquals("Artist A", result[0].artist)
        assertEquals("Song B", result[1].title)
        assertEquals("Artist B, Artist C", result[1].artist)
    }

    @Test
    fun parseCsvWithoutHeaders() {
        val csv = """
            After Dark,Mr.Kitty
            Metamorphosis,INTERWORLD
        """.trimIndent()

        val result = CsvTrackParser.parse(csv)
        assertEquals(2, result.size)
        assertEquals("After Dark", result[0].title)
        assertEquals("Mr.Kitty", result[0].artist)
    }

    @Test
    fun parseSingleColumnSongList() {
        val csv = """
            Song Title
            Song One
            Song Two
            Song Three
        """.trimIndent()

        val result = CsvTrackParser.parse(csv)
        assertEquals(3, result.size)
        assertEquals("Song One", result[0].title)
        assertEquals("", result[0].artist)
        assertEquals("Song Three", result[2].title)
    }

    @Test
    fun parseQuotedCommas() {
        val csv = """
            "Theme, Part 1",Artist Name
        """.trimIndent()

        val result = CsvTrackParser.parse(csv)
        assertEquals(1, result.size)
        assertEquals("Theme, Part 1", result[0].title)
        assertEquals("Artist Name", result[0].artist)
    }

    @Test
    fun parseSpotifyRealWorldSample() {
        val header = "Track URI,ISRC,Track Name,Album Name,Artist Name(s),Release Date,Duration (ms)"
        val row1 = "spotify:track:4ZLzoOkj0MPWrTLvooIuaa,USSM11808093,\"Get You The Moon (feat. Snow)\",\"Get You The Moon (feat. Snow)\",\"Kina;Snow\",2018-10-08,179080"
        val row2 = "spotify:track:0xpeiTGSEchBhqrjDuIcSS,INT202404917,\"Angu Vaana Konilu - From \"\"ARM\"\"\",\"Angu Vaana Konilu (From \"\"ARM\"\")\",\"Dhibu Ninan Thomas;Vaikom Vijayalakshmi;Manu Manjith\",2024-09-14,249000"
        val csv = listOf(header, row1, row2).joinToString("\n")

        val result = CsvTrackParser.parse(csv)
        assertEquals(2, result.size)
        assertEquals("Get You The Moon (feat. Snow)", result[0].title)
        assertEquals("Kina, Snow", result[0].artist)
        assertEquals("Angu Vaana Konilu - From \"ARM\"", result[1].title)
        assertEquals("Dhibu Ninan Thomas, Vaikom Vijayalakshmi, Manu Manjith", result[1].artist)
    }

    @Test
    fun parseAllUploadedUserCsvFiles() {
        val files = listOf(
            "C:/Users/karth/.gemini/antigravity/brain/d0d72f49-18eb-4d64-a1b3-268c935f9f75/.user_uploaded/media_1794165818229_00d59560.csv",
            "C:/Users/karth/.gemini/antigravity/brain/d0d72f49-18eb-4d64-a1b3-268c935f9f75/.user_uploaded/media_1794165818261_db46f823.csv",
            "C:/Users/karth/.gemini/antigravity/brain/d0d72f49-18eb-4d64-a1b3-268c935f9f75/.user_uploaded/media_1794165818262_f1ae39c3.csv",
            "C:/Users/karth/.gemini/antigravity/brain/d0d72f49-18eb-4d64-a1b3-268c935f9f75/.user_uploaded/media_1794165818259_8f30781c.csv",
            "C:/Users/karth/.gemini/antigravity/brain/d0d72f49-18eb-4d64-a1b3-268c935f9f75/.user_uploaded/media_1794165818230_97305fa4.csv"
        )

        for (path in files) {
            val file = File(path)
            if (file.exists()) {
                val parsed = CsvTrackParser.parse(file.inputStream())
                assertTrue("File $path should have parsed songs, got ${parsed.size}", parsed.isNotEmpty())
                for (row in parsed) {
                    assertTrue("Title should not be blank: '${row.title}'", row.title.isNotBlank())
                    assertTrue("Title should not be header: '${row.title}'", !row.title.equals("Track Name", ignoreCase = true))
                }
            }
        }
    }
}
