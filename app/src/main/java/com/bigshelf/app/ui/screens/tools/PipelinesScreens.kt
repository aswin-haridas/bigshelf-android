package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.Canvas
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.data.Run
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.components.BsButton
import com.bigshelf.app.ui.components.BsIcon
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.BsProgress
import com.bigshelf.app.ui.components.ButtonVariant
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.Spinner
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.components.toast
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.MonoFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// The SSE stream the web uses is replaced by polling: fast while a run moves, slow otherwise
private const val ACTIVE_POLL_MS = 2000L
private const val IDLE_POLL_MS = 15000L

/** ISO timestamp from the API; naive ones are UTC. */
private fun instant(iso: String?): Instant? = iso?.let {
    runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC) }.getOrNull()
}

private val CLOCK = DateTimeFormatter.ofPattern("h:mm:ss a").withZone(ZoneId.systemDefault())
private fun clock(iso: String?) = instant(iso)?.let(CLOCK::format) ?: "—"

/** "2m 05s"-style elapsed time. */
private fun fmtSecs(secs: Long): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s.toString().padStart(2, '0')}s"
        else -> "${s}s"
    }
}

/** Runs still going count up to now (web `duration`). */
private fun duration(run: Run): String {
    val start = instant(run.startedAt) ?: return "—"
    return fmtSecs(Duration.between(start, instant(run.finishedAt) ?: Instant.now()).seconds.coerceAtLeast(0))
}

/** "5m ago" (web `ago`). */
private fun ago(iso: String?): String {
    val at = instant(iso) ?: return ""
    val mins = Duration.between(at, Instant.now()).toMinutes()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${(mins + 30) / 60}h ago"
        else -> "${(mins + 720) / 1440}d ago"
    }
}

/** Port of StatusIcon.tsx. */
@Composable
private fun StatusIcon(status: String, size: Int = 16) {
    when (status) {
        "running" -> Spinner(size = size.dp, tint = BsColors.Info)
        "queued" -> BsIcon(BsIcons.Clock, size = size.dp, tint = BsColors.Muted)
        "success" -> BsIcon(BsIcons.CheckCircleFill, size = size.dp, tint = BsColors.Success)
        "failed" -> BsIcon(BsIcons.XCircleFill, size = size.dp, tint = BsColors.Danger)
        else -> BsIcon(BsIcons.Prohibit, size = size.dp, tint = BsColors.MutedFaint)
    }
}

/** Port of ProgressRing: 40dp ring with the percentage in the middle. */
@Composable
private fun ProgressRing(value: Int, tone: Color) {
    val track = BsColors.BorderFocus
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(3.dp.toPx()))
            drawArc(tone, -90f, 360f * value / 100f, false, Offset(inset, inset), arcSize, style = stroke)
        }
        Text("$value", style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.Muted)
    }
}

/**
 * Port of PipelineCard.tsx: name and description on top, last run bottom-left, and
 * bottom-right the run button - or the progress ring while a run is in flight.
 */
@Composable
private fun PipelineCard(
    name: String,
    caption: String,
    lastRun: Run? = null,
    onOpen: (() -> Unit)? = null,
    onRun: (() -> Unit)? = null,
    starting: Boolean = false,
    progress: Int? = null,
    tone: Color? = null,
) {
    val active = lastRun?.isActive == true
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BsShapes.Xl)
            .background(BsColors.Surface)
            .border(1.dp, BsColors.Border, BsShapes.Xl)
            .then(if (onOpen != null) Modifier.bsClickable(scale = 0.98f, onClick = onOpen) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(name, style = BsText.Sm.copy(fontWeight = BsWeight.Semibold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, Modifier.padding(top = 2.dp), style = BsText.Xs, color = BsColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (lastRun != null) {
                    StatusIcon(lastRun.status, 14)
                    val state = if (lastRun.status == "queued" && (lastRun.queuePosition ?: 0) > 0) "waiting behind ${lastRun.queuePosition}" else lastRun.status
                    Text(
                        buildString {
                            append("$state · ${ago(lastRun.createdAt)}")
                            if (lastRun.startedAt != null) append(" · ${duration(lastRun)}")
                            if (lastRun.failed > 0) append(" · ${lastRun.failed} failed")
                        },
                        style = BsText.Xs2.copy(fontFamily = MonoFamily),
                        color = BsColors.MutedFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (onRun != null) {
                    Text("Never run", style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.MutedFaint)
                }
            }
            if (onRun != null && !active) {
                BsIconButton(BsIcons.PlayFill, "Run $name", onClick = onRun, loading = starting, tint = BsColors.Foreground)
            } else {
                ProgressRing(
                    progress ?: lastRun?.percent ?: 0,
                    tone ?: if ((lastRun?.failed ?: 0) > 0) BsColors.Danger else BsColors.Primary,
                )
            }
        }
    }
}

/** Port of modules/ai/Pipelines.tsx: one card per pipeline plus the face tagging progress. */
@Composable
fun PipelinesScreen(onBack: () -> Unit, onRun: (String) -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val query = rememberQuery("pipelines", invalidatedBy = setOf("pipelines")) { api.pipelines() }
    val data = query.data
    var starting by remember { mutableStateOf<String?>(null) }

    val anyActive = data?.runs?.any { it.isActive } == true
    LaunchedEffect(anyActive) {
        while (true) {
            delay(if (anyActive) ACTIVE_POLL_MS else IDLE_POLL_MS)
            query.refetch()
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Pipelines", leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) })
        when {
            data == null && query.isLoading -> LoadingText()
            data == null -> EmptyState("Couldn't load pipelines")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalBottomBarInset.current),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Face tagging runs per upload, so it's a progress-only card with no run button
                val faces = data.faceTagging
                if (faces != null && faces.total > 0) {
                    item(key = "faces") {
                        PipelineCard(
                            name = "Face tagging",
                            caption = "${faces.done}/${faces.total} photos" + if (faces.failed > 0) " · ${faces.failed} failed" else "",
                            progress = faces.done * 100 / faces.total,
                            tone = when {
                                faces.failed > 0 -> BsColors.Danger
                                faces.done >= faces.total -> BsColors.Success
                                else -> BsColors.Primary
                            },
                        )
                    }
                }
                items(data.pipelines, key = { it.id }) { p ->
                    // Runs come newest first, so the first match is the latest run
                    val latest = data.runs.firstOrNull { it.pipeline == p.id }
                    PipelineCard(
                        name = p.name,
                        caption = p.description,
                        lastRun = latest,
                        onOpen = latest?.let { { onRun(it.id) } },
                        starting = starting == p.id,
                        onRun = {
                            if (starting == null) {
                                starting = p.id
                                scope.launch {
                                    try {
                                        val run = api.startRun(p.id)
                                        toast.success(if ((run.queuePosition ?: 0) > 0) "Run queued behind the current one" else "Run started")
                                        query.refetch()
                                        onRun(run.id)
                                    } catch (_: Exception) {
                                        toast.error("Failed to start pipeline")
                                    } finally {
                                        starting = null
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** One node of the run (RunGraph `Step`): picking photos, the pipeline or its stages, refreshing views. */
private data class Step(val id: String, val title: String, val status: String, val rows: List<Pair<String, String>>)

/** Web `stepsOf`: everything derives from the run itself. */
private fun stepsOf(run: Run, pipelineName: String): List<Step> {
    val pipelineSteps = if (run.stages.isNotEmpty()) {
        run.stages.map { stage ->
            // Spinning while the current photo is in it, failed once any photo failed there
            val status = when {
                run.isActive && run.stage == stage.id -> "running"
                stage.failed > 0 -> "failed"
                stage.done > 0 -> "success"
                run.isActive -> "queued"
                run.status == "success" -> "success"
                else -> "cancelled"
            }
            Step(
                stage.id, stage.name, status,
                listOf(
                    "Done" to "${stage.done}/${run.total}",
                    "Failed" to "${stage.failed}",
                    "Avg / photo" to if (stage.done > 0) "%.1fs".format(stage.secs / stage.done) else "—",
                ),
            )
        }
    } else {
        listOf(
            Step(
                "run", pipelineName, run.status,
                if (run.status == "queued") listOf("Queue" to if ((run.queuePosition ?: 0) > 0) "${run.queuePosition} ahead" else "Next to start")
                else listOf("Done" to "${run.done}", "Failed" to "${run.failed}", "Duration" to duration(run)),
            ),
        )
    }
    return listOf(Step("select", "Select photos", "success", listOf("Trigger" to "Manual", "Matched" to "${run.total} photo${if (run.total == 1) "" else "s"}", "Created" to clock(run.createdAt)))) +
        pipelineSteps +
        Step("refresh", "Refresh views", if (run.finishedAt != null) "success" else "queued", listOf("Views" to "Photos, faces, people", "Finished" to clock(run.finishedAt)))
}

private val LOG_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

private fun statusTone(status: String): Color = when (status) {
    "running" -> BsColors.Info
    "success" -> BsColors.Success
    "failed" -> BsColors.Danger
    "queued" -> BsColors.Muted
    else -> BsColors.MutedFaint
}

private fun statusLabel(run: Run): String = when {
    run.status == "queued" && (run.queuePosition ?: 0) > 0 -> "Queued · ${run.queuePosition} ahead"
    run.status == "queued" -> "Next to start"
    else -> run.status.replaceFirstChar { it.uppercase() }
}

/** Rough time left at the pace so far; null until there's a pace to measure. */
private fun eta(run: Run): String? {
    if (run.status != "running") return null
    val start = instant(run.startedAt) ?: return null
    val processed = run.done + run.failed
    if (processed <= 0 || run.total <= processed) return null
    val elapsed = Duration.between(start, Instant.now()).seconds.coerceAtLeast(1)
    return fmtSecs(elapsed * (run.total - processed) / processed)
}

@Composable
private fun StatusPill(run: Run) {
    val tone = statusTone(run.status)
    Row(
        Modifier.clip(BsShapes.Full).background(tone.copy(alpha = 0.14f)).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusIcon(run.status, 14)
        Text(statusLabel(run), style = BsText.Xs2.copy(fontWeight = BsWeight.Medium), color = tone, maxLines = 1)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, tone: Color = BsColors.Foreground) {
    Column(modifier) {
        Text(label, style = BsText.Xs2, color = BsColors.MutedFaint)
        Text(value, style = BsText.Sm.copy(fontFamily = MonoFamily, fontWeight = BsWeight.Medium), color = tone, maxLines = 1)
    }
}

/** Headline card: one big percentage, the bar, and the numbers that used to repeat further down. */
@Composable
private fun RunHero(run: Run, pipelineName: String) {
    val processed = run.done + run.failed
    val left = eta(run)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(BsShapes.Xl)
            .background(BsColors.Surface)
            .border(1.dp, BsColors.Border, BsShapes.Xl)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(pipelineName, style = BsText.Sm.copy(fontWeight = BsWeight.Semibold))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${run.percent}", style = BsText.Xl4.copy(fontFamily = MonoFamily, fontWeight = BsWeight.Semibold))
                    Text("%", Modifier.padding(start = 2.dp, bottom = 6.dp), style = BsText.Base, color = BsColors.Muted)
                }
                Text("%,d of %,d photos".format(processed, run.total), style = BsText.Xs, color = BsColors.Muted)
            }
            StatusPill(run)
        }
        BsProgress(run.percent.toFloat(), height = 8.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Done", "%,d".format(run.done), Modifier.weight(1f), if (run.done > 0) BsColors.Success else BsColors.Foreground)
            Stat("Failed", "%,d".format(run.failed), Modifier.weight(1f), if (run.failed > 0) BsColors.Danger else BsColors.Foreground)
            Stat("Elapsed", duration(run), Modifier.weight(1f))
            if (left != null) Stat("Left", "~$left", Modifier.weight(1f), BsColors.Info)
        }
    }
}

/**
 * A single scrolling run summary, with expandable steps and compact activity.
 */
@Composable
fun RunScreen(runId: String, onBack: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val query = rememberQuery("run", runId, invalidatedBy = setOf("pipelines")) { api.run(runId) }
    val names = rememberQuery("pipelines", invalidatedBy = setOf("pipelines")) { api.pipelines() }
    val run = query.data
    var picked by remember(runId) { mutableStateOf<String?>(null) }
    var cancelling by remember { mutableStateOf(false) }

    val active = run?.isActive != false
    LaunchedEffect(active) {
        while (active) {
            delay(ACTIVE_POLL_MS)
            query.refetch()
        }
    }

    val pipelineName = run?.let { r -> names.data?.pipelines?.firstOrNull { it.id == r.pipeline }?.name ?: r.pipeline } ?: "Run"
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Run details",
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            if (run?.isActive == true) {
                BsButton(
                    onClick = {
                        cancelling = true
                        scope.launch {
                            try {
                                api.cancelRun(runId)
                                query.refetch()
                            } catch (_: Exception) {
                                toast.error("Failed to cancel run")
                            } finally {
                                cancelling = false
                            }
                        }
                    },
                    variant = ButtonVariant.Ghost,
                    contentColor = BsColors.Danger,
                    loading = cancelling,
                ) { Text("Cancel") }
            }
        }
        if (run == null) {
            if (query.isLoading) LoadingText() else EmptyState("Run not found")
            return@Column
        }

        val steps = stepsOf(run, pipelineName)
        val step = steps.firstOrNull { it.id == picked }
        val isStage = run.stages.any { it.id == step?.id }
        val logs = if (step == null) run.logs else run.logs.filter {
            it.step == step.id || (isStage && it.step == "run")
        }
        val listState = rememberLazyListState()
        var previousLogCount by remember(runId, picked) { mutableStateOf(logs.size) }
        // Follow new output only when already at the end; opening the screen or a step stays put.
        LaunchedEffect(logs.size, picked) {
            if (logs.size > previousLogCount && !listState.canScrollForward) {
                listState.animateScrollToItem(logs.lastIndex + 3)
            }
            previousLogCount = logs.size
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { RunHero(run, pipelineName) }
            item(key = "steps") {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("Steps", style = BsText.Xs.copy(fontWeight = BsWeight.Medium), color = BsColors.Muted)
                    steps.forEach { s ->
                        val selected = s.id == step?.id
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(BsShapes.Lg)
                                .semantics { stateDescription = if (selected) "Expanded" else "Collapsed" }
                                .background(if (selected) BsColors.Surface else Color.Transparent)
                                .bsClickable { picked = if (selected) null else s.id }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatusIcon(s.status, 16)
                                Text(s.title, Modifier.weight(1f), style = BsText.Xs, color = if (selected) BsColors.Foreground else BsColors.MutedSoft)
                                Text(if (selected) "−" else "+", style = BsText.Sm, color = BsColors.Muted)
                            }
                            if (selected) s.rows.forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(label, Modifier.weight(1f), style = BsText.Xs2, color = BsColors.MutedFaint)
                                    Text(value, Modifier.weight(1f), style = BsText.Xs2, color = BsColors.MutedSoft)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "activity") {
                if (logs.isNotEmpty()) {
                    Text(if (step == null) "Activity" else "Activity · ${step.title}", style = BsText.Xs, color = BsColors.Muted)
                } else {
                    Text(
                        when {
                            step != null -> "No activity for this step yet."
                            run.status == "queued" -> "Activity will appear when this run starts."
                            run.isActive -> "Waiting for activity…"
                            else -> "No activity recorded."
                        },
                        style = BsText.Xs2, color = BsColors.MutedFaint,
                    )
                }
            }
            itemsIndexed(logs) { _, line ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(instant(line.timestamp)?.let(LOG_CLOCK::format) ?: "—", style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.MutedFaint)
                    Text(
                        line.message,
                        style = BsText.Xs2.copy(fontFamily = MonoFamily),
                        color = when (line.level) {
                            "error" -> BsColors.Danger
                            "warn", "warning" -> BsColors.Warning
                            else -> BsColors.MutedSoft
                        },
                    )
                }
            }
            item(key = "run-id") {
                Text("Run #$runId", Modifier.padding(top = 8.dp), style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.MutedFaint)
            }
        }
    }
}
