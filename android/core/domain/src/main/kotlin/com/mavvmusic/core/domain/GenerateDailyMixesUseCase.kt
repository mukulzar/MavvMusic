package com.mavvmusic.core.domain

import com.mavvmusic.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

data class SmartPlaylist(
    val title: String,
    val tracks: List<Track>,
)

data class DailyMixGeneration(
    val playlists: List<SmartPlaylist>,
    val warnings: List<String>,
)

open class SmartPlaylistGenerator(
    private val history: ListeningHistoryRepository,
    private val catalog: MusicCatalogRepository,
) {
    suspend fun generate(): DailyMixGeneration = supervisorScope {
        val artists = history.topArtistsThisWeek(MAX_MIXES)
        val results = artists.mapIndexed { index, artist ->
            async {
                val seed = history.findTrack(artist.seedTrackId)
                if (seed == null) {
                    return@async MixResult(
                        playlist = null,
                        warning = "A seed track for ${artist.artist} is no longer available.",
                    )
                }

                try {
                    val radioTracks = catalog.getRadioQueue(seed.id)
                    val tracks = (listOf(seed) + radioTracks)
                        .distinctBy(Track::id)
                        .take(MAX_TRACKS_PER_MIX)
                    MixResult(
                        playlist = SmartPlaylist("Mix ${index + 1}: ${artist.artist}", tracks),
                        warning = if (tracks.size == 1) {
                            "Radio for ${artist.artist} returned no new tracks; showing its seed."
                        } else {
                            null
                        },
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    MixResult(
                        playlist = SmartPlaylist("Mix ${index + 1}: ${artist.artist}", listOf(seed)),
                        warning = "Radio for ${artist.artist} could not be refreshed; showing its seed track.",
                    )
                }
            }
        }.map { it.await() }

        DailyMixGeneration(
            playlists = results.mapNotNull(MixResult::playlist),
            warnings = results.mapNotNull(MixResult::warning),
        )
    }

    private data class MixResult(
        val playlist: SmartPlaylist?,
        val warning: String?,
    )

    private companion object {
        const val MAX_MIXES = 3
        const val MAX_TRACKS_PER_MIX = 30
    }
}

class GenerateDailyMixesUseCase(
    history: ListeningHistoryRepository,
    catalog: MusicCatalogRepository,
) : SmartPlaylistGenerator(history, catalog) {
    suspend operator fun invoke(): DailyMixGeneration = generate()
}

class GetRadioQueueUseCase(
    private val catalog: MusicCatalogRepository,
) {
    suspend operator fun invoke(seedVideoId: String): List<Track> {
        require(seedVideoId.isNotBlank()) { "A track is required to start a radio mix." }
        return catalog.getRadioQueue(seedVideoId)
    }
}
