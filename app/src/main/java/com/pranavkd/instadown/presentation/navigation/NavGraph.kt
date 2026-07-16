package com.pranavkd.instadown.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pranavkd.instadown.presentation.components.BottomNavBar
import com.pranavkd.instadown.presentation.components.NavItem
import com.pranavkd.instadown.presentation.downloads.DownloadsScreen
import com.pranavkd.instadown.presentation.home.HomeScreen

val navItems = listOf(
    NavItem(Screen.Home.route, "Home", Icons.Filled.Home),
    NavItem(Screen.Downloads.route, "Downloads", Icons.AutoMirrored.Filled.List),
    NavItem(Screen.Settings.route, "Settings", Icons.Filled.Settings)
)

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDownloads = {
                    navController.navigate(Screen.Downloads.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen()
        }
        composable(Screen.Settings.route) {
            DownloadsScreen()
        }
    }
}

@Composable
fun CurrentBottomNavBar(
    navController: NavHostController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    BottomNavBar(
        items = navItems,
        currentRoute = currentRoute,
        onNavigate = { route ->
            if (route != currentRoute) {
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        modifier = modifier
    )
}
