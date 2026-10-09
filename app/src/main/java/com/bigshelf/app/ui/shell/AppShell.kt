package com.bigshelf.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.screens.ArchiveFolderScreen
import com.bigshelf.app.ui.screens.ArchiveScreen
import com.bigshelf.app.ui.screens.BinScreen
import com.bigshelf.app.ui.screens.FavouritesScreen
import com.bigshelf.app.ui.screens.HomeScreen
import com.bigshelf.app.ui.screens.SettingsScreen
import com.bigshelf.app.ui.screens.people.FacePhotosScreen
import com.bigshelf.app.ui.screens.people.FacesScreen
import com.bigshelf.app.ui.screens.people.PeopleScreen
import com.bigshelf.app.ui.screens.people.PeopleTab
import com.bigshelf.app.ui.screens.people.PersonScreen
import com.bigshelf.app.ui.screens.people.UntaggedScreen
import com.bigshelf.app.ui.screens.photo.PhotoScreen
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.launch

/** The concrete path of a back-stack entry, e.g. "people/42" (web `location.pathname`). */
private fun NavBackStackEntry.path(): String {
    val route = destination.route ?: return ""
    val args = arguments ?: return route
    return Regex("""\{(\w+)\}""").replace(route) { args.getString(it.groupValues[1]).orEmpty() }
}

/**
 * Port of routes/_app.tsx (mobile layout): `p-3` page with the top bar that opens the
 * sidebar drawer, the routed page, the global selection bar and the upload progress box.
 */
@Composable
fun AppShell() {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val path = entry?.path().orEmpty()

    // Selection is per-section: carrying it into e.g. Bin would let its actions run on photos
    // picked elsewhere. Photo pages keep it, so opening a photo and coming back keeps it.
    val section = remember { mutableStateOf<String?>(null) }
    val from = remember { mutableStateOf(Routes.HOME) }
    LaunchedEffect(path) {
        if (path.isEmpty() || path.startsWith("photos/")) return@LaunchedEffect
        if (section.value != null && section.value != path) SelectionStore.clear()
        section.value = path
        from.value = path
    }

    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        scrimColor = BsColors.Background.copy(alpha = 0.7f),
        drawerContent = {
            Sidebar(
                currentPath = path,
                onNavigate = { route ->
                    scope.launch { drawer.close() }
                    go(route)
                },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(BsColors.Background)) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
                // Mobile top bar - opens the sidebar drawer
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .bsClickable { scope.launch { drawer.open() } }
                            .background(BsColors.Background, BsShapes.Md)
                            .padding(8.dp),
                    ) {
                        BsIcon(BsIcons.List, size = 20.dp, contentDescription = "Open menu")
                    }
                    Text("BigShelf", style = BsText.Lg.copy(fontWeight = BsWeight.Semibold))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AppNavHost(nav, onTopLevel = ::go, sectionFrom = from.value)
                }
            }
            // One bar for the whole app - shows whenever any photo is selected
            SelectionActionBar(
                path = if (path.startsWith("photos/")) from.value else path,
                onPersonGone = { go(Routes.PEOPLE) },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
            UploadProgressModal(Modifier.align(Alignment.BottomStart).navigationBarsPadding())
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, onTopLevel: (String) -> Unit, sectionFrom: String) {
    val openPhoto: (String) -> Unit = { nav.navigate(Routes.photo(it)) }
    val peopleTab: (PeopleTab) -> Unit = { tab ->
        onTopLevel(
            when (tab) {
                PeopleTab.People -> Routes.PEOPLE
                PeopleTab.Faces -> Routes.FACES
                PeopleTab.Untagged -> Routes.UNTAGGED
            },
        )
    }
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(onOpenPhoto = openPhoto, onPipelines = { onTopLevel(Routes.soon("Pipelines")) }) }
        composable(Routes.FAVOURITES) { FavouritesScreen(onOpenPhoto = openPhoto) }
        composable(Routes.PEOPLE) { PeopleScreen(onPerson = { nav.navigate(Routes.person(it)) }, onTab = peopleTab) }
        composable(Routes.FACES) { FacesScreen(onFace = { nav.navigate(Routes.face(it)) }, onTab = peopleTab) }
        composable(Routes.UNTAGGED) { UntaggedScreen(onOpenPhoto = openPhoto, onTab = peopleTab) }
        composable(Routes.PERSON, arguments = listOf(navArgument("personId") { type = NavType.StringType })) {
            PersonScreen(
                personId = it.arguments?.getString("personId").orEmpty(),
                onBack = { onTopLevel(Routes.PEOPLE) },
                onOpenPhoto = openPhoto,
            )
        }
        composable(Routes.FACE, arguments = listOf(navArgument("faceId") { type = NavType.StringType })) {
            FacePhotosScreen(
                faceId = it.arguments?.getString("faceId").orEmpty(),
                onBack = { onTopLevel(Routes.FACES) },
                onOpenPhoto = openPhoto,
            )
        }
        composable(Routes.BIN) { BinScreen(onOpenPhoto = openPhoto) }
        composable(Routes.ARCHIVE) { ArchiveScreen(onFolder = { nav.navigate(Routes.archiveFolder(it)) }) }
        composable(Routes.ARCHIVE_FOLDER, arguments = listOf(navArgument("folder") { type = NavType.StringType })) {
            ArchiveFolderScreen(
                folder = it.arguments?.getString("folder").orEmpty(),
                onBack = { onTopLevel(Routes.ARCHIVE) },
                onOpenPhoto = openPhoto,
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.PHOTO, arguments = listOf(navArgument("photoId") { type = NavType.StringType })) {
            PhotoScreen(
                photoId = it.arguments?.getString("photoId").orEmpty(),
                onBack = { if (!nav.popBackStack()) onTopLevel(sectionFrom) },
                onPerson = { nav.navigate(Routes.person(it)) },
            )
        }
        composable(Routes.SOON, arguments = listOf(navArgument("label") { type = NavType.StringType })) {
            val label = it.arguments?.getString("label").orEmpty()
            EmptyState(
                title = label,
                description = "$label isn't in the Android app yet. Open BigShelf on the web to use it.",
            )
        }
    }
}
