package com.mavvmusic.core.data

import com.mavvmusic.core.domain.MusicCatalogRepository
import com.mavvmusic.core.domain.ResolvedStream
import com.mavvmusic.core.domain.StreamResolver
import com.mavvmusic.core.model.Album
import com.mavvmusic.core.model.Artist
import com.mavvmusic.core.model.Track

class LocalMockProvider : MusicCatalogRepository, StreamResolver {
    private val artist = Artist(id = "nova-rue", name = "Nova Rue")

    private val tracks = listOf(
        Track(
            id = "blue-hours",
            title = "Blue Hours",
            artist = artist,
            album = Album("soft-focus", "Soft Focus", BLUE_HOURS_ART),
            durationMs = 222_000,
            artworkUri = BLUE_HOURS_ART,
        ),
        Track(
            id = "afterglow",
            title = "Afterglow",
            artist = artist,
            album = Album("afterglow", "Afterglow", AFTERGLOW_ART),
            durationMs = 198_000,
            artworkUri = AFTERGLOW_ART,
        ),
        Track(
            id = "neon-pulse",
            title = "Neon Pulse",
            artist = Artist("the-still", "The Still"),
            album = Album("neon-pulse", "Neon Pulse", NEON_ART),
            durationMs = 214_000,
            artworkUri = NEON_ART,
        ),
        Track(
            id = "night-shift",
            title = "Night Shift",
            artist = Artist("mira-vale", "Mira Vale"),
            album = Album("night-shift", "Night Shift", NIGHT_ART),
            durationMs = 241_000,
            artworkUri = NIGHT_ART,
        ),
        Track(
            id = "soft-focus",
            title = "Soft Focus",
            artist = artist,
            album = Album("soft-focus", "Soft Focus", BLUE_HOURS_ART),
            durationMs = 248_000,
            artworkUri = BLUE_HOURS_ART,
        ),
        Track(
            id = "long-way-home",
            title = "Long Way Home",
            artist = Artist("the-still", "The Still"),
            album = Album("night-shift", "Night Shift", NIGHT_ART),
            durationMs = 231_000,
            artworkUri = NIGHT_ART,
        ),
    )

    override suspend fun getHomeFeed(): List<Track> = tracks

    override suspend fun search(query: String): List<Track> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return tracks.filter { track ->
            track.title.contains(needle, ignoreCase = true) ||
                track.artist.name.contains(needle, ignoreCase = true) ||
                track.album.title.contains(needle, ignoreCase = true)
        }
    }

    override suspend fun getTrack(trackId: String): Track? =
        tracks.firstOrNull { it.id == trackId }

    override suspend fun getRadioQueue(seedVideoId: String): List<Track> {
        val seedIndex = tracks.indexOfFirst { it.id == seedVideoId }
        if (seedIndex < 0) return emptyList()
        return (tracks.drop(seedIndex + 1) + tracks.take(seedIndex)).take(20)
    }

    override suspend fun resolve(trackId: String): ResolvedStream {
        requireNotNull(getTrack(trackId)) { "Track $trackId is not available in the local demo catalog." }
        return ResolvedStream(
            uri = DEMO_AUDIO_URI,
            mimeType = "audio/wav",
            bitrate = 1_411_200,
            expiresAtMs = null,
        )
    }

    private companion object {
        const val DEMO_AUDIO_URI = "android.resource://com.mavvmusic/raw/mavv_demo"
        const val BLUE_HOURS_ART =
            "https://images.unsplash.com/photo-1706821177425-cecc34309a94?auto=format&fit=crop&w=400&q=85"
        const val AFTERGLOW_ART =
            "https://images.unsplash.com/photo-1706821177427-26ba27bd43b7?auto=format&fit=crop&w=500&q=85"
        const val NEON_ART =
            "https://images.unsplash.com/photo-1773592617122-12b44bd81ffd?auto=format&fit=crop&w=500&q=85"
        const val NIGHT_ART =
            "https://images.unsplash.com/photo-1792963762172-5f333e733ec9?auto=format&fit=crop&w=500&q=85"
    }
}
