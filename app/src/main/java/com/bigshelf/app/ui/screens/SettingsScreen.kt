package com.bigshelf.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import com.bigshelf.app.state.rememberQuery
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

/** `rounded-xl border border-border bg-surface/30` card; rows draw the `divide-y` line themselves. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BsShapes.Xl)
            .background(BsColors.Surface.copy(alpha = 0.3f))
            .border(1.dp, BsColors.Border, BsShapes.Xl),
    ) { content() }
}

/** `divide-y divide-border`: a hairline above every row but the first. */
private fun Modifier.divider(show: Boolean) = drawBehind {
    if (show) drawLine(BsColors.Border, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
}

/** Label + hint on the left, control on the right (`px-4 py-3`). */
@Composable
private fun SettingRow(label: String, hint: String?, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
    SettingRow(label, hint, Modifier.divider(divider).bsClickable(scale = 1f) { onChange(!checked) }) {
        BsSwitch(checked, onChange)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
}

/** Section title with a one-line explanation under it. */
@Composable
private fun Section(title: String, hint: String) {
    Column {
        SectionTitle(title)
        Text(hint, style = BsText.Xs, color = BsColors.Muted)
    }
}

// Triplets set sizes, the web slider's range (3-6)
private val SET_SIZES = (3..6).map { TabItem(it, "$it") }

/** People hidden with Skip on Triptych / Triplets, each with an Unskip to bring them back. */
@Composable
private fun SkippedPeople() {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val people = rememberQuery("people-all", invalidatedBy = setOf("people")) { api.people().items }
    val skipped = people.data.orEmpty().filter { it.isSkipped }
    var pending by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Section("Skipped people", "Skipped people never appear in Triptych or Triplets.")
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
                    "No skipped people. Use Skip on the Triptych page to hide someone.",
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
                                        toast.success("${person.name} will appear in Triptych and Triplets again")
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
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("Triptych", "How Triptych groups photos of one outfit.")
            SettingsCard {
                ToggleRow("AI images", "Let Triptych use AI images, at most one per set.", settings.triptychAi, settings::updateTriptychAi)
                ToggleRow(
                    "Skip small sets", "Leave out sets of 3 or fewer photos.",
                    settings.triptychSkipSmall, settings::updateTriptychSkipSmall, divider = true,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("Triplets", "How Triplets picks each set of one person's photos.")
            SettingsCard {
                ToggleRow("AI images", "Let Triplets pick AI images.", settings.includeAi, settings::updateIncludeAi)
                ToggleRow(
                    "Solo photos only", "Skip group photos with other tagged people.",
                    settings.tripletSolo, settings::updateTripletSolo, divider = true,
                )
                Column(
                    Modifier.divider(true).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text("Photos per set", style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
                        Text("People with fewer photos than this are left out.", style = BsText.Xs, color = BsColors.Muted)
                    }
                    BsTabs(SET_SIZES, settings.tripletCount, settings::updateTripletCount)
                }
            }
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
                        if (regenerating) Spinner(size = 16.dp) else BsIcon(BsIcons.ArrowsClockwise, size = 16.dp)
                        Text("Regenerate")
                    }
                }
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
