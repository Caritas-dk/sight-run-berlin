import Combine
import CoreLocation
import Foundation
import UIKit

// Svarer til RunService.kt og RunStore.kt på Android: styrer hele løbet med GPS (eller demo-løber),
// rutemotor og lydkø. Kører videre med slukket skærm, så telefonen kan ligge i lommen
// (baggrundstilstandene "location" og "audio" i Info.plist).

enum Phase { case idle, running, finished }

struct Speaking: Equatable {
    let kind: Kind
    let title: String
    let line: String
    let part: Int
    let parts: Int
    let poiNumber: Int?
}

struct RunUiState: Equatable {
    var phase: Phase = .idle
    var demo = false
    var demoSpeed = 10
    var paused = false
    var progressM = 0.0
    var totalM = 21_097.5
    var elapsedSec = 0
    var lat: Double?
    var lon: Double?
    var distanceToStartM: Double?
    var atStart = false
    var turnDistanceM: Double?
    var turnText = ""
    var turnDir = 0
    var turnSharp = false
    var offRoute = false
    var speaking: Speaking?
    var nextPoiName: String?
    var nextPoiDistanceM: Double?
    var playedPois = Set<Int>()
    var heard = 0
    var notice: String?
    var splits: [KmSplit] = []

    var lastSplit: KmSplit? { splits.last }
    var fastestSplit: KmSplit? { splits.min { $0.splitSec < $1.splitSec } }
    /// Sekunder pr. km.
    var paceSecPerKm: Int? { progressM > 200 ? Int(Double(elapsedSec) / (progressM / 1000)) : nil }
}

final class RunController: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let preStartLat = 52.5134   // ca. 440 m øst for TU Berlin på Straße des 17. Juni
    static let preStartLon = 13.3330
    static let plannedPace = 355.0     // 5:55 min/km

    @Published private(set) var state = RunUiState()
    @Published private(set) var voiceNotice: String?
    @Published private(set) var locationDenied = false

    let route: Route
    private let language: LanguageStore
    private var engine: RunEngine!
    private var announcer: Announcer!
    private let location = CLLocationManager()
    private var demoTimer: Timer?
    private var clockTimer: Timer?
    private var cancellables = Set<AnyCancellable>()

    private var demo = false
    private var demoSpeed = 10
    private var paused = false
    private var running = false
    private var elapsed: TimeInterval = 0
    private var lastTick = Date()
    private var demoM = 0.0
    private var walkToStart = false
    private var walkLat = RunController.preStartLat
    private var walkLon = RunController.preStartLon
    private var lastDemoTick = Date()
    private var pendingGpsStart = false

    init(route: Route = .load(), language: LanguageStore = .shared) {
        self.route = route
        self.language = language
        super.init()
        location.delegate = self
        location.desiredAccuracy = kCLLocationAccuracyBest
        location.activityType = .fitness
        location.distanceFilter = kCLDistanceFilterNone
        location.pausesLocationUpdatesAutomatically = false
        makeEngine()
        // Skift sprog midt i løbet: næste besked kommer på det nye sprog.
        language.$lang.dropFirst().sink { [weak self] l in
            guard let self else { return }
            self.engine.lang = l
            self.announcer.setLanguage(l)
            if self.running, let lat = self.state.lat, let lon = self.state.lon { self.publish(lat, lon) }
        }.store(in: &cancellables)
    }

    var strings: Strings { language.lang.strings }
    var voiceName: String? { announcer.voiceName }

    private func makeEngine() {
        let lang = language.lang
        announcer = Announcer(
            lang: lang,
            progressM: { [weak self] in self?.engine.progressM ?? 0 },
            distanceToNextTurnM: { [weak self] in
                guard let self else { return .greatestFiniteMagnitude }
                return self.route.nextManeuver(self.engine.progressM).map { $0.atM - self.engine.progressM } ?? .greatestFiniteMagnitude
            },
            onSpeaking: { [weak self] a in self?.state.speaking = a.map(RunController.speaking) },
            onVoiceNotice: { [weak self] msg in self?.voiceNotice = msg }
        )
        engine = RunEngine(route: route, lang: lang, elapsedSec: { [weak self] in Int(self?.elapsed ?? 0) }) { [weak self] a in
            self?.announcer.enqueue(a)
        }
    }

    private static func speaking(_ a: Announcement) -> Speaking {
        Speaking(kind: a.kind, title: a.title, line: a.sentences.indices.contains(a.index) ? a.sentences[a.index] : "",
                 part: a.index + 1, parts: a.sentences.count, poiNumber: a.poi?.n)
    }

    // MARK: - Start og stop

    func startGpsRun() {
        switch location.authorizationStatus {
        case .notDetermined:
            pendingGpsStart = true
            location.requestWhenInUseAuthorization()
        case .denied, .restricted:
            locationDenied = true
            state.notice = strings.needLocation
        default:
            start(demo: false, speed: 1, startKm: 0, walkToStart: false)
        }
    }

    func start(demo: Bool, speed: Int, startKm: Double, walkToStart: Bool) {
        if running { return }
        makeEngine()
        self.demo = demo
        demoSpeed = speed
        running = true
        paused = false
        locationDenied = false
        // Ved demo-start midt på ruten lader vi som om, de første km blev løbet i 5:55/km.
        elapsed = startKm * RunController.plannedPace
        demoM = startKm * 1000
        if startKm > 0 { engine.jumpTo(demoM) }
        state = RunUiState(phase: .running, demo: demo, demoSpeed: speed, totalM: route.totalM)
        Announcer.configureSession()
        UIApplication.shared.isIdleTimerDisabled = demo

        if demo {
            self.walkToStart = walkToStart && startKm == 0
            walkLat = RunController.preStartLat
            walkLon = RunController.preStartLon
            lastDemoTick = Date()
            demoTimer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in self?.demoTick() }
        } else {
            location.allowsBackgroundLocationUpdates = true
            location.showsBackgroundLocationIndicator = true
            location.startUpdatingLocation()
        }
        lastTick = Date()
        clockTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.clockTick() }
    }

    func stop() {
        stopTracking()
        announcer.clear()
        running = false
        UIApplication.shared.isIdleTimerDisabled = false
        state = RunUiState()
    }

    func backToOverview() { stop() }

    private func stopTracking() {
        location.stopUpdatingLocation()
        location.allowsBackgroundLocationUpdates = false
        demoTimer?.invalidate(); demoTimer = nil
        clockTimer?.invalidate(); clockTimer = nil
    }

    // MARK: - Styring under løbet

    func togglePause() { setPaused(!paused) }

    func setPaused(_ p: Bool) {
        paused = p
        if p { announcer.pause() } else {
            lastTick = Date()
            lastDemoTick = Date()
            announcer.resume()
        }
        state.paused = p
    }

    func repeatLast() { announcer.repeatLast() }
    func skipNarration() { announcer.skipNarration() }

    func setDemoSpeed(_ s: Int) {
        demoSpeed = s
        state.demoSpeed = s
    }

    // MARK: - Demo-løber: 5:55 min/km ganget med hastigheden; 2× mens der fortælles

    private func demoTick() {
        let now = Date()
        let dt = now.timeIntervalSince(lastDemoTick)
        lastDemoTick = now
        if paused { return }
        let nowMs = Int64(now.timeIntervalSince1970 * 1000)
        if walkToStart && !engine.atStart {
            // Gå først fra et punkt ca. 440 m væk hen til startpunktet (1,4 m/s), så startindikatoren kan ses.
            let d = Geo.distanceM(walkLat, walkLon, route.start.lat, route.start.lon)
            let f = min(1.4 * dt * Double(demoSpeed) / max(d, 0.1), 1)
            walkLat += (route.start.lat - walkLat) * f
            walkLon += (route.start.lon - walkLon) * f
            engine.onLocation(lat: walkLat, lon: walkLon, accuracyM: 5, timeMs: nowMs)
            publish(walkLat, walkLon)
            afterUpdate()
            return
        }
        if engine.finished { return }
        let eff = announcer.isSpeakingNarration ? min(demoSpeed, 2) : demoSpeed
        demoM = min(demoM + 1000 / RunController.plannedPace * dt * Double(eff), route.totalM)
        elapsed += dt * Double(eff)
        let pos = route.positionAt(demoM)
        engine.onLocation(lat: pos.lat, lon: pos.lon, accuracyM: 5, timeMs: nowMs)
        publish(pos.lat, pos.lon)
        afterUpdate()
    }

    private func clockTick() {
        let now = Date()
        if !demo && !paused && engine.atStart && !engine.finished { elapsed += now.timeIntervalSince(lastTick) }
        lastTick = now
        state.elapsedSec = Int(elapsed)
    }

    // MARK: - GPS

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let st = manager.authorizationStatus
        if st == .denied || st == .restricted {
            locationDenied = true
            if pendingGpsStart { state.notice = strings.needLocation }
            pendingGpsStart = false
        } else if st == .authorizedWhenInUse || st == .authorizedAlways {
            locationDenied = false
            state.notice = nil
            if pendingGpsStart {
                pendingGpsStart = false
                start(demo: false, speed: 1, startKm: 0, walkToStart: false)
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard running, !demo, !paused else { return }
        for loc in locations where loc.horizontalAccuracy >= 0 {
            engine.onLocation(lat: loc.coordinate.latitude, lon: loc.coordinate.longitude,
                              accuracyM: loc.horizontalAccuracy, timeMs: Int64(loc.timestamp.timeIntervalSince1970 * 1000))
        }
        if let last = locations.last { publish(last.coordinate.latitude, last.coordinate.longitude) }
        afterUpdate()
    }

    // MARK: - Tilstand til skærmene

    private func publish(_ lat: Double, _ lon: Double) {
        let p = engine.progressM
        let turnIndex = route.maneuvers.firstIndex { $0.atM > p - 10 }
        let turn = turnIndex.map { route.maneuvers[$0] }
        let poi = route.nextPoi(p)
        let s = strings
        var st = state
        st.progressM = p
        st.lat = lat
        st.lon = lon
        st.distanceToStartM = engine.distanceToStartM
        st.atStart = engine.atStart
        st.turnDistanceM = turn.map { max($0.atM - p, 0) }
        st.turnText = turnIndex.map { Strings.cap(engine.phraseFor($0)) } ?? s.straightToFinish
        st.turnDir = turn?.dir ?? 0
        st.turnSharp = turn?.sharp ?? false
        st.offRoute = engine.offRoute
        st.nextPoiName = poi?.name(language.lang.code)
        st.nextPoiDistanceM = poi.map { $0.atM - p }
        st.playedPois = engine.playedPois
        st.heard = engine.heardCount
        st.splits = engine.splits
        state = st
    }

    private func afterUpdate() {
        announcer.tick()
        if engine.finished && running {
            running = false
            // Lad målfortællingen blive læst færdig, før skærmen skifter.
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
                guard let self else { return }
                self.state.phase = .finished
                self.state.progressM = self.route.totalM
                self.state.heard = self.engine.heardCount
                self.stopTracking()
                UIApplication.shared.isIdleTimerDisabled = false
            }
        }
    }
}
