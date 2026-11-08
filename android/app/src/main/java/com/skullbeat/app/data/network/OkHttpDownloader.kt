package com.skullbeat.app.data.network

import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val body = when {
            dataToSend != null -> dataToSend.toRequestBody(null)
            httpMethod.equals("POST", ignoreCase = true) ||
            httpMethod.equals("PUT", ignoreCase = true) ||
            httpMethod.equals("PATCH", ignoreCase = true) -> ByteArray(0).toRequestBody(null)
            else -> null
        }

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, body)

        for ((key, values) in headers) {
            for (value in values) {
                requestBuilder.addHeader(key, value)
            }
        }

        if (requestBuilder.build().header("User-Agent") == null) {
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }

        val okHttpResponse = client.newCall(requestBuilder.build()).execute()
        val responseBody = okHttpResponse.body?.string().orEmpty()
        val latestUrl = okHttpResponse.request.url.toString()
        val responseCode = okHttpResponse.code
        val responseMessage = okHttpResponse.message
        val responseHeaders = okHttpResponse.headers.toMultimap()

        return Response(responseCode, responseMessage, responseHeaders, responseBody, latestUrl)
    }
}
