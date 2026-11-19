import Foundation
import Combine

// Port af app/src/main/java/dk/berlinsightrun/i18n/Strings.kt.
// De faste tekster ligger i UIStrings.swift, der genereres fra Kotlin-filen.
// Her ligger de tekster, der bygges af tal og gadenavne.

enum Lang: String, CaseIterable, Identifiable {
    case da, de, en, fr

    var id: String { rawValue }
    var code: String { rawValue }
    var label: String {
        switch self {
        case .da: return "Dansk"
        case .de: return "Deutsch"
        case .en: return "English"
        case .fr: return "Français"
        }
    }
    /// Sprogkode til talesyntese.
    var bcp47: String {
        switch self {
        case .da: return "da-DK"
        case .de: return "de-DE"
        case .en: return "en-GB"
        case .fr: return "fr-FR"
        }
    }
    var strings: Strings { Strings(lang: self) }

    static func fromCode(_ code: String?) -> Lang? {
        guard let c = code?.lowercased() else { return nil }
        return Lang(rawValue: String(c.prefix(2)))
    }

    static func deviceDefault() -> Lang {
        for id in Locale.preferredLanguages { if let l = fromCode(id) { return l } }
        return .en
    }
}

/// Valgt sprog, delt mellem skærme og løb og gemt mellem opstarter.
final class LanguageStore: ObservableObject {
    static let shared = LanguageStore()
    private let key = "language"
    @Published var lang: Lang {
        didSet { UserDefaults.standard.set(lang.code, forKey: key) }
    }
    private init() {
        lang = Lang.fromCode(UserDefaults.standard.string(forKey: key)) ?? Lang.deviceDefault()
    }
}

@dynamicMemberLookup
struct Strings {
    let lang: Lang

    var ui: UIStrings {
        switch lang {
        case .da: return .da
        case .de: return .de
        case .en: return .en
        case .fr: return .fr
        }
    }

    subscript<T>(dynamicMember keyPath: KeyPath<UIStrings, T>) -> T { ui[keyPath: keyPath] }

    // MARK: iOS-udgaver af tekster, der henviser til Androids indstillinger

    var needLocation: String {
        switch lang {
        case .da: return "Appen skal kende din præcise placering for at guide dig. Giv adgang under Indstillinger › Berlin Sightrun › Lokalitet, eller prøv demo-løb."
        case .de: return "Die App braucht deinen genauen Standort, um dich zu führen. Erlaube den Zugriff unter Einstellungen › Berlin Sightrun › Standort oder probiere den Demo-Lauf."
        case .en: return "The app needs your precise location to guide you. Allow access in Settings › Berlin Sightrun › Location, or try the demo run."
        case .fr: return "L'application a besoin de votre position précise pour vous guider. Autorisez l'accès dans Réglages › Berlin Sightrun › Position, ou essayez la course de démonstration."
        }
    }
    var noLocation: String { needLocation }
    var ttsMissing: String {
        switch lang {
        case .da: return "Der er ingen dansk stemme på telefonen. Hent en under Indstillinger › Tilgængelighed › Oplæst indhold › Stemmer › Dansk."
        case .de: return "Auf dem iPhone fehlt eine deutsche Stimme. Lade eine unter Einstellungen › Bedienungshilfen › Gesprochene Inhalte › Stimmen › Deutsch."
        case .en: return "There is no English voice on this iPhone. Download one in Settings › Accessibility › Spoken Content › Voices › English."
        case .fr: return "Aucune voix française sur cet iPhone. Téléchargez-en une dans Réglages › Accessibilité › Contenu énoncé › Voix › Français."
        }
    }
    /// Vises, når den bedste stemme kun har standardkvalitet.
    var voiceTip: String {
        switch lang {
        case .da: return "Tip: Hent en forbedret eller premium-stemme under Indstillinger › Tilgængelighed › Oplæst indhold › Stemmer › Dansk. Den lyder meget mere naturlig."
        case .de: return "Tipp: Lade eine erweiterte oder Premium-Stimme unter Einstellungen › Bedienungshilfen › Gesprochene Inhalte › Stimmen › Deutsch. Sie klingt viel natürlicher."
        case .en: return "Tip: Download an enhanced or premium voice in Settings › Accessibility › Spoken Content › Voices › English. It sounds much more natural."
        case .fr: return "Astuce : téléchargez une voix améliorée ou premium dans Réglages › Accessibilité › Contenu énoncé › Voix › Français. Elle est bien plus naturelle."
        }
    }
    // MARK: Tekster med tal og navne

    func startAtKm(_ km: String) -> String {
        switch lang {
        case .da: return "Start ved km \(km)"
        case .de: return "Start bei km \(km)"
        case .en: return "Start at km \(km)"
        case .fr: return "Départ au km \(km)"
        }
    }

    func nextPoi(_ name: String, _ km: String) -> String {
        switch lang {
        case .da: return "Næste seværdighed: \(name) om \(km) km"
        case .de: return "Nächste Sehenswürdigkeit: \(name) in \(km) km"
        case .en: return "Next sight: \(name) in \(km) km"
        case .fr: return "Prochain monument : \(name) dans \(km) km"
        }
    }

    func eyebrowNarrating(_ n: Int, _ total: Int) -> String {
        switch lang {
        case .da: return "FORTÆLLER NU · \(n) AF \(total)"
        case .de: return "ERZÄHLT GERADE · \(n) VON \(total)"
        case .en: return "NOW TELLING · \(n) OF \(total)"
        case .fr: return "RÉCIT EN COURS · \(n) SUR \(total)"
        }
    }

    func goToStartSpeech(_ distance: String) -> String {
        switch lang {
        case .da: return "Du er \(distance) fra starten. Gå hen til TU Berlins hovedbygning på Straße des 17. Juni. Løbet starter automatisk, når du er der."
        case .de: return "Du bist \(distance) vom Start entfernt. Geh zum Hauptgebäude der TU Berlin an der Straße des 17. Juni. Der Lauf startet automatisch, wenn du dort bist."
        case .en: return "You are \(distance) from the start. Walk to the TU Berlin main building on Straße des 17. Juni. The run starts automatically when you get there."
        case .fr: return "Vous êtes à \(distance) du départ. Rendez-vous devant le bâtiment principal de la TU Berlin, sur la Straße des 17. Juni. La course démarre automatiquement à votre arrivée."
        }
    }

    private func frArt(_ s: String) -> String {
        if s == "Unter den Linden" { return s }
        let l = s.lowercased()
        if l.hasSuffix("straße") || l.hasSuffix("allee") || l.hasSuffix("brücke") { return "la \(s)" }
        return "le \(s)"
    }

    func turn(_ dir: Int, _ sharp: Bool, _ street: String) -> String {
        let l = street.lowercased()
        let bridge = l.contains("brücke")
        switch lang {
        case .da:
            let side = dir > 0 ? "højre" : "venstre"
            let prep = bridge ? "over \(street)" : (l.hasSuffix("platz") || street.hasSuffix("Markt")) ? "ind på \(street)" : "ad \(street)"
            return "drej \(sharp ? "skarpt " : "")til \(side) \(prep)"
        case .de:
            let side = dir > 0 ? "rechts" : "links"
            let prep: String
            if bridge { prep = "über die \(street)" }
            else if l.hasSuffix("straße") { prep = "in die \(street)" }
            else if l.hasSuffix("allee") { prep = "auf die \(street)" }
            else if l.hasSuffix("damm") || l.hasSuffix("platz") { prep = "auf den \(street)" }
            else { prep = "Richtung \(street)" }
            return "\(sharp ? "scharf " : "")\(side) abbiegen \(prep)"
        case .en:
            let side = dir > 0 ? "right" : "left"
            return "turn \(sharp ? "sharp " : "")\(side) \(bridge ? "over" : "onto") \(street)"
        case .fr:
            let side = dir > 0 ? "droite" : "gauche"
            let prep = bridge ? "pour traverser \(frArt(street))" : "vers \(frArt(street))"
            return "tournez \(sharp ? "franchement " : "")à \(side) \(prep)"
        }
    }

    func preAnnounce(_ phrase: String) -> String {
        switch lang {
        case .da: return "Om 100 meter, \(phrase)."
        case .de: return "In 100 Metern \(phrase)."
        case .en: return "In 100 metres, \(phrase)."
        case .fr: return "Dans 100 mètres, \(phrase)."
        }
    }

    func nowAnnounce(_ phrase: String) -> String {
        switch lang {
        case .da: return "\(Strings.cap(phrase)) nu."
        case .de: return "Jetzt \(phrase)."
        case .en: return "\(Strings.cap(phrase)) now."
        case .fr: return "\(Strings.cap(phrase)) maintenant."
        }
    }

    func kmTitle(_ k: Int) -> String {
        switch lang {
        case .da: return "\(k) kilometer"
        case .de: return "\(k) Kilometer"
        case .en: return k == 1 ? "1 kilometre" : "\(k) kilometres"
        case .fr: return k == 1 ? "1 kilomètre" : "\(k) kilomètres"
        }
    }

    /// Ved hver kilometer: samlet tid, tid for seneste kilometer og gennemsnitsfart (sekunder).
    func kmCall(_ k: Int, _ totalSec: Int, _ splitSec: Int, _ avgSec: Int) -> String {
        switch lang {
        case .da:
            return "\(k) kilometer. Tid: \(duration(totalSec)). Sidste kilometer: \(pace(splitSec))." + (k > 1 ? " Gennemsnit: \(pace(avgSec)) per kilometer." : "")
        case .de:
            return "\(k) Kilometer. Zeit: \(duration(totalSec)). Letzter Kilometer: \(pace(splitSec))." + (k > 1 ? " Schnitt: \(pace(avgSec)) pro Kilometer." : "")
        case .en:
            return "\(kmTitle(k)). Time: \(duration(totalSec)). Last kilometre: \(pace(splitSec))." + (k > 1 ? " Average: \(pace(avgSec)) per kilometre." : "")
        case .fr:
            return "\(kmTitle(k)). Temps : \(duration(totalSec)). Dernier kilomètre : \(pace(splitSec))." + (k > 1 ? " Moyenne : \(pace(avgSec)) au kilomètre." : "")
        }
    }

    private func duration(_ sec: Int) -> String {
        let h = sec / 3600, m = sec % 3600 / 60, s = sec % 60
        switch lang {
        case .da:
            return h > 0 ? "\(h) \(h == 1 ? "time" : "timer") og \(m) \(m == 1 ? "minut" : "minutter")"
                         : "\(m) \(m == 1 ? "minut" : "minutter") og \(s) sekunder"
        case .de:
            return h > 0 ? "\(h) \(h == 1 ? "Stunde" : "Stunden") und \(m) \(m == 1 ? "Minute" : "Minuten")"
                         : "\(m) \(m == 1 ? "Minute" : "Minuten") und \(s) Sekunden"
        case .en:
            return h > 0 ? "\(h) \(h == 1 ? "hour" : "hours") \(m) \(m == 1 ? "minute" : "minutes")"
                         : "\(m) \(m == 1 ? "minute" : "minutes") \(s) seconds"
        case .fr:
            return h > 0 ? "\(h) \(h == 1 ? "heure" : "heures") \(m)"
                         : "\(m) \(m <= 1 ? "minute" : "minutes") \(s)"
        }
    }

    private func pace(_ sec: Int) -> String {
        let m = sec / 60, s = sec % 60
        let word: String
        switch lang {
        case .da: word = m == 1 ? "minut" : "minutter"
        case .de: word = m == 1 ? "Minute" : "Minuten"
        case .en: word = m == 1 ? "minute" : "minutes"
        case .fr: word = m <= 1 ? "minute" : "minutes"
        }
        return "\(m) \(word)" + (s > 0 ? " \(s)" : "")
    }

    func toStart(_ distance: String) -> String {
        switch lang {
        case .da: return "\(distance) til start"
        case .de: return "\(distance) bis zum Start"
        case .en: return "\(distance) to the start"
        case .fr: return "\(distance) du départ"
        }
    }

    func kmReached(_ k: Int) -> String {
        switch lang {
        case .da, .de: return "KILOMETER \(k)"
        case .en: return "KILOMETRE \(k)"
        case .fr: return "KILOMÈTRE \(k)"
        }
    }

    func offRoute(_ street: String) -> String {
        switch lang {
        case .da: return "Du er drejet væk fra ruten. Vend om, og løb tilbage til \(street)."
        case .de: return "Du hast die Route verlassen. Dreh um und lauf zurück Richtung \(street)."
        case .en: return "You have left the route. Turn around and run back to \(street)."
        case .fr: return "Vous avez quitté le parcours. Faites demi-tour et revenez vers \(frArt(street))."
        }
    }

    func distance(_ m: Double) -> String {
        if m < 1000 {
            let w = ["da": "meter", "de": "Meter", "en": "metres", "fr": "mètres"][lang.code]!
            return "\(Strings.roundedMetres(m)) \(w)"
        }
        let w = ["da": "kilometer", "de": "Kilometer", "en": "kilometres", "fr": "kilomètres"][lang.code]!
        return "\(km(m)) \(w)"
    }

    // MARK: Format

    /// "5:48" eller "1:02:03"
    func clock(_ sec: Int) -> String { Format.time(sec) }

    func km(_ m: Double) -> String {
        String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), m / 1000.0)
            .replacingOccurrences(of: ".", with: String(ui.decimalSeparator))
    }

    func shortDistance(_ m: Double) -> String { m < 1000 ? "\(Int(m / 10) * 10) m" : "\(km(m)) km" }

    static func cap(_ s: String) -> String { s.prefix(1).uppercased() + s.dropFirst() }
    static func roundedMetres(_ m: Double) -> Int { Int((m / 10).rounded()) * 10 }
}

enum Format {
    static func time(_ sec: Int) -> String {
        let h = sec / 3600, m = sec % 3600 / 60, s = sec % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }

    static func pace(_ secPerKm: Int?) -> String {
        guard let p = secPerKm else { return "–:––" }
        return String(format: "%d:%02d", p / 60, p % 60)
    }
}
