package dk.berlinsightrun

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.berlinsightrun.i18n.Lang
import dk.berlinsightrun.i18n.LanguageStore
import dk.berlinsightrun.route.Route
import dk.berlinsightrun.route.RouteRepository
import dk.berlinsightrun.run.Phase
import dk.berlinsightrun.run.RunService
import dk.berlinsightrun.run.RunStore
import dk.berlinsightrun.run.RunUiState
import dk.berlinsightrun.ui.OverviewScreen
import dk.berlinsightrun.ui.RunScreen
import dk.berlinsightrun.ui.SummaryScreen
import dk.berlinsightrun.ui.theme.Navy900
import dk.berlinsightrun.ui.theme.SightrunTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Navy900.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Navy900.toArgb()),
        )
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        val route = RouteRepository.get(this)
        LanguageStore.init(this)
        setContent {
            SightrunTheme {
                val state by RunStore.state.collectAsStateWithLifecycle()
                val lang by LanguageStore.lang.collectAsStateWithLifecycle()
                SightrunApp(route, lang, state)
            }
        }
    }
}

private sealed interface PendingStart {
    data object Gps : PendingStart
    data class Demo(val speed: Int, val km: Double, val walk: Boolean) : PendingStart
}

@Composable
private fun SightrunApp(route: Route, lang: Lang, state: RunUiState) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PendingStart?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        when (val p = pending) {
            PendingStart.Gps -> {
                if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) RunService.start(context, demo = false)
                else RunStore.update { it.copy(notice = lang.strings.needLocation) }
            }
            is PendingStart.Demo -> RunService.start(context, demo = true, speed = p.speed, startKm = p.km, walkToStart = p.walk)
            null -> Unit
        }
        pending = null
    }

    fun notificationPermission(): List<String> =
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    when (state.phase) {
        Phase.IDLE -> OverviewScreen(
            route = route,
            lang = lang,
            notice = state.notice,
            onLanguage = { LanguageStore.set(context, it) },
            onStartGps = {
                pending = PendingStart.Gps
                launcher.launch(
                    (listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) +
                        notificationPermission()).toTypedArray()
                )
            },
            onStartDemo = { speed, km, walk ->
                val perms = notificationPermission()
                if (perms.isEmpty()) RunService.start(context, demo = true, speed = speed, startKm = km, walkToStart = walk)
                else {
                    pending = PendingStart.Demo(speed, km, walk)
                    launcher.launch(perms.toTypedArray())
                }
            },
        )

        Phase.RUNNING -> RunScreen(
            route = route,
            lang = lang,
            s = state,
            onToggle = { RunService.send(context, RunService.ACTION_TOGGLE) },
            onRepeat = { RunService.send(context, RunService.ACTION_REPEAT) },
            onSkip = { RunService.send(context, RunService.ACTION_SKIP) },
            onStop = { RunService.send(context, RunService.ACTION_STOP) },
            onSpeed = { RunService.send(context, RunService.ACTION_SPEED, speed = it) },
        )

        Phase.FINISHED -> SummaryScreen(route, lang, state) {
            RunService.send(context, RunService.ACTION_STOP)
            RunStore.reset()
        }
    }
}
