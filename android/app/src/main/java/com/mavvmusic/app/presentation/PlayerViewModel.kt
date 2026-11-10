package com.mavvmusic.app.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mavvmusic.core.database.MusicDatabase
import com.mavvmusic.core.database.PlaybackHistoryEntity
import com.mavvmusic.core.domain.PausePlaybackUseCase
import com.mavvmusic.core.domain.GetRadioQueueUseCase
import com.mavvmusic.core.domain.ListeningHistoryRepository
import com.mavvmusic.core.domain.PlayTrackUseCase
import com.mavvmusic.core.domain.PlaybackRepository
import com.mavvmusic.core.domain.ResumePlaybackUseCase
import com.mavvmusic.core.domain.SeekPlaybackUseCase
import com.mavvmusic.core.model.PlaybackState
import com.mavvmusic.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val playback: PlaybackState = PlaybackState(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface PlayerIntent {
    data class Play(val track: Track, val queue: List<Track>) : PlayerIntent
    data class StartRadio(val track: Track) : PlayerIntent
    data object Pause : PlayerIntent
    data object Resume : PlayerIntent
    data class Seek(val positionMs: Long) : PlayerIntent
    data object Next : PlayerIntent
    data class PlaybackChanged(val playback: PlaybackState) : PlayerIntent
    data class Failed(val message: String) : PlayerIntent
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playTrack: PlayTrackUseCase,
    private val getRadioQueue: GetRadioQueueUseCase,
    private val pausePlayback: PausePlaybackUseCase,
    private val resumePlayback: ResumePlaybackUseCase,
    private val seekPlayback: SeekPlaybackUseCase,
    private val playbackRepository: PlaybackRepository,
    private val database: MusicDatabase,
    private val listeningHistory: ListeningHistoryRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        PlayerState(playback = playbackRepository.state.value),
    )
    val state = mutableState.asStateFlow()
    private var queue: List<Track> = emptyList()
    private var playJob: Job? = null
    private var pendingTrackId: String? = null
    private var lastHistoryTrackId = playbackRepository.state.value.currentTrack?.id

    init {
        viewModelScope.launch {
            playbackRepository.state.collect { playback ->
                val playbackTrack = playback.currentTrack
                if (playbackTrack != null && playbackTrack.id != lastHistoryTrackId) {
                    lastHistoryTrackId = playbackTrack.id
                    recordHistory(playbackTrack)
                }
                if (pendingTrackId != null) {
                    if (playback.currentTrack?.id != pendingTrackId || !playback.isLoading) {
                        return@collect
                    }
                    pendingTrackId = null
                }
                onIntent(PlayerIntent.PlaybackChanged(playback))
            }
        }
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.Play -> {
                val selectedQueue = intent.queue.ifEmpty { listOf(intent.track) }
                queue = selectedQueue
                pendingTrackId = intent.track.id
                mutableState.value = mutableState.value.copy(
                    playback = mutableState.value.playback.copy(
                        currentTrack = intent.track,
                        isPlaying = false,
                        isLoading = true,
                        positionMs = 0,
                        durationMs = intent.track.durationMs,
                        error = null,
                    ),
                    isLoading = true,
                    error = null,
                )
                playJob?.cancel()
                playJob = viewModelScope.launch {
                    try {
                        play(intent.track, selectedQueue)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        pendingTrackId = null
                        reportFailure(error)
                    }
                }
            }
            is PlayerIntent.StartRadio -> startRadio(intent.track)
            PlayerIntent.Pause -> launchPlaybackAction { pausePlayback() }
            PlayerIntent.Resume -> launchPlaybackAction { resumePlayback() }
            is PlayerIntent.Seek -> launchPlaybackAction { seekPlayback(intent.positionMs) }
            PlayerIntent.Next -> viewModelScope.launch {
                try {
                    next()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    reportFailure(error)
                }
            }
            is PlayerIntent.PlaybackChanged -> {
                mutableState.value = mutableState.value.copy(
                    playback = intent.playback,
                    isLoading = intent.playback.isLoading,
                    error = intent.playback.error,
                )
            }
            is PlayerIntent.Failed -> {
                mutableState.value = mutableState.value.copy(
                    playback = mutableState.value.playback.copy(
                        isLoading = false,
                        error = intent.message,
                    ),
                    isLoading = false,
                    error = intent.message,
                )
            }
        }
    }

    private fun launchPlaybackAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportFailure(error)
            }
        }
    }

    private suspend fun play(track: Track, tracks: List<Track> = queue) {
        if (tracks.isNotEmpty()) queue = tracks
        if (queue.isEmpty()) queue = listOf(track)
        Log.d("MavvMusicPlay", "Starting playback for: ${track.id}")
        lastHistoryTrackId = track.id
        recordHistory(track)
        playTrack(track, queue)
    }

    private fun startRadio(track: Track) {
        queue = listOf(track)
        pendingTrackId = track.id
        mutableState.value = mutableState.value.copy(
            playback = mutableState.value.playback.copy(
                currentTrack = track,
                isPlaying = false,
                isLoading = true,
                positionMs = 0,
                durationMs = track.durationMs,
                error = null,
            ),
            isLoading = true,
            error = null,
        )
        playJob?.cancel()
        playJob = viewModelScope.launch {
            try {
                val radioTracks = getRadioQueue(track.id)
                val radioQueue = (listOf(track) + radioTracks)
                    .distinctBy(Track::id)
                require(radioQueue.size > 1) {
                    "YouTube Music did not return any new tracks for this radio mix."
                }
                play(track, radioQueue)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                pendingTrackId = null
                reportFailure(error)
            }
        }
    }

    private suspend fun recordHistory(track: Track) {
        val playedAtMs = System.currentTimeMillis()
        database.musicDao().insertPlaybackHistory(
            PlaybackHistoryEntity(
                trackId = track.id,
                playedAtMs = playedAtMs,
            ),
        )
        listeningHistory.recordTrackPlayed(track, playedAtMs)
    }

    private suspend fun next() {
        val currentId = mutableState.value.playback.currentTrack?.id
            ?: throw IllegalStateException("Start a track before skipping to the next one.")
        require(queue.isNotEmpty()) { "There is no active playback queue." }
        val currentIndex = queue.indexOfFirst { it.id == currentId }
        require(currentIndex >= 0) { "The current track is not in the active playback queue." }
        require(currentIndex < queue.lastIndex) { "You have reached the end of the queue." }
        play(queue[currentIndex + 1])
    }

    private fun reportFailure(error: Exception) {
        val message = error.message ?: "Playback could not be started."
        Log.e("MavvMusicPlay", "Playback failed: $message", error)
        onIntent(PlayerIntent.Failed(message))
    }
}
