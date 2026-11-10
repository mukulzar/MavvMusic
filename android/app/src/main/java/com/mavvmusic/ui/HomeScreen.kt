package com.mavvmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mavvmusic.app.presentation.HomeIntent
import com.mavvmusic.app.presentation.HomeViewModel
import com.mavvmusic.app.presentation.LibraryIntent
import com.mavvmusic.app.presentation.LibrarySection
import com.mavvmusic.app.presentation.LibraryViewModel
import com.mavvmusic.app.presentation.PlayerIntent
import com.mavvmusic.app.presentation.PlayerViewModel
import com.mavvmusic.core.model.Track

private data class MoodOption(val title: String, val query: String, val genre: String? = null)

private val moodOptions = listOf(
    MoodOption("Workout", "workout music"),
    MoodOption("Feel good", "feel good songs"),
    MoodOption("Romance", "romantic songs"),
    MoodOption("Synthwave", "synthwave music", "Synthwave"),
    MoodOption("Lo-fi", "lofi music", "Lo-fi"),
    MoodOption("R&B", "R&B music", "R&B"),
)

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    homeViewModel: HomeViewModel = hiltViewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPlaylist: (String) -> Unit = {},
) {
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val selectedMood = state.selectedMood
    val moodError = state.moodError
    val quickPicks = if (selectedMood != null) state.moodTracks else state.tracks

    LaunchedEffect(homeViewModel) {
        homeViewModel.onIntent(HomeIntent.RefreshHomeFeed())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item(key = "home-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Home",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = "Listening history")
                }
                IconButton(onClick = { homeViewModel.onIntent(HomeIntent.RefreshHomeFeed(force = true)) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh recommendations")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        item(key = "mood-filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedMood == null,
                    onClick = { homeViewModel.onIntent(HomeIntent.ClearMood) },
                    label = { Text("For you") },
                )
                moodOptions.forEach { mood ->
                    FilterChip(
                        selected = state.selectedMood == mood.title,
                        onClick = {
                            homeViewModel.onIntent(
                                HomeIntent.SelectMood(mood.title, mood.query, mood.genre),
                            )
                        },
                        label = { Text(mood.title) },
                    )
                }
            }
        }

        state.error?.let { message ->
            item(key = "home-error") {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item(key = "quick-picks-heading") {
            SectionHeading(
                title = "Quick picks",
                actionLabel = if (quickPicks.isNotEmpty()) "Play all" else null,
                onAction = {
                    quickPicks.firstOrNull()?.let { track ->
                        playerViewModel.onIntent(PlayerIntent.Play(track, quickPicks))
                    }
                },
            )
        }

        when {
            selectedMood != null && state.isLoadingMood -> item(key = "mood-loading") {
                CenteredStatus("Finding ${selectedMood.lowercase()} picks...", loading = true)
            }
            state.isLoading -> item(key = "quick-picks-loading") {
                CenteredStatus("Loading music…", loading = true)
            }
            moodError != null -> item(key = "mood-error") {
                Text(
                    moodError,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            quickPicks.isEmpty() -> item(key = "quick-picks-empty") {
                Text(
                    if (state.selectedMood == null) "No tracks are available yet."
                    else "No tracks matched this collection.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> items(quickPicks.take(8), key = { "quick-${it.id}" }) { track ->
                val isLiked = libraryState.likedTracks.any { it.id == track.id }
                TrackRow(
                    track = track,
                    onClick = { playerViewModel.onIntent(PlayerIntent.Play(track, quickPicks)) },
                    onStartMix = { playerViewModel.onIntent(PlayerIntent.StartRadio(track)) },
                    trailingContent = {
                        IconButton(
                            onClick = { libraryViewModel.onIntent(LibraryIntent.ToggleLiked(track)) },
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isLiked) "Unlike ${track.title}" else "Like ${track.title}",
                                tint = if (isLiked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }

        item(key = "mixes-heading") {
            SectionHeading(title = "Mixes")
        }

        when {
            state.isLoadingDailyMixes -> item(key = "mixes-loading") {
                CenteredStatus("Building your mixes…", loading = true)
            }
            state.dailyMixes.isEmpty() && libraryState.likedTracks.isEmpty() -> item(key = "mixes-empty") {
                Text(
                    "Play a few tracks to build mixes around your listening.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> item(key = "mixes-shelf") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (libraryState.likedTracks.isNotEmpty()) {
                        item(key = "liked-mix") {
                            PlaylistArtworkCard(
                                title = "Liked Music",
                                subtitle = "${libraryState.likedTracks.size} tracks",
                                tracks = libraryState.likedTracks,
                                onClick = {
                                    libraryState.likedTracks.firstOrNull()?.let { track ->
                                        playerViewModel.onIntent(
                                            PlayerIntent.Play(track, libraryState.likedTracks),
                                        )
                                    }
                                },
                                isLikedCollection = true,
                            )
                        }
                    }
                    items(state.dailyMixes, key = { "daily-${it.title}" }) { mix ->
                        PlaylistArtworkCard(
                            title = mix.title,
                            subtitle = "${mix.tracks.size} tracks",
                            tracks = mix.tracks,
                            onClick = {
                                mix.tracks.firstOrNull()?.let { track ->
                                    playerViewModel.onIntent(PlayerIntent.Play(track, mix.tracks))
                                }
                            },
                        )
                    }
                }
            }
        }

        state.dailyMixWarning?.let { warning ->
            item(key = "mix-warning") {
                Text(
                    warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.genreShelves.isNotEmpty()) {
            state.genreShelves.forEach { shelf ->
                item(key = "genre-heading-${shelf.genre}") {
                    SectionHeading(title = "Recommended for you - ${shelf.genre}")
                }
                item(key = "genre-shelf-${shelf.genre}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shelf.tracks, key = { "${shelf.genre}-${it.id}" }) { track ->
                            DiscoveryTrackCard(track) {
                                playerViewModel.onIntent(PlayerIntent.Play(track, shelf.tracks))
                            }
                        }
                    }
                }
            }
        } else if (!state.isLoadingGenreShelves) {
            item(key = "genre-learning") {
                Text(
                    state.genreWarning
                        ?: "Your genre recommendations will grow as you listen to tagged tracks.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (libraryState.playlists.isNotEmpty()) {
            item(key = "playlists-heading") {
                SectionHeading(title = "Your playlists")
            }
            item(key = "playlists-shelf") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(libraryState.playlists, key = { "playlist-${it.playlistId}" }) { playlist ->
                        PlaylistArtworkCard(
                            title = playlist.title,
                            subtitle = "Playlist",
                            tracks = emptyList(),
                            onClick = { onOpenPlaylist(playlist.playlistId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
    onStartMix: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrackArtwork(track, Modifier.size(52.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onStartMix != null) {
            IconButton(onClick = onStartMix) {
                Icon(Icons.Filled.Radio, contentDescription = "Start mix from ${track.title}")
            }
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun SectionHeading(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun CenteredStatus(message: String, loading: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistArtworkCard(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    onClick: () -> Unit,
    isLikedCollection: Boolean = false,
) {
    Column(
        modifier = Modifier.width(144.dp).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(144.dp).clip(MaterialTheme.shapes.small),
            color = if (isLikedCollection) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
        ) {
            val artwork = tracks.firstOrNull()?.artworkUri
            if (isLikedCollection) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = "Cover art for $title",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DiscoveryTrackCard(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(144.dp).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TrackArtwork(track, Modifier.fillMaxWidth().height(144.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrackArtwork(track: Track, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        if (track.artworkUri != null) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "Album art for ${track.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
