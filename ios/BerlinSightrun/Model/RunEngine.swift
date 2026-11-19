import Foundation

// Port af app/src/main/java/dk/berlinsightrun/run/RunEngine.kt.
// Ren logik uden iOS-afhængigheder: tager GPS-punkter, holder styr på positionen på ruten og beslutter,
// hvad der skal siges, på det valgte sprog. Kan unit-testes med et afspillet spor.

/// Tid for én kilometer. Alle tider i sekunder.
struct KmSplit: Equatable {
    let km: Int
    let totalSec: Int
    let splitSec: Int
    let avgSec: Int
}

final class RunEngine {
    static let maxAccuracyM = 30.0
    static let startRadiusM = 50.0
    static let onRouteM = 40.0
    static let offRouteM = 40.0
    static let backOnRouteM = 25.0
    static let offRouteDelayMs: Int64 = 10_000
    static let preAnnounceM = 100.0
    static let nowAnnounceM = 15.0
    static let missedFenceM = 150.0
    static let finishToleranceM = 30.0

    private let route: Route
    var lang: Lang
    /// Løbetid i sekunder (pauser fratrukket). Leveres af løbet.
    private let elapsedSec: () -> Int
    private let announce: (Announcement) -> Void

    /// Kilometertider indtil nu, i rækkefølge.
    private(set) var splits: [KmSplit] = []
    private(set) var progressM = 0.0
    private(set) var distanceToRouteM = 0.0
    private(set) var offRoute = false
    private(set) var atStart = false
    private(set) var distanceToStartM: Double?
    private(set) var finished = false
    private(set) var heardCount = 0
    private(set) var playedPois = Set<Int>()

    var narrationsEnabled = true
    var kmCallsEnabled = true

    private var s: Strings { lang.strings }
    private var preAnnounced: [Bool]
    private var nowAnnounced: [Bool]
    private var nextKm = 1
    private var offSinceMs: Int64?
    private var startWarned = false
    /// Løbetid ved sidste hop; første split efter et hop regnes herfra.
    private var splitBaseSec = 0

    init(route: Route, lang: Lang = .da, elapsedSec: @escaping () -> Int = { 0 }, announce: @escaping (Announcement) -> Void) {
        self.route = route
        self.lang = lang
        self.elapsedSec = elapsedSec
        self.announce = announce
        preAnnounced = Array(repeating: false, count: route.maneuvers.count)
        nowAnnounced = Array(repeating: false, count: route.maneuvers.count)
    }

    /// Laver en besked, der kan skifte sprog, mens den står i kø eller bliver talt.
    private func say(_ kind: Kind, staleAfterM: Double? = nil, poi: Poi? = nil, force: Bool = false,
                     onDone: (() -> Void)? = nil, make: @escaping (Strings, Lang) -> (String, String)) {
        announce(Announcement(kind: kind, lang: lang, staleAfterM: staleAfterM, poi: poi, force: force, onDone: onDone) { l in
            make(l.strings, l)
        })
    }

    func onLocation(lat: Double, lon: Double, accuracyM: Double, timeMs: Int64) {
        if finished { return }
        if accuracyM > RunEngine.maxAccuracyM { return }

        if !atStart {
            let d = Geo.distanceM(lat, lon, route.start.lat, route.start.lon)
            distanceToStartM = d
            if d > RunEngine.startRadiusM {
                if !startWarned {
                    startWarned = true
                    say(.system, force: true) { s, _ in (s.goToStartTitle, s.goToStartSpeech(s.distance(d))) }
                }
                return
            }
            atStart = true
            distanceToStartM = nil
            say(.system, force: true) { s, _ in (s.welcomeTitle, s.welcome) }
        }

        let p = route.project(lat: lat, lon: lon, fromM: progressM)
        distanceToRouteM = p.distanceM
        updateOffRoute(p.distanceM, timeMs)
        if p.distanceM <= RunEngine.onRouteM { progressM = max(progressM, p.progressM) }

        checkManeuvers()
        checkPois(lat, lon)
        checkKilometres()

        if progressM >= route.totalM - RunEngine.finishToleranceM {
            finished = true
            progressM = route.totalM
            let r = route
            say(.narration, force: true) { s, l in (s.finishTitle, r.finishText(l.code)) }
        }
    }

    /// Til demo-tilstand og test: placér løberen direkte på ruten.
    func onRoutePosition(_ m: Double, timeMs: Int64) {
        let pos = route.positionAt(m)
        onLocation(lat: pos.lat, lon: pos.lon, accuracyM: 5, timeMs: timeMs)
    }

    /// Svingets tekst på det aktuelle sprog, fx "drej til højre ad Kaiserdamm".
    func phraseFor(_ index: Int) -> String {
        let m = route.maneuvers[index]
        return s.turn(m.dir, m.sharp, m.street)
    }

    private func updateOffRoute(_ distance: Double, _ timeMs: Int64) {
        if distance > RunEngine.offRouteM {
            let since = offSinceMs ?? timeMs
            if offSinceMs == nil { offSinceMs = timeMs }
            if !offRoute && timeMs - since >= RunEngine.offRouteDelayMs {
                offRoute = true
                let street = route.waypoints[route.positionAt(progressM).segment].street
                let st = street.isEmpty ? "Straße des 17. Juni" : street
                say(.offRoute) { s, _ in (s.offTitle, s.offRoute(st)) }
            }
        } else if distance <= RunEngine.backOnRouteM {
            offSinceMs = nil
            if offRoute {
                offRoute = false
                say(.offRoute) { s, _ in (s.backOnTitle, s.backOn) }
            }
        }
    }

    private func checkManeuvers() {
        for (i, m) in route.maneuvers.enumerated() {
            let d = m.atM - progressM
            if !preAnnounced[i] && d <= RunEngine.preAnnounceM && d > 25 {
                preAnnounced[i] = true
                say(.turn, staleAfterM: m.atM) { s, _ in (s.turnAheadTitle, s.preAnnounce(s.turn(m.dir, m.sharp, m.street))) }
            }
            if !nowAnnounced[i] && d <= RunEngine.nowAnnounceM {
                nowAnnounced[i] = true
                preAnnounced[i] = true
                if d > -40 {
                    say(.turn, staleAfterM: m.atM + 60) { s, _ in (s.turnNowTitle, s.nowAnnounce(s.turn(m.dir, m.sharp, m.street))) }
                }
            }
        }
    }

    private func checkPois(_ lat: Double, _ lon: Double) {
        for poi in route.pois {
            if playedPois.contains(poi.n) { continue }
            let inFence = progressM >= poi.atM - poi.radiusM && Geo.distanceM(lat, lon, poi.lat, poi.lon) <= poi.radiusM
            // Hvis GPS'en har misset geofencet, udløses fortællingen alligevel lidt efter.
            let passed = progressM >= poi.atM + RunEngine.missedFenceM
            if !inFence && !passed { continue }
            playedPois.insert(poi.n)
            if !narrationsEnabled { continue }
            say(.narration, staleAfterM: poi.atM + 2_500, poi: poi, onDone: { [weak self] in self?.heardCount += 1 }) { _, l in
                (poi.name(l.code), poi.text(l.code))
            }
        }
    }

    private func checkKilometres() {
        while nextKm <= 21 && progressM >= Double(nextKm) * 1000 {
            let k = nextKm
            nextKm += 1
            let total = elapsedSec()
            let split = total - (splits.last?.totalSec ?? splitBaseSec)
            let sp = KmSplit(km: k, totalSec: total, splitSec: split, avgSec: total / k)
            splits.append(sp)
            if kmCallsEnabled {
                say(.km, staleAfterM: Double(k) * 1000 + 400) { s, _ in (s.kmTitle(k), s.kmCall(k, sp.totalSec, sp.splitSec, sp.avgSec)) }
            }
        }
    }

    /// Hop i ruten (demo): alt før `m` markeres som overstået uden at blive sagt.
    func jumpTo(_ m: Double) {
        atStart = true
        progressM = min(max(m, 0), route.totalM - 50)
        // Første kilometer efter hoppet: regn med samme fart som indtil nu for den del, der er sprunget over.
        let e = elapsedSec()
        splitBaseSec = progressM > 0 ? e - Int((progressM.truncatingRemainder(dividingBy: 1000)) / progressM * Double(e)) : e
        splits.removeAll()
        for (i, man) in route.maneuvers.enumerated() {
            let passed = man.atM < progressM + 20
            nowAnnounced[i] = passed
            preAnnounced[i] = passed || man.atM - progressM < RunEngine.preAnnounceM
        }
        for p in route.pois where p.atM < progressM { playedPois.insert(p.n) }
        nextKm = Int(progressM / 1000) + 1
    }
}
