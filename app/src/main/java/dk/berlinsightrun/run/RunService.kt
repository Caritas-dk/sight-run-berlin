package dk.berlinsightrun.run

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dk.berlinsightrun.MainActivity
import dk.berlinsightrun.R
import dk.berlinsightrun.audio.Announcement
import dk.berlinsightrun.audio.Announcer
import dk.berlinsightrun.i18n.LanguageStore
import dk.berlinsightrun.i18n.Strings
import dk.berlinsightrun.route.Route
import dk.berlinsightrun.route.RouteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Forgrundstjeneste, der styrer hele løbet: GPS (eller demo-løber), rutemotor og lydkø.
 * Kører videre med slukket skærm, så telefonen kan ligge i lommen.
 */
class RunService : Service() {

    private lateinit var route: Route
    private lateinit var engine: RunEngine
    private lateinit var announcer: Announcer
    private lateinit var fused: FusedLocationProviderClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var demoJob: Job? = null
    private var clockJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var demo = false
    private var demoSpeed = 10
    private var paused = false
    private var running = false
    private var elapsedMs = 0L
    private var lastTickMs = 0L
    private var demoM = 0.0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (paused) return
            for (loc in result.locations) {
                engine.onLocation(loc.latitude, loc.longitude, loc.accuracy, loc.time)
            }
            result.lastLocation?.let { publish(it.latitude, it.longitude) }
            afterUpdate()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        route = RouteRepository.get(this)
        LanguageStore.init(this)
        val lang = LanguageStore.lang.value
        fused = LocationServices.getFusedLocationProviderClient(this)
        announcer = Announcer(
            context = this,
            initialLang = lang,
            progressM = { engine.progressM },
            distanceToNextTurnM = { route.nextManeuver(engine.progressM)?.let { it.atM - engine.progressM } ?: Double.MAX_VALUE },
            onSpeaking = { a -> RunStore.update { it.copy(speaking = a?.toSpeaking()) } },
            onTtsProblem = { msg -> RunStore.update { it.copy(notice = msg) } },
        )
        engine = RunEngine(route, lang, elapsedSec = { elapsedMs / 1000 }) { announcer.enqueue(it) }
        createChannel()
        // Skift sprog midt i løbet: næste besked kommer på det nye sprog.
        scope.launch {
            LanguageStore.lang.collect { l ->
                engine.lang = l
                announcer.setLanguage(l)
                if (running) {
                    RunStore.state.value.let { st -> if (st.lat != null && st.lon != null) publish(st.lat, st.lon) }
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRun(
                demo = intent.getBooleanExtra(EXTRA_DEMO, false),
                speed = intent.getIntExtra(EXTRA_SPEED, 10),
                startKm = intent.getDoubleExtra(EXTRA_START_KM, 0.0),
                walkToStart = intent.getBooleanExtra(EXTRA_WALK, false),
            )
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_TOGGLE -> setPaused(!paused)
            ACTION_REPEAT -> announcer.repeatLast()
            ACTION_SKIP -> announcer.skipNarration()
            ACTION_SPEED -> {
                demoSpeed = intent.getIntExtra(EXTRA_SPEED, demoSpeed)
                RunStore.update { it.copy(demoSpeed = demoSpeed) }
            }
            ACTION_STOP -> stopRun(finished = false)
        }
        return START_NOT_STICKY
    }

    private fun startRun(demo: Boolean, speed: Int, startKm: Double, walkToStart: Boolean = false) {
        if (running) return
        this.demo = demo
        demoSpeed = speed
        running = true
        paused = false
        // Ved demo-start midt på ruten lader vi som om, de første km blev løbet i 5:55/km.
        elapsedMs = (startKm * 355_000).toLong()
        demoM = startKm * 1000.0
        if (startKm > 0) engine.jumpTo(demoM)

        val type = if (demo || !hasLocationPermission()) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(strings.notifStarting), type)

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BerlinSightrun:run").apply { acquire(4 * 60 * 60 * 1000L) }

        RunStore.update {
            RunUiState(phase = Phase.RUNNING, demo = demo, demoSpeed = speed, totalM = route.totalM, notice = it.notice)
        }

        if (demo) startDemoRunner(walkToStart && startKm == 0.0) else startGps()
        startClock()
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (!hasLocationPermission()) {
            RunStore.update { it.copy(notice = strings.noLocation) }
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(true)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    /** Demo: en virtuel løber i 5:55 min/km, ganget med demoSpeed. Sænker farten, mens der fortælles. */
    private fun startDemoRunner(walkToStart: Boolean) {
        demoJob = scope.launch {
            val speedMps = 1000.0 / 355.0
            var last = SystemClock.elapsedRealtime()
            // Valgfrit: gå først fra et punkt ca. 440 m væk hen til startpunktet (1,4 m/s), så startindikatoren kan ses.
            if (walkToStart) {
                var lat = PRE_START_LAT
                var lon = PRE_START_LON
                while (isActive && !engine.atStart) {
                    delay(200)
                    val now = SystemClock.elapsedRealtime()
                    val dt = (now - last) / 1000.0
                    last = now
                    if (paused) continue
                    val d = dk.berlinsightrun.route.Geo.distanceM(lat, lon, route.start.lat, route.start.lon)
                    val f = (1.4 * dt * demoSpeed / d).coerceAtMost(1.0)
                    lat += (route.start.lat - lat) * f
                    lon += (route.start.lon - lon) * f
                    engine.onLocation(lat, lon, 5f, System.currentTimeMillis())
                    publish(lat, lon)
                    afterUpdate()
                }
            }
            while (isActive) {
                delay(200)
                val now = SystemClock.elapsedRealtime()
                val dt = (now - last) / 1000.0
                last = now
                if (paused || engine.finished) continue
                val eff = if (announcer.isSpeakingNarration) min(demoSpeed, 2) else demoSpeed
                demoM = (demoM + speedMps * dt * eff).coerceAtMost(route.totalM)
                elapsedMs += (dt * eff * 1000).toLong()
                val pos = route.positionAt(demoM)
                engine.onLocation(pos.lat, pos.lon, 5f, System.currentTimeMillis())
                publish(pos.lat, pos.lon)
                afterUpdate()
            }
        }
    }

    private fun startClock() {
        lastTickMs = SystemClock.elapsedRealtime()
        clockJob = scope.launch {
            var n = 0
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                if (!demo && !paused && engine.atStart && !engine.finished) elapsedMs += now - lastTickMs
                lastTickMs = now
                RunStore.update { it.copy(elapsedSec = elapsedMs / 1000) }
                if (++n % 5 == 0) updateNotification()
            }
        }
    }

    private fun publish(lat: Double, lon: Double) {
        val p = engine.progressM
        val turnIndex = route.maneuvers.indexOfFirst { it.atM > p - 10 }
        val turn = route.maneuvers.getOrNull(turnIndex)
        val poi = route.nextPoi(p)
        RunStore.update {
            it.copy(
                progressM = p,
                lat = lat,
                lon = lon,
                distanceToStartM = engine.distanceToStartM,
                atStart = engine.atStart,
                turnDistanceM = turn?.let { t -> (t.atM - p).coerceAtLeast(0.0) },
                turnText = if (turn != null) engine.phraseFor(turnIndex).replaceFirstChar { c -> c.uppercase() } else strings.straightToFinish,
                turnDir = turn?.dir ?: 0,
                turnSharp = turn?.sharp ?: false,
                offRoute = engine.offRoute,
                nextPoiName = poi?.name(engine.lang.code),
                nextPoiDistanceM = poi?.let { q -> q.atM - p },
                playedPois = engine.playedPois.toSet(),
                heard = engine.heardCount,
                splits = engine.splits.toList(),
            )
        }
    }

    private fun afterUpdate() {
        announcer.tick()
        if (engine.finished && running) {
            running = false
            scope.launch {
                // Lad målfortællingen blive læst færdig, før skærmen skifter.
                delay(1500)
                RunStore.update { it.copy(phase = Phase.FINISHED, progressM = route.totalM, heard = engine.heardCount) }
                stopTracking()
                delay(60_000)
                stopRun(finished = true)
            }
        }
    }

    private fun setPaused(p: Boolean) {
        paused = p
        if (p) announcer.pause() else {
            lastTickMs = SystemClock.elapsedRealtime()
            announcer.resume()
        }
        RunStore.update { it.copy(paused = p) }
        updateNotification()
    }

    private fun stopTracking() {
        fused.removeLocationUpdates(locationCallback)
        demoJob?.cancel()
        clockJob?.cancel()
    }

    private fun stopRun(finished: Boolean) {
        stopTracking()
        announcer.clear()
        running = false
        if (!finished) RunStore.reset()
        wakeLock?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopTracking()
        announcer.shutdown()
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private val strings: Strings get() = LanguageStore.lang.value.strings

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ---------- Notifikation ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(a: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, RunService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Berlin Sightrun")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFFB5E61D.toInt())
            .addAction(0, if (paused) strings.resume else strings.pause, action(ACTION_TOGGLE, 1))
            .addAction(0, strings.stopRun, action(ACTION_STOP, 2))
            .build()
    }

    private fun updateNotification() {
        if (!running) return
        val s = RunStore.state.value
        val turn = s.turnDistanceM?.let { "${strings.shortDistance(it)}: ${s.turnText}" } ?: ""
        val text = "${strings.km(s.progressM)} km · ${Format.time(s.elapsedSec)}" + if (turn.isNotEmpty()) " · $turn" else ""
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun Announcement.toSpeaking() = Speaking(
        kind = kind,
        title = title,
        line = sentences.getOrElse(index) { "" },
        part = index + 1,
        parts = sentences.size,
        poiNumber = poi?.n,
    )

    companion object {
        private const val CHANNEL_ID = "run"
        private const val NOTIFICATION_ID = 21

        const val ACTION_START = "dk.berlinsightrun.START"
        const val ACTION_PAUSE = "dk.berlinsightrun.PAUSE"
        const val ACTION_RESUME = "dk.berlinsightrun.RESUME"
        const val ACTION_TOGGLE = "dk.berlinsightrun.TOGGLE"
        const val ACTION_STOP = "dk.berlinsightrun.STOP"
        const val ACTION_REPEAT = "dk.berlinsightrun.REPEAT"
        const val ACTION_SKIP = "dk.berlinsightrun.SKIP"
        const val ACTION_SPEED = "dk.berlinsightrun.SPEED"
        const val EXTRA_DEMO = "demo"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_START_KM = "startKm"
        const val EXTRA_WALK = "walkToStart"
        private const val PRE_START_LAT = 52.5134
        private const val PRE_START_LON = 13.3330

        fun start(context: Context, demo: Boolean, speed: Int = 10, startKm: Double = 0.0, walkToStart: Boolean = false) {
            val i = Intent(context, RunService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_DEMO, demo).putExtra(EXTRA_SPEED, speed).putExtra(EXTRA_START_KM, startKm)
                .putExtra(EXTRA_WALK, walkToStart)
            ContextCompat.startForegroundService(context, i)
        }

        fun send(context: Context, action: String, speed: Int? = null) {
            val i = Intent(context, RunService::class.java).setAction(action)
            speed?.let { i.putExtra(EXTRA_SPEED, it) }
            context.startService(i)
        }
    }
}
