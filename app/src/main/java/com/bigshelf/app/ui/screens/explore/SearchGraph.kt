package com.bigshelf.app.ui.screens.explore

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bigshelf.app.data.PhotoFilter
import com.bigshelf.app.ui.Routes

private fun optional(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

/** The Search tab's pages: albums, places and the map, events, colours, bursts, smart filters. */
fun NavGraphBuilder.searchGraph(nav: NavHostController, onBack: () -> Unit, openPhoto: (String) -> Unit) {
    val go: (String) -> Unit = { nav.navigate(it) }
    composable(Routes.SEARCH) { SearchScreen(onNavigate = go, onOpenPhoto = openPhoto) }
    composable(Routes.ALBUMS) { AlbumsScreen(onBack = onBack, onNavigate = go) }
    composable(Routes.ALBUM, arguments = listOf(navArgument("albumId") { type = NavType.StringType })) {
        AlbumScreen(it.arguments?.getString("albumId").orEmpty(), onBack = onBack, onNavigate = go, onOpenPhoto = openPhoto)
    }
    composable(Routes.PLACES) { PlacesScreen(onBack = onBack, onNavigate = go) }
    composable(Routes.MAP, arguments = listOf(optional("lat"), optional("lon"))) {
        MapScreen(
            lat = it.arguments?.getString("lat")?.toDoubleOrNull(),
            lon = it.arguments?.getString("lon")?.toDoubleOrNull(),
            onBack = onBack,
            onNavigate = go,
        )
    }
    composable(Routes.EVENTS) { EventsScreen(onBack = onBack, onNavigate = go) }
    composable(Routes.COLOR, arguments = listOf(navArgument("color") { type = NavType.StringType })) {
        ColorScreen(it.arguments?.getString("color").orEmpty(), onBack = onBack, onOpenPhoto = openPhoto)
    }
    composable(Routes.BURSTS) { BurstsScreen(onBack = onBack, onOpenPhoto = openPhoto) }
    composable(
        Routes.FILTERED,
        arguments = listOf(optional("title"), optional("from"), optional("to"), optional("bbox"), optional("unorganized")),
    ) {
        val args = it.arguments
        val filter = PhotoFilter(
            takenFrom = args?.getString("from"),
            takenTo = args?.getString("to"),
            bbox = args?.getString("bbox")?.split(",")?.mapNotNull(String::toDoubleOrNull)?.takeIf { box -> box.size == 4 },
            unorganized = args?.getString("unorganized") == "true",
        )
        FilteredPhotosScreen(args?.getString("title") ?: "Photos", filter, onBack = onBack, onOpenPhoto = openPhoto, onNavigate = go)
    }
}
