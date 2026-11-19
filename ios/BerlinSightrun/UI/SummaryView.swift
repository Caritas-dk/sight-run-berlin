import SwiftUI

/// Resultat efter målet: tid, fart, fortællinger, kilometertider. Svarer til SummaryScreen.kt.
struct SummaryView: View {
    @ObservedObject var run: RunController
    @ObservedObject var language: LanguageStore

    private var s: RunUiState { run.state }
    private var t: Strings { language.lang.strings }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(t.done).font(.system(size: 12, weight: .bold)).foregroundStyle(Color.lime)
            VStack(alignment: .leading, spacing: 2) {
                Text(Format.time(s.elapsedSec))
                    .font(.system(size: 52, weight: .heavy)).foregroundStyle(.white).monospacedDigit()
                Text(t.summarySub).font(.system(size: 14)).foregroundStyle(Color.muted)
            }
            RouteMapView(route: run.route, playedPois: s.playedPois, interactive: false)
                .frame(height: 180)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .allowsHitTesting(false)
            HStack(spacing: 8) {
                StatTile(value: Format.pace(s.paceSecPerKm), label: t.avgPace, highlight: true)
                StatTile(value: "\(s.heard)/\(run.route.pois.count)", label: t.heard)
                StatTile(value: t.km(s.progressM), label: t.kmRun)
            }
            if !s.splits.isEmpty {
                Text(t.splitsTitle).font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
                splitBars
            } else {
                Spacer()
            }
            Button(t.toOverview) { run.backToOverview() }
                .buttonStyle(PrimaryButtonStyle())
        }
        .padding(18)
        .background(Color.navy900.ignoresSafeArea())
    }

    private var splitBars: some View {
        let maxSec = max(s.splits.map(\.splitSec).max() ?? 1, 1)
        let fastest = s.fastestSplit
        return ScrollView {
            VStack(spacing: 4) {
                ForEach(s.splits, id: \.km) { sp in
                    HStack(spacing: 8) {
                        Text("\(sp.km)").font(.system(size: 12)).foregroundStyle(Color.muted).frame(width: 22, alignment: .leading)
                        GeometryReader { g in
                            RoundedRectangle(cornerRadius: 4)
                                .fill(sp == fastest ? Color.lime : Color.berlinBlue)
                                .frame(width: g.size.width * CGFloat(sp.splitSec) / CGFloat(maxSec), height: 14)
                        }
                        .frame(height: 14)
                        Text(t.clock(sp.splitSec) + (sp == fastest ? " · \(t.fastest)" : ""))
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(sp == fastest ? Color.lime : .white)
                            .frame(width: 118, alignment: .leading)
                    }
                }
            }
        }
    }
}
