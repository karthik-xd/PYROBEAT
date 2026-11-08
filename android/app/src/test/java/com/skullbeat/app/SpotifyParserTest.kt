package com.skullbeat.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyParserTest {

    @Test
    fun parseSpotifyLink_playlist() {
        val url = "https://open.spotify.com/playlist/37i9dQZF1DXdLEN7aqioXM?si=123"
        val match = Regex("(playlist|album|track)[/:]([a-zA-Z0-9]+)").find(url)
        org.junit.Assert.assertNotNull(match)
        assertEquals("playlist", match!!.groupValues[1])
        assertEquals("37i9dQZF1DXdLEN7aqioXM", match.groupValues[2])
    }

    @Test
    fun parseSpotifyLink_track() {
        val url = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"
        val match = Regex("(playlist|album|track)[/:]([a-zA-Z0-9]+)").find(url)
        org.junit.Assert.assertNotNull(match)
        assertEquals("track", match!!.groupValues[1])
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", match.groupValues[2])
    }

    @Test
    fun parseSampleNextDataHtml() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
            <script id="__NEXT_DATA__" type="application/json">
            {
              "props": {
                "pageProps": {
                  "state": {
                    "data": {
                      "entity": {
                        "name": "Synthwave Chill",
                        "trackList": [
                          { "title": "Memory Reboot", "subtitle": "VØJ, Narvent" },
                          { "title": "Nightcall", "subtitle": "Kavinsky" }
                        ]
                      }
                    }
                  }
                }
              }
            }
            </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val nextDataMatch = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(sampleHtml)
        org.junit.Assert.assertNotNull(nextDataMatch)

        val json = JSONObject(nextDataMatch!!.groupValues[1])
        val entity = json.getJSONObject("props").getJSONObject("pageProps").getJSONObject("state").getJSONObject("data").getJSONObject("entity")
        val title = entity.getString("name")
        val trackList = entity.getJSONArray("trackList")

        assertEquals("Synthwave Chill", title)
        assertEquals(2, trackList.length())
        assertEquals("Memory Reboot", trackList.getJSONObject(0).getString("title"))
        assertEquals("VØJ, Narvent", trackList.getJSONObject(0).getString("subtitle"))
    }
}
