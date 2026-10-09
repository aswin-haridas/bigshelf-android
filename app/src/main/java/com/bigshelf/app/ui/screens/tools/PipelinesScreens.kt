package com.bigshelf.app.ui.screens.tools

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.bigshelf.app.ui.theme.Neutral
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

/** "2m 05s"-style elapsed time; runs still going count up to now (web `duration`). */
private fun duration(run: Run): String {
    val start = instant(run.startedAt) ?: return "—"
    val secs = Duration.between(start, instant(run.finishedAt) ?: Instant.now()).seconds.coerceAtLeast(0)
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s.toString().padStart(2, '0')}s"
        else -> "${s}s"
    }
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
            data == null -> EmptyState("Couldn't load pipelines", description = "Check your connection and try again.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
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
                if (run.status == "queued") listOf("Waiting behind" to "${run.queuePosition ?: 0} runs")
                else listOf("Done" to "${run.done}", "Failed" to "${run.failed}", "Duration" to duration(run)),
            ),
        )
    }
    return listOf(Step("select", "Select photos", "success", listOf("Trigger" to "Manual", "Matched" to "${run.total} photo${if (run.total == 1) "" else "s"}", "Created" to clock(run.createdAt)))) +
        pipelineSteps +
        Step("refresh", "Refresh views", if (run.finishedAt != null) "success" else "queued", listOf("Views" to "Photos, faces, people", "Finished" to clock(run.finishedAt)))
}

/**
 * Port of RunDetail.tsx for phones: status card, the steps as a row of chips (the web's node
 * graph), the picked step's numbers, then its log lines following the tail like a CI console.
 */
@Composable
fun RunScreen(runId: String, onBack: () -> Unit) {
    val api = BigShelfApp.api
    val scope = rememberCoroutineScope()
    val query = rememberQuery("run", runId, invalidatedBy = setOf("pipelines")) { api.run(runId) }
    val names = rememberQuery("pipelines", invalidatedBy = setOf("pipelines")) { api.pipelines() }
    val run = query.data
    var picked by remember { mutableStateOf<String?>(null) }
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
            pipelineName,
            count = "#$runId",
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
                    variant = ButtonVariant.Secondary,
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
        // Until a step is picked, the first pipeline step - that's where the action is
        val step = steps.firstOrNull { it.id == picked } ?: steps[1]
        // Staged pipelines have no "run" node, so run-wide lines show with every stage
        val isStage = run.stages.any { it.id == step.id }
        val logs = run.logs.filter { it.step == step.id || (isStage && it.step == "run") }

        // Status card
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(BsShapes.Lg)
                .background(BsColors.Surface)
                .border(1.dp, BsColors.Border, BsShapes.Lg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusIcon(run.status, 20)
                Column(Modifier.weight(1f)) {
                    Text(run.status.replaceFirstChar { it.uppercase() }, style = BsText.Sm.copy(fontWeight = BsWeight.Semibold))
                    Text(
                        "${run.done} ok · ${run.failed} failed · ${duration(run)}",
                        style = BsText.Xs2.copy(fontFamily = MonoFamily),
                        color = BsColors.MutedFaint,
                    )
                }
                Text("${run.done + run.failed}/${run.total}", style = BsText.Xs.copy(fontFamily = MonoFamily), color = BsColors.Muted)
            }
            BsProgress(run.percent.toFloat())
        }

        // Steps as chips
        LazyRow(
            Modifier.padding(top = 16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(steps, key = { it.id }) { s ->
                val selected = s.id == step.id
                Row(
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(BsShapes.Full)
                        .background(if (selected) Neutral.N800 else BsColors.Surface)
                        .border(1.dp, if (selected) BsColors.BorderFocus else BsColors.Border, BsShapes.Full)
                        .bsClickable(scale = 0.96f) { picked = s.id }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusIcon(s.status, 14)
                    Text(s.title, style = BsText.Xs.copy(fontWeight = BsWeight.Medium), color = if (selected) BsColors.Foreground else BsColors.Muted)
                }
            }
        }

        // The picked step's numbers
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            step.rows.forEach { (label, value) ->
                Column {
                    Text(label, style = BsText.Xs2, color = BsColors.MutedFaint)
                    Text(value, style = BsText.Xs.copy(fontFamily = MonoFamily), color = BsColors.MutedSoft)
                }
            }
        }

        // Logs, following the tail
        Text(
            "${logs.size} log lines",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = BsText.Xs2.copy(fontFamily = MonoFamily),
            color = BsColors.MutedFaint,
        )
        val logState = rememberLazyListState()
        LaunchedEffect(logs.size) { if (logs.isNotEmpty()) logState.animateScrollToItem(logs.lastIndex) }
        LazyColumn(
            Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxWidth()
                .clip(BsShapes.Lg)
                .background(Color.Black.copy(alpha = 0.4f))
                .border(1.dp, BsColors.Border, BsShapes.Lg),
            state = logState,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(logs) { i, line ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", Modifier.width(24.dp), style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.MutedFaint)
                    Column {
                        Text(clock(line.timestamp), style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.MutedFaint)
                        Text(
                            line.message,
                            style = BsText.Xs2.copy(fontFamily = MonoFamily),
                            color = if (line.level == "error") BsColors.Danger else BsColors.MutedSoft,
                        )
                    }
                }
            }
            if (logs.isEmpty()) {
                item { Text("No log lines yet", Modifier.padding(12.dp), style = BsText.Xs2.copy(fontFamily = MonoFamily), color = BsColors.MutedFaint) }
            }
        }
    }
}
