import AVFoundation
import Foundation

// Port af app/src/main/java/dk/berlinsightrun/audio/Announcer.kt.
// Prioriteret lydkø. Rutevejledning afbryder fortællinger, fortællinger venter, til næste sving er
// mindst 250 m væk, og musikken dæmpes, så længe køen taler. Alle metoder kaldes fra hovedtråden.

final class Announcer: NSObject, AVSpeechSynthesizerDelegate, AVAudioPlayerDelegate {
    private var lang: Lang
    private let progressM: () -> Double
    private let distanceToNextTurnM: () -> Double
    private let onSpeaking: (Announcement?) -> Void
    private let onVoiceNotice: (String?) -> Void

    private let synth = AVSpeechSynthesizer()
    private var queue: [Announcement] = []
    private var paused = false
    private var current: Announcement?
    private var currentId = 0
    private var counter = 0
    private var player: AVAudioPlayer?
    private var lastSpoken: Announcement?
    private var voice: AVSpeechSynthesisVoice?
    private var releaseWork: DispatchWorkItem?
    private var sessionActive = false

    init(lang: Lang,
         progressM: @escaping () -> Double,
         distanceToNextTurnM: @escaping () -> Double,
         onSpeaking: @escaping (Announcement?) -> Void,
         onVoiceNotice: @escaping (String?) -> Void) {
        self.lang = lang
        self.progressM = progressM
        self.distanceToNextTurnM = distanceToNextTurnM
        self.onSpeaking = onSpeaking
        self.onVoiceNotice = onVoiceNotice
        super.init()
        synth.delegate = self
        // Stemmen bruger appens egen lydsession, så den kan dæmpe anden musik.
        synth.usesApplicationAudioSession = true
        applyVoice()
    }

    // MARK: - Stemmevalg

    /// Vælger den bedste installerede stemme: premium før forbedret før standard, rigtigt land (fx en-GB)
    /// før andre lande, og aldrig sjove stemmer eller personlige stemmer.
    static func bestVoice(for lang: Lang) -> AVSpeechSynthesisVoice? {
        let candidates = AVSpeechSynthesisVoice.speechVoices().filter { v in
            guard v.language.lowercased().hasPrefix(lang.code) else { return false }
            if #available(iOS 17.0, *) {
                if v.voiceTraits.contains(.isNoveltyVoice) || v.voiceTraits.contains(.isPersonalVoice) { return false }
            }
            return true
        }
        return candidates.max { score($0, lang) < score($1, lang) }
    }

    static func score(_ v: AVSpeechSynthesisVoice, _ lang: Lang) -> Int {
        voiceScore(quality: v.quality, countryMatches: v.language.caseInsensitiveCompare(lang.bcp47) == .orderedSame,
                   isUSEnglish: v.language == "en-US", name: v.name)
    }

    /// Ren funktion, så valget kan unit-testes.
    static func voiceScore(quality: AVSpeechSynthesisVoiceQuality, countryMatches: Bool, isUSEnglish: Bool, name: String) -> Int {
        var s: Int
        switch quality {
        case .premium: s = 3000
        case .enhanced: s = 2000
        default: s = 1000
        }
        if countryMatches { s += 300 } else if isUSEnglish { s += 200 }
        // Siri-lignende og kendte gode stemmer foran tilfældige standardstemmer
        if name.range(of: "siri", options: .caseInsensitive) != nil { s += 50 }
        return s
    }

    private func applyVoice() {
        voice = Announcer.bestVoice(for: lang) ?? AVSpeechSynthesisVoice(language: lang.bcp47)
        if voice == nil {
            onVoiceNotice(lang.strings.ttsMissing)
        } else if voice?.quality == .default {
            onVoiceNotice(lang.strings.voiceTip)
        } else {
            onVoiceNotice(nil)
        }
    }

    var voiceName: String? { voice.map { "\($0.name) (\($0.language))" } }

    /// Skifter talesprog. Igangværende sætning tales færdig, næste besked kommer på det nye sprog.
    func setLanguage(_ newLang: Lang) {
        if newLang == lang { return }
        lang = newLang
        applyVoice()
        // Beskeder i kø og den, der tales lige nu, oversættes, så de ikke læses op på det gamle sprog.
        queue.forEach { $0.relocalize(newLang) }
        lastSpoken?.relocalize(newLang)
        if let cur = current, !paused {
            stopOutput(cur)
            cur.relocalize(newLang)
            play(cur)
        } else {
            current?.relocalize(newLang)
        }
    }

    // MARK: - Kø

    func enqueue(_ a: Announcement) {
        if let cur = current, a.priority < cur.priority {
            stopOutput(cur)
            current = nil
            queue.insert(cur, at: 0)
        }
        queue.append(a)
        // Stabil sortering: samme prioritet bevarer rækkefølgen.
        queue = queue.enumerated().sorted { ($0.element.priority, $0.offset) < ($1.element.priority, $1.offset) }.map(\.element)
        pump()
    }

    /// Kaldes ved hver positionsopdatering, så ventende fortællinger kan starte efter et sving.
    func tick() { pump() }

    var isSpeakingNarration: Bool { current?.kind == .narration }

    func pause() {
        paused = true
        if let cur = current { stopOutput(cur); queue.insert(cur, at: 0) }
        current = nil
        releaseSession()
        onSpeaking(nil)
    }

    func resume() {
        paused = false
        pump()
    }

    func skipNarration() {
        guard let cur = current, cur.kind == .narration else { return }
        stopOutput(cur)
        current = nil
        pump()
    }

    func repeatLast() {
        if let a = lastSpoken { enqueue(a.copyForRepeat()) }
    }

    func clear() {
        if let cur = current { stopOutput(cur) }
        current = nil
        queue.removeAll()
        releaseWork?.cancel()
        releaseSession()
        onSpeaking(nil)
    }

    private func pump() {
        if paused || current != nil { return }
        let now = progressM()
        queue.removeAll { a in a.staleAfterM.map { now > $0 } ?? false }
        guard let idx = queue.firstIndex(where: { $0.kind != .narration || $0.force || distanceToNextTurnM() > 250 }) else {
            scheduleRelease()
            onSpeaking(nil)
            return
        }
        let next = queue.remove(at: idx)
        current = next
        play(next)
    }

    private func play(_ a: Announcement) {
        releaseWork?.cancel()
        acquireSession()
        lastSpoken = a
        onSpeaking(a)
        counter += 1
        let id = counter
        currentId = id

        if a.kind == .narration, let poi = a.poi, let url = Announcer.recordedAudio(poi: poi, lang: a.currentLang) {
            playRecorded(a, url: url, id: id)
            return
        }
        guard a.index < a.sentences.count else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in self?.segmentDone(id) }
            return
        }
        let u = AVSpeechUtterance(string: a.sentences[a.index])
        u.voice = voice
        u.rate = AVSpeechUtteranceDefaultSpeechRate
        u.pitchMultiplier = 1.0
        u.postUtteranceDelay = 0.05
        utteranceIds[ObjectIdentifier(u)] = id
        synth.speak(u)
    }

    private var utteranceIds: [ObjectIdentifier: Int] = [:]

    /// Indspillede fortællinger: en fil i app-pakken med navnet `<sprog>_<nr>`, fx `de_07.m4a`.
    static func recordedAudio(poi: Poi, lang: Lang, bundle: Bundle = .main) -> URL? {
        let name = String(format: "%@_%02d", lang.code, poi.n)
        for ext in ["m4a", "mp3", "caf", "wav", "aac"] {
            if let url = bundle.url(forResource: name, withExtension: ext) { return url }
        }
        return nil
    }

    private func playRecorded(_ a: Announcement, url: URL, id: Int) {
        do {
            let p = try AVAudioPlayer(contentsOf: url)
            p.delegate = self
            player = p
            p.currentTime = a.audioPosition
            p.play()
        } catch {
            player = nil
            // Faldback til tekst-til-tale
            let u = AVSpeechUtterance(string: a.sentences.indices.contains(a.index) ? a.sentences[a.index] : a.text)
            u.voice = voice
            utteranceIds[ObjectIdentifier(u)] = id
            synth.speak(u)
        }
    }

    private func segmentDone(_ id: Int) {
        guard id == currentId, let a = current else { return }
        player = nil
        a.audioPosition = 0
        a.index += 1
        if a.index < a.sentences.count {
            play(a)
        } else {
            a.onDone?()
            current = nil
            currentId = 0
            pump()
        }
    }

    private func stopOutput(_ a: Announcement) {
        currentId = 0
        if let p = player {
            a.audioPosition = p.currentTime
            p.stop()
        }
        player = nil
        synth.stopSpeaking(at: .immediate)
    }

    // MARK: - AVSpeechSynthesizerDelegate

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        let key = ObjectIdentifier(utterance)
        guard let id = utteranceIds.removeValue(forKey: key) else { return }
        DispatchQueue.main.async { self.segmentDone(id) }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        utteranceIds.removeValue(forKey: ObjectIdentifier(utterance))
    }

    // MARK: - AVAudioPlayerDelegate

    func audioPlayerDidFinishPlaying(_ p: AVAudioPlayer, successfully flag: Bool) {
        let id = currentId
        DispatchQueue.main.async {
            if let a = self.current { a.index = max(a.sentences.count - 1, 0) } // hele filen er én bid
            self.segmentDone(id)
        }
    }

    // MARK: - Lydsession: dæmp anden lyd, mens appen taler

    static func configureSession() {
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playback, mode: .voicePrompt, options: [.duckOthers, .interruptSpokenAudioAndMixWithOthers])
    }

    private func acquireSession() {
        guard !sessionActive else { return }
        Announcer.configureSession()
        try? AVAudioSession.sharedInstance().setActive(true)
        sessionActive = true
    }

    private func scheduleRelease() {
        releaseWork?.cancel()
        let w = DispatchWorkItem { [weak self] in self?.releaseSession() }
        releaseWork = w
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: w)
    }

    private func releaseSession() {
        guard sessionActive, current == nil else { return }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        sessionActive = false
    }
}
