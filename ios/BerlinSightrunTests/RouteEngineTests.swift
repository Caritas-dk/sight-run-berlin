import XCTest
@testable import BerlinSightrun

// Port af app/src/test/java/dk/berlinsightrun/RouteEngineTest.kt. Samme rute, samme forventninger.
final class RouteEngineTests: XCTestCase {

    private static let route: Route = {
        let bundle = Bundle(for: RunController.self)
        return Route.load(bundle: bundle)
    }()
    private var route: Route { Self.route }

    private func runDemo(_ engine: RunEngine, from: Double = 0, to: Double, step: Double = 3) {
        var m = from
        while m < to && !engine.finished { engine.onRoutePosition(m, timeMs: 0); m += step }
    }

    func testRouteIsHalfMarathon() {
        XCTAssertEqual(route.cum.last!, 21_097.5, accuracy: 0.5)
        XCTAssertEqual(route.pois.count, 20)
        XCTAssertGreaterThan(route.maneuvers.count, 15, "Ruten skal have sving")
    }

    func testEveryLanguageHasAllTexts() {
        for lang in Lang.allCases {
            for poi in route.pois {
                XCTAssertFalse((poi.names[lang.code] ?? "").isEmpty, "\(lang.code) navn \(poi.n)")
                XCTAssertGreaterThanOrEqual((poi.texts[lang.code] ?? "").split(separator: " ").count, 50, "\(lang.code) tekst \(poi.n)")
            }
            XCTAssertFalse((route.finishTexts[lang.code] ?? "").isEmpty, "\(lang.code) målbesked")
        }
    }

    func testFullDemoRunInEveryLanguage() {
        for lang in Lang.allCases {
            var said: [Announcement] = []
            let engine = RunEngine(route: route, lang: lang) { said.append($0) }
            var m = 0.0
            var t: Int64 = 0
            while !engine.finished && m <= route.totalM + 10 {
                engine.onRoutePosition(m, timeMs: t)
                m += 3
                t += 1000
            }
            XCTAssertTrue(engine.finished)
            let narrations = said.filter { $0.kind == .narration && $0.poi != nil }
            XCTAssertEqual(Set(narrations.map { $0.poi!.n }).count, 20)
            XCTAssertTrue(narrations.allSatisfy { $0.text == $0.poi!.text(lang.code) })
            XCTAssertGreaterThanOrEqual(said.filter { $0.kind == .turn }.count, route.maneuvers.count)
            XCTAssertEqual(said.filter { $0.kind == .km }.count, 21)
            XCTAssertEqual(said.last?.text, route.finishText(lang.code))
        }
    }

    func testLanguageCanChangeMidRun() {
        var said: [Announcement] = []
        let engine = RunEngine(route: route, lang: .da) { said.append($0) }
        runDemo(engine, to: 1_500)
        engine.lang = .fr
        runDemo(engine, from: 1_500, to: 3_000)
        let schloss = said.first { $0.poi?.n == 3 }!
        XCTAssertEqual(schloss.text, route.pois.first { $0.n == 3 }!.text("fr"))
        let lastTurn = said.last { $0.kind == .turn }!
        XCTAssertTrue(lastTurn.text.lowercased().contains("tournez"), lastTurn.text)
    }

    func testQueuedNarrationSwitchesLanguage() {
        var said: [Announcement] = []
        let engine = RunEngine(route: route, lang: .da) { said.append($0) }
        engine.jumpTo(2_200)
        runDemo(engine, from: 2_200, to: 2_600)
        let schloss = said.first { $0.poi?.n == 3 }!
        schloss.index = 3 // halvvejs i den danske fortælling
        schloss.relocalize(.fr)
        XCTAssertEqual(schloss.text, route.pois.first { $0.n == 3 }!.text("fr"))
        XCTAssertEqual(schloss.currentLang, .fr)
        XCTAssertTrue((2...4).contains(schloss.index))
        XCTAssertEqual(schloss.title, "Château de Charlottenbourg")
        let turn = said.last { $0.kind == .turn }!
        turn.relocalize(.de)
        XCTAssertTrue(turn.text.contains("abbiegen"))
    }

    func testKilometreSplitsAreSpokenAndStored() {
        for lang in Lang.allCases {
            var said: [Announcement] = []
            var elapsed = 0.0
            let engine = RunEngine(route: route, lang: lang, elapsedSec: { Int(elapsed) }) { said.append($0) }
            var m = 0.0
            // 5:55 min/km de første 10 km, derefter 6:10 min/km
            while !engine.finished {
                engine.onRoutePosition(m, timeMs: Int64(elapsed * 1000))
                m += 3
                elapsed += 3 * (m < 10_000 ? 0.355 : 0.370)
            }
            XCTAssertEqual(engine.splits.count, 21)
            XCTAssertTrue(engine.splits.prefix(9).allSatisfy { (354...356).contains($0.splitSec) })
            XCTAssertTrue(engine.splits.dropFirst(11).allSatisfy { (369...371).contains($0.splitSec) })
            let five = said.first { $0.kind == .km && $0.text.hasPrefix("5 ") }!
            let expected: String
            switch lang {
            case .da: expected = "Sidste kilometer: 5 minutter 55"
            case .de: expected = "Letzter Kilometer: 5 Minuten 55"
            case .en: expected = "Last kilometre: 5 minutes 55"
            case .fr: expected = "Dernier kilomètre : 5 minutes 55"
            }
            XCTAssertTrue(five.text.contains(expected), "\(lang.code): \(five.text)")
            XCTAssertEqual(five.sentences.count, 4)
        }
    }

    func testRunStartsOnlyAtTheStartPoint() {
        var said: [Announcement] = []
        let engine = RunEngine(route: route, lang: .en) { said.append($0) }
        let start = route.start
        // ca. 440 m væk: ikke ved start, appen beder løberen gå derhen
        engine.onLocation(lat: RunController.preStartLat, lon: RunController.preStartLon, accuracyM: 8, timeMs: 0)
        XCTAssertFalse(engine.atStart)
        XCTAssertTrue((400.0...470.0).contains(engine.distanceToStartM!))
        XCTAssertTrue(said.count == 1 && said[0].text.hasPrefix("You are 440 metres from the start"), said.first?.text ?? "")
        // ca. 100 m væk: stadig rød
        engine.onLocation(lat: start.lat + 0.0009, lon: start.lon, accuracyM: 8, timeMs: 1000)
        XCTAssertFalse(engine.atStart)
        // ca. 30 m væk: grøn, løbet starter
        engine.onLocation(lat: start.lat + 0.00027, lon: start.lon, accuracyM: 8, timeMs: 2000)
        XCTAssertTrue(engine.atStart)
        XCTAssertNil(engine.distanceToStartM)
        XCTAssertTrue(said.contains { $0.text.hasPrefix("Welcome to Berlin Sightrun") })
        XCTAssertTrue(said.contains { $0.poi?.n == 1 }) // fortællingen om TU Berlin følger lige efter
    }

    func testOffRouteIsDetectedAfterTenSeconds() {
        var said: [Announcement] = []
        let engine = RunEngine(route: route, lang: .de) { said.append($0) }
        engine.jumpTo(5_000)
        let p = route.positionAt(5_000)
        for i in 0..<12 { engine.onLocation(lat: p.lat + 0.0007, lon: p.lon, accuracyM: 5, timeMs: Int64(i) * 1000) }
        XCTAssertTrue(engine.offRoute)
        XCTAssertTrue(said.contains { $0.kind == .offRoute && $0.text.hasPrefix("Du hast die Route verlassen") })
    }

    func testProjectionDoesNotJumpAcrossTheRoute() {
        // Kurfürstendamm køres frem og tilbage: projektionen må ikke hoppe til den anden retning.
        let outbound = route.positionAt(6_400)
        XCTAssertTrue((6_300.0...6_700.0).contains(route.project(lat: outbound.lat, lon: outbound.lon, fromM: 6_350).progressM))
        let back = route.positionAt(6_900)
        XCTAssertTrue((6_800.0...7_200.0).contains(route.project(lat: back.lat, lon: back.lon, fromM: 6_850).progressM))
    }

    func testSentencesAreNotSplitInsideNames() {
        for lang in Lang.allCases {
            let parts = Announcement.splitSentences(route.pois.first { $0.n == 6 }!.text(lang.code))
            XCTAssertFalse(parts.contains { $0.count < 12 })
        }
    }

    func testDayNumbersDoNotSplitSentences() {
        XCTAssertEqual(Announcement.splitSentences("Gaden hedder Straße des 17. Juni efter opstanden. Nu løber du.").count, 2)
    }

    func testBerlinTurnPhrases() {
        XCTAssertEqual(Lang.de.strings.turn(1, false, "Schloßstraße"), "rechts abbiegen in die Schloßstraße")
        XCTAssertEqual(Lang.de.strings.turn(-1, false, "Kaiserdamm"), "links abbiegen auf den Kaiserdamm")
        XCTAssertEqual(Lang.fr.strings.turn(1, false, "Karl-Marx-Allee"), "tournez à droite vers la Karl-Marx-Allee")
        XCTAssertEqual(Lang.da.strings.turn(1, false, "Kurfürstendamm"), "drej til højre ad Kurfürstendamm")
    }

    func testBestVoiceIsPreferred() {
        // Højere kvalitet vinder over alt andet
        XCTAssertGreaterThan(Announcer.voiceScore(quality: .premium, countryMatches: false, isUSEnglish: false, name: "A"),
                             Announcer.voiceScore(quality: .enhanced, countryMatches: true, isUSEnglish: false, name: "B"))
        // Samme kvalitet: rigtigt land vinder
        XCTAssertGreaterThan(Announcer.voiceScore(quality: .enhanced, countryMatches: true, isUSEnglish: false, name: "A"),
                             Announcer.voiceScore(quality: .enhanced, countryMatches: false, isUSEnglish: true, name: "B"))
    }

    /// Skal give samme tekst som Android-appen for de samme tal.
    func testKmCallMatchesAndroid() {
        XCTAssertEqual(Lang.da.strings.kmCall(5, 1773, 353, 355),
                       "5 kilometer. Tid: 29 minutter og 33 sekunder. Sidste kilometer: 5 minutter 53. Gennemsnit: 5 minutter 55 per kilometer.")
    }

    func testKmFormattingMatchesAndroid() {
        XCTAssertEqual(Lang.da.strings.km(50), "0,1")
        XCTAssertEqual(Lang.en.strings.km(21_097.5), "21.1")
    }
}
