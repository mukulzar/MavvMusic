package com.mavvmusic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import com.mavvmusic.app.presentation.LibraryViewModel
import com.mavvmusic.app.presentation.LibraryIntent
import com.mavvmusic.app.presentation.LibrarySection
import com.mavvmusic.app.presentation.PlayerViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    Destination("home", "Home", Icons.Filled.Home),
    Destination("search", "Search", Icons.Filled.Search),
    Destination("library", "Library", Icons.Filled.LibraryMusic),
    Destination("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                MiniPlayer(playerViewModel)
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    onOpenHistory = {
                        libraryViewModel.onIntent(LibraryIntent.SelectSection(LibrarySection.HISTORY))
                        navController.navigate("library") {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenPlaylist = { playlistId ->
                        libraryViewModel.onIntent(LibraryIntent.OpenPlaylist(playlistId))
                        navController.navigate("library") {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable("search") { SearchScreen(playerViewModel, libraryViewModel) }
            composable("library") { LibraryScreen(playerViewModel, libraryViewModel) }
            composable("settings") { SettingsScreen(libraryViewModel) }
        }
    }
}
