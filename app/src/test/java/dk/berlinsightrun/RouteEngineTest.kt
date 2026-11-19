package dk.berlinsightrun

import dk.berlinsightrun.audio.Announcement
import dk.berlinsightrun.audio.Kind
import dk.berlinsightrun.i18n.Lang
import dk.berlinsightrun.route.Route
import dk.berlinsightrun.run.RunEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RouteEngineTest {

    private val route = Route.parse(File("src/main/assets/route/berlin_sightrun.json").readText())

    @Test
    fun routeIsHalfMarathon() {
        assertEquals(21_097.5, route.cum.last(), 0.5)
        assertEquals(20, route.pois.size)
        assertTrue("Ruten skal have sving", route.maneuvers.size > 15)
    }

    @Test
    fun everyLanguageHasAllTexts() {
        for (lang in Lang.entries) {
            for (poi in route.pois) {
                assertTrue("${lang.code} navn ${poi.n}", poi.names[lang.code]?.isNotBlank() == true)
                assertTrue("${lang.code} tekst ${poi.n}", (poi.texts[lang.code]?.split(" ")?.size ?: 0) >= 50)
            }
            assertTrue("${lang.code} målbesked", route.finishTexts[lang.code]?.isNotBlank() == true)
        }
    }

    @Test
    fun fullDemoRunInEveryLanguage() {
        for (lang in Lang.entries) {
            val said = mutableListOf<Announcement>()
            val engine = RunEngine(route, lang) { said += it }
            var m = 0.0
            var t = 0L
            while (!engine.finished && m <= route.totalM + 10) {
                engine.onRoutePosition(m, t)
                m += 3.0
                t += 1000
            }
            assertTrue(engine.finished)
            val narrations = said.filter { it.kind == Kind.NARRATION && it.poi != null }
            assertEquals(20, narrations.map { it.poi!!.n }.toSet().size)
            assertTrue(narrations.all { it.text == it.poi!!.text(lang.code) })
            assertTrue(said.count { it.kind == Kind.TURN } >= route.maneuvers.size)
            assertEquals(21, said.count { it.kind == Kind.KM })
            assertEquals(route.finishText(lang.code), said.last().text)
        }
    }

    @Test
    fun languageCanChangeMidRun() {
        val said = mutableListOf<Announcement>()
        val engine = RunEngine(route, Lang.DA) { said += it }
        var m = 0.0
        while (m < 1_500.0) { engine.onRoutePosition(m, 0); m += 3.0 }
        engine.lang = Lang.FR
        while (m < 3_000.0) { engine.onRoutePosition(m, 0); m += 3.0 }
        val schloss = said.first { it.poi?.n == 3 }
        assertEquals(route.pois.first { it.n == 3 }.text("fr"), schloss.text)
        val lastTurn = said.last { it.kind == Kind.TURN }
        assertTrue("Sidste sving: ${lastTurn.text}", lastTurn.text.contains("tournez"))
    }

    @Test
    fun queuedNarrationSwitchesLanguage() {
        val said = mutableListOf<Announcement>()
        val engine = RunEngine(route, Lang.DA) { said += it }
        engine.jumpTo(2_200.0)
        var m = 2_200.0
        while (m < 2_600.0) { engine.onRoutePosition(m, 0); m += 3.0 }
        val schloss = said.first { it.poi?.n == 3 }
        schloss.index = 3 // halvvejs i den danske fortælling
        schloss.relocalize(Lang.FR)
        assertEquals(route.pois.first { it.n == 3 }.text("fr"), schloss.text)
        assertEquals(Lang.FR, schloss.currentLang)
        assertTrue(schloss.index in 2..4)
        assertEquals("Château de Charlottenbourg", schloss.title)
        val turn = said.last { it.kind == Kind.TURN }
        turn.relocalize(Lang.DE)
        assertTrue(turn.text.contains("abbiegen"))
    }

    @Test
    fun kilometreSplitsAreSpokenAndStored() {
        for (lang in Lang.entries) {
            val said = mutableListOf<Announcement>()
            var elapsed = 0.0
            val engine = RunEngine(route, lang, elapsedSec = { elapsed.toLong() }) { said += it }
            var m = 0.0
            // 5:55 min/km de første 10 km, derefter 6:10 min/km
            while (!engine.finished) {
                engine.onRoutePosition(m, (elapsed * 1000).toLong())
                m += 3.0
                elapsed += 3.0 * (if (m < 10_000) 0.355 else 0.370)
            }
            assertEquals(21, engine.splits.size)
            assertTrue(engine.splits.take(9).all { it.splitSec in 354L..356L })
            assertTrue(engine.splits.drop(11).all { it.splitSec in 369L..371L })
            val five = said.first { it.kind == Kind.KM && it.text.startsWith("5 ") }
            val expected = when (lang) {
                Lang.DA -> "Sidste kilometer: 5 minutter 55"
                Lang.DE -> "Letzter Kilometer: 5 Minuten 55"
                Lang.EN -> "Last kilometre: 5 minutes 55"
                Lang.FR -> "Dernier kilomètre : 5 minutes 55"
            }
            assertTrue("${lang.code}: ${five.text}", five.text.contains(expected))
            assertTrue(five.sentences.size == 4)
        }
    }

    @Test
    fun runStartsOnlyAtTheStartPoint() {
        val said = mutableListOf<Announcement>()
        val engine = RunEngine(route, Lang.EN) { said += it }
        val start = route.start
        // ca. 440 m væk: ikke ved start, appen beder løberen gå derhen
        engine.onLocation(52.5134, 13.3330, 8f, 0)
        assertFalse(engine.atStart)
        assertTrue(engine.distanceToStartM!! in 400.0..470.0)
        assertTrue(said.single().text.startsWith("You are 440 metres from the start"))
        // ca. 100 m væk: stadig rød
        engine.onLocation(start.lat + 0.0009, start.lon, 8f, 1000)
        assertFalse(engine.atStart)
        // ca. 30 m væk: grøn, løbet starter
        engine.onLocation(start.lat + 0.00027, start.lon, 8f, 2000)
        assertTrue(engine.atStart)
        assertEquals(null, engine.distanceToStartM)
        assertTrue(said.any { it.text.startsWith("Welcome to Berlin Sightrun") })
        assertTrue(said.any { it.poi?.n == 1 }) // fortællingen om TU Berlin følger lige efter
    }

    @Test
    fun offRouteIsDetectedAfterTenSeconds() {
        val said = mutableListOf<Announcement>()
        val engine = RunEngine(route, Lang.DE) { said += it }
        engine.jumpTo(5_000.0)
        val p = route.positionAt(5_000.0)
        repeat(12) { i -> engine.onLocation(p.lat + 0.0007, p.lon, 5f, i * 1000L) }
        assertTrue(engine.offRoute)
        assertTrue(said.any { it.kind == Kind.OFF_ROUTE && it.text.startsWith("Du hast die Route verlassen") })
    }

    @Test
    fun projectionDoesNotJumpAcrossTheRoute() {
        // Kurfürstendamm køres frem og tilbage: projektionen må ikke hoppe til den anden retning.
        val outbound = route.positionAt(6_400.0)
        val proj = route.project(outbound.lat, outbound.lon, fromM = 6_350.0)
        assertTrue(proj.progressM in 6_300.0..6_700.0)
        val back = route.positionAt(6_900.0)
        assertTrue(route.project(back.lat, back.lon, fromM = 6_850.0).progressM in 6_800.0..7_200.0)
    }

    @Test
    fun sentencesAreNotSplitInsideNames() {
        for (lang in Lang.entries) {
            val parts = Announcement.splitSentences(route.pois.first { it.n == 6 }.text(lang.code))
            assertFalse(parts.any { it.length < 12 })
        }
    }

    @Test
    fun dayNumbersDoNotSplitSentences() {
        val parts = Announcement.splitSentences("Gaden hedder Straße des 17. Juni efter opstanden. Nu løber du.")
        assertEquals(2, parts.size)
    }

    @Test
    fun berlinTurnPhrases() {
        assertEquals("rechts abbiegen in die Schloßstraße", Lang.DE.strings.turn(1, false, "Schloßstraße"))
        assertEquals("links abbiegen auf den Kaiserdamm", Lang.DE.strings.turn(-1, false, "Kaiserdamm"))
        assertEquals("tournez à droite vers la Karl-Marx-Allee", Lang.FR.strings.turn(1, false, "Karl-Marx-Allee"))
        assertEquals("drej til højre ad Kurfürstendamm", Lang.DA.strings.turn(1, false, "Kurfürstendamm"))
    }

    @Test
    fun bestVoiceIsPreferred() {
        val score = dk.berlinsightrun.audio.Announcer.Companion::scoreVoice
        // Højere kvalitet vinder over alt andet
        assertTrue(score(500, false, false, 300) > score(400, true, false, 100))
        // Samme kvalitet: rigtigt land og lokal stemme vinder
        assertTrue(score(400, true, false, 300) > score(400, false, false, 300))
        assertTrue(score(400, true, false, 300) > score(400, true, true, 300))
    }
}
