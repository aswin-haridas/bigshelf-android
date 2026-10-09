package com.bigshelf.app.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.theme.Neutral
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
import com.bigshelf.app.ui.screens.tools.DuplicatesScreen
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
    Tab("Favourites", Routes.FAVOURITES, BsIcons.Heart, BsIcons.HeartFill),
    Tab("People", Routes.PEOPLE, BsIcons.Users, BsIcons.UsersFill),
    Tab("Library", Routes.LIBRARY, BsIcons.SquaresFour, BsIcons.SquaresFourFill),
)

/** Which tab owns [path]: Library holds everything that isn't Photos, Favourites or People. */
private fun tabFor(path: String) = when {
    path.isEmpty() || path == Routes.HOME -> Routes.HOME
    path == Routes.FAVOURITES -> Routes.FAVOURITES
    path.startsWith("people") || path.startsWith("faces") -> Routes.PEOPLE
    else -> Routes.LIBRARY
}

/**
 * App shell: the routed page over a bottom navigation bar and the upload progress box. While
 * photos are selected the selection actions take the bar's place; the viewer hides both.
 */
@Composable
fun AppShell() {
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
    // Something sits in the bottom slot unless the viewer has the screen to itself
    val docked = selecting || !fullScreen
    Column(Modifier.fillMaxSize().background(BsColors.Background)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .then(if (docked) Modifier else Modifier.navigationBarsPadding()),
        ) {
            AppNavHost(nav, onTopLevel = ::go, onBack = ::back, sectionFrom = from.value)
            UploadProgressModal(Modifier.align(Alignment.BottomCenter))
        }
        when {
            // One bar for the whole app: selection actions replace the nav while anything is picked
            selecting -> SelectionActionBar(
                path = if (inViewer) from.value else path,
                onPersonGone = { go(Routes.PEOPLE) },
            )
            !fullScreen -> BottomBar(active = tabFor(path), onSelect = ::go)
        }
    }
}

/** 64dp bar: 24dp icons (filled when active, on a neutral-800 pill) over `text-xs` labels. */
@Composable
private fun BottomBar(active: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BsColors.Background)
            .drawBehind { drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .height(64.dp),
    ) {
        TABS.forEach { tab ->
            val selected = tab.route == active
            val pill by animateColorAsState(if (selected) Neutral.N800 else Color.Transparent, BsMotion.fast(), label = "tabPill")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .bsClickable(scale = 0.94f) { onSelect(tab.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.clip(BsShapes.Full).background(pill).padding(horizontal = 20.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BsIcon(
                        if (selected) tab.activeIcon else tab.icon,
                        size = 24.dp,
                        tint = if (selected) BsColors.Foreground else BsColors.Muted,
                        contentDescription = tab.label,
                    )
                }
                Text(
                    tab.label,
                    Modifier.padding(top = 4.dp),
                    style = BsText.Xs2.copy(fontWeight = BsWeight.Medium),
                    color = if (selected) BsColors.Foreground else BsColors.Muted,
                )
            }
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
        composable(Routes.HOME) { HomeScreen(onOpenPhoto = openPhoto) }
        composable(Routes.LIBRARY) { LibraryScreen(onNavigate = { nav.navigate(it) }) }
        composable(Routes.FAVOURITES) { FavouritesScreen(onOpenPhoto = openPhoto) }
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
            )
        }
        composable(Routes.SOON, arguments = listOf(navArgument("label") { type = NavType.StringType })) {
            val label = it.arguments?.getString("label").orEmpty()
            Column(Modifier.fillMaxSize()) {
                PageHeader(label, leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) })
                EmptyState(
                    title = "Web only",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    description = "$label isn't in the Android app yet. Open BigShelf on the web to use it.",
                )
            }
        }
    }
}
