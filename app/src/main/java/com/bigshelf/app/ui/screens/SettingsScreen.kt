package com.bigshelf.app.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.SystemStats
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsProgress
import com.bigshelf.app.ui.components.BsSwitch
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
private fun SettingRow(label: String, hint: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
            Text(hint, style = BsText.Xs, color = BsColors.Muted)
        }
        control()
    }
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = false) {
    // The whole row toggles, like the web's wrapping <label>
    SettingRow(label, hint, Modifier.divider(divider).bsClickable(scale = 1f) { onChange(!checked) }) {
        BsSwitch(checked, onChange)
    }
}

@Composable
private fun SectionTitle(title: String, description: String) {
    Column {
        Text(title, style = BsText.Sm.copy(fontWeight = BsWeight.Medium))
        Text(description, style = BsText.Xs, color = BsColors.Muted)
    }
}

/** Port of modules/settings/settings.tsx - the parts that apply to the Android app. */
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsCard {
            ToggleRow("AI images in Home page", "Show ChatGPT images in the Home grid.", settings.showAi, settings::updateShowAi)
            ToggleRow(
                "Archive on download",
                "Move photos to Archive after downloading them.",
                settings.archiveOnDownload,
                settings::updateArchiveOnDownload,
                divider = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("System", "Live use of the machine running Bigshelf, updated every few seconds.")
            SettingsCard {
                val current = stats
                when {
                    statsError -> Text(
                        "Unable to load system stats.",
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
                SettingRow("Thumbnails", "Rebuild the grid thumbnails of all your photos.", Modifier.divider(true)) {
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
