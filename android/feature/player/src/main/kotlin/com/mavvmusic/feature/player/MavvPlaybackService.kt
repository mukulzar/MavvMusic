package com.mavvmusic.feature.player

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mavvmusic.core.domain.StreamResolver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MavvPlaybackService : MediaSessionService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var playbackStateStore: PlaybackStateStore
    private lateinit var streamResolver: StreamResolver
    private lateinit var mediaCache: Cache
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var streamRefreshJob: Job? = null
    private var activeTrackId: String? = null
    private var activeCacheOnPlay = false
    private val refreshedStreamUris = mutableSetOf<String>()

    private val positionTicker = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                playbackStateStore.updatePosition(
                    positionMs = player.currentPosition,
                    durationMs = player.duration,
                    isPlaying = true,
                )
                mainHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playbackStateStore.updatePosition(player.currentPosition, player.duration, isPlaying)
            mainHandler.removeCallbacks(positionTicker)
            if (isPlaying) mainHandler.post(positionTicker)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            playbackStateStore.updatePosition(
                player.currentPosition,
                player.duration,
                player.isPlaying,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.isForbiddenHttpStatus() && refreshExpiredStream()) return
            playbackStateStore.reportError(
                error.localizedMessage ?: "Playback failed. Try playing this track again.",
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        playbackStateStore = EntryPointAccessors.fromApplication(
            applicationContext,
            PlaybackServiceEntryPoint::class.java,
        ).also { entryPoint ->
            streamResolver = entryPoint.streamResolver()
            mediaCache = entryPoint.mediaCache()
        }.playbackStateStore()
        player = ExoPlayer.Builder(this).build().apply { addListener(listener) }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> playFromIntent(intent)
            ACTION_PAUSE -> player.pause()
            ACTION_RESUME -> player.play()
            ACTION_SEEK -> player.seekTo(intent.getLongExtra(EXTRA_POSITION_MS, 0L))
            ACTION_SKIP -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(positionTicker)
        streamRefreshJob?.cancel()
        serviceScope.cancel()
        mediaSession?.release()
        if (::player.isInitialized) {
            player.removeListener(listener)
            player.release()
        }
        super.onDestroy()
    }

    private fun playFromIntent(intent: Intent) {
        val uri = requireNotNull(intent.getStringExtra(EXTRA_URI)) {
            "The player service received no audio URI."
        }
        activeTrackId = intent.getStringExtra(EXTRA_TRACK_ID)
        activeCacheOnPlay = intent.getBooleanExtra(EXTRA_CACHE_ON_PLAY, false)
        streamRefreshJob?.cancel()
        streamRefreshJob = null
        refreshedStreamUris.clear()
        val metadata = MediaMetadata.Builder()
            .setTitle(intent.getStringExtra(EXTRA_TITLE).orEmpty())
            .setArtist(intent.getStringExtra(EXTRA_ARTIST).orEmpty())
            .setAlbumTitle(intent.getStringExtra(EXTRA_ALBUM).orEmpty())
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
        val mediaItem = MediaItem.Builder()
                .setMediaId(activeTrackId.orEmpty())
                .setUri(uri)
                .setMediaMetadata(metadata)
                .setCustomCacheKey(activeTrackId.orEmpty())
                .build()
        player.setMediaSource(createMediaSource(mediaItem))
        player.prepare()
        player.play()
    }

    private fun refreshExpiredStream(): Boolean {
        val trackId = activeTrackId ?: return false
        val currentItem = player.currentMediaItem ?: return false
        val expiredUri = currentItem.localConfiguration?.uri?.toString() ?: return false
        if (streamRefreshJob?.isActive == true) return true
        if (!refreshedStreamUris.add(expiredUri)) return false

        val positionMs = player.currentPosition.coerceAtLeast(0)
        val wasPlaying = player.playWhenReady
        streamRefreshJob = serviceScope.launch {
            try {
                val refreshedStream = withContext(Dispatchers.IO) {
                    streamResolver.resolve(trackId)
                }
                if (!isActive || activeTrackId != trackId) return@launch
                val itemToRefresh = player.currentMediaItem ?: return@launch
                val replacement = itemToRefresh.buildUpon()
                    .setUri(refreshedStream.uri)
                    .build()
                player.setMediaSource(createMediaSource(replacement), positionMs)
                player.prepare()
                if (wasPlaying) player.play() else player.pause()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (activeTrackId == trackId) {
                    playbackStateStore.reportError(
                        error.message ?: "The expired stream could not be refreshed.",
                    )
                }
            }
        }
        return true
    }

    private fun PlaybackException.isForbiddenHttpStatus(): Boolean {
        if (errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return false
        return generateSequence(cause) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .any { it.responseCode == HTTP_FORBIDDEN }
    }

    private fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        val useCache = activeCacheOnPlay &&
            (uri?.scheme.equals("http", ignoreCase = true) ||
                uri?.scheme.equals("https", ignoreCase = true))
        val upstreamFactory = DefaultDataSource.Factory(this)
        val dataSourceFactory: DataSource.Factory = if (useCache) {
            CacheDataSource.Factory()
                .setCache(mediaCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstreamFactory
        }
        return DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    companion object {
        const val ACTION_PLAY = "com.mavvmusic.player.PLAY"
        const val ACTION_PAUSE = "com.mavvmusic.player.PAUSE"
        const val ACTION_RESUME = "com.mavvmusic.player.RESUME"
        const val ACTION_SEEK = "com.mavvmusic.player.SEEK"
        const val ACTION_SKIP = "com.mavvmusic.player.SKIP"
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ALBUM = "album"
        const val EXTRA_URI = "uri"
        const val EXTRA_CACHE_ON_PLAY = "cache_on_play"
        const val EXTRA_POSITION_MS = "position_ms"
        private const val HTTP_FORBIDDEN = 403
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}
