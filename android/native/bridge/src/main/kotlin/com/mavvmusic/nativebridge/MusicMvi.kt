package com.mavvmusic.nativebridge

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mavvmusic.core.domain.CacheRepository
import com.mavvmusic.core.domain.ClearCacheUseCase
import com.mavvmusic.core.domain.GetHomeFeedUseCase
import com.mavvmusic.core.domain.ObserveMusicSettingsUseCase
import com.mavvmusic.core.domain.PausePlaybackUseCase
import com.mavvmusic.core.domain.PlayTrackUseCase
import com.mavvmusic.core.domain.ResumePlaybackUseCase
import com.mavvmusic.core.domain.SearchTracksUseCase
import com.mavvmusic.core.domain.SeekPlaybackUseCase
import com.mavvmusic.core.domain.SetAutoCacheOnPlayUseCase
import com.mavvmusic.core.domain.PlaybackRepository
import com.mavvmusic.core.model.PlaybackState
import com.mavvmusic.core.model.CacheStatus
import com.mavvmusic.core.model.Track
import com.mavvmusic.core.model.MusicSettings
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

data class HomeState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val activeFilter: String = "For you",
    val libraryFilter: String = "All",
    val likedTrackIds: Set<String> = emptySet(),
    val error: String? = null,
)

data class SearchState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class PlayerState(
    val playback: PlaybackState = PlaybackState(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

internal fun isPlaybackTickOnly(previous: PlayerState, current: PlayerState): Boolean =
    previous.copy(
        playback = previous.playback.copy(
            positionMs = current.playback.positionMs,
            durationMs = current.playback.durationMs,
            isPlaying = current.playback.isPlaying,
        ),
    ) == current

data class CacheState(
    val cachedTrackIds: Set<String> = emptySet(),
    val isClearing: Boolean = false,
    val error: String? = null,
)

data class SettingsState(
    val autoCacheOnPlay: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class MusicAppState(
    val home: HomeState,
    val search: SearchState,
    val player: PlayerState,
    val cache: CacheState,
    val settings: SettingsState,
)

sealed interface MusicEffect {
    data class ShowError(val message: String) : MusicEffect
    data object OpenPlayerSheet : MusicEffect
}

sealed interface HomeIntent {
    data class FeedLoaded(val tracks: List<Track>) : HomeIntent
    data class FilterChanged(val filter: String) : HomeIntent
    data class LibraryFilterChanged(val filter: String) : HomeIntent
    data class TrackLiked(val trackId: String) : HomeIntent
    data class Failed(val message: String) : HomeIntent
}

sealed interface SearchIntent {
    data class QueryChanged(val query: String) : SearchIntent
    data class ResultsLoaded(val results: List<Track>) : SearchIntent
    data class Failed(val message: String) : SearchIntent
}

sealed interface PlayerIntent {
    data class Play(val track: Track, val queue: List<Track>) : PlayerIntent
    data object Pause : PlayerIntent
    data object Resume : PlayerIntent
    data class Seek(val positionMs: Long) : PlayerIntent
    data object Next : PlayerIntent
    data class PlaybackChanged(val playback: PlaybackState) : PlayerIntent
    data class Failed(val message: String) : PlayerIntent
}

sealed interface CacheIntent {
    data object Clear : CacheIntent
    data object Cleared : CacheIntent
    data class Changed(val trackIds: Set<String>) : CacheIntent
    data class Failed(val message: String) : CacheIntent
}

sealed interface SettingsIntent {
    data class AutoCacheChanged(val enabled: Boolean) : SettingsIntent
    data class Loaded(val settings: MusicSettings) : SettingsIntent
    data class Failed(val message: String) : SettingsIntent
}

object HomeReducer {
    fun reduce(state: HomeState, intent: HomeIntent): HomeState = when (intent) {
        is HomeIntent.FeedLoaded -> state.copy(tracks = intent.tracks, isLoading = false, error = null)
        is HomeIntent.FilterChanged -> state.copy(activeFilter = intent.filter)
        is HomeIntent.LibraryFilterChanged -> state.copy(libraryFilter = intent.filter)
        is HomeIntent.TrackLiked -> state.copy(
            likedTrackIds = if (intent.trackId in state.likedTrackIds) {
                state.likedTrackIds - intent.trackId
            } else {
                state.likedTrackIds + intent.trackId
            },
        )
        is HomeIntent.Failed -> state.copy(isLoading = false, error = intent.message)
    }
}

object SearchReducer {
    fun reduce(state: SearchState, intent: SearchIntent): SearchState = when (intent) {
        is SearchIntent.QueryChanged ->
            state.copy(query = intent.query, results = emptyList(), isLoading = intent.query.isNotBlank(), error = null)
        is SearchIntent.ResultsLoaded ->
            state.copy(results = intent.results, isLoading = false, error = null)
        is SearchIntent.Failed ->
            state.copy(isLoading = false, error = intent.message)
    }
}

object PlayerReducer {
    fun reduce(state: PlayerState, intent: PlayerIntent): PlayerState = when (intent) {
        is PlayerIntent.Play -> state.copy(
            playback = state.playback.copy(error = null),
            isLoading = true,
            error = null,
        )
        PlayerIntent.Pause, PlayerIntent.Resume, is PlayerIntent.Seek, PlayerIntent.Next -> state
        is PlayerIntent.PlaybackChanged -> state.copy(
            playback = intent.playback,
            isLoading = false,
            error = intent.playback.error,
        )
        is PlayerIntent.Failed -> state.copy(
            playback = state.playback.copy(error = intent.message),
            isLoading = false,
            error = intent.message,
        )
    }
}

object CacheReducer {
    fun reduce(state: CacheState, intent: CacheIntent): CacheState = when (intent) {
        CacheIntent.Clear -> state.copy(isClearing = true, error = null)
        CacheIntent.Cleared -> state.copy(cachedTrackIds = emptySet(), isClearing = false, error = null)
        is CacheIntent.Changed -> state.copy(cachedTrackIds = intent.trackIds, isClearing = false, error = null)
        is CacheIntent.Failed -> state.copy(isClearing = false, error = intent.message)
    }
}

object SettingsReducer {
    fun reduce(state: SettingsState, intent: SettingsIntent): SettingsState = when (intent) {
        is SettingsIntent.AutoCacheChanged ->
            state.copy(autoCacheOnPlay = intent.enabled, isSaving = true, error = null)
        is SettingsIntent.Loaded ->
            state.copy(autoCacheOnPlay = intent.settings.autoCacheOnPlay, isSaving = false, error = null)
        is SettingsIntent.Failed ->
            state.copy(isSaving = false, error = intent.message)
    }
}

class SearchViewModel(
    private val searchTracks: SearchTracksUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchState())
    private val mutableEffects = Channel<MusicEffect>(Channel.BUFFERED)
    val state = mutableState.asStateFlow()
    val effects = mutableEffects.receiveAsFlow()

    fun dispatch(intent: SearchIntent) {
        mutableState.value = SearchReducer.reduce(mutableState.value, intent)
        if (intent !is SearchIntent.QueryChanged) return
        val query = intent.query
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                mutableState.value = SearchReducer.reduce(
                    mutableState.value,
                    SearchIntent.ResultsLoaded(searchTracks(query)),
                )
            } catch (error: Exception) {
                val message = error.message ?: "Search could not be completed."
                mutableState.value = SearchReducer.reduce(mutableState.value, SearchIntent.Failed(message))
                mutableEffects.trySend(MusicEffect.ShowError(message))
            }
        }
    }
}

class PlayerViewModel(
    private val playTrack: PlayTrackUseCase,
    private val pausePlayback: PausePlaybackUseCase,
    private val resumePlayback: ResumePlaybackUseCase,
    private val seekPlayback: SeekPlaybackUseCase,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PlayerState())
    private val mutableEffects = Channel<MusicEffect>(Channel.BUFFERED)
    val state = mutableState.asStateFlow()
    val effects = mutableEffects.receiveAsFlow()
    private var queue: List<Track> = emptyList()

    init {
        viewModelScope.launch {
            playbackRepository.state.collect { playback ->
                mutableState.value = PlayerReducer.reduce(
                    mutableState.value,
                    PlayerIntent.PlaybackChanged(playback),
                )
            }
        }
    }

    fun dispatch(intent: PlayerIntent) {
        mutableState.value = PlayerReducer.reduce(mutableState.value, intent)
        viewModelScope.launch {
            try {
                when (intent) {
                    is PlayerIntent.Play -> play(intent.track, intent.queue)
                    PlayerIntent.Pause -> pausePlayback()
                    PlayerIntent.Resume -> resumePlayback()
                    is PlayerIntent.Seek -> seekPlayback(intent.positionMs)
                    PlayerIntent.Next -> next()
                    is PlayerIntent.PlaybackChanged, is PlayerIntent.Failed -> Unit
                }
            } catch (error: Exception) {
                val message = error.message ?: "Playback could not be started."
                Log.e("MavvMusicPlay", "Playback failed: $message", error)
                mutableState.value = PlayerReducer.reduce(mutableState.value, PlayerIntent.Failed(message))
                mutableEffects.trySend(MusicEffect.ShowError(message))
            }
        }
    }

    private suspend fun play(track: Track, tracks: List<Track> = queue) {
        if (tracks.isNotEmpty()) queue = tracks
        Log.d("MavvMusicPlay", "Executing resolve and play for: ${track.id}")
        playTrack(track)
    }

    private suspend fun next() {
        val currentId = mutableState.value.playback.currentTrack?.id
            ?: throw IllegalStateException("Start a track before skipping to the next one.")
        require(queue.isNotEmpty()) { "There is no active playback queue." }
        val currentIndex = queue.indexOfFirst { it.id == currentId }
        require(currentIndex >= 0) { "The current track is not in the active queue." }
        if (currentIndex == queue.lastIndex) {
            mutableEffects.trySend(MusicEffect.ShowError("You have reached the end of the queue."))
            return
        }
        play(queue[currentIndex + 1])
    }
}

class CacheViewModel(
    private val cacheRepository: CacheRepository,
    private val clearCache: ClearCacheUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CacheState())
    private val mutableEffects = Channel<MusicEffect>(Channel.BUFFERED)
    val state = mutableState.asStateFlow()
    val effects = mutableEffects.receiveAsFlow()

    init {
        refresh()
    }

    fun dispatch(intent: CacheIntent) {
        mutableState.value = CacheReducer.reduce(mutableState.value, intent)
        if (intent != CacheIntent.Clear) return
        viewModelScope.launch {
            try {
                clearCache()
                mutableState.value = CacheReducer.reduce(mutableState.value, CacheIntent.Cleared)
            } catch (error: Exception) {
                val message = error.message ?: "The cache could not be cleared."
                mutableState.value = CacheReducer.reduce(mutableState.value, CacheIntent.Failed(message))
                mutableEffects.trySend(MusicEffect.ShowError(message))
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val ids = cacheRepository.cachedTrackIds()
                mutableState.value = CacheReducer.reduce(mutableState.value, CacheIntent.Changed(ids))
            } catch (error: Exception) {
                val message = error.message ?: "Cache status could not be loaded."
                mutableState.value = CacheReducer.reduce(mutableState.value, CacheIntent.Failed(message))
                mutableEffects.trySend(MusicEffect.ShowError(message))
            }
        }
    }
}

class SettingsViewModel(
    private val observeSettings: ObserveMusicSettingsUseCase,
    private val setAutoCacheOnPlay: SetAutoCacheOnPlayUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsState())
    val state = mutableState.asStateFlow()
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            observeSettings().collect { settings ->
                mutableState.value = SettingsReducer.reduce(mutableState.value, SettingsIntent.Loaded(settings))
            }
        }
    }

    fun dispatch(intent: SettingsIntent) {
        mutableState.value = SettingsReducer.reduce(mutableState.value, intent)
        if (intent !is SettingsIntent.AutoCacheChanged) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            try {
                setAutoCacheOnPlay(intent.enabled)
            } catch (error: Exception) {
                val message = error.message ?: "The playback preference could not be saved."
                mutableState.value = SettingsReducer.reduce(
                    mutableState.value,
                    SettingsIntent.Loaded(observeSettings().value),
                )
                mutableState.value = SettingsReducer.reduce(mutableState.value, SettingsIntent.Failed(message))
            }
        }
    }
}

class HomeViewModel(
    private val getHomeFeed: GetHomeFeedUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    private val mutableEffects = Channel<MusicEffect>(Channel.BUFFERED)
    val state = mutableState.asStateFlow()
    val effects = mutableEffects.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                mutableState.value = HomeReducer.reduce(
                    mutableState.value,
                    HomeIntent.FeedLoaded(getHomeFeed()),
                )
            } catch (error: Exception) {
                val message = error.message ?: "Your music could not be loaded."
                mutableState.value = HomeReducer.reduce(mutableState.value, HomeIntent.Failed(message))
                mutableEffects.trySend(MusicEffect.ShowError(message))
            }
        }
    }

    fun dispatch(intent: HomeIntent) {
        mutableState.value = HomeReducer.reduce(mutableState.value, intent)
    }
}

class MusicMviViewModel(
    private val homeViewModel: HomeViewModel,
    private val searchViewModel: SearchViewModel,
    private val playerViewModel: PlayerViewModel,
    private val cacheViewModel: CacheViewModel,
    private val settingsViewModel: SettingsViewModel,
) : ViewModel() {
    val state: StateFlow<MusicAppState> = combine(
        homeViewModel.state,
        searchViewModel.state,
        playerViewModel.state,
        cacheViewModel.state,
        settingsViewModel.state,
    ) { home, search, player, cache, settings ->
        MusicAppState(home = home, search = search, player = player, cache = cache, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MusicAppState(
        home = homeViewModel.state.value,
        search = searchViewModel.state.value,
        player = playerViewModel.state.value,
        cache = cacheViewModel.state.value,
        settings = settingsViewModel.state.value,
    ))

    private val mutableEffects = Channel<MusicEffect>(Channel.BUFFERED)
    val effects = mutableEffects.receiveAsFlow()

    init {
        viewModelScope.launch {
            merge(
                homeViewModel.effects,
                searchViewModel.effects,
                playerViewModel.effects,
                cacheViewModel.effects,
            ).collect { effect -> mutableEffects.send(effect) }
        }
        viewModelScope.launch {
            playerViewModel.state
                .map { it.playback.currentTrack?.id to it.playback.cacheStatus }
                .distinctUntilChanged()
                .collect { (trackId, status) ->
                    if (trackId != null && status == CacheStatus.CACHED) {
                        cacheViewModel.refresh()
                    }
                }
        }
    }

    fun dispatch(name: String, payloadJson: String) {
        val payload = try {
            JSONObject(payloadJson)
        } catch (_: JSONException) {
            mutableEffects.trySend(MusicEffect.ShowError("An intent payload was not valid JSON."))
            return
        }
        when (name) {
            "home.filterChanged" -> homeViewModel.dispatch(HomeIntent.FilterChanged(payload.optString("filter")))
            "search.queryChanged" -> searchViewModel.dispatch(SearchIntent.QueryChanged(payload.optString("query")))
            "player.play" -> {
                val trackId = payload.optString("trackId")
                val searchTracks = searchViewModel.state.value.results
                val availableTracks = if (searchTracks.any { it.id == trackId }) {
                    searchTracks
                } else {
                    homeViewModel.state.value.tracks
                }
                val track = availableTracks.firstOrNull { it.id == trackId }
                if (track == null) {
                    val message = "Track $trackId is no longer available."
                    playerViewModel.dispatch(PlayerIntent.Failed(message))
                    mutableEffects.trySend(MusicEffect.ShowError(message))
                } else {
                    playerViewModel.dispatch(PlayerIntent.Play(track, availableTracks))
                }
            }
            "player.pause" -> playerViewModel.dispatch(PlayerIntent.Pause)
            "player.resume" -> playerViewModel.dispatch(PlayerIntent.Resume)
            "player.seek" -> playerViewModel.dispatch(PlayerIntent.Seek(payload.optLong("positionMs")))
            "player.next" -> playerViewModel.dispatch(PlayerIntent.Next)
            "player.open" -> mutableEffects.trySend(MusicEffect.OpenPlayerSheet)
            "cache.clear" -> cacheViewModel.dispatch(CacheIntent.Clear)
            "settings.autoCacheChanged" -> settingsViewModel.dispatch(
                SettingsIntent.AutoCacheChanged(payload.optBoolean("enabled")),
            )
            "library.filterChanged" -> homeViewModel.dispatch(
                HomeIntent.LibraryFilterChanged(payload.optString("filter")),
            )
            "library.toggleLiked" -> homeViewModel.dispatch(HomeIntent.TrackLiked(payload.optString("trackId")))
            else -> mutableEffects.trySend(MusicEffect.ShowError("Unsupported intent: $name"))
        }
    }
}
