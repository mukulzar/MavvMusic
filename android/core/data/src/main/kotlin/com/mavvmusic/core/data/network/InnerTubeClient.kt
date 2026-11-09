package com.mavvmusic.core.data.network

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val innerTubeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}

@Singleton
class InnerTubeClient @Inject constructor() {
    val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(innerTubeJson)
        }
    }
    val innerTube: InnerTube = InnerTube(httpClient)
    val musicClient: YouTubeClient = YouTubeClient.WEB_REMIX
    val json: Json = innerTubeJson
}
