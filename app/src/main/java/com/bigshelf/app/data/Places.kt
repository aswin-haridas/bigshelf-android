package com.bigshelf.app.data

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.bigshelf.app.BigShelfApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A place on the Search tab: every place cell the Geocoder gave the same name, merged. */
data class NamedPlace(
    val name: String,
    val count: Int,
    // [minLat, minLon, maxLat, maxLon] covering all its cells, for GET /photos?bbox=
    val bbox: List<Double>,
    val cover: Photo?,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Town names for coordinates, from the platform Geocoder. Names are cached in memory and in
 * SharedPreferences (keyed to ~1 km), since each lookup is a network round trip.
 */
object PlaceNames {
    private val memory = ConcurrentHashMap<String, String>()
    private val lanes = Semaphore(4)

    private fun key(lat: Double, lon: Double) = String.format(Locale.US, "%.2f,%.2f", lat, lon)

    suspend fun name(context: Context, lat: Double, lon: Double): String? {
        val key = key(lat, lon)
        memory[key]?.let { return it }
        val prefs = context.getSharedPreferences("bigshelf-place-names", Context.MODE_PRIVATE)
        prefs.getString(key, null)?.let { memory[key] = it; return it }
        if (!Geocoder.isPresent()) return null
        val name = lanes.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    // The listener overload (API 33+) does the same lookup; this one covers API 26
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                }.getOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea ?: it.countryName }
            }
        } ?: return null
        memory[key] = name
        prefs.edit().putString(key, name).apply()
        return name
    }

    /** Coordinates as a fallback label: "12.97°, 77.59°". */
    fun coordinates(lat: Double, lon: Double) = String.format(Locale.US, "%.2f°, %.2f°", lat, lon)

    /**
     * The backend's place cells, named and merged by name, busiest first. Only cells within
     * [MERGE_KM] of the name's busiest cell merge, so two towns that share a name stay apart.
     */
    suspend fun places(context: Context): List<NamedPlace> = coroutineScope {
        val cells = BigShelfApp.api.places()
        val names = cells.map { c -> async { name(context, c.latitude, c.longitude) } }.awaitAll()
        val places = mutableListOf<NamedPlace>()
        cells.zip(names).groupBy { (cell, name) -> name ?: coordinates(cell.latitude, cell.longitude) }
            .forEach { (name, group) ->
                var rest = group.map { it.first }.sortedByDescending { it.count }
                while (rest.isNotEmpty()) {
                    val head = rest.first()
                    val (near, far) = rest.partition { distanceKm(head, it) <= MERGE_KM }
                    places += merge(name, near)
                    rest = far
                }
            }
        places.sortedByDescending { it.count }
    }

    private const val MERGE_KM = 60f

    private fun distanceKm(a: PlaceCell, b: PlaceCell): Float {
        val out = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
        return out[0] / 1000f
    }

    // Cells arrive busiest first; the first one sets the cover and centre
    private fun merge(name: String, cells: List<PlaceCell>): NamedPlace {
        val boxes = cells.map { it.bbox }.filter { it.size == 4 }
        return NamedPlace(
            name = name,
            count = cells.sumOf { it.count },
            bbox = listOf(boxes.minOf { it[0] }, boxes.minOf { it[1] }, boxes.maxOf { it[2] }, boxes.maxOf { it[3] }),
            cover = cells.first().cover,
            latitude = cells.first().latitude,
            longitude = cells.first().longitude,
        )
    }
}
