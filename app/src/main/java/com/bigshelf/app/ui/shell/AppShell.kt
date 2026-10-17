package com.bigshelf.app.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.bigshelf.app.ui.screens.SettingsScreen
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
import com.bigshelf.app.ui.screens.tools.FramerScreen
import com.bigshelf.app.ui.screens.tools.OUTFIT_SWAP_PROMPT
import com.bigshelf.app.ui.screens.tools.TRIPLETS_ENDPOINT
import com.bigshelf.app.ui.screens.tools.TRIPTYCH_ENDPOINT
import com.bigshelf.app.ui.screens.tools.TriptychScreen
import com.bigshelf.app.ui.screens.tools.OrganizeScreen
import com.bigshelf.app.ui.screens.tools.PipelinesScreen
import com.bigshelf.app.ui.screens.tools.RunScreen
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
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
private val SEARCH_PAGES = listOf("search", "albums", "places", "map", "events", "colors", "bursts", "filtered")

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

    // Tabs replace the stack; pages inside a tab push onto it so Back returns to the tab
    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }
    fun back() {
        if (!nav.popBackStack()) go(Routes.HOME)
    }

    val selecting = SelectionStore.count > 0
    val inViewer = path.startsWith("photos/")
    // Landscape-locked Duplicates needs every bit of height, so it drops the bottom bar too
    val fullScreen = inViewer || path == Routes.DUPLICATES
    // The floating nav overlays the page; the selection bar still docks below it
    val floating = !selecting && !fullScreen
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize().background(BsColors.Background)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .then(if (selecting || floating) Modifier else Modifier.navigationBarsPadding()),
        ) {
            CompositionLocalProvider(LocalBottomBarInset provides if (floating) navInset + 84.dp else 0.dp) {
                AppNavHost(nav, onTopLevel = ::go, onBack = ::back, sectionFrom = from.value)
            }
            UploadProgressModal(Modifier.align(Alignment.BottomCenter))
            if (floating) BottomBar(active = tabFor(path), onSelect = ::go, modifier = Modifier.align(Alignment.BottomCenter))
        }
        // One bar for the whole app: selection actions replace the nav while anything is picked
        if (selecting) SelectionActionBar(
            path = if (inViewer) from.value else path,
            onPersonGone = { go(Routes.PEOPLE) },
        )
    }
}

/** Height the floating bar covers at the bottom of the page; scrolling content pads by it. */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

/** Floating pill (Photos, People, Library) beside a round Search button, over the content. */
@Composable
private fun BottomBar(active: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val search = TABS.first { it.route == Routes.SEARCH }
    Row(
        modifier.navigationBarsPadding().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .shadow(8.dp, BsShapes.Full)
                .background(BsColors.SurfaceHover, BsShapes.Full)
                .padding(6.dp)
                .animateContentSize(BsMotion.spec()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TABS.filter { it != search }.forEach { tab ->
                val selected = tab.route == active
                val tint by animateColorAsState(if (selected) BsColors.Foreground else BsColors.Muted, BsMotion.fast(), label = "tabTint")
                Row(
                    Modifier
                        .height(48.dp)
                        .background(if (selected) BsColors.BorderFocus else Color.Transparent, BsShapes.Full)
                        .bsClickable(scale = 0.9f) { onSelect(tab.route) }
                        .padding(horizontal = if (selected) 18.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BsIcon(if (selected) tab.activeIcon else tab.icon, size = 24.dp, tint = tint, contentDescription = tab.label)
                    if (selected) Text(tab.label, style = BsText.Sm, fontWeight = BsWeight.Medium, color = tint)
                }
            }
        }
        val selected = active == Routes.SEARCH
        Box(
            Modifier
                .size(60.dp)
                .shadow(8.dp, BsShapes.Full)
                .background(if (selected) BsColors.BorderFocus else BsColors.SurfaceHover, BsShapes.Full)
                .bsClickable(scale = 0.9f) { onSelect(search.route) },
            contentAlignment = Alignment.Center,
        ) {
            BsIcon(
                if (selected) search.activeIcon else search.icon, size = 24.dp,
                tint = if (selected) BsColors.Foreground else BsColors.MutedSoft, contentDescription = search.label,
            )
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, onTopLevel: (String) -> Unit, onBack: () -> Unit, sectionFrom: String) {
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
        composable(Routes.PEOPLE) { PeopleScreen(onPerson = { nav.navigate(Routes.person(it)) }, onTab = peopleTab) }
        composable(Routes.FACES) { FacesScreen(onFace = { nav.navigate(Routes.face(it)) }, onTab = peopleTab) }
        composable(Routes.UNTAGGED) { UntaggedScreen(onOpenPhoto = openPhoto, onTab = peopleTab) }
        composable(Routes.PERSON, arguments = listOf(navArgument("personId") { type = NavType.StringType })) {
            PersonScreen(
                personId = it.arguments?.getString("personId").orEmpty(),
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
            )
        }
        composable(Routes.TRIPLETS) {
            // Match outfit (default) uses the triptych grouping; off, random photos of one person.
            // Downloading moves the photos to Archive (when on in Settings)
            val settings = BigShelfApp.settings
            // Keyed so switching modes starts that endpoint's state fresh
            key(settings.tripletOutfit) {
                TriptychScreen(
                    "Triplets",
                    if (settings.tripletOutfit) "All similar photos of one outfit" else "${settings.tripletCount} photos of the same person",
                    if (settings.tripletOutfit) TRIPTYCH_ENDPOINT else TRIPLETS_ENDPOINT,
                    onBack = onBack, onOpenPhoto = openPhoto,
                )
            }
        }
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
