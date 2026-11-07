package com.bigshelf.app.ui.shell

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import com.bigshelf.app.ui.theme.BsWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.bigshelf.app.state.EventStream
import com.bigshelf.app.state.SelectionStore
import com.bigshelf.app.state.rememberQuery
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.screens.ArchiveFolderScreen
import com.bigshelf.app.ui.screens.ArchiveScreen
import com.bigshelf.app.ui.screens.BackupScreen
import com.bigshelf.app.ui.screens.BinScreen
import com.bigshelf.app.ui.screens.HomeScreen
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.screens.SettingsScreen
import com.bigshelf.app.ui.screens.TripletSettingsCard
import com.bigshelf.app.ui.screens.SharingScreen
import com.bigshelf.app.ui.screens.explore.searchGraph
import com.bigshelf.app.ui.screens.people.FacePhotosScreen
import com.bigshelf.app.ui.screens.people.FacesScreen
import com.bigshelf.app.ui.screens.people.PeopleScreen
import com.bigshelf.app.ui.screens.people.PeopleTab
import com.bigshelf.app.ui.screens.people.PersonScreen
import com.bigshelf.app.ui.screens.people.UntaggedScreen
import com.bigshelf.app.ui.screens.photo.PhotoScreen
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.ui.screens.tools.COMPOSER_ENDPOINT
import com.bigshelf.app.ui.screens.tools.DuplicatesScreen
import com.bigshelf.app.ui.screens.tools.RatingScreen
import com.bigshelf.app.ui.screens.tools.SwipeScreen
import com.bigshelf.app.ui.screens.tools.FramerScreen
import com.bigshelf.app.ui.screens.tools.OUTFIT_SWAP_PROMPT
import com.bigshelf.app.ui.screens.tools.TRIPLETS_ENDPOINT
import com.bigshelf.app.ui.screens.tools.TriptychScreen
import com.bigshelf.app.ui.screens.tools.OrganizeScreen
import com.bigshelf.app.ui.screens.tools.PipelinesScreen
import com.bigshelf.app.ui.screens.tools.RunScreen
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsMotion

/** The concrete path of a back-stack entry, e.g. "people/42" (web `location.pathname`). */
private fun NavBackStackEntry.path(): String {
    val route = destination.route ?: return ""
    val args = arguments ?: return route
    return Regex("""\{(\w+)\}""").replace(route) { args.getString(it.groupValues[1]).orEmpty() }
}

/** Bottom-bar tabs: label, route, icon, filled icon when active. */
private data class Tab(val label: String, val route: String, val icon: ImageVector, val activeIcon: ImageVector)

private val TABS = listOf(
    Tab("Photos", Routes.HOME, BsIcons.Images, BsIcons.ImagesFill),
    Tab("Search", Routes.SEARCH, BsIcons.MagnifyingGlass, BsIcons.MagnifyingGlassFill),
    Tab("People", Routes.PEOPLE, BsIcons.Users, BsIcons.UsersFill),
    Tab("Library", Routes.LIBRARY, BsIcons.SquaresFour, BsIcons.SquaresFourFill),
)

// Pages the Search tab owns: albums, places and the map, events, colours, bursts, smart filters
private val SEARCH_PAGES = listOf("search", "albums", "places", "map", "events", "colors", "bursts", "filtered", "tags")

/** Which tab owns [path]: Library holds everything that isn't Photos, Search or People. */
private fun tabFor(path: String) = when {
    path.isEmpty() || path == Routes.HOME -> Routes.HOME
    SEARCH_PAGES.any { path == it || path.startsWith("$it/") || path.startsWith("$it?") } -> Routes.SEARCH
    path.startsWith("people") || path.startsWith("faces") -> Routes.PEOPLE
    else -> Routes.LIBRARY
}

/**
 * App shell: the routed page over a bottom navigation bar and the upload progress box. While
 * photos are selected the selection actions take the bar's place; the viewer hides both.
 */
@Composable
fun AppShell() {
    // Live server updates (processing finished, another device's edits) while in the foreground
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { EventStream.listen() } }

    val nav = rememberNavController()
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

    // Keep each tab's saved scroll and navigation state when switching sections.
    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun back() {
        if (!nav.popBackStack()) go(Routes.HOME)
    }

    val selecting = SelectionStore.count > 0
    val inViewer = path.startsWith("photos/")
    // Landscape-locked Duplicates needs every bit of height, and Swipe keeps its actions where the
    // thumb rests; Organize also keeps its exit and actions in focus, so these drop the bottom bar
    val fullScreen = inViewer || path == Routes.DUPLICATES || path == Routes.RATING || path == Routes.SWIPE || path == Routes.ORGANIZE
    // Nav and selection bar both dock below the page; selection replaces the nav
    val showNav = !selecting && !fullScreen
    Column(Modifier.fillMaxSize().background(BsColors.Background)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                // Landscape: keep the punch-hole camera off the grid (viewer stays full-bleed)
                .then(if (fullScreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)))
                .then(if (selecting || showNav) Modifier else Modifier.navigationBarsPadding()),
        ) {
            AppNavHost(nav, path, onTopLevel = ::go, onBack = ::back, sectionFrom = from.value)
            UploadProgressModal(Modifier.align(Alignment.BottomCenter))
        }
        if (showNav) BottomBar(active = tabFor(path), onSelect = ::go)
        // One bar for the whole app: selection actions replace the nav while anything is picked
        if (selecting) SelectionActionBar(
            path = if (inViewer) from.value else path,
            onPersonGone = { go(Routes.PEOPLE) },
        )
    }
}

/**
 * Height a bar overlaying the bottom of the page covers; scrolling content pads by it. Always 0
 * now that the nav docks below the page instead of floating over it.
 */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

/**
 * Labeled destinations with a distinct upload action in the center.
 */
@Composable
private fun BottomBar(active: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BsColors.Background)
            .drawBehind { drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .height(64.dp)
            .selectableGroup(),
    ) {
        val upload = rememberUploadPicker()
        TABS.forEachIndexed { i, tab ->
            // Upload sits in the middle, a plus on a surface circle so it reads as an action, not a tab
            if (i == TABS.size / 2) Column(
                Modifier.weight(1f).fillMaxHeight().bsClickable(scale = 0.9f, onClick = upload),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                BsIcon(BsIcons.Plus, size = 24.dp, tint = BsColors.Foreground)
                Text("Upload", style = BsText.Xs2, color = BsColors.Foreground)
            }
            val selected = tab.route == active
            val tint by animateColorAsState(if (selected) BsColors.Foreground else BsColors.Muted, BsMotion.fast(), label = "tabTint")
            Column(
                Modifier.weight(1f).fillMaxHeight().selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = { if (!selected) onSelect(tab.route) },
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                BsIcon(if (selected) tab.activeIcon else tab.icon, size = 24.dp, tint = tint)
                Text(tab.label, style = BsText.Xs2, color = tint)
            }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, path: String, onTopLevel: (String) -> Unit, onBack: () -> Unit, sectionFrom: String) {
    val openPhoto: (String) -> Unit = { nav.navigate(Routes.photo(it)) }
    // Keep People tab data above the destinations, which leave composition when switching tabs.
    val api = BigShelfApp.api
    val people = rememberQuery("people", invalidatedBy = setOf("people"), active = path == Routes.PEOPLE) { api.people() }
    val faces = rememberQuery("faces", invalidatedBy = setOf("faces"), active = path == Routes.FACES) { api.faces() }
    val untagged = rememberQuery("untagged", invalidatedBy = setOf("people"), active = path == Routes.UNTAGGED) { api.untaggedPhotos() }
    val peopleTab: (PeopleTab) -> Unit = { tab ->
        onTopLevel(
            when (tab) {
                PeopleTab.People -> Routes.PEOPLE
                PeopleTab.Faces -> Routes.FACES
                PeopleTab.Untagged -> Routes.UNTAGGED
            },
        )
    }
    NavHost(
        nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(BsMotion.spec()) },
        exitTransition = { fadeOut(BsMotion.fast()) },
        popEnterTransition = { fadeIn(BsMotion.spec()) },
        popExitTransition = { fadeOut(BsMotion.fast()) },
    ) {
        composable(Routes.HOME) { HomeScreen(onOpenPhoto = openPhoto, onProfile = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.LIBRARY) { LibraryScreen(onNavigate = { nav.navigate(it) }) }
        searchGraph(nav, onBack = onBack, openPhoto = openPhoto)
        composable(Routes.PEOPLE) { PeopleScreen(people, onPerson = { nav.navigate(Routes.person(it)) }, onTab = peopleTab) }
        composable(Routes.FACES) { FacesScreen(faces, onFace = { nav.navigate(Routes.face(it)) }, onTab = peopleTab, onOpenPhoto = openPhoto) }
        composable(Routes.UNTAGGED) { UntaggedScreen(untagged, onOpenPhoto = openPhoto, onTab = peopleTab) }
        composable(Routes.PERSON, arguments = listOf(navArgument("personId") { type = NavType.StringType })) {
            PersonScreen(
                personId = it.arguments?.getString("personId").orEmpty(),
                onMerged = { id ->
                    nav.navigate(Routes.person(id)) {
                        popUpTo(Routes.PERSON) { inclusive = true }
                    }
                },
                onBack = onBack,
                onOpenPhoto = openPhoto,
            )
        }
        composable(Routes.FACE, arguments = listOf(navArgument("faceId") { type = NavType.StringType })) {
            FacePhotosScreen(
                faceId = it.arguments?.getString("faceId").orEmpty(),
                onBack = onBack,
                onOpenPhoto = openPhoto,
            )
        }
        composable(Routes.BIN) { BinScreen(onOpenPhoto = openPhoto, onBack = onBack) }
        composable(Routes.ARCHIVE) { ArchiveScreen(onFolder = { nav.navigate(Routes.archiveFolder(it)) }, onBack = onBack) }
        composable(Routes.ARCHIVE_FOLDER, arguments = listOf(navArgument("folder") { type = NavType.StringType })) {
            ArchiveFolderScreen(
                folder = it.arguments?.getString("folder").orEmpty(),
                onBack = onBack,
                onOpenPhoto = openPhoto,
            )
        }
        composable(Routes.WARDROBE) {
            com.bigshelf.app.ui.screens.explore.TagScreen("fashion", onBack, openPhoto, title = "Wardrobe")
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = onBack) }
        composable(Routes.BACKUP) { BackupScreen(onBack = onBack) }
        composable(Routes.SHARING) { SharingScreen(onBack = onBack, onNavigate = { nav.navigate(it) }) }
        composable(Routes.PIPELINES) { PipelinesScreen(onBack = onBack, onRun = { nav.navigate(Routes.run(it)) }) }
        composable(Routes.RUN, arguments = listOf(navArgument("runId") { type = NavType.StringType })) {
            RunScreen(runId = it.arguments?.getString("runId").orEmpty(), onBack = onBack)
        }
        composable(Routes.DUPLICATES) { DuplicatesScreen(onBack = onBack, onOpenPhoto = openPhoto) }
        composable(Routes.ORGANIZE) { OrganizeScreen(onBack = onBack, onOpenPhoto = openPhoto) }
        composable(Routes.PHOTO, arguments = listOf(navArgument("photoId") { type = NavType.StringType })) {
            PhotoScreen(
                photoId = it.arguments?.getString("photoId").orEmpty(),
                onBack = { if (!nav.popBackStack()) onTopLevel(sectionFrom) },
                onPerson = { nav.navigate(Routes.person(it)) },
                onMap = { lat, lon -> nav.navigate(Routes.map(lat, lon)) },
                onOpenPhoto = { id -> nav.navigate(Routes.photo(id)) },
                onFramer = { id -> nav.navigate(Routes.framer(id)) },
                onDeleted = { if (!nav.popBackStack(sectionFrom, inclusive = false)) onTopLevel(sectionFrom) },
            )
        }
        composable(Routes.TRIPLETS) {
            // Random photos of one person; the Match toggles fill the set with photos like the first.
            // Downloading moves the photos to Archive (when on in Settings)
            val settings = BigShelfApp.settings
            var editingSettings by remember { mutableStateOf(false) }
            TriptychScreen(
                "Matches",
                "Up to ${settings.tripletCount} photos${if (settings.tripletMatchFaces) " of the same profile" else " per set"}",
                TRIPLETS_ENDPOINT,
                onBack = onBack, onOpenPhoto = openPhoto,
                onSettings = { editingSettings = true },
            )
            // The same options as the Triplets section of Settings
            if (editingSettings) {
                BsModal(title = "Matches settings", onClose = { editingSettings = false }) {
                    TripletSettingsCard()
                }
            }
        }
        composable(Routes.RATING) { RatingScreen(onBack = onBack, onOpenPhoto = openPhoto) }
        composable(Routes.SWIPE) { SwipeScreen(onBack = onBack) }
        composable(Routes.COMPOSER) {
            // A clear-face photo, then the same person in another outfit, with the prompt to swap it in
            TriptychScreen(
                "Composer", "A clear face, and an outfit of theirs to swap in", COMPOSER_ENDPOINT,
                onBack = onBack, onOpenPhoto = openPhoto, prompt = OUTFIT_SWAP_PROMPT,
            )
        }
        composable(Routes.FRAMER, arguments = listOf(navArgument("photo") { type = NavType.StringType; nullable = true })) {
            FramerScreen(photoId = it.arguments?.getString("photo"), onBack = onBack)
        }
    }
}
