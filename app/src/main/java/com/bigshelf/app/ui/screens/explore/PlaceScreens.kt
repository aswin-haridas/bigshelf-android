package com.bigshelf.app.ui.screens.explore

import android.content.Context
import com.bigshelf.app.ui.shell.LocalBottomBarInset
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Point
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bigshelf.app.BigShelfApp
import com.bigshelf.app.BuildConfig
import com.bigshelf.app.data.PhotoFilter
import com.bigshelf.app.data.PhotoLocation
import com.bigshelf.app.data.PlaceNames
import com.bigshelf.app.state.rememberQuery
import com.bigshelf.app.ui.Routes
import com.bigshelf.app.ui.components.BsIconButton
import com.bigshelf.app.ui.components.EmptyState
import com.bigshelf.app.ui.components.LoadingText
import com.bigshelf.app.ui.components.PageHeader
import com.bigshelf.app.ui.components.bsClickable
import com.bigshelf.app.ui.photos.photoCount
import com.bigshelf.app.ui.theme.BsColors
import com.bigshelf.app.ui.theme.BsIcons
import com.bigshelf.app.ui.theme.BsShapes
import com.bigshelf.app.ui.theme.BsText
import com.bigshelf.app.ui.theme.BsWeight
import com.bigshelf.app.ui.theme.MonoFamily
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import java.io.File

/** The town name for a point (cached Geocoder lookup); null while loading or unknown. */
@Composable
fun rememberPlaceName(lat: Double?, lon: Double?): String? {
    val context = LocalContext.current
    val name by produceState<String?>(null, lat, lon) {
        value = if (lat != null && lon != null) PlaceNames.name(context, lat, lon) else null
    }
    return name
}

/** Every place, busiest first; the header opens the map. */
@Composable
fun PlacesScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val places = rememberQuery("places", invalidatedBy = setOf("photos")) { PlaceNames.places(context) }
    val list = places.data.orEmpty()
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Places",
            count = countLabel(list.size, "place"),
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        ) {
            BsIconButton(BsIcons.MapTrifold, "Map", onClick = { onNavigate(Routes.map()) }, tint = BsColors.Foreground)
        }
        when {
            places.isLoading && places.data == null -> LoadingText(text = "Finding places…")
            list.isEmpty() -> EmptyState("No places yet")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + LocalBottomBarInset.current),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list, key = { "${it.name}-${it.latitude}" }) { place ->
                    CoverCard(
                        place.name,
                        photoCount(place.count),
                        place.cover,
                        onClick = { onNavigate(Routes.filtered(place.name, PhotoFilter(bbox = place.bbox))) },
                    )
                }
            }
        }
    }
}

/** A bubble on the map: photos whose pins fall in one screen cell at this zoom. */
private data class Cluster(val x: Float, val y: Float, val members: List<PhotoLocation>)

// Pins closer than this on screen share a bubble
private val CLUSTER_CELL = 64.dp
private val BUBBLE = 44.dp

private fun mapView(context: Context): MapView {
    // OSM's tile policy wants a real user agent; tiles cache in the app's cache dir
    Configuration.getInstance().apply {
        userAgentValue = BuildConfig.APPLICATION_ID
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 2.0
        maxZoomLevel = 19.0
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
        // Dark app: invert the light OSM tiles, then drop their colour to sit on the neutral UI
        overlayManager.tilesOverlay.setColorFilter(
            ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ).apply { postConcat(ColorMatrix().apply { setSaturation(0f) }) },
            ),
        )
    }
}

/**
 * Where photos were taken: OpenStreetMap with a count bubble per group of nearby photos,
 * regrouped as you zoom. A bubble opens its photos. Opened from a photo's Info, it starts
 * centred on [lat]/[lon]; otherwise it fits every geotagged photo.
 */
@Composable
fun MapScreen(lat: Double?, lon: Double?, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val locations = rememberQuery("locations", invalidatedBy = setOf("photos")) { BigShelfApp.api.locations() }
    val map = remember { mapView(context) }
    // Bumped on every pan/zoom so the bubbles regroup
    var moved by remember { mutableIntStateOf(0) }
    var placed by remember { mutableStateOf(false) }

    DisposableEffect(map) {
        // Undelayed, so bubbles follow the map frame by frame (a few thousand projections)
        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { moved++; return false }
            override fun onZoom(event: ZoomEvent?): Boolean { moved++; return false }
        })
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }

    // First fit: the photo it was opened for, else all of them
    LaunchedEffect(locations.data) {
        val points = locations.data ?: return@LaunchedEffect
        if (placed) return@LaunchedEffect
        val place = {
            when {
                lat != null && lon != null -> {
                    map.controller.setZoom(15.0)
                    map.controller.setCenter(GeoPoint(lat, lon))
                }
                points.isEmpty() -> map.controller.setZoom(2.0)
                else -> {
                    val box = BoundingBox(
                        points.maxOf { it.latitude }, points.maxOf { it.longitude },
                        points.minOf { it.latitude }, points.minOf { it.longitude },
                    )
                    // One spot (or a few metres apart): a town-level view instead of max zoom
                    if (box.latitudeSpan < 0.01 && box.longitudeSpanWithDateLine < 0.01) {
                        map.controller.setZoom(13.0)
                        map.controller.setCenter(box.centerWithDateLine)
                    } else {
                        map.zoomToBoundingBox(box, false, with(density) { 48.dp.roundToPx() })
                    }
                }
            }
            placed = true
            moved++
        }
        if (map.isLayoutOccurred) place() else map.addOnFirstLayoutListener { _, _, _, _, _ -> place() }
    }

    val cell = with(density) { CLUSTER_CELL.toPx() }
    val clusters = remember(moved, locations.data) {
        val points = locations.data.orEmpty()
        if (!placed || points.isEmpty() || map.width == 0) return@remember emptyList()
        val projection = map.projection
        val point = Point()
        val buckets = HashMap<Long, MutableList<Pair<PhotoLocation, Point>>>()
        for (loc in points) {
            projection.toPixels(GeoPoint(loc.latitude, loc.longitude), point)
            if (point.x < -cell || point.y < -cell || point.x > map.width + cell || point.y > map.height + cell) continue
            val key = (Math.floorDiv(point.x, cell.toInt()).toLong() shl 32) or (Math.floorDiv(point.y, cell.toInt()).toLong() and 0xffffffffL)
            buckets.getOrPut(key) { mutableListOf() } += loc to Point(point)
        }
        buckets.values.map { members ->
            Cluster(
                members.map { it.second.x }.average().toFloat(),
                members.map { it.second.y }.average().toFloat(),
                members.map { it.first },
            )
        }
    }

    fun open(cluster: Cluster) {
        val m = cluster.members
        // Half-open on the max side server-side, so nudge it past the last pin
        val bbox = listOf(m.minOf { it.latitude }, m.minOf { it.longitude }, m.maxOf { it.latitude } + 1e-6, m.maxOf { it.longitude } + 1e-6)
        scope.launch {
            val name = PlaceNames.name(context, m.first().latitude, m.first().longitude)
            // A zoomed-out bubble can cover several towns
            val spread = FloatArray(1).also {
                Location.distanceBetween(bbox[0], bbox[1], bbox[2], bbox[3], it)
            }[0] > 60_000f
            val title = when {
                name == null -> "Map area"
                spread -> "$name and nearby"
                else -> name
            }
            onNavigate(Routes.filtered(title, PhotoFilter(bbox = bbox)))
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Map",
            count = locations.data?.let { "${photoCount(it.size)} with a location" },
            leading = { BsIconButton(BsIcons.ArrowLeft, "Back", onClick = onBack, tint = BsColors.Foreground) },
        )
        Box(Modifier.weight(1f).clip(BsShapes.Sm)) {
            AndroidView({ map }, Modifier.fillMaxSize())
            val radius = with(density) { (BUBBLE / 2).roundToPx() }
            clusters.forEach { cluster ->
                Box(
                    Modifier
                        .offset { IntOffset(cluster.x.toInt() - radius, cluster.y.toInt() - radius) }
                        .size(BUBBLE)
                        .clip(BsShapes.Full)
                        .background(BsColors.Primary)
                        .border(2.dp, BsColors.Foreground, BsShapes.Full)
                        .bsClickable { open(cluster) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (cluster.members.size > 999) "999+" else "${cluster.members.size}",
                        style = BsText.Xs2.copy(fontWeight = BsWeight.Semibold, fontFamily = MonoFamily),
                        color = BsColors.Foreground,
                    )
                }
            }
            if (locations.data?.isEmpty() == true) {
                Text(
                    "No located photos",
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .clip(BsShapes.Lg)
                        .background(BsColors.Background.copy(alpha = 0.9f))
                        .padding(16.dp),
                    style = BsText.Xs,
                    color = BsColors.MutedSoft,
                )
            }
        }
    }
}
