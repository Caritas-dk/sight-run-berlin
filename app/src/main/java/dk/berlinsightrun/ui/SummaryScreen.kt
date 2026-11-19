package dk.berlinsightrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.berlinsightrun.i18n.Lang
import dk.berlinsightrun.route.Route
import dk.berlinsightrun.run.Format
import dk.berlinsightrun.run.RunUiState
import dk.berlinsightrun.ui.theme.Lime
import dk.berlinsightrun.ui.theme.BerlinBlue
import dk.berlinsightrun.ui.theme.Muted
import dk.berlinsightrun.ui.theme.Navy900
import dk.berlinsightrun.ui.theme.White

/** En vandret bjælke pr. kilometer. Kortere bjælke = hurtigere kilometer; den hurtigste er lime. */
@Composable
private fun SplitBars(s: RunUiState, t: dk.berlinsightrun.i18n.Strings, modifier: Modifier) {
    val maxSec = s.splits.maxOf { it.splitSec }.coerceAtLeast(1)
    val fastest = s.fastestSplit
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(s.splits) { sp ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${sp.km}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                Box(Modifier.weight(1f).height(14.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(sp.splitSec.toFloat() / maxSec)
                            .height(14.dp)
                            .background(if (sp == fastest) Lime else BerlinBlue, RoundedCornerShape(4.dp))
                    )
                }
                Text(
                    t.clock(sp.splitSec) + if (sp == fastest) " · ${t.fastest}" else "",
                    color = if (sp == fastest) Lime else White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(118.dp),
                )
            }
        }
    }
}

@Composable
fun SummaryScreen(route: Route, lang: Lang, s: RunUiState, onDone: () -> Unit) {
    val t = lang.strings
    Column(
        Modifier
            .fillMaxSize()
            .background(Navy900)
            .systemBarsPadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(t.done, color = Lime, style = MaterialTheme.typography.labelSmall)
        Column {
            Text(Format.time(s.elapsedSec), color = White, style = MaterialTheme.typography.displayLarge)
            Text(t.summarySub, color = Muted, fontSize = 14.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            RouteMap(route = route, modifier = Modifier.fillMaxSize(), playedPois = s.playedPois, interactive = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Format.pace(s.paceSecPerKm), t.avgPace, highlight = true, modifier = Modifier.weight(1f))
            StatTile("${s.heard}/${route.pois.size}", t.heard, modifier = Modifier.weight(1f))
            StatTile(t.km(s.progressM), t.kmRun, modifier = Modifier.weight(1f))
        }
        if (s.splits.isNotEmpty()) {
            Text(t.splitsTitle, color = White, style = MaterialTheme.typography.titleMedium)
            SplitBars(s, t, Modifier.weight(1f).fillMaxWidth())
        } else {
            Spacer(Modifier.weight(1f))
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Navy900),
        ) { Text(t.toOverview, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
    }
}
