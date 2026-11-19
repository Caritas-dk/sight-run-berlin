package dk.berlinsightrun.run

import dk.berlinsightrun.audio.Announcement
import dk.berlinsightrun.audio.Kind
import dk.berlinsightrun.i18n.Lang
import dk.berlinsightrun.i18n.Strings
import dk.berlinsightrun.route.Geo
import dk.berlinsightrun.route.Poi
import dk.berlinsightrun.route.Route
import kotlin.math.max

/**
 * Ren logik uden Android-afhængigheder: tager GPS-punkter, holder styr på positionen på
 * ruten og beslutter, hvad der skal siges, på det valgte sprog. Kan unit-testes med et
 * afspillet spor.
 */
/** Tid for én kilometer. Alle tider i sekunder. */
data class KmSplit(val km: Int, val totalSec: Long, val splitSec: Long, val avgSec: Long)

class RunEngine(
    private val route: Route,
    var lang: Lang = Lang.DA,
    /** Løbetid i sekunder (pauser fratrukket). Leveres af tjenesten. */
    private val elapsedSec: () -> Long = { 0L },
    private val announce: (Announcement) -> Unit,
) {
    /** Kilometertider indtil nu, i rækkefølge. */
    val splits = mutableListOf<KmSplit>()

    var progressM = 0.0
        private set
    var distanceToRouteM = 0.0
        private set
    var offRoute = false
        private set
    var atStart = false
        private set
    var distanceToStartM: Double? = null
        private set
    var finished = false
        private set
    var heardCount = 0
        private set
    val playedPois = mutableSetOf<Int>()

    var narrationsEnabled = true
    var kmCallsEnabled = true

    private val s: Strings get() = lang.strings
    private val preAnnounced = BooleanArray(route.maneuvers.size)
    private val nowAnnounced = BooleanArray(route.maneuvers.size)
    private var nextKm = 1
    private var offSinceMs: Long? = null
    private var startWarned = false

    /** Laver en besked, der kan skifte sprog, mens den står i kø eller bliver talt. */
    private fun say(
        kind: Kind,
        staleAfterM: Double? = null,
        poi: Poi? = null,
        force: Boolean = false,
        onDone: (() -> Unit)? = null,
        make: (Strings, Lang) -> Pair<String, String>,
    ) = announce(Announcement(kind, lang, staleAfterM, poi, force, onDone) { l -> make(l.strings, l) })

    fun onLocation(lat: Double, lon: Double, accuracyM: Float, timeMs: Long) {
        if (finished) return
        if (accuracyM > MAX_ACCURACY_M) return

        if (!atStart) {
            val d = Geo.distanceM(lat, lon, route.start.lat, route.start.lon)
            distanceToStartM = d
            if (d > START_RADIUS_M) {
                if (!startWarned) {
                    startWarned = true
                    say(Kind.SYSTEM, force = true) { s, _ -> s.goToStartTitle to s.goToStartSpeech(s.distance(d)) }
                }
                return
            }
            atStart = true
            distanceToStartM = null
            say(Kind.SYSTEM, force = true) { s, _ -> s.welcomeTitle to s.welcome }
        }

        val p = route.project(lat, lon, progressM)
        distanceToRouteM = p.distanceM
        updateOffRoute(p.distanceM, timeMs)
        if (p.distanceM <= ON_ROUTE_M) progressM = max(progressM, p.progressM)

        checkManeuvers()
        checkPois(lat, lon)
        checkKilometres()

        if (progressM >= route.totalM - FINISH_TOLERANCE_M) {
            finished = true
            progressM = route.totalM
            say(Kind.NARRATION, force = true) { s, l -> s.finishTitle to route.finishText(l.code) }
        }
    }

    /** Til demo-tilstand og test: placér løberen direkte på ruten. */
    fun onRoutePosition(m: Double, timeMs: Long) {
        val pos = route.positionAt(m)
        onLocation(pos.lat, pos.lon, 5f, timeMs)
    }

    /** Svingets tekst på det aktuelle sprog, fx "drej til højre ad Rue de Rivoli". */
    fun phraseFor(index: Int): String {
        val m = route.maneuvers[index]
        return s.turn(m.dir, m.sharp, m.street)
    }

    private fun updateOffRoute(distance: Double, timeMs: Long) {
        if (distance > OFF_ROUTE_M) {
            val since = offSinceMs ?: timeMs.also { offSinceMs = it }
            if (!offRoute && timeMs - since >= OFF_ROUTE_DELAY_MS) {
                offRoute = true
                val street = route.waypoints[route.positionAt(progressM).segment].street.ifBlank { "Straße des 17. Juni" }
                say(Kind.OFF_ROUTE) { s, _ -> s.offTitle to s.offRoute(street) }
            }
        } else if (distance <= BACK_ON_ROUTE_M) {
            offSinceMs = null
            if (offRoute) {
                offRoute = false
                say(Kind.OFF_ROUTE) { s, _ -> s.backOnTitle to s.backOn }
            }
        }
    }

    private fun checkManeuvers() {
        route.maneuvers.forEachIndexed { i, m ->
            val d = m.atM - progressM
            if (!preAnnounced[i] && d <= PRE_ANNOUNCE_M && d > 25.0) {
                preAnnounced[i] = true
                say(Kind.TURN, staleAfterM = m.atM) { s, _ -> s.turnAheadTitle to s.preAnnounce(s.turn(m.dir, m.sharp, m.street)) }
            }
            if (!nowAnnounced[i] && d <= NOW_ANNOUNCE_M) {
                nowAnnounced[i] = true
                preAnnounced[i] = true
                if (d > -40.0) {
                    say(Kind.TURN, staleAfterM = m.atM + 60.0) { s, _ -> s.turnNowTitle to s.nowAnnounce(s.turn(m.dir, m.sharp, m.street)) }
                }
            }
        }
    }

    private fun checkPois(lat: Double, lon: Double) {
        for (poi in route.pois) {
            if (poi.n in playedPois) continue
            val inFence = progressM >= poi.atM - poi.radiusM &&
                Geo.distanceM(lat, lon, poi.lat, poi.lon) <= poi.radiusM
            // Hvis GPS'en har misset geofencet, udløses fortællingen alligevel lidt efter.
            val passed = progressM >= poi.atM + MISSED_FENCE_M
            if (!inFence && !passed) continue
            playedPois += poi.n
            if (!narrationsEnabled) continue
            say(Kind.NARRATION, staleAfterM = poi.atM + 2_500.0, poi = poi, onDone = { heardCount++ }) { _, l ->
                poi.name(l.code) to poi.text(l.code)
            }
        }
    }

    private fun checkKilometres() {
        while (nextKm <= 21 && progressM >= nextKm * 1000.0) {
            val k = nextKm++
            val total = elapsedSec()
            val split = total - (splits.lastOrNull()?.totalSec ?: splitBaseSec)
            val sp = KmSplit(k, total, split, total / k)
            splits += sp
            if (kmCallsEnabled) {
                say(Kind.KM, staleAfterM = k * 1000.0 + 400.0) { s, _ -> s.kmTitle(k) to s.kmCall(k, sp.totalSec, sp.splitSec, sp.avgSec) }
            }
        }
    }

    /** Løbetid ved sidste hop; første split efter et hop regnes herfra. */
    private var splitBaseSec = 0L

    /** Hop i ruten (demo): alt før [m] markeres som overstået uden at blive sagt. */
    fun jumpTo(m: Double) {
        atStart = true
        progressM = m.coerceIn(0.0, route.totalM - 50.0)
        // Første kilometer efter hoppet: regn med samme fart som indtil nu for den del, der er sprunget over.
        val e = elapsedSec()
        splitBaseSec = if (progressM > 0) e - ((progressM % 1000.0) / progressM * e).toLong() else e
        splits.clear()
        route.maneuvers.forEachIndexed { i, man ->
            val passed = man.atM < progressM + 20.0
            nowAnnounced[i] = passed
            preAnnounced[i] = passed || man.atM - progressM < PRE_ANNOUNCE_M
        }
        route.pois.filter { it.atM < progressM }.forEach { playedPois += it.n }
        nextKm = (progressM / 1000.0).toInt() + 1
    }

    companion object {
        const val MAX_ACCURACY_M = 30f
        const val START_RADIUS_M = 50.0
        const val ON_ROUTE_M = 40.0
        const val OFF_ROUTE_M = 40.0
        const val BACK_ON_ROUTE_M = 25.0
        const val OFF_ROUTE_DELAY_MS = 10_000L
        const val PRE_ANNOUNCE_M = 100.0
        const val NOW_ANNOUNCE_M = 15.0
        const val MISSED_FENCE_M = 150.0
        const val FINISH_TOLERANCE_M = 30.0
    }
}
