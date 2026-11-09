package com.mavvmusic.feature.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mavvmusic.core.domain.StreamResolver
import com.mavvmusic.core.model.Album
import com.mavvmusic.core.model.Artist
import com.mavvmusic.core.model.CacheStatus
import com.mavvmusic.core.model.Track
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
import org.json.JSONArray

class MavvPlaybackService : MediaSessionService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var playbackStateStore: PlaybackStateStore
    private lateinit var streamResolver: StreamResolver
    private lateinit var mediaCache: Cache
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    private var playbackSetupJob: Job? = null
    private var streamRefreshJob: Job? = null
    private var activeTrackId: String? = null
    private var activeCachedTrackId: String? = null
    private var activeCacheOnPlay = false
    
    private var activeQueueTracks: Map<String, Track> = emptyMap()
    private var activeQueueIds: List<String> = emptyList()
    private var isPrefetching = false

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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val track = mediaItem?.let { activeQueueTracks[it.mediaId] } ?: return
            activeTrackId = track.id
            val currentState = playbackStateStore.state.value
            playbackStateStore.setCurrent(
                currentState.copy(
                    currentTrack = track,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = player.duration.takeIf { it > 0 } ?: track.durationMs,
                    isPlaying = player.isPlaying,
                    isLoading = false,
                    cacheStatus = if (track.id == activeCachedTrackId) {
                        CacheStatus.CACHED
                    } else {
                        CacheStatus.NOT_CACHED
                    },
                    error = null,
                ),
            )
            // PREFETCH: When a new track starts, queue up the next one!
            prefetchNextTrack(track.id)
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
        val upstreamFactory = DefaultDataSource.Factory(this)
        val dataSourceFactory = DataSource.Factory {
            if (activeCacheOnPlay) {
                CacheDataSource.Factory()
                    .setCache(mediaCache)
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
            } else {
                upstreamFactory.createDataSource()
            }
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply { addListener(listener) }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildPlaceholderNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PLACEHOLDER_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(PLACEHOLDER_NOTIFICATION_ID, notification)
        }
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

    private fun buildPlaceholderNotification(): Notification {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    PLAYBACK_CHANNEL_ID,
                    "Playback",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        return NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_icon_circular_play)
            .setContentTitle("MavvMusic")
            .setContentText("Preparing playback...")
            .setOngoing(true)
            .build()
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
        val trackId = requireNotNull(intent.getStringExtra(EXTRA_TRACK_ID)) {
            "The player service received no track ID."
        }
        val queueIds = intent.getStringArrayListExtra(EXTRA_QUEUE_IDS).orEmpty()
        activeQueueIds = queueIds
        
        val currentIndex = queueIds.indexOf(trackId)
        val trackIdsToPrepare = if (currentIndex >= 0) {
            queueIds.drop(currentIndex).take(2)
        } else {
            listOf(trackId)
        }
        val cachedUri = intent.getStringExtra(EXTRA_URI)
        activeCachedTrackId = trackId.takeIf { cachedUri != null }
        val queueTracks = parseQueueTracks(intent.getStringExtra(EXTRA_QUEUE_TRACKS))
        require(trackId in queueTracks) { "The playback queue is missing track $trackId." }
        activeQueueTracks = queueTracks
        activeTrackId = trackId
        activeCacheOnPlay = intent.getBooleanExtra(EXTRA_CACHE_ON_PLAY, false)
        
        playbackSetupJob?.cancel()
        streamRefreshJob?.cancel()
        streamRefreshJob = null
        refreshedStreamUris.clear()
        isPrefetching = false
        player.stop()
        player.clearMediaItems()
        playbackStateStore.setLoading(true)
        
        playbackSetupJob = serviceScope.launch {
            try {
                val resolvedItems = mutableListOf<MediaItem>()
                for ((index, id) in trackIdsToPrepare.withIndex()) {
                    val track = queueTracks[id] ?: break
                    try {
                        val uri = if (index == 0 && cachedUri != null) {
                            cachedUri
                        } else {
                            withContext(Dispatchers.IO) { streamResolver.resolve(id).uri }
                        }
                        resolvedItems += createMediaItem(track, uri)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (index == 0) throw error
                        android.util.Log.w(
                            "MavvMusicPlay",
                            "Could not pre-buffer next track $id; playing current track only.",
                            error,
                        )
                        break
                    }
                }
                if (!isActive || activeTrackId != trackId) return@launch
                require(resolvedItems.isNotEmpty()) { "No playable tracks were resolved." }
                player.setMediaItems(resolvedItems, 0, 0L)
                playbackStateStore.setLoading(false)
                player.prepare()
                player.play()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (activeTrackId == trackId) {
                    playbackStateStore.reportError(
                        error.message ?: "Playback could not be prepared.",
                    )
                }
            }
        }
    }

    private fun prefetchNextTrack(currentId: String) {
        if (isPrefetching) return
        val currentIndex = activeQueueIds.indexOf(currentId)
        if (currentIndex == -1 || currentIndex + 1 >= activeQueueIds.size) return
        
        val nextTrackId = activeQueueIds[currentIndex + 1]
        
        // Check if already in player queue
        for (i in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(i).mediaId == nextTrackId) {
                return // Already queued
            }
        }
        
        isPrefetching = true
        serviceScope.launch {
            try {
                val nextTrack = activeQueueTracks[nextTrackId] ?: return@launch
                val uri = withContext(Dispatchers.IO) { streamResolver.resolve(nextTrackId).uri }
                val mediaItem = createMediaItem(nextTrack, uri)
                
                // Double check before adding
                var queued = false
                for (i in 0 until player.mediaItemCount) {
                    if (player.getMediaItemAt(i).mediaId == nextTrackId) {
                        queued = true; break
                    }
                }
                if (!queued) {
                    player.addMediaItem(mediaItem)
                }
            } catch (e: Exception) {
                android.util.Log.e("MavvMusicPlay", "Failed to prefetch next track", e)
            } finally {
                isPrefetching = false
            }
        }
    }

    private fun createMediaItem(track: Track, uri: String) = MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist.name)
                .setAlbumTitle(track.album.title)
                .setArtworkUri(track.artworkUri?.let { android.net.Uri.parse(it) })
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .setCustomCacheKey(track.id)
        .build()

    private fun parseQueueTracks(serialized: String?): Map<String, Track> {
        if (serialized == null) return emptyMap()
        val queue = JSONArray(serialized)
        return buildMap {
            for (index in 0 until queue.length()) {
                val item = queue.getJSONObject(index)
                val id = item.getString("id")
                val artistId = item.getString("artistId")
                val artistName = item.getString("artist")
                val albumId = item.getString("albumId")
                val albumTitle = item.getString("album")
                val artworkUri = item.optString("artworkUri").takeUnless { it.isEmpty() || it == "null" }
                put(
                    id,
                    Track(
                        id = id,
                        title = item.getString("title"),
                        artist = Artist(artistId, artistName),
                        album = Album(albumId, albumTitle, artworkUri),
                        durationMs = item.getLong("durationMs"),
                        artworkUri = artworkUri,
                    ),
                )
            }
        }
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
                player.replaceMediaItem(player.currentMediaItemIndex, replacement)
                player.seekTo(positionMs)
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
        const val EXTRA_QUEUE_IDS = "queue_ids"
        const val EXTRA_QUEUE_TRACKS = "queue_tracks"
        const val EXTRA_CACHE_ON_PLAY = "cache_on_play"
        const val EXTRA_POSITION_MS = "position_ms"
        private const val PLACEHOLDER_NOTIFICATION_ID = 1001
        private const val PLAYBACK_CHANNEL_ID = "mavv_playback_channel"
        private const val HTTP_FORBIDDEN = 403
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}