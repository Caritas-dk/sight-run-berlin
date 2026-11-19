import Foundation

// Port af app/src/main/java/dk/berlinsightrun/route/Route.kt. Samme regler og samme tal.

struct Waypoint: Equatable {
    let name: String
    let lat: Double
    let lon: Double
    let street: String
}

struct Poi: Identifiable, Equatable {
    let n: Int
    let names: [String: String]
    let km: Double
    let lat: Double
    let lon: Double
    let radiusM: Double
    /// Sti med {lang}, fx "audio/{lang}/06.ogg".
    let audioTemplate: String?
    let texts: [String: String]

    var id: Int { n }
    /// Udløsningspunktets position på ruten i gade-meter.
    var atM: Double { km * 1000.0 }

    func name(_ lang: String) -> String { names[lang] ?? names["en"] ?? names.values.first ?? "" }
    func text(_ lang: String) -> String { texts[lang] ?? texts["en"] ?? texts.values.first ?? "" }
}

/// Et sving: `dir` er 1 for højre og -1 for venstre. Teksten laves af sprogmodulet.
struct Maneuver: Equatable {
    let atM: Double
    let dir: Int
    let sharp: Bool
    let street: String
}

struct RoutePosition {
    let lat: Double
    let lon: Double
    let segment: Int
    let headingDeg: Double
}

struct Projection {
    let progressM: Double
    let distanceM: Double
    let segment: Int
}

enum Geo {
    private static let R = 6_371_000.0
    private static func rad(_ d: Double) -> Double { d * .pi / 180 }

    static func distanceM(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let dLat = rad(lat2 - lat1)
        let dLon = rad(lon2 - lon1)
        let h = pow(sin(dLat / 2), 2) + cos(rad(lat1)) * cos(rad(lat2)) * pow(sin(dLon / 2), 2)
        return 2 * R * asin(sqrt(h))
    }

    static func bearingDeg(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let y = sin(rad(lon2 - lon1)) * cos(rad(lat2))
        let x = cos(rad(lat1)) * sin(rad(lat2)) - sin(rad(lat1)) * cos(rad(lat2)) * cos(rad(lon2 - lon1))
        return (atan2(y, x) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
    }
}

/// Halvmarathonruten. Waypoints skaleres, så hele ruten måler præcis `totalM` gade-meter.
final class Route {
    let name: String
    let totalM: Double
    let waypoints: [Waypoint]
    let pois: [Poi]
    let finishTexts: [String: String]
    let scale: Double
    /// Gade-meter ved hvert waypoint.
    let cum: [Double]
    private(set) var maneuvers: [Maneuver] = []

    init(name: String, totalM: Double, waypoints: [Waypoint], pois: [Poi], finishTexts: [String: String]) {
        precondition(waypoints.count >= 2, "Ruten skal have mindst to punkter")
        self.name = name
        self.totalM = totalM
        self.waypoints = waypoints
        self.pois = pois.sorted { $0.km < $1.km }
        self.finishTexts = finishTexts
        var raw = [0.0]
        for i in 1..<waypoints.count {
            let a = waypoints[i - 1], b = waypoints[i]
            raw.append(raw[i - 1] + Geo.distanceM(a.lat, a.lon, b.lat, b.lon))
        }
        scale = totalM / raw.last!
        cum = raw.map { $0 * (totalM / raw.last!) }
        maneuvers = detectManeuvers()
    }

    var start: Waypoint { waypoints[0] }

    func finishText(_ lang: String) -> String { finishTexts[lang] ?? finishTexts["en"] ?? finishTexts.values.first ?? "" }

    private func detectManeuvers() -> [Maneuver] {
        var list: [Maneuver] = []
        for i in 1..<(waypoints.count - 1) {
            let p = waypoints[i - 1], c = waypoints[i], n = waypoints[i + 1]
            var d = Geo.bearingDeg(c.lat, c.lon, n.lat, n.lon) - Geo.bearingDeg(p.lat, p.lon, c.lat, c.lon)
            d = (d + 540).truncatingRemainder(dividingBy: 360) - 180
            if abs(d) < 35 { continue }
            list.append(Maneuver(atM: cum[i], dir: d > 0 ? 1 : -1, sharp: abs(d) > 125, street: c.street))
        }
        return list
    }

    func nextManeuver(_ progressM: Double) -> Maneuver? { maneuvers.first { $0.atM > progressM } }
    func nextPoi(_ progressM: Double) -> Poi? { pois.first { $0.atM > progressM } }

    /// Punkt på ruten efter `m` gade-meter.
    func positionAt(_ m: Double) -> RoutePosition {
        let mm = min(max(m, 0), totalM)
        var i = 1
        while i < cum.count - 1 && cum[i] < mm { i += 1 }
        let a = waypoints[i - 1], b = waypoints[i]
        let len = (cum[i] - cum[i - 1]) > 0 ? (cum[i] - cum[i - 1]) : 1
        let t = min(max((mm - cum[i - 1]) / len, 0), 1)
        return RoutePosition(
            lat: a.lat + (b.lat - a.lat) * t,
            lon: a.lon + (b.lon - a.lon) * t,
            segment: i - 1,
            headingDeg: Geo.bearingDeg(a.lat, a.lon, b.lat, b.lon)
        )
    }

    /// Projicerer en GPS-position vinkelret ind på ruten. Kun segmenter mellem `fromM - backM` og
    /// `fromM + aheadM` søges, så ruten ikke springer til et andet sted, hvor den passerer tæt på sig selv
    /// (Kurfürstendamm og Karl-Marx-Allee, hvor ruten vender).
    func project(lat: Double, lon: Double, fromM: Double, backM: Double = 60, aheadM: Double = 350) -> Projection {
        let lo = fromM - backM, hi = fromM + aheadM
        var best: Projection?
        let kx = cos(lat * .pi / 180) * 111_320.0
        let ky = 110_540.0
        for i in 0..<(waypoints.count - 1) {
            if cum[i + 1] < lo || cum[i] > hi { continue }
            let a = waypoints[i], b = waypoints[i + 1]
            let ax = (a.lon - lon) * kx, ay = (a.lat - lat) * ky
            let bx = (b.lon - lon) * kx, by = (b.lat - lat) * ky
            let dx = bx - ax, dy = by - ay
            let len2 = dx * dx + dy * dy
            let t = len2 == 0 ? 0 : min(max((-ax * dx - ay * dy) / len2, 0), 1)
            let dist = hypot(ax + t * dx, ay + t * dy)
            let along = min(max(cum[i] + t * (cum[i + 1] - cum[i]), lo), hi)
            if best == nil || dist < best!.distanceM { best = Projection(progressM: along, distanceM: dist, segment: i) }
        }
        return best ?? Projection(progressM: fromM, distanceM: .greatestFiniteMagnitude, segment: 0)
    }

    // MARK: - Indlæsning

    static let resourceName = "berlin_sightrun"

    static func load(bundle: Bundle = .main) -> Route {
        guard let url = bundle.url(forResource: resourceName, withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let route = try? parse(data) else {
            fatalError("Kunne ikke læse \(resourceName).json fra app-pakken")
        }
        return route
    }

    private struct JSONRoute: Decodable {
        struct W: Decodable { let name: String; let lat: Double; let lon: Double; let street: String? }
        struct P: Decodable {
            let n: Int; let name: [String: String]; let km: Double; let lat: Double; let lon: Double
            let radiusM: Double; let audio: String?; let text: [String: String]
        }
        let name: String
        let distanceMeters: Double
        let waypoints: [W]
        let pois: [P]
        let finishText: [String: String]
    }

    static func parse(_ data: Data) throws -> Route {
        let j = try JSONDecoder().decode(JSONRoute.self, from: data)
        return Route(
            name: j.name,
            totalM: j.distanceMeters,
            waypoints: j.waypoints.map { Waypoint(name: $0.name, lat: $0.lat, lon: $0.lon, street: $0.street ?? "") },
            pois: j.pois.map {
                Poi(n: $0.n, names: $0.name, km: $0.km, lat: $0.lat, lon: $0.lon, radiusM: $0.radiusM,
                    audioTemplate: ($0.audio?.isEmpty ?? true) ? nil : $0.audio, texts: $0.text)
            },
            finishTexts: j.finishText
        )
    }
}
