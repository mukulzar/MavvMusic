package com.mavvmusic.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavvmusic.app.presentation.LibraryIntent
import com.mavvmusic.app.presentation.LibrarySection
import com.mavvmusic.app.presentation.LibraryViewModel
import com.mavvmusic.app.presentation.PlayerIntent
import com.mavvmusic.app.presentation.PlayerViewModel
import com.mavvmusic.core.model.Track

@Composable
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by libraryViewModel.state.collectAsStateWithLifecycle()
    val selectedPlaylist = state.selectedPlaylist
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var showAddTracks by rememberSaveable { mutableStateOf(false) }
    var playlistToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.onIntent(LibraryIntent.Refresh)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedPlaylist != null) {
                IconButton(onClick = { libraryViewModel.onIntent(LibraryIntent.ClosePlaylist) }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to playlists")
                }
            }
            Text(
                text = selectedPlaylist?.title ?: "Your library",
                modifier = Modifier.weight(1f).padding(start = if (selectedPlaylist == null) 8.dp else 0.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            if (state.section == LibrarySection.PLAYLISTS && selectedPlaylist == null) {
                IconButton(onClick = { showCreatePlaylist = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Create playlist")
                }
            } else if (selectedPlaylist != null) {
                IconButton(onClick = { showAddTracks = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add songs to playlist")
                }
            }
        }

        if (selectedPlaylist == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibrarySection.entries.forEach { section ->
                    FilterChip(
                        selected = state.section == section,
                        onClick = { libraryViewModel.onIntent(LibraryIntent.SelectSection(section)) },
                        label = { Text(section.label()) },
                    )
                }
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            when {
                selectedPlaylist != null -> PlaylistTracks(
                    tracks = state.playlistTracks,
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    onAddTracks = { showAddTracks = true },
                    onRemove = { trackId ->
                        libraryViewModel.onIntent(
                            LibraryIntent.RemoveTrackFromPlaylist(selectedPlaylist.playlistId, trackId),
                        )
                    },
                )
                state.section == LibrarySection.PLAYLISTS -> PlaylistList(
                    playlists = state.playlists,
                    onOpen = { libraryViewModel.onIntent(LibraryIntent.OpenPlaylist(it)) },
                    onDelete = { playlistToDelete = it },
                    onCreate = { showCreatePlaylist = true },
                )
                state.section == LibrarySection.LIKED -> TrackList(
                    tracks = state.likedTracks,
                    emptyMessage = "Songs you like will appear here.",
                    playerViewModel = playerViewModel,
                    trailingContent = { track ->
                        IconButton(
                            onClick = { libraryViewModel.onIntent(LibraryIntent.ToggleLiked(track)) },
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = "Unlike ${track.title}",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
                state.section == LibrarySection.HISTORY -> TrackList(
                    tracks = state.historyTracks,
                    emptyMessage = "Tracks you play will appear here.",
                    playerViewModel = playerViewModel,
                    trailingContent = { track ->
                        LikeButton(track, state.likedTracks, libraryViewModel)
                    },
                )
                else -> CachedTrackList(
                    tracks = state.cachedTracks,
                    likedTracks = state.likedTracks,
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                )
            }
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylist = false },
            onCreate = { title ->
                libraryViewModel.onIntent(LibraryIntent.CreatePlaylist(title))
                showCreatePlaylist = false
            },
        )
    }

    if (showAddTracks && selectedPlaylist != null) {
        AddTracksDialog(
            state = state,
            libraryViewModel = libraryViewModel,
            onDismiss = { showAddTracks = false },
        )
    }

    playlistToDelete?.let { playlistId ->
        val playlistTitle = state.playlists.firstOrNull { it.playlistId == playlistId }?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("“$playlistTitle” and its song list will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        libraryViewModel.onIntent(LibraryIntent.DeletePlaylist(playlistId))
                        playlistToDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlaylistList(
    playlists: List<com.mavvmusic.core.database.PlaylistEntity>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
) {
    if (playlists.isEmpty()) {
        EmptyLibrary(
            message = "Make a playlist for every mood, moment, or long way home.",
            actionLabel = "Create playlist",
            onAction = onCreate,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(playlists, key = { it.playlistId }) { playlist ->
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist.playlistId) },
                leadingContent = {
                    Icon(
                        Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                },
                headlineContent = { Text(playlist.title, maxLines = 1) },
                supportingContent = { Text("Playlist") },
                trailingContent = {
                    IconButton(onClick = { onDelete(playlist.playlistId) }) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete ${playlist.title}",
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun PlaylistTracks(
    tracks: List<Track>,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onAddTracks: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = onAddTracks,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  Add songs")
        }
        if (tracks.isEmpty()) {
            EmptyLibrary(
                message = "This playlist is ready for its first song.",
                actionLabel = "Find songs",
                onAction = onAddTracks,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(tracks, key = Track::id) { track ->
                    TrackRow(
                        track = track,
                        onClick = { playerViewModel.onIntent(PlayerIntent.Play(track, tracks)) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(track.id) }) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Remove ${track.title} from playlist",
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    emptyMessage: String,
    playerViewModel: PlayerViewModel,
    trailingContent: @Composable (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyLibrary(emptyMessage)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(tracks, key = { index, track -> "$index-${track.id}" }) { _, track ->
            TrackRow(
                track = track,
                onClick = { playerViewModel.onIntent(PlayerIntent.Play(track, tracks)) },
                trailingContent = { trailingContent(track) },
            )
        }
    }
}

@Composable
private fun CachedTrackList(
    tracks: List<com.mavvmusic.app.presentation.CachedLibraryTrack>,
    likedTracks: List<Track>,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
) {
    if (tracks.isEmpty()) {
        EmptyLibrary(message = "Music you save for offline listening will show up here.")
        return
    }
    val queue = tracks.map { it.track }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(tracks, key = { it.track.id }) { cached ->
            TrackRow(
                track = cached.track,
                onClick = { playerViewModel.onIntent(PlayerIntent.Play(cached.track, queue)) },
                trailingContent = { LikeButton(cached.track, likedTracks, libraryViewModel) },
            )
        }
    }
}

@Composable
private fun LikeButton(
    track: Track,
    likedTracks: List<Track>,
    libraryViewModel: LibraryViewModel,
) {
    val isLiked = likedTracks.any { it.id == track.id }
    IconButton(onClick = { libraryViewModel.onIntent(LibraryIntent.ToggleLiked(track)) }) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike ${track.title}" else "Like ${track.title}",
            tint = if (isLiked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibrary(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title) }, enabled = title.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddTracksDialog(
    state: com.mavvmusic.app.presentation.LibraryState,
    libraryViewModel: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    val playlistId = state.selectedPlaylist?.playlistId ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add songs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.addTrackQuery,
                    onValueChange = {
                        libraryViewModel.onIntent(LibraryIntent.SearchPlaylistTracks(it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search songs") },
                    singleLine = true,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when {
                    state.isSearchingTracks -> Box(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    state.addTrackResults.isEmpty() && state.addTrackQuery.isNotBlank() ->
                        Text("No songs found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    ) {
                        items(state.addTrackResults, key = Track::id) { track ->
                            ListItem(
                                modifier = Modifier.fillMaxWidth(),
                                headlineContent = { Text(track.title, maxLines = 1) },
                                supportingContent = { Text(track.artist.name, maxLines = 1) },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            libraryViewModel.onIntent(
                                                LibraryIntent.AddTrackToPlaylist(playlistId, track),
                                            )
                                        },
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Add ${track.title}",
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private fun LibrarySection.label() = when (this) {
    LibrarySection.PLAYLISTS -> "Playlists"
    LibrarySection.LIKED -> "Liked songs"
    LibrarySection.HISTORY -> "History"
    LibrarySection.DOWNLOADS -> "Downloads"
}
