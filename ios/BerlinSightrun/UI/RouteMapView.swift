import MapKit
import SwiftUI

/// Kort med ruten i lime, nummererede seværdigheder, startzonen og løberen. Svarer til RouteMap.kt.
struct RouteMapView: View {
    let route: Route
    var runner: CLLocationCoordinate2D?
    var follow = false
    var playedPois: Set<Int> = []
    var showFences = false
    var startReached = false
    var interactive = true

    @State private var position: MapCameraPosition = .automatic

    private var coords: [CLLocationCoordinate2D] {
        route.waypoints.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
    }

    private var overviewRect: MKMapRect {
        let rects = coords.map { MKMapRect(origin: MKMapPoint($0), size: MKMapSize(width: 0, height: 0)) }
        let r = rects.dropFirst().reduce(rects[0]) { $0.union($1) }
        return r.insetBy(dx: -r.size.width * 0.08, dy: -r.size.height * 0.25)
    }

    var body: some View {
        Map(position: $position, interactionModes: interactive ? .all : []) {
            MapPolyline(coordinates: coords)
                .stroke(Color.lime, style: StrokeStyle(lineWidth: follow ? 7 : 4, lineCap: .round, lineJoin: .round))

            if showFences {
                ForEach(route.pois) { p in
                    MapCircle(center: CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon), radius: p.radiusM)
                        .foregroundStyle(Color.berlinBlue.opacity(0.12))
                        .stroke(Color.berlinBlue.opacity(0.6), lineWidth: 1)
                }
                MapCircle(center: CLLocationCoordinate2D(latitude: route.start.lat, longitude: route.start.lon),
                          radius: RunEngine.startRadiusM)
                    .foregroundStyle((startReached ? Color.startGreen : Color.startRed).opacity(0.25))
                    .stroke(startReached ? Color.startGreen : Color.startRed, lineWidth: 2)
            }

            ForEach(route.pois) { p in
                Annotation("", coordinate: CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon), anchor: .center) {
                    PoiBadge(n: p.n, played: playedPois.contains(p.n), size: follow ? 26 : 18)
                }
            }

            Annotation("", coordinate: coords.last!, anchor: .center) {
                Image(systemName: "flag.checkered")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Color.navy900)
                    .frame(width: 22, height: 22)
                    .background(.white, in: RoundedRectangle(cornerRadius: 5))
            }

            if let r = runner {
                Annotation("", coordinate: r, anchor: .center) {
                    ZStack {
                        Circle().fill(.white.opacity(0.25)).frame(width: 34, height: 34)
                        Circle().fill(.white).frame(width: 16, height: 16)
                            .overlay(Circle().stroke(Color.berlinBlue, lineWidth: 4))
                    }
                }
            }
        }
        .mapStyle(.standard(elevation: .flat, emphasis: .muted, pointsOfInterest: .excludingAll))
        .environment(\.colorScheme, .dark)
        .onAppear {
            position = follow && runner != nil ? camera(for: runner!) : .rect(overviewRect)
        }
        .onChange(of: runner?.latitude) { _, _ in
            if follow, let r = runner { withAnimation(.linear(duration: 0.25)) { position = camera(for: r) } }
        }
        .onChange(of: runner?.longitude) { _, _ in
            if follow, let r = runner { position = camera(for: r) }
        }
    }

    private func camera(for c: CLLocationCoordinate2D) -> MapCameraPosition {
        .camera(MapCamera(centerCoordinate: c, distance: 1400, heading: 0, pitch: 0))
    }
}
