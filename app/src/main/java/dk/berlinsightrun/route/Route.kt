package dk.berlinsightrun.route

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class Waypoint(val name: String, val lat: Double, val lon: Double, val street: String)

data class Poi(
    val n: Int,
    val names: Map<String, String>,
    val km: Double,
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
    /** Sti med {lang}, fx "audio/{lang}/06.ogg". */
    val audioTemplate: String?,
    val texts: Map<String, String>,
) {
    /** Udløsningspunktets position på ruten i gade-meter. */
    val atM: Double get() = km * 1000.0

    fun name(lang: String): String = names[lang] ?: names["en"] ?: names.values.first()
    fun text(lang: String): String = texts[lang] ?: texts["en"] ?: texts.values.first()
    fun audioAsset(lang: String): String? = audioTemplate?.replace("{lang}", lang)
}

/** Et sving: [dir] er 1 for højre og -1 for venstre. Teksten laves af sprogmodulet. */
data class Maneuver(
    val atM: Double,
    val dir: Int,
    val sharp: Boolean,
    val street: String,
)

data class RoutePosition(val lat: Double, val lon: Double, val segment: Int, val headingDeg: Double)

data class Projection(val progressM: Double, val distanceM: Double, val segment: Int)

object Geo {
    private const val R = 6_371_000.0
    private fun rad(d: Double) = Math.toRadians(d)

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = rad(lat2 - lat1)
        val dLon = rad(lon2 - lon1)
        val h = sin(dLat / 2).let { it * it } +
            cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * R * asin(sqrt(h))
    }

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(rad(lon2 - lon1)) * cos(rad(lat2))
        val x = cos(rad(lat1)) * sin(rad(lat2)) - sin(rad(lat1)) * cos(rad(lat2)) * cos(rad(lon2 - lon1))
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}

/**
 * Halvmarathonruten. Waypoints ligger i luftlinje; [scale] omregner til gade-meter, så
 * hele ruten måler præcis [totalM]. Når ruten er snappet til gaderne med en router,
 * bliver scale tæt på 1.
 */
class Route(
    val name: String,
    val totalM: Double,
    val waypoints: List<Waypoint>,
    val pois: List<Poi>,
    val finishTexts: Map<String, String>,
) {
    fun finishText(lang: String): String = finishTexts[lang] ?: finishTexts["en"] ?: finishTexts.values.first()

    private val rawCum: DoubleArray
    val scale: Double
    /** Gade-meter ved hvert waypoint. */
    val cum: DoubleArray
    val maneuvers: List<Maneuver>

    init {
        require(waypoints.size >= 2) { "Ruten skal have mindst to punkter" }
        rawCum = DoubleArray(waypoints.size)
        for (i in 1 until waypoints.size) {
            val a = waypoints[i - 1]
            val b = waypoints[i]
            rawCum[i] = rawCum[i - 1] + Geo.distanceM(a.lat, a.lon, b.lat, b.lon)
        }
        scale = totalM / rawCum.last()
        cum = DoubleArray(rawCum.size) { rawCum[it] * scale }
        maneuvers = detectManeuvers()
    }

    val start: Waypoint get() = waypoints.first()

    private fun detectManeuvers(): List<Maneuver> {
        val list = mutableListOf<Maneuver>()
        for (i in 1 until waypoints.size - 1) {
            val p = waypoints[i - 1]
            val c = waypoints[i]
            val n = waypoints[i + 1]
            var d = Geo.bearingDeg(c.lat, c.lon, n.lat, n.lon) - Geo.bearingDeg(p.lat, p.lon, c.lat, c.lon)
            d = ((d + 540.0) % 360.0) - 180.0
            if (abs(d) < 35.0) continue
            list += Maneuver(cum[i], if (d > 0) 1 else -1, abs(d) > 125.0, c.street)
        }
        return list
    }

    fun nextManeuver(progressM: Double): Maneuver? = maneuvers.firstOrNull { it.atM > progressM }

    fun nextPoi(progressM: Double): Poi? = pois.firstOrNull { it.atM > progressM }

    /** Punkt på ruten efter [m] gade-meter. */
    fun positionAt(m: Double): RoutePosition {
        val mm = m.coerceIn(0.0, totalM)
        var i = 1
        while (i < cum.size - 1 && cum[i] < mm) i++
        val a = waypoints[i - 1]
        val b = waypoints[i]
        val len = (cum[i] - cum[i - 1]).takeIf { it > 0 } ?: 1.0
        val t = ((mm - cum[i - 1]) / len).coerceIn(0.0, 1.0)
        return RoutePosition(
            lat = a.lat + (b.lat - a.lat) * t,
            lon = a.lon + (b.lon - a.lon) * t,
            segment = i - 1,
            headingDeg = Geo.bearingDeg(a.lat, a.lon, b.lat, b.lon),
        )
    }

    /**
     * Projicerer en GPS-position vinkelret ind på ruten. Kun segmenter mellem
     * [fromM] - [backM] og [fromM] + [aheadM] søges, så ruten ikke springer til et
     * andet sted, hvor den passerer tæt på sig selv (Kurfürstendamm og Karl-Marx-Allee, hvor ruten vender).
     */
    fun project(lat: Double, lon: Double, fromM: Double, backM: Double = 60.0, aheadM: Double = 350.0): Projection {
        val lo = fromM - backM
        val hi = fromM + aheadM
        var best: Projection? = null
        val kx = cos(Math.toRadians(lat)) * 111_320.0
        val ky = 110_540.0
        for (i in 0 until waypoints.size - 1) {
            if (cum[i + 1] < lo || cum[i] > hi) continue
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val ax = (a.lon - lon) * kx
            val ay = (a.lat - lat) * ky
            val bx = (b.lon - lon) * kx
            val by = (b.lat - lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val dist = hypot(ax + t * dx, ay + t * dy)
            val along = (cum[i] + t * (cum[i + 1] - cum[i])).coerceIn(lo, hi)
            if (best == null || dist < best.distanceM) best = Projection(along, dist, i)
        }
        return best ?: Projection(fromM, Double.MAX_VALUE, 0)
    }

    companion object {
        const val ASSET = "route/berlin_sightrun.json"

        fun load(context: Context): Route =
            parse(context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })

        fun parse(json: String): Route {
            val o = JSONObject(json)
            val wps = o.getJSONArray("waypoints")
            val waypoints = (0 until wps.length()).map {
                val w = wps.getJSONObject(it)
                Waypoint(w.getString("name"), w.getDouble("lat"), w.getDouble("lon"), w.optString("street", ""))
            }
            val ps = o.getJSONArray("pois")
            val pois = (0 until ps.length()).map {
                val p = ps.getJSONObject(it)
                Poi(
                    n = p.getInt("n"),
                    names = localized(p, "name"),
                    km = p.getDouble("km"),
                    lat = p.getDouble("lat"),
                    lon = p.getDouble("lon"),
                    radiusM = p.getDouble("radiusM"),
                    audioTemplate = p.optString("audio").takeIf { a -> a.isNotBlank() },
                    texts = localized(p, "text"),
                )
            }.sortedBy { it.km }
            return Route(
                name = o.getString("name"),
                totalM = o.getDouble("distanceMeters"),
                waypoints = waypoints,
                pois = pois,
                finishTexts = localized(o, "finishText"),
            )
        }

        /** Læser et felt, der enten er en tekst eller et objekt med en tekst pr. sprog. */
        private fun localized(o: JSONObject, key: String): Map<String, String> {
            val obj = o.optJSONObject(key) ?: return mapOf("da" to o.getString(key))
            return obj.keys().asSequence().associateWith { obj.getString(it) }
        }
    }
}

/** Én indlæst rute pr. proces. */
object RouteRepository {
    @Volatile private var cached: Route? = null
    fun get(context: Context): Route = cached ?: synchronized(this) {
        cached ?: Route.load(context.applicationContext).also { cached = it }
    }
}
