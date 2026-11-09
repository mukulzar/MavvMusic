package com.mavvmusic.core.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class OkHttpDownloader(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val data = request.dataToSend()

        val builder = Request.Builder().url(url).method(
            httpMethod,
            if (httpMethod == "POST") RequestBody.create(null, data ?: ByteArray(0)) else null
        )

        headers.forEach { (key, values) -> values.forEach { value -> builder.addHeader(key, value) } }

        val response = client.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
            .newCall(builder.build())
            .execute()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body?.string(),
            response.request.url.toString()
        )
    }
}