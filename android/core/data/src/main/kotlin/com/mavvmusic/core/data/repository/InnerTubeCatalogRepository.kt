package com.mavvmusic.core.data.repository

import android.util.Log
import com.mavvmusic.core.domain.MusicCatalogRepository
import com.mavvmusic.core.model.Album
import com.mavvmusic.core.model.Artist
import com.mavvmusic.core.model.Track
import com.mavvmusic.core.data.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class InnerTubeCatalogRepository @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MusicCatalogRepository {
    override suspend fun search(query: String): List<Track> = withContext(ioDispatcher) {
        try {
            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()
            val result = extractor.initialPage
            Log.d("MavvMusicDebug", "Raw items found: ${result.items.size}")

            val tracks = result.items.mapNotNull { item ->
                val streamItem = item as? StreamInfoItem ?: return@mapNotNull null
                val url = streamItem.url
                if (url.contains("watch?v=")) {
                    val videoId = url.substringAfter("watch?v=").substringBefore("&")
                    val artistName = streamItem.uploaderName ?: "Unknown Artist"
                    val artworkUri = streamItem.thumbnails.firstOrNull()?.url
                    Track(
                        id = videoId,
                        title = streamItem.name,
                        artist = Artist(id = artistName, name = artistName),
                        album = Album(id = videoId, title = streamItem.name, artworkUri = artworkUri),
                        durationMs = streamItem.duration * 1000,
                        artworkUri = artworkUri,
                    )
                } else {
                    null
                }
            }
            Log.d("MavvMusicDebug", "Successfully mapped tracks: ${tracks.size}")
            tracks
        } catch (e: SearchExtractor.NothingFoundException) {
            Log.w("MavvMusicError", "NewPipe search returned no results: ${e.message}", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("MavvMusicError", "NewPipe failed: ${e.message}", e)
            throw e
        }
    }

    override suspend fun getTrack(trackId: String): Track? =
        search(trackId).firstOrNull { it.id == trackId }

    override suspend fun getHomeFeed(): List<Track> {
        return search("Top Hits")
    }
}