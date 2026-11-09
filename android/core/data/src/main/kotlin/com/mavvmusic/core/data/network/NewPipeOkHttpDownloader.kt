package com.mavvmusic.core.data.network

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewPipeOkHttpDownloader @Inject constructor(
    private val httpClient: OkHttpClient,
) : Downloader() {
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val method = request.httpMethod().uppercase()
        val body = request.dataToSend()?.toRequestBody()
            ?: if (method == "GET" || method == "HEAD") null else ByteArray(0).toRequestBody()
        val httpRequest = okhttp3.Request.Builder()
            .url(request.url())
            .apply {
                request.headers().forEach { (name, values) ->
                    values.forEach { value -> addHeader(name, value) }
                }
            }
            .method(method, body)
            .build()

        return httpClient.newCall(httpRequest).execute().use { response ->
            ExtractorResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
