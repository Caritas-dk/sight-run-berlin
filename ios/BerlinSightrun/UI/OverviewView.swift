import SwiftUI

/// Forside: sprogvalg, kort, nøgletal, seværdigheder og start. Svarer til OverviewScreen.kt.
struct OverviewView: View {
    @ObservedObject var run: RunController
    @ObservedObject var language: LanguageStore
    @State private var selected: Poi?
    @State private var showDemo = false

    private var s: Strings { language.lang.strings }
    private var route: Route { run.route }

    var body: some View {
        VStack(spacing: 0) {
            header
            languagePicker
            RouteMapView(route: route, interactive: false)
                .frame(height: 210)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .padding(.horizontal, 14)
                .allowsHitTesting(false)

            HStack(spacing: 8) {
                StatTile(value: s.km(route.totalM), label: s.kmLabel, highlight: true)
                StatTile(value: "\(route.pois.count)", label: s.sightsLabel)
                StatTile(value: String(Format.time(Int(route.totalM / 1000 * RunController.plannedPace)).dropLast(3)), label: s.etaLabel)
            }
            .padding(.horizontal, 14)
            .padding(.top, 10)

            if let notice = run.state.notice ?? run.voiceNotice {
                Text(notice)
                    .font(.system(size: 14))
                    .foregroundStyle(Color.navy900)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.warn, in: RoundedRectangle(cornerRadius: 14))
                    .padding(.horizontal, 14)
                    .padding(.top, 10)
            }

            HStack(alignment: .lastTextBaseline) {
                Text(s.listTitle).font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
                Spacer()
                Text("km").font(.system(size: 12)).foregroundStyle(Color.muted)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 8)

            ScrollView {
                LazyVStack(spacing: 6) {
                    ForEach(route.pois) { p in
                        Button { selected = p } label: {
                            HStack(spacing: 12) {
                                PoiBadge(n: p.n, played: false, size: 30)
                                Text(p.name(language.lang.code))
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .multilineTextAlignment(.leading)
                                Spacer()
                                Text(s.km(p.atM)).font(.system(size: 14)).foregroundStyle(Color.muted)
                            }
                            .padding(.horizontal, 12)
                            .frame(minHeight: 48)
                            .background(Color.navy700, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
            }

            VStack(spacing: 8) {
                Button { run.startGpsRun() } label: {
                    Label(s.startRun, systemImage: "play.fill")
                }
                .buttonStyle(PrimaryButtonStyle())
                Button(s.tryDemo) { showDemo = true }
                    .buttonStyle(OutlineButtonStyle())
            }
            .padding(14)
        }
        .background(Color.navy900.ignoresSafeArea())
        .sheet(item: $selected) { p in poiSheet(p) }
        .sheet(isPresented: $showDemo) {
            DemoSheet(s: s) { speed, km, walk in
                showDemo = false
                run.start(demo: true, speed: speed, startKm: km, walkToStart: walk)
            }
            .presentationDetents([.medium, .large])
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Color.navy700).frame(width: 40, height: 40)
                Circle().fill(Color.lime).frame(width: 12, height: 12)
            }
            VStack(alignment: .leading, spacing: 1) {
                (Text("Berlin ").foregroundStyle(.white) + Text("Sightrun").foregroundStyle(Color.lime))
                    .font(.system(size: 20, weight: .heavy))
                Text(s.tagline).font(.system(size: 12)).foregroundStyle(Color.muted)
            }
            Spacer()
        }
        .padding(.horizontal, 18)
        .padding(.top, 8)
    }

    private var languagePicker: some View {
        HStack(spacing: 8) {
            ForEach(Lang.allCases) { l in
                Chip(text: l.code.uppercased(), selected: l == language.lang) { language.lang = l }
                    .accessibilityLabel(l.label)
            }
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    private func poiSheet(_ p: Poi) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("\(p.n). \(p.name(language.lang.code))")
                    .font(.system(size: 22, weight: .heavy)).foregroundStyle(.white)
                Text("km \(s.km(p.atM)) · \(s.geofence) \(Int(p.radiusM)) m")
                    .font(.system(size: 13, weight: .bold)).foregroundStyle(Color.lime)
                Text(p.text(language.lang.code))
                    .font(.system(size: 16)).foregroundStyle(.white).lineSpacing(4)
                Button(s.close) { selected = nil }
                    .buttonStyle(OutlineButtonStyle())
                    .padding(.top, 8)
            }
            .padding(20)
        }
        .background(Color.navy700.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }
}

/// Demo-løb: hastighed, startsted og valgfri gåtur hen til start. Svarer til DemoDialog i OverviewScreen.kt.
struct DemoSheet: View {
    let s: Strings
    let onStart: (Int, Double, Bool) -> Void
    @State private var speed = 10
    @State private var km = 0.0
    @State private var walk = true

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(s.demoTitle).font(.system(size: 22, weight: .heavy)).foregroundStyle(.white)
            Text(s.demoBody).font(.system(size: 14)).foregroundStyle(Color.muted)
            Text(s.speed).font(.system(size: 15, weight: .bold)).foregroundStyle(.white)
            SpeedChips(selected: speed) { speed = $0 }
            Text(s.startAtKm(s.km(km * 1000))).font(.system(size: 15, weight: .bold)).foregroundStyle(.white)
            Slider(value: $km, in: 0...20, step: 0.1).tint(Color.lime)
            if km == 0 {
                Toggle(s.demoWalkOption, isOn: $walk)
                    .tint(Color.lime)
                    .foregroundStyle(.white)
                    .font(.system(size: 14))
            }
            Spacer(minLength: 0)
            Button(s.startDemo) { onStart(speed, km, walk && km == 0) }
                .buttonStyle(PrimaryButtonStyle())
        }
        .padding(20)
        .background(Color.navy700.ignoresSafeArea())
    }
}
