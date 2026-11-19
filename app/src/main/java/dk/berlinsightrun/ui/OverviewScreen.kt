package dk.berlinsightrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.berlinsightrun.i18n.Lang
import dk.berlinsightrun.i18n.Strings
import dk.berlinsightrun.route.Poi
import dk.berlinsightrun.route.Route
import dk.berlinsightrun.run.Format
import dk.berlinsightrun.ui.theme.Lime
import dk.berlinsightrun.ui.theme.Muted
import dk.berlinsightrun.ui.theme.Navy600
import dk.berlinsightrun.ui.theme.Navy700
import dk.berlinsightrun.ui.theme.Navy900
import dk.berlinsightrun.ui.theme.BerlinBlue
import dk.berlinsightrun.ui.theme.Warn
import dk.berlinsightrun.ui.theme.White

private const val PLANNED_PACE = 355L // 5:55 min/km

@Composable
fun OverviewScreen(
    route: Route,
    lang: Lang,
    notice: String?,
    onLanguage: (Lang) -> Unit,
    onStartGps: () -> Unit,
    onStartDemo: (speed: Int, startKm: Double, walkToStart: Boolean) -> Unit,
) {
    val s = lang.strings
    var selected by remember { mutableStateOf<Poi?>(null) }
    var demoDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Navy900)
            .systemBarsPadding()
    ) {
        Header(s)
        LanguagePicker(lang, s, onLanguage)
        Box(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            RouteMap(route = route, modifier = Modifier.fillMaxSize(), interactive = false)
        }
        Row(
            Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(s.km(route.totalM), s.kmLabel, highlight = true, modifier = Modifier.weight(1f))
            StatTile("${route.pois.size}", s.sightsLabel, modifier = Modifier.weight(1f))
            StatTile(
                Format.time((route.totalM / 1000 * PLANNED_PACE).toLong()).substringBeforeLast(':'),
                s.etaLabel, modifier = Modifier.weight(1f),
            )
        }
        if (notice != null) {
            Text(
                notice,
                color = Navy900,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                    .fillMaxWidth()
                    .background(Warn, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(s.listTitle, style = MaterialTheme.typography.titleMedium, color = White)
            Text("km", color = Muted, fontSize = 12.sp)
        }
        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(route.pois, key = { it.n }) { p ->
                PoiRow(p.n, p.name(lang.code), s.km(p.atM), played = false) { selected = p }
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onStartGps,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Navy900),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(s.startRun, style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { demoDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
            ) {
                Text(s.tryDemo, color = Lime, fontWeight = FontWeight.Bold)
            }
        }
    }

    selected?.let { p ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Navy700,
            title = { Text("${p.n}. ${p.name(lang.code)}", color = White, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("km ${s.km(p.atM)} · ${s.geofence} ${p.radiusM.toInt()} m", color = Lime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(p.text(lang.code), color = White, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(s.close, color = Lime) } },
        )
    }

    if (demoDialog) DemoDialog(
        s = s,
        onDismiss = { demoDialog = false },
        onStart = { speed, km, walk -> demoDialog = false; onStartDemo(speed, km, walk) },
    )
}

@Composable
private fun Header(s: Strings) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Navy700, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(12.dp).background(Lime, CircleShape)) }
        Column {
            Text(
                buildAnnotatedString {
                    append("Berlin ")
                    withStyle(SpanStyle(color = Lime)) { append("Sightrun") }
                },
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = White,
            )
            Text(s.tagline, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun LanguagePicker(current: Lang, s: Strings, onLanguage: (Lang) -> Unit) {
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .semantics { contentDescription = s.language },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Lang.entries.forEach { l ->
            FilterChip(
                selected = l == current,
                onClick = { onLanguage(l) },
                label = { Text(l.code.uppercase(), fontWeight = FontWeight.Bold) },
                modifier = Modifier.semantics { contentDescription = l.label },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lime, selectedLabelColor = Navy900,
                    containerColor = Navy900, labelColor = Muted,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = l == current,
                    borderColor = Navy600, selectedBorderColor = Lime,
                ),
            )
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier
            .background(Navy700, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = if (highlight) Lime else White)
        Text(label, fontSize = 11.5.sp, color = Muted, maxLines = 1)
    }
}

@Composable
fun PoiRow(n: Int, name: String, km: String, played: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Navy700)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(if (played) Lime else BerlinBlue, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (played) Navy900 else White) }
        Text(name, Modifier.weight(1f), color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(km, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun DemoDialog(s: Strings, onDismiss: () -> Unit, onStart: (Int, Double, Boolean) -> Unit) {
    var speed by remember { mutableIntStateOf(10) }
    var km by remember { mutableFloatStateOf(0f) }
    var walk by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Navy700,
        title = { Text(s.demoTitle, color = White, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(s.demoBody, color = Muted, style = MaterialTheme.typography.bodyMedium)
                Text(s.speed, color = White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10, 30).forEach { v ->
                        FilterChip(
                            selected = speed == v,
                            onClick = { speed = v },
                            label = { Text("$v×") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BerlinBlue, selectedLabelColor = White, labelColor = Muted,
                            ),
                        )
                    }
                }
                Text(s.startAtKm(s.km(km * 1000.0)), color = White, fontWeight = FontWeight.Bold)
                Slider(
                    value = km, onValueChange = { km = (it * 10).toInt() / 10f }, valueRange = 0f..20f,
                    colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime),
                )
                if (km == 0f) Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = walk, onValueChange = { walk = it }, role = Role.Checkbox),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = walk, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Lime, checkmarkColor = Navy900))
                    Text(s.demoWalkOption, color = White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(speed, km.toDouble(), walk && km == 0f) }) { Text(s.startDemo, color = Lime, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel, color = Muted) } },
    )
}
