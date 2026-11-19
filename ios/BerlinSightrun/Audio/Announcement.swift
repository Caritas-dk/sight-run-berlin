import Foundation

// Port af app/src/main/java/dk/berlinsightrun/audio/Announcement.kt.

/// Lavere tal = højere prioritet. En besked med højere prioritet afbryder den igangværende.
enum Kind: Int {
    case offRoute = 1
    case turn = 2
    case system = 3   // samme prioritet som sving (se `priority`)
    case narration = 4
    case km = 5

    var priority: Int {
        switch self {
        case .offRoute: return 1
        case .turn, .system: return 2
        case .narration: return 3
        case .km: return 4
        }
    }
}

/// En besked i lydkøen. `localize` laver titel og tekst på et givet sprog, så en besked, der allerede
/// står i kø eller bliver talt, kan skifte sprog, når brugeren vælger et nyt.
final class Announcement {
    let kind: Kind
    let lang: Lang
    /// Beskeden droppes, hvis løberen er nået forbi denne position (gade-meter), før den blev afspillet.
    let staleAfterM: Double?
    let poi: Poi?
    /// Fortællinger venter normalt på, at næste sving er mindst 250 m væk; force springer det over.
    let force: Bool
    let onDone: (() -> Void)?
    private let localize: (Lang) -> (String, String)

    private(set) var currentLang: Lang
    private(set) var title: String
    private(set) var text: String
    private(set) var sentences: [String]
    var index = 0
    /// Hvor langt en indspillet lydfil nåede, hvis den blev afbrudt (sekunder).
    var audioPosition: TimeInterval = 0

    init(kind: Kind, lang: Lang, staleAfterM: Double? = nil, poi: Poi? = nil, force: Bool = false,
         onDone: (() -> Void)? = nil, localize: @escaping (Lang) -> (String, String)) {
        self.kind = kind
        self.lang = lang
        self.staleAfterM = staleAfterM
        self.poi = poi
        self.force = force
        self.onDone = onDone
        self.localize = localize
        currentLang = lang
        let (t, x) = localize(lang)
        title = t
        text = x
        sentences = Announcement.splitSentences(x)
    }

    var priority: Int { kind.priority }

    /// Skifter sprog. En fortælling, der er halvvejs, fortsætter cirka samme sted i den nye tekst.
    func relocalize(_ newLang: Lang) {
        if newLang == currentLang { return }
        let fraction = sentences.isEmpty ? 0 : Double(index) / Double(sentences.count)
        (title, text) = localize(newLang)
        sentences = Announcement.splitSentences(text)
        index = min(max(Int(floor(fraction * Double(sentences.count))), 0), max(sentences.count - 1, 0))
        audioPosition = 0
        currentLang = newLang
    }

    func copyForRepeat() -> Announcement {
        Announcement(kind: kind, lang: currentLang, poi: poi, force: true, localize: localize)
    }

    /// Deler ikke foran et månedsnavn, så "Straße des 17. Juni" forbliver én sætning.
    private static let sentenceBreak = try! NSRegularExpression(
        pattern: "(?<=[.!?])\\s+(?=[A-ZÆØÅÉÀÂÇÈÊÎÔÛÄÖÜ0-9])(?!(?:Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)\\b)"
    )

    static func splitSentences(_ text: String) -> [String] {
        let ns = text as NSString
        var parts: [String] = []
        var last = 0
        for m in sentenceBreak.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            parts.append(ns.substring(with: NSRange(location: last, length: m.range.location - last)))
            last = m.range.location + m.range.length
        }
        parts.append(ns.substring(from: last))
        return parts.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
    }
}
