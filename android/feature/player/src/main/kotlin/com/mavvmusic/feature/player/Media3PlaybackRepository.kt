package com.mavvmusic.feature.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.mavvmusic.core.domain.PlaybackRepository
import com.mavvmusic.core.model.CacheStatus
import com.mavvmusic.core.model.PlaybackState
import com.mavvmusic.core.model.Track
import kotlinx.coroutines.flow.StateFlow

class Media3PlaybackRepository(
    context: Context,
    private val stateStore: PlaybackStateStore,
) : PlaybackRepository {
    private val appContext = context.applicationContext
    override val state: StateFlow<PlaybackState> = stateStore.state

    override suspend fun play(track: Track, playableUri: String, cacheStatus: CacheStatus) {
        stateStore.setCurrent(
            PlaybackState(
                currentTrack = track,
                isPlaying = false,
                durationMs = track.durationMs,
                cacheStatus = cacheStatus,
            ),
        )
        val intent = serviceIntent(MavvPlaybackService.ACTION_PLAY)
            .putExtra(MavvPlaybackService.EXTRA_TRACK_ID, track.id)
            .putExtra(MavvPlaybackService.EXTRA_TITLE, track.title)
            .putExtra(MavvPlaybackService.EXTRA_ARTIST, track.artist.name)
            .putExtra(MavvPlaybackService.EXTRA_ALBUM, track.album.title)
            .putExtra(MavvPlaybackService.EXTRA_URI, playableUri)
        ContextCompat.startForegroundService(appContext, intent)
    }

    override fun pause() {
        appContext.startService(serviceIntent(MavvPlaybackService.ACTION_PAUSE))
    }

    override fun resume() {
        appContext.startService(serviceIntent(MavvPlaybackService.ACTION_RESUME))
    }

    override fun seek(positionMs: Long) {
        appContext.startService(
            serviceIntent(MavvPlaybackService.ACTION_SEEK)
                .putExtra(MavvPlaybackService.EXTRA_POSITION_MS, positionMs.coerceAtLeast(0)),
        )
    }

    override fun skipToNext() {
        appContext.startService(serviceIntent(MavvPlaybackService.ACTION_SKIP))
    }

    private fun serviceIntent(action: String) =
        Intent(appContext, MavvPlaybackService::class.java).setAction(action)
}
