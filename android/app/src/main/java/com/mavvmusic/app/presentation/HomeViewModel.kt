package com.mavvmusic.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mavvmusic.core.domain.GenerateDailyMixesUseCase
import com.mavvmusic.core.domain.ListeningHistoryRepository
import com.mavvmusic.core.domain.SearchTracksUseCase
import com.mavvmusic.core.domain.SmartPlaylist
import com.mavvmusic.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class HomeGenreShelf(
    val genre: String,
    val tracks: List<Track>,
)

data class HomeState(
    val tracks: List<Track> = emptyList(),
    val dailyMixes: List<SmartPlaylist> = emptyList(),
    val genreShelves: List<HomeGenreShelf> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingDailyMixes: Boolean = true,
    val isLoadingGenreShelves: Boolean = true,
    val selectedMood: String? = null,
    val moodTracks: List<Track> = emptyList(),
    val isLoadingMood: Boolean = false,
    val error: String? = null,
    val dailyMixWarning: String? = null,
    val genreWarning: String? = null,
    val moodError: String? = null,
)

sealed interface HomeIntent {
    data class RefreshHomeFeed(val force: Boolean = false) : HomeIntent
    data class SelectMood(val mood: String, val query: String, val genre: String? = null) : HomeIntent
    data object ClearMood : HomeIntent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeFeed: com.mavvmusic.core.domain.GetHomeFeedUseCase,
    private val generateDailyMixes: GenerateDailyMixesUseCase,
    private val listeningHistory: ListeningHistoryRepository,
    private val searchTracks: SearchTracksUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null
    private var moodJob: Job? = null
    private var hasLoadedFeed = false

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.RefreshHomeFeed -> refresh(intent.force)
            is HomeIntent.SelectMood -> selectMood(intent.mood, intent.query, intent.genre)
            HomeIntent.ClearMood -> {
                moodJob?.cancel()
                mutableState.value = mutableState.value.copy(
                    selectedMood = null,
                    moodTracks = emptyList(),
                    isLoadingMood = false,
                    moodError = null,
                )
            }
        }
    }

    private fun refresh(force: Boolean) {
        if (!force && (hasLoadedFeed || refreshJob?.isActive == true)) return
        refreshJob?.cancel()
        mutableState.value = mutableState.value.copy(
            isLoading = true,
            isLoadingDailyMixes = true,
            isLoadingGenreShelves = true,
            error = null,
            dailyMixWarning = null,
            genreWarning = null,
        )
        refreshJob = viewModelScope.launch {
            launch {
                try {
                    mutableState.value = mutableState.value.copy(
                        tracks = getHomeFeed(),
                        isLoading = false,
                        error = null,
                    )
                    hasLoadedFeed = true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    hasLoadedFeed = true
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Your music could not be loaded.",
                    )
                }
            }
            launch {
                try {
                    val generation = generateDailyMixes()
                    mutableState.value = mutableState.value.copy(
                        dailyMixes = generation.playlists,
                        dailyMixWarning = generation.warnings
                            .takeIf(List<String>::isNotEmpty)
                            ?.joinToString(" "),
                        isLoadingDailyMixes = false,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableState.value = mutableState.value.copy(
                        isLoadingDailyMixes = false,
                        dailyMixWarning = error.message ?: "Daily mixes could not be refreshed.",
                    )
                }
            }
            launch {
                try {
                    val topGenres = listeningHistory.topGenresThisWeek(GENRE_SHELF_COUNT)
                    val results = supervisorScope {
                        topGenres.map { profile ->
                            async {
                                try {
                                    val tracks = searchTracks(profile.genre)
                                        .distinctBy(Track::id)
                                        .take(MAX_TRACKS_PER_SHELF)
                                    if (tracks.isEmpty()) {
                                        null to "No recommendations were found for ${profile.genre}."
                                    } else {
                                        HomeGenreShelf(profile.genre, tracks) to null
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    null to (error.message
                                        ?: "Recommendations for ${profile.genre} could not be loaded.")
                                }
                            }
                        }.awaitAll()
                    }
                    mutableState.value = mutableState.value.copy(
                        genreShelves = results.mapNotNull { it.first },
                        genreWarning = results.mapNotNull { it.second }
                            .takeIf(List<String>::isNotEmpty)
                            ?.joinToString(" "),
                        isLoadingGenreShelves = false,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableState.value = mutableState.value.copy(
                        isLoadingGenreShelves = false,
                        genreWarning = error.message ?: "Your taste profile could not be loaded.",
                    )
                }
            }
        }
    }

    private fun selectMood(mood: String, query: String, genre: String?) {
        moodJob?.cancel()
        mutableState.value = mutableState.value.copy(
            selectedMood = mood,
            isLoadingMood = true,
            moodError = null,
        )
        moodJob = viewModelScope.launch {
            try {
                mutableState.value = mutableState.value.copy(
                    moodTracks = searchTracks(query)
                        .map { track -> if (genre == null) track else track.copy(genre = genre) }
                        .distinctBy(Track::id),
                    isLoadingMood = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isLoadingMood = false,
                    moodError = error.message ?: "That collection could not be loaded.",
                )
            }
        }
    }

    private companion object {
        const val GENRE_SHELF_COUNT = 2
        const val MAX_TRACKS_PER_SHELF = 20
    }
}
