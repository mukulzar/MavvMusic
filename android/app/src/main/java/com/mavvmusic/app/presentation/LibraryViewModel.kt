package com.mavvmusic.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mavvmusic.core.database.CachedMusicItem
import com.mavvmusic.core.database.LikedTrackEntity
import com.mavvmusic.core.database.MusicDatabase
import com.mavvmusic.core.database.PlaylistEntity
import com.mavvmusic.core.database.PlaylistTrackEntity
import com.mavvmusic.core.database.toMetadataEntity
import com.mavvmusic.core.database.toTrack
import com.mavvmusic.core.domain.SearchTracksUseCase
import com.mavvmusic.core.model.Album
import com.mavvmusic.core.model.Artist
import com.mavvmusic.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibrarySection {
    PLAYLISTS,
    LIKED,
    HISTORY,
    DOWNLOADS,
}

data class CachedLibraryTrack(
    val track: Track,
    val sizeBytes: Long,
    val lastPlayedAtMs: Long,
)

data class LibraryState(
    val section: LibrarySection = LibrarySection.PLAYLISTS,
    val isLoading: Boolean = true,
    val error: String? = null,
    val playlists: List<PlaylistEntity> = emptyList(),
    val selectedPlaylist: PlaylistEntity? = null,
    val playlistTracks: List<Track> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val historyTracks: List<Track> = emptyList(),
    val cachedTracks: List<CachedLibraryTrack> = emptyList(),
    val addTrackQuery: String = "",
    val addTrackResults: List<Track> = emptyList(),
    val isSearchingTracks: Boolean = false,
)

sealed interface LibraryIntent {
    data object Refresh : LibraryIntent
    data class SelectSection(val section: LibrarySection) : LibraryIntent
    data class OpenPlaylist(val playlistId: String) : LibraryIntent
    data object ClosePlaylist : LibraryIntent
    data class CreatePlaylist(val title: String) : LibraryIntent
    data class DeletePlaylist(val playlistId: String) : LibraryIntent
    data class ToggleLiked(val track: Track) : LibraryIntent
    data class SearchPlaylistTracks(val query: String) : LibraryIntent
    data class AddTrackToPlaylist(val playlistId: String, val track: Track) : LibraryIntent
    data class RemoveTrackFromPlaylist(val playlistId: String, val trackId: String) : LibraryIntent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val searchTracks: SearchTracksUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null

    init {
        onIntent(LibraryIntent.Refresh)
    }

    fun onIntent(intent: LibraryIntent) {
        when (intent) {
            LibraryIntent.Refresh -> refresh()
            is LibraryIntent.SelectSection -> mutableState.value = mutableState.value.copy(
                section = intent.section,
                selectedPlaylist = null,
                error = null,
            )
            is LibraryIntent.OpenPlaylist -> viewModelScope.launch {
                runLibraryAction {
                    val playlist = database.musicDao().playlists()
                        .firstOrNull { it.playlistId == intent.playlistId }
                        ?: throw IllegalStateException("That playlist is no longer available.")
                    mutableState.value = mutableState.value.copy(
                        section = LibrarySection.PLAYLISTS,
                        selectedPlaylist = playlist,
                        playlistTracks = database.musicDao().playlistTracks(intent.playlistId).map { it.toTrack() },
                        addTrackQuery = "",
                        addTrackResults = emptyList(),
                    )
                }
            }
            LibraryIntent.ClosePlaylist -> mutableState.value = mutableState.value.copy(
                selectedPlaylist = null,
                addTrackQuery = "",
                addTrackResults = emptyList(),
            )
            is LibraryIntent.CreatePlaylist -> viewModelScope.launch {
                runLibraryAction {
                    val title = intent.title.trim()
                    require(title.isNotEmpty()) { "Enter a playlist name." }
                    val playlist = PlaylistEntity(
                        playlistId = UUID.randomUUID().toString(),
                        title = title,
                        updatedAtMs = System.currentTimeMillis(),
                    )
                    database.musicDao().createPlaylist(playlist)
                    val playlists = database.musicDao().playlists()
                    mutableState.value = mutableState.value.copy(
                        section = LibrarySection.PLAYLISTS,
                        playlists = playlists,
                        selectedPlaylist = playlist,
                        playlistTracks = emptyList(),
                        error = null,
                    )
                }
            }
            is LibraryIntent.DeletePlaylist -> viewModelScope.launch {
                runLibraryAction {
                    database.musicDao().deletePlaylist(intent.playlistId)
                    mutableState.value = mutableState.value.copy(
                        playlists = database.musicDao().playlists(),
                        selectedPlaylist = mutableState.value.selectedPlaylist
                            ?.takeUnless { it.playlistId == intent.playlistId },
                        playlistTracks = if (mutableState.value.selectedPlaylist?.playlistId == intent.playlistId) {
                            emptyList()
                        } else {
                            mutableState.value.playlistTracks
                        },
                    )
                }
            }
            is LibraryIntent.ToggleLiked -> viewModelScope.launch {
                runLibraryAction {
                    val dao = database.musicDao()
                    dao.upsertTrackMetadata(intent.track.toMetadataEntity())
                    if (dao.isTrackLiked(intent.track.id)) {
                        dao.removeLikedTrack(intent.track.id)
                    } else {
                        dao.setTrackLiked(LikedTrackEntity(intent.track.id, System.currentTimeMillis()))
                    }
                    mutableState.value = mutableState.value.copy(
                        likedTracks = dao.likedTracks().map { it.toTrack() },
                        error = null,
                    )
                }
            }
            is LibraryIntent.SearchPlaylistTracks -> searchPlaylistTracks(intent.query)
            is LibraryIntent.AddTrackToPlaylist -> viewModelScope.launch {
                runLibraryAction {
                    val dao = database.musicDao()
                    dao.upsertTrackMetadata(intent.track.toMetadataEntity())
                    dao.addTrackToPlaylist(
                        PlaylistTrackEntity(
                            playlistId = intent.playlistId,
                            trackId = intent.track.id,
                            addedAtMs = System.currentTimeMillis(),
                        ),
                    )
                    dao.touchPlaylist(intent.playlistId, System.currentTimeMillis())
                    mutableState.value = mutableState.value.copy(
                        playlists = dao.playlists(),
                        playlistTracks = if (mutableState.value.selectedPlaylist?.playlistId == intent.playlistId) {
                            dao.playlistTracks(intent.playlistId).map { it.toTrack() }
                        } else {
                            mutableState.value.playlistTracks
                        },
                        addTrackResults = mutableState.value.addTrackResults
                            .filterNot { it.id == intent.track.id },
                        error = null,
                    )
                }
            }
            is LibraryIntent.RemoveTrackFromPlaylist -> viewModelScope.launch {
                runLibraryAction {
                    val dao = database.musicDao()
                    dao.removeTrackFromPlaylist(intent.playlistId, intent.trackId)
                    dao.touchPlaylist(intent.playlistId, System.currentTimeMillis())
                    mutableState.value = mutableState.value.copy(
                        playlists = dao.playlists(),
                        playlistTracks = dao.playlistTracks(intent.playlistId).map { it.toTrack() },
                        error = null,
                    )
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            runLibraryAction {
                val dao = database.musicDao()
                mutableState.value = mutableState.value.copy(
                    playlists = dao.playlists(),
                    likedTracks = dao.likedTracks().map { it.toTrack() },
                    historyTracks = dao.playbackHistory().map { it.toTrack() },
                    cachedTracks = dao.cachedMusic().map { it.toLibraryTrack() },
                    isLoading = false,
                )
                val selected = mutableState.value.selectedPlaylist
                if (selected != null) {
                    mutableState.value = mutableState.value.copy(
                        playlistTracks = dao.playlistTracks(selected.playlistId).map { it.toTrack() },
                    )
                }
            }
            if (mutableState.value.isLoading) {
                mutableState.value = mutableState.value.copy(isLoading = false)
            }
        }
    }

    private fun searchPlaylistTracks(query: String) {
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(
            addTrackQuery = query,
            addTrackResults = emptyList(),
            isSearchingTracks = query.isNotBlank(),
            error = null,
        )
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            try {
                val results = searchTracks(query)
                if (mutableState.value.addTrackQuery == query) {
                    mutableState.value = mutableState.value.copy(
                        addTrackResults = results,
                        isSearchingTracks = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mutableState.value.addTrackQuery == query) {
                    mutableState.value = mutableState.value.copy(
                        isSearchingTracks = false,
                        error = error.message ?: "Tracks could not be searched.",
                    )
                }
            }
        }
    }

    private suspend fun runLibraryAction(action: suspend () -> Unit) {
        try {
            action()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                error = error.message ?: "Your library could not be updated.",
            )
        }
    }

    private fun CachedMusicItem.toLibraryTrack() = CachedLibraryTrack(
        track = track?.toTrack() ?: Track(
            id = cachedTrack.trackId,
            title = "Downloaded track",
            artist = Artist("unknown", "Unknown artist"),
            album = Album("unknown", "Unknown album", null),
            durationMs = 0,
            artworkUri = null,
        ),
        sizeBytes = cachedTrack.sizeBytes,
        lastPlayedAtMs = cachedTrack.lastPlayedAtMs,
    )
}
