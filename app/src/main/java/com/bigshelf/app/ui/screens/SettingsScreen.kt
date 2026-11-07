package com.bigshelf.app.ui.screens

import com.bigshelf.app.state.AuthStore
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.activity.compose.rememberLauncherForActivityResult
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Backup
import com.bigshelf.app.data.BackupWorker
import com.bigshelf.app.data.SystemStats
import com.bigshelf.app.data.Updater
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsModal
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsProgress
import com.bigshelf.app.ui.components.BsSwitch
import com.bigshelf.app.ui.components.BsTabs
import com.bigshelf.app.ui.components.TabItem
import com.bigshelf.app.state.PhotoEvents
import com.bigshelf.app.ui.photos.photoCountLabel
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.shell.formatBytes
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SYSTEM_STATS_POLL_MS = 3000L

/** Same card as Library's groups: `rounded-xl bg-surface`; rows draw the `divide-y` line themselves. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BsShapes.Xl)
            .background(BsColors.Surface),
    ) { content() }
}

/** `divide-y divide-border`: a hairline above every row but the first. */
private fun Modifier.divider(show: Boolean) = drawBehind {
    if (show) drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
}

/**
 * Label + hint on the left, control on the right (`px-4 py-3`). The control drops below when the
 * label's longest word wouldn't fit beside it, so neither breaks mid-word at big font scales.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRow(label: String, hint: String?, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
            if (hint != null) Text(hint, style = BsText.Xs, color = BsColors.Muted)
        }
        control()
    }
}

@Composable
private fun ToggleRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = false) {
    // The whole row toggles, like the web's wrapping <label>
    SettingRow(label, hint, Modifier.divider(divider).toggleable(value = checked, role = Role.Switch, onValueChange = onChange)) {
        BsSwitch(checked, onChange, Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(start = 4.dp), style = BsText.Xs.copy(fontWeight = BsWeight.Medium), color = BsColors.Muted)
}

/**
 * Self-update status. Android lets an app install its own updates only after "Install unknown
 * apps" is switched on for it once; Allow opens that page. From then on updates install silently.
 */
@Composable
private fun AutoUpdateRow() {
    val context = LocalContext.current
    // Re-read on return from the system page, where the switch is flipped
    var allowed by remember { mutableStateOf(Updater.canInstall(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = Updater.canInstall(context)
        onPauseOrDispose {}
    }
    SettingRow(
        "Automatic updates",
        if (allowed) "New versions install at launch and in the background on Wi-Fi" else "Allow installing updates once to turn on",
        Modifier.divider(true),
    ) {
        if (!allowed) {
            BsButton(onClick = { context.startActivity(Updater.allowIntent(context)) }, variant = ButtonVariant.Secondary) {
                Text("Allow")
            }
        }
    }
}

/** Section title with a one-line explanation under it. */
@Composable
private fun Section(title: String, hint: String) {
    Column {
        SectionTitle(title)
        Text(hint, Modifier.padding(start = 4.dp), style = BsText.Xs, color = BsColors.MutedFaint)
    }
}

/** People hidden with Skip on Triplets, each with an Unskip to bring them back. */
@Composable
private fun SkippedPeople() {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val people = rememberQuery("people-all", invalidatedBy = setOf("people")) { api.people().items }
    val skipped = people.data.orEmpty().filter { it.isSkipped }
    var pending by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("Skipped people", "Skipped people never appear in Matches.")
        SettingsCard {
            when {
                people.isLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Spinner(size = 16.dp, tint = BsColors.Muted)
                }
                people.isError && people.data == null -> Text(
                    "Unable to load skipped people.",
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = BsText.Xs,
                    color = BsColors.Muted,
                )
                skipped.isEmpty() -> Text(
                    "No skipped people. Use Skip on the Triplets page to hide someone.",
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = BsText.Xs,
                    color = BsColors.Muted,
                )
                else -> skipped.forEachIndexed { i, person ->
                    SettingRow(person.name, photoCountLabel(person.photoCount), Modifier.divider(i > 0)) {
                        BsButton(
                            onClick = {
                                pending = person.id
                                scope.launch {
                                    try {
                                        api.unskipPerson(person.id)
                                        PhotoEvents.invalidate("people")
                                        toast.success("${person.name} will appear in Matches again")
                                    } catch (_: Exception) {
                                        toast.error("Couldn't unskip this person")
                                    } finally {
                                        pending = null
                                    }
                                }
                            },
                            variant = ButtonVariant.Secondary,
                            enabled = pending == null,
                        ) {
                            if (pending == person.id) Spinner(size = 16.dp) else BsIcon(BsIcons.UserPlus, size = 16.dp)
                            Text("Unskip")
                        }
                    }
                }
            }
        }
    }
}

/** Port of modules/settings/settings.tsx. */
private val ORDERS = listOf(
    TabItem("newest", "Newest"), TabItem("oldest", "Oldest"), TabItem("best", "Best"), TabItem("random", "Shuffle"),
)

/** Label + hint over a full-width segmented control (sizes, order), a divider above it. */
@Composable
private fun TabsRow(label: String, hint: String, content: @Composable () -> Unit) {
    Column(
        Modifier.divider(true).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(label, style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
            Text(hint, style = BsText.Xs, color = BsColors.Muted)
        }
        content()
    }
}

/** The Triplets options card, shared by Settings and the gear on the Triplets page. */
@Composable
fun TripletSettingsCard() {
    val settings = BigShelfApp.settings
    var choosingSize by remember { mutableStateOf(false) }
    SettingsCard {
        SettingRow("Match", "Choose what each set has in common.") {}
        val matches = listOf(
            Triple("Faces", settings.tripletMatchFaces, settings::updateTripletMatchFaces),
            Triple("Outfit", settings.tripletOutfit, settings::updateTripletOutfit),
            Triple("Colors", settings.tripletMatchColors, settings::updateTripletMatchColors),
            Triple("Pose", settings.tripletMatchPose, settings::updateTripletMatchPose),
            Triple("Scene", settings.tripletMatchScene, settings::updateTripletMatchScene),
        )
        matches.chunked(3).forEach { row ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, checked, change) ->
                    BsButton(
                        onClick = { change(!checked) }, modifier = Modifier.weight(1f),
                        variant = if (checked) ButtonVariant.Primary else ButtonVariant.Secondary,
                    ) { Text(label) }
                }
            }
        }
        ToggleRow("AI images", "Include AI images.", settings.includeAi, settings::updateIncludeAi, divider = true)
        ToggleRow(
            "Clear face first", "Lead with a sharp, close face.",
            settings.tripletClearLead, settings::updateTripletClearLead, divider = true,
        )
        SettingRow("Photos per set", "Strict matching may return fewer photos.") {
            BsButton(onClick = { choosingSize = true }, variant = ButtonVariant.Secondary) {
                Text("${settings.tripletCount} photos")
            }
        }
        TabsRow("Order", "How the photos in a set are laid out.") {
            BsTabs(ORDERS, settings.tripletOrder, settings::updateTripletOrder)
        }
    }
    if (choosingSize) BsModal(title = "Photos per set", onClose = { choosingSize = false }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..9).forEach { count ->
                BsButton(
                    onClick = { settings.updateTripletCount(count); choosingSize = false },
                    modifier = Modifier.fillMaxWidth(),
                    variant = if (settings.tripletCount == count) ButtonVariant.Primary else ButtonVariant.Secondary,
                ) { Text("$count photos") }
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val api = BigShelfApp.api
    val settings = BigShelfApp.settings
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<SystemStats?>(null) }
    var statsError by remember { mutableStateOf(false) }
    var regenerating by remember { mutableStateOf(false) }

    // Live CPU / memory, polled like the web's refetchInterval
    LaunchedEffect(Unit) {
        while (true) {
            try {
                stats = api.systemStats()
                statsError = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                statsError = true
            }
            delay(SYSTEM_STATS_POLL_MS)
        }
    }

    Column(Modifier.fillMaxSize()) {
    PageHeader("Settings", leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) })
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsCard {
            ToggleRow("AI images on Home", "Show ChatGPT images in the Home grid.", settings.showAi, settings::updateShowAi)
            ToggleRow(
                "Archive on download",
                "Move photos to Archive after downloading them.",
                settings.archiveOnDownload,
                settings::updateArchiveOnDownload,
                divider = true,
            )
            ToggleRow(
                "Autosplit on upload",
                "Split collages and long images into separate photos on upload.",
                settings.autosplit,
                settings::updateAutosplit,
                divider = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("Matches", "How Matches picks and matches each set of photos.")
            TripletSettingsCard()
        }

        SkippedPeople()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("System")
            SettingsCard {
                val current = stats
                when {
                    statsError -> Text(
                        "Unavailable",
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = BsText.Xs,
                        color = BsColors.Muted,
                    )
                    current == null -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Spinner(size = 16.dp, tint = BsColors.Muted)
                    }
                    else -> listOf("CPU" to current.cpuPercent, "Memory" to current.memoryPercent).forEachIndexed { i, (label, value) ->
                        Column(
                            Modifier.divider(i > 0).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(label, Modifier.weight(1f), style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
                                Text("${value.toInt()}%", style = BsText.Sm, color = BsColors.Muted)
                            }
                            BsProgress(value)
                        }
                    }
                }
                // Outside the stats conditional: maintenance works even when stats fail to load
                SettingRow("Thumbnails", null, Modifier.divider(true)) {
                    BsButton(
                        onClick = {
                            regenerating = true
                            scope.launch {
                                try {
                                    if (api.regenerateThumbnails().status == "started") toast.success("Regenerating thumbnails in the background")
                                    else toast.info("Thumbnails are already being regenerated")
                                } catch (_: Exception) {
                                    toast.error("Couldn't start regenerating thumbnails")
                                } finally {
                                    regenerating = false
                                }
                            }
                        },
                        variant = ButtonVariant.Secondary,
                        enabled = !regenerating,
                    ) {
                        // Text only: with an icon the button squeezed "Thumbnails" into "Thumbn/ails"
                        if (regenerating) Spinner(size = 16.dp) else Text("Regenerate")
                    }
                }
                if (Updater.supported()) AutoUpdateRow()
            }
        }
        // Also at the bottom of Library; here because the Home avatar opens Settings
        SettingsCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .bsClickable(scale = 1f) { scope.launch { AuthStore.logout() } }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BsIcon(BsIcons.SignOut, size = 20.dp, tint = BsColors.Danger)
                Text("Log out", style = BsText.Sm, color = BsColors.Danger)
            }
        }
        Box(Modifier.height(8.dp))
    }
    }
}

/** Automatic backup of this phone's photos and videos, and the storage they take on BigShelf. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val api = BigShelfApp.api
    val backup = BigShelfApp.backup
    val work by remember { backup.workInfo() }.collectAsState(emptyList())
    val progress = work.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress
    val total = progress?.getInt(BackupWorker.TOTAL, 0) ?: 0
    // Recounted as the run moves on; null without media permission
    val waiting by produceState<Int?>(null, backup.enabled, backup.paused, progress) {
        value = if (backup.hasPermission()) withContext(Dispatchers.IO) { backup.pending().size } else null
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (backup.hasPermission()) backup.updateEnabled(true)
        else toast.error("Allow access to photos and videos to back them up")
    }
    val storage = rememberQuery("storage", invalidatedBy = setOf("photos")) { api.storage() }
    val counts = rememberQuery("storage-counts", invalidatedBy = setOf("photos", "bin-photos", "archive-photos")) {
        coroutineScope {
            val library = async { api.photos(1, size = 1).total }
            val archive = async { api.archiveFolders().sumOf { it.count } }
            val bin = async { api.bin(1, size = 1).total }
            listOf("Library" to library.await(), "Archive" to archive.await(), "Bin" to bin.await())
        }
    }

    val (status, statusHint) = when {
        backup.paused -> "Paused" to waiting?.let { "$it waiting" }
        total > 0 -> "Backing up ${progress!!.getInt(BackupWorker.DONE, 0) + 1} of $total" to null
        waiting == 0 -> "Up to date" to null
        else -> (waiting?.let { "$it waiting" } ?: "On") to "Wi-Fi only"
    }

    Column(Modifier.fillMaxSize()) {
    PageHeader("Backup & storage", leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) })
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsCard {
            ToggleRow("Back up this phone", null, backup.enabled, { on ->
                if (on) permissions.launch(Backup.requestedPermissions.toTypedArray()) else backup.updateEnabled(false)
            })
            if (backup.enabled) {
                SettingRow(status, statusHint, Modifier.divider(true)) {
                    BsButton(onClick = { backup.updatePaused(!backup.paused) }, variant = ButtonVariant.Secondary) {
                        BsIcon(if (backup.paused) BsIcons.Play else BsIcons.Pause, size = 16.dp)
                        Text(if (backup.paused) "Resume" else "Pause")
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Storage")
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        storage.data?.let { formatBytes(it.usedBytes) } ?: if (storage.isError) "Unavailable" else "…",
                        style = BsText.Xl2.copy(fontWeight = BsWeight.Medium),
                    )
                    Text("used", style = BsText.Xs, color = BsColors.Muted)
                }
                counts.data?.forEach { (label, n) ->
                    Row(Modifier.fillMaxWidth().divider(true).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(label, Modifier.weight(1f), style = BsText.Sm)
                        Text("%,d %s".format(n, if (n == 1) "item" else "items"), style = BsText.Sm, color = BsColors.Muted)
                    }
                }
            }
        }
        Box(Modifier.height(8.dp))
    }
    }
}
