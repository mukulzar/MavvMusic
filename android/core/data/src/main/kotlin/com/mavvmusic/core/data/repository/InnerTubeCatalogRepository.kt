package com.mavvmusic.core.data.repository

import android.util.Log
import com.mavvmusic.core.data.IoDispatcher
import com.mavvmusic.core.data.network.InnerTubeClient
import com.mavvmusic.core.domain.MusicCatalogRepository
import com.mavvmusic.core.model.Album
import com.mavvmusic.core.model.Artist
import com.mavvmusic.core.model.Track
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val SONGS_FILTER = "EgWKAQIIAWoKEAkQChADEAQQBQ%3D%3D"
private const val MUSIC_HOME_BROWSE_ID = "FEmusic_home"
private const val MAX_RADIO_QUEUE_SIZE = 50
private val DURATION_PATTERN = Regex("""(?:\d{1,2}:)?\d{1,2}:\d{2}""")
private val SEPARATOR_TEXT = setOf("•", "·", "-", "|")

@Singleton
public class InnerTubeCatalogRepository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val innerTubeClient: InnerTubeClient,
) : MusicCatalogRepository {
    override suspend fun search(query: String): List<Track> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val response = innerTubeClient.innerTube.search(
                client = innerTubeClient.musicClient,
                query = query,
                params = SONGS_FILTER,
                setLogin = false,
            )
            val root = innerTubeClient.json.parseToJsonElement(response.bodyAsText())
            val renderers = root.musicItemRenderers().toList()
            Log.d("MavvMusicDebug", "Raw music items found: ${renderers.size}")
            val tracks = renderers.mapNotNull(JsonObject::toTrack).distinctBy(Track::id)
            Log.d("MavvMusicDebug", "Successfully mapped tracks: ${tracks.size}")
            tracks
        } catch (e: Exception) {
            Log.e("MavvMusicError", "InnerTubeX music search failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun getTrack(trackId: String): Track? =
        search(trackId).firstOrNull { it.id == trackId }

    override suspend fun getRadioQueue(seedVideoId: String): List<Track> = withContext(ioDispatcher) {
        require(seedVideoId.isNotBlank()) { "A seed track is required to create a radio queue." }
        try {
            val response = innerTubeClient.innerTube.next(
                client = innerTubeClient.musicClient,
                videoId = seedVideoId,
                playlistId = "RDAMVM$seedVideoId",
            )
            val root = innerTubeClient.json.parseToJsonElement(response.bodyAsText())
            root.musicItemRenderers()
                .mapNotNull(JsonObject::toTrack)
                .distinctBy(Track::id)
                .take(MAX_RADIO_QUEUE_SIZE)
                .toList()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.e("MavvMusicError", "InnerTubeX radio request failed.", error)
            throw error
        }
    }

    override suspend fun getHomeFeed(): List<Track> = withContext(ioDispatcher) {
        try {
            val response = innerTubeClient.innerTube.browse(
                client = innerTubeClient.musicClient,
                browseId = MUSIC_HOME_BROWSE_ID,
                setLogin = false,
            )
            val root = innerTubeClient.json.parseToJsonElement(response.bodyAsText())
            val tracks = root.musicItemRenderers()
                .mapNotNull(JsonObject::toTrack)
                .distinctBy(Track::id)
                .toList()
            Log.d("MavvMusicDebug", "YouTube Music home feed tracks: ${tracks.size}")
            tracks
        } catch (e: Exception) {
            Log.e("MavvMusicError", "InnerTubeX home feed failed: ${e.message}", e)
            throw e
        }
    }

}

private fun JsonElement.musicItemRenderers(): Sequence<JsonObject> = sequence {
    when (this@musicItemRenderers) {
        is JsonObject -> {
            val itemRenderer = this@musicItemRenderers["musicResponsiveListItemRenderer"]
                ?: this@musicItemRenderers["musicTwoRowItemRenderer"]
                ?: this@musicItemRenderers["musicPlaylistPanelVideoRenderer"]
                ?: this@musicItemRenderers["playlistPanelVideoRenderer"]
            if (itemRenderer is JsonObject) {
                yield(itemRenderer)
            } else {
                this@musicItemRenderers.values.forEach { yieldAll(it.musicItemRenderers()) }
            }
        }
        is JsonArray -> this@musicItemRenderers.forEach { yieldAll(it.musicItemRenderers()) }
        else -> Unit
    }
}

private fun JsonObject.toTrack(): Track? {
    val trackId = findVideoId() ?: return null
    val columns = this["flexColumns"] as? JsonArray
    val title = columns?.getOrNull(0)?.rendererText()
        ?: this["title"].rendererText()
        ?: return null
    val metadataRuns = columns?.getOrNull(1)?.rendererRuns()
        ?.takeIf { it.isNotEmpty() }
        ?: (
            this["subtitle"].rendererRuns() +
                this["longBylineText"].rendererRuns() +
                this["shortBylineText"].rendererRuns()
            )
    val meaningfulRuns = metadataRuns.filterNot { it.text in SEPARATOR_TEXT || it.text.isBlank() }
    val artistRun = meaningfulRuns.firstOrNull { it.pageType.contains("ARTIST") }
        ?: meaningfulRuns.firstOrNull()
    val albumRun = meaningfulRuns.firstOrNull { it.pageType.contains("ALBUM") }
        ?: meaningfulRuns.getOrNull(1)
    val artistName = artistRun?.text ?: "Unknown Artist"
    val albumTitle = albumRun?.text ?: title
    val artworkUri = findArtworkUri()
    val durationMs = metadataRuns.asSequence()
        .mapNotNull { DURATION_PATTERN.find(it.text)?.value }
        .firstNotNullOfOrNull(::durationTextToMillis)
        ?: this["lengthText"].rendererText()?.let { DURATION_PATTERN.find(it)?.value }
            ?.let(::durationTextToMillis)
        ?: 0L

    return Track(
        id = trackId,
        title = title,
        artist = Artist(
            id = artistRun?.browseId ?: artistName,
            name = artistName,
        ),
        album = Album(
            id = albumRun?.browseId ?: trackId,
            title = albumTitle,
            artworkUri = artworkUri,
        ),
        durationMs = durationMs,
        artworkUri = artworkUri,
        genre = findGenre(),
    )
}

private fun JsonObject.findGenre(): String? {
    fun find(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            element["genre"]?.stringValue()?.trim()?.takeIf(String::isNotEmpty)
                ?: element["genreName"]?.stringValue()?.trim()?.takeIf(String::isNotEmpty)
                ?: element.values.firstNotNullOfOrNull(::find)
        }
        is JsonArray -> element.firstNotNullOfOrNull(::find)
        else -> null
    }
    return find(this)
}

private data class MusicTextRun(
    val text: String,
    val browseId: String?,
    val pageType: String,
)

private fun JsonElement?.rendererText(): String? {
    val element = this ?: return null
    val renderer = (element as? JsonObject)
        ?.get("musicResponsiveListItemFlexColumnRenderer")
        ?.jsonObject
        ?: (element as? JsonObject)
    val text = renderer?.get("text") ?: renderer?.get("title") ?: element
    return when (text) {
        is JsonPrimitive -> text.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        is JsonObject -> text["simpleText"]?.stringValue()
            ?: (text["runs"] as? JsonArray)?.runTexts()
                ?.joinToString("") { it.text }?.trim()?.takeIf(String::isNotEmpty)
        else -> null
    }
}

private fun JsonElement?.rendererRuns(): List<MusicTextRun> {
    val element = this as? JsonObject ?: return emptyList()
    val renderer = element["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject ?: element
    val text = renderer["text"]?.jsonObject
        ?: renderer["subtitle"]?.jsonObject
        ?: renderer.takeIf { it["runs"] is JsonArray }
        ?: return emptyList()
    return text["runs"]?.jsonArray?.mapNotNull { runElement ->
        val run = runElement as? JsonObject ?: return@mapNotNull null
        val value = run["text"]?.stringValue()?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val browseEndpoint = run["navigationEndpoint"]?.jsonObject
            ?.get("browseEndpoint")?.jsonObject
        val pageType = browseEndpoint?.get("browseEndpointContextSupportedConfigs")
            ?.jsonObject?.get("browseEndpointContextMusicConfig")
            ?.jsonObject?.get("pageType")?.stringValue().orEmpty()
        MusicTextRun(
            text = value,
            browseId = browseEndpoint?.get("browseId")?.stringValue(),
            pageType = pageType,
        )
    }.orEmpty()
}

private fun JsonObject.findVideoId(): String? {
    fun find(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            val watchEndpointId = element["watchEndpoint"]?.jsonObject
                ?.get("videoId")?.stringValue()
                ?: element["playNavigationEndpoint"]?.jsonObject
                    ?.get("watchEndpoint")?.jsonObject?.get("videoId")?.stringValue()
            watchEndpointId ?: element["videoId"]?.stringValue()
                ?: element.values.firstNotNullOfOrNull(::find)
        }
        is JsonArray -> element.firstNotNullOfOrNull(::find)
        else -> null
    }
    return find(this)
}

private fun JsonObject.findArtworkUri(): String? {
    fun find(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            val thumbnails = element["thumbnails"] as? JsonArray
            val uri = thumbnails?.mapNotNull { (it as? JsonObject)?.get("url")?.stringValue() }
                ?.lastOrNull()
            uri ?: element.values.firstNotNullOfOrNull(::find)
        }
        is JsonArray -> element.firstNotNullOfOrNull(::find)
        else -> null
    }
    return find(this)
}

private fun JsonArray.runTexts(): List<MusicTextRun> = mapNotNull { runElement ->
    val run = runElement as? JsonObject ?: return@mapNotNull null
    val text = run["text"]?.stringValue()?.trim()?.takeIf(String::isNotEmpty)
        ?: return@mapNotNull null
    MusicTextRun(text, null, "")
}

private fun JsonElement.stringValue(): String? =
    (this as? JsonPrimitive)?.contentOrNull

private fun durationTextToMillis(value: String): Long? {
    val parts = value.split(':').map(String::toLongOrNull)
    if (parts.any { it == null } || parts.isEmpty()) return null
    val seconds = parts.last() ?: return null
    val minutes = parts.getOrNull(parts.lastIndex - 1) ?: 0L
    val hours = parts.getOrNull(parts.lastIndex - 2) ?: 0L
    return (hours * 3600 + minutes * 60 + seconds) * 1000
}