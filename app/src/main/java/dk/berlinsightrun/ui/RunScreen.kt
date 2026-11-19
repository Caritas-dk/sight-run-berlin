@file:Suppress("DEPRECATION")

package dk.berlinsightrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.berlinsightrun.audio.Kind
import dk.berlinsightrun.i18n.Lang
import dk.berlinsightrun.i18n.Strings
import dk.berlinsightrun.route.Route
import dk.berlinsightrun.run.Format
import dk.berlinsightrun.run.KmSplit
import dk.berlinsightrun.run.RunUiState
import dk.berlinsightrun.ui.theme.Lime
import dk.berlinsightrun.ui.theme.Muted
import dk.berlinsightrun.ui.theme.Navy700
import dk.berlinsightrun.ui.theme.Navy900
import dk.berlinsightrun.ui.theme.BerlinBlue
import dk.berlinsightrun.ui.theme.StartGreen
import dk.berlinsightrun.ui.theme.StartRed
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dk.berlinsightrun.ui.theme.Warn
import dk.berlinsightrun.ui.theme.White
import org.osmdroid.util.GeoPoint

@Composable
fun RunScreen(
    route: Route,
    lang: Lang,
    s: RunUiState,
    onToggle: () -> Unit,
    onRepeat: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onSpeed: (Int) -> Unit,
) {
    var confirmStop by remember { mutableStateOf(false) }
    val t = lang.strings
    val view = LocalView.current
    DisposableEffect(s.demo) {
        view.keepScreenOn = s.demo
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Navy900)) {
        RouteMap(
            route = route,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f),
            runner = if (s.lat != null && s.lon != null) GeoPoint(s.lat, s.lon) else null,
            follow = true,
            playedPois = s.playedPois,
            showFences = true,
            startReached = s.atStart,
        )

        Column(
            Modifier.statusBarsPadding().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val beforeStart = !s.atStart
            val justStarted = s.atStart && s.progressM < 60.0
            if (beforeStart || justStarted) StartIndicator(t, s)
            if (!beforeStart) TurnCard(s, t)
            s.lastSplit?.takeIf { s.progressM < it.km * 1000.0 + 300.0 }?.let { KmSplitCard(t, it) }
            s.speaking?.takeIf { it.kind != Kind.KM }?.let { SpeakingCard(t, it.kind, it.title, it.line, it.part, it.parts, it.poiNumber, route.pois.size) }
        }

        BottomPanel(
            s = s,
            t = t,
            modifier = Modifier.align(Alignment.BottomCenter),
            onToggle = onToggle, onRepeat = onRepeat, onSkip = onSkip,
            onStop = { confirmStop = true }, onSpeed = onSpeed,
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = Navy700,
            title = { Text(t.stopTitle, color = White, fontWeight = FontWeight.ExtraBold) },
            text = { Text(t.stopBody, color = Muted) },
            confirmButton = { TextButton(onClick = { confirmStop = false; onStop() }) { Text(t.stopConfirm, color = Warn, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(t.keepRunning, color = Lime) } },
        )
    }
}

@Composable
private fun TurnCard(s: RunUiState, t: Strings) {
    val (bg, icon, big, small) = when {
        s.distanceToStartM != null -> Quad(Lime, Icons.Filled.Flag, t.shortDistance(s.distanceToStartM), t.goToStart)
        s.offRoute -> Quad(Warn, Icons.Filled.UTurnLeft, t.offTitle, t.offSub)
        s.turnDistanceM == null -> Quad(Lime, Icons.Filled.Straight, t.shortDistance(s.totalM - s.progressM), t.straightToFinish)
        else -> Quad(Lime, turnIcon(s.turnDir, s.turnSharp), t.shortDistance(s.turnDistanceM), s.turnText)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(52.dp).background(Navy900, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = bg, modifier = Modifier.size(32.dp)) }
        Column {
            Text(big, color = Navy900, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(small, color = Navy900, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class Quad(val bg: androidx.compose.ui.graphics.Color, val icon: ImageVector, val big: String, val small: String)

private fun turnIcon(dir: Int, sharp: Boolean): ImageVector = when {
    dir > 0 && sharp -> Icons.Filled.TurnSharpRight
    dir > 0 -> Icons.Filled.TurnRight
    dir < 0 && sharp -> Icons.Filled.TurnSharpLeft
    dir < 0 -> Icons.Filled.TurnLeft
    else -> Icons.Filled.Straight
}

@Composable
private fun SpeakingCard(t: Strings, kind: Kind, title: String, line: String, part: Int, parts: Int, poiNumber: Int?, poiCount: Int) {
    val eyebrow = when (kind) {
        Kind.NARRATION -> if (poiNumber != null) t.eyebrowNarrating(poiNumber, poiCount) else t.eyebrowFinish
        Kind.TURN -> t.eyebrowGuidance
        Kind.OFF_ROUTE -> t.eyebrowOff
        Kind.KM -> t.eyebrowKm
        Kind.SYSTEM -> "BERLIN SIGHTRUN"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Navy700, RoundedCornerShape(18.dp))
            .border(1.dp, BerlinBlue, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Headphones, null, tint = Lime, modifier = Modifier.size(18.dp))
            Text(eyebrow, color = Lime, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(
                t.musicDucked, color = Navy900, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(Lime, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(title, color = White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(line, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(
            progress = { part.toFloat() / parts.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Lime, trackColor = Navy900,
        )
    }
}

/**
 * Rød cirkel med afstanden, indtil løberen er inden for 50 m af startpunktet ved TU Berlin.
 * Bliver grøn med et flueben, når løbet starter, og forsvinder efter de første 60 m.
 */
@Composable
private fun StartIndicator(t: Strings, s: RunUiState) {
    val ok = s.atStart
    val color = if (ok) StartGreen else StartRed
    val title = when {
        ok -> t.atStartTitle
        s.distanceToStartM != null -> t.toStart(t.shortDistance(s.distanceToStartM))
        else -> t.searchingGps
    }
    val sub = if (ok) t.atStartSub else t.goToStart
    Row(
        Modifier
            .fillMaxWidth()
            .background(Navy700, RoundedCornerShape(18.dp))
            .border(1.5.dp, color, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(58.dp).border(7.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(if (ok) 34.dp else 18.dp).background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (ok) Icon(Icons.Filled.Check, contentDescription = null, tint = Navy900, modifier = Modifier.size(24.dp))
            }
        }
        Column {
            Text(title, color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(sub, color = Muted, fontSize = 13.sp)
        }
    }
}

/** Vises i ca. 300 m efter hver kilometer: samlet tid, seneste kilometer og gennemsnit. */
@Composable
private fun KmSplitCard(t: Strings, sp: KmSplit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Navy700, RoundedCornerShape(18.dp))
            .border(1.5.dp, Lime, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t.kmReached(sp.km), color = Lime, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth()) {
            SplitValue(t.clock(sp.totalSec), t.time, Modifier.weight(1f))
            SplitValue(t.clock(sp.splitSec), t.lastKm, Modifier.weight(1f), highlight = true)
            SplitValue(t.clock(sp.avgSec), t.avgPaceShort, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SplitValue(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    Column(modifier) {
        Text(value, color = if (highlight) Lime else White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Muted, fontSize = 11.5.sp)
    }
}

@Composable
private fun BottomPanel(
    s: RunUiState,
    t: Strings,
    modifier: Modifier,
    onToggle: () -> Unit,
    onRepeat: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onSpeed: (Int) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Navy900, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            BigStat(t.km(s.progressM), t.kmOf, Lime, Modifier.weight(1f))
            BigStat(Format.time(s.elapsedSec), t.time, White, Modifier.weight(1f))
            BigStat(Format.pace(s.paceSecPerKm), t.avgPaceShort, White, Modifier.weight(1f))
        }
        LinearProgressIndicator(
            progress = { (s.progressM / s.totalM).toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = BerlinBlue, trackColor = Navy700,
        )
        s.lastSplit?.let {
            Text(
                "${t.lastKm} ${t.clock(it.splitSec)}",
                color = Lime, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            s.nextPoiName?.let { t.nextPoi(it, t.km(s.nextPoiDistanceM ?: 0.0)) } ?: t.nextFinish,
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (s.demo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Text("Demo", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                listOf(1, 5, 10, 30).forEach { sp ->
                    FilterChip(
                        selected = s.demoSpeed == sp, onClick = { onSpeed(sp) }, label = { Text("$sp×") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BerlinBlue, selectedLabelColor = White, labelColor = Muted),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Icons.Filled.Replay, t.repeat, onRepeat)
            FilledIconButton(
                onClick = onToggle, modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lime, contentColor = Navy900),
            ) {
                Icon(if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (s.paused) t.resume else t.pause, Modifier.size(34.dp))
            }
            RoundButton(Icons.Filled.SkipNext, t.skip, onSkip)
        }
        TextButton(onClick = onStop, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(t.stopRun, color = Muted, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Muted, fontSize = 11.5.sp)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick, modifier = Modifier.size(54.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Navy700, contentColor = White),
    ) { Icon(icon, contentDescription = label) }
}

