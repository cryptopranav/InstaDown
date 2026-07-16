package com.pranavkd.instadown.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Downloads : Screen("downloads")
    data object Settings : Screen("settings")
}
