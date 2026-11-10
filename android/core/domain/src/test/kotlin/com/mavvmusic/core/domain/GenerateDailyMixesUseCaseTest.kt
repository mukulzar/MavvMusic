package com.mavvmusic.core.domain

import com.mavvmusic.core.model.Album
import com.mavvmusic.core.model.Artist
import com.mavvmusic.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateDailyMixesUseCaseTest {
    @Test
    fun `generates one bounded mix per top artist with seed first`() {
        runBlocking {
            val seeds = listOf(track("seed-1", "Artist One"), track("seed-2", "Artist Two"))
            val history = FakeHistory(seeds.mapIndexed { index, track ->
                ListeningArtistProfile(track.artist.name, track.id, 5 - index)
            }, seeds)
            val catalog = FakeCatalog { seedId ->
                listOf(track("radio-$seedId", "Radio Artist"), seeds.first())
            }

            val result = GenerateDailyMixesUseCase(history, catalog)()

            assertEquals(listOf("Mix 1: Artist One", "Mix 2: Artist Two"), result.playlists.map { it.title })
            assertEquals(seeds.first(), result.playlists.first().tracks.first())
            assertEquals(listOf("seed-1", "radio-seed-1"), result.playlists.first().tracks.map { it.id })
            assertTrue(result.warnings.isEmpty())
        }
    }

    @Test
    fun `keeps a seed-only mix when its radio request fails`() {
        runBlocking {
            val seed = track("seed-1", "Artist One")
            val history = FakeHistory(
                artists = listOf(ListeningArtistProfile("Artist One", seed.id, 4)),
                tracks = listOf(seed),
            )
            val catalog = FakeCatalog { throw IllegalStateException("network unavailable") }

            val result = GenerateDailyMixesUseCase(history, catalog)()

            assertEquals(listOf(seed), result.playlists.single().tracks)
            assertEquals(1, result.warnings.size)
        }
    }

    @Test(expected = CancellationException::class)
    fun `propagates cancellation instead of producing fallback mixes`() {
        runBlocking {
            val seed = track("seed-1", "Artist One")
            val history = FakeHistory(
                artists = listOf(ListeningArtistProfile("Artist One", seed.id, 4)),
                tracks = listOf(seed),
            )
            val catalog = FakeCatalog { throw CancellationException("cancelled") }

            GenerateDailyMixesUseCase(history, catalog)()
        }
    }

    private class FakeHistory(
        private val artists: List<ListeningArtistProfile>,
        tracks: List<Track>,
    ) : ListeningHistoryRepository {
        private val tracksById = tracks.associateBy(Track::id)

        override suspend fun topArtistsThisWeek(limit: Int) = artists.take(limit)
        override suspend fun topGenresThisWeek(limit: Int) = emptyList<ListeningGenreProfile>()
        override suspend fun findTrack(trackId: String) = tracksById[trackId]
        override suspend fun recordTrackPlayed(track: Track, timestampMs: Long) = Unit
        override suspend fun clearHistory() = Unit
    }

    private class FakeCatalog(
        private val radio: suspend (String) -> List<Track>,
    ) : MusicCatalogRepository {
        override suspend fun getHomeFeed() = emptyList<Track>()
        override suspend fun search(query: String) = emptyList<Track>()
        override suspend fun getTrack(trackId: String): Track? = null
        override suspend fun getRadioQueue(seedVideoId: String) = radio(seedVideoId)
    }

    private companion object {
        fun track(id: String, artistName: String) = Track(
            id = id,
            title = id,
            artist = Artist(id, artistName),
            album = Album(id, id, null),
            durationMs = 180_000,
            artworkUri = null,
        )
    }
}
