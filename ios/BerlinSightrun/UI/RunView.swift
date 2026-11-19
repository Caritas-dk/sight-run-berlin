import CoreLocation
import SwiftUI

/// Løbeskærm: kort der følger løberen, sving, startindikator, kilometerkort, fortælling og styring.
/// Svarer til RunScreen.kt.
struct RunView: View {
    @ObservedObject var run: RunController
    @ObservedObject var language: LanguageStore
    @State private var confirmStop = false

    private var s: RunUiState { run.state }
    private var t: Strings { language.lang.strings }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .top) {
                RouteMapView(
                    route: run.route,
                    runner: s.lat.flatMap { lat in s.lon.map { CLLocationCoordinate2D(latitude: lat, longitude: $0) } },
                    follow: true,
                    playedPois: s.playedPois,
                    showFences: true,
                    startReached: s.atStart
                )
                .frame(height: geo.size.height * 0.62)
                .ignoresSafeArea(edges: .top)

                VStack(spacing: 10) {
                    let beforeStart = !s.atStart
                    let justStarted = s.atStart && s.progressM < 60
                    if beforeStart || justStarted { StartIndicator(t: t, s: s) }
                    if !beforeStart { TurnCard(s: s, t: t) }
                    if let sp = s.lastSplit, s.progressM < Double(sp.km) * 1000 + 300 { KmSplitCard(t: t, sp: sp) }
                    if let sp = s.speaking, sp.kind != .km { SpeakingCard(t: t, sp: sp, poiCount: run.route.pois.count) }
                    Spacer()
                }
                .padding(14)

                VStack {
                    Spacer()
                    bottomPanel
                }
            }
        }
        .background(Color.navy900.ignoresSafeArea())
        .alert(t.stopTitle, isPresented: $confirmStop) {
            Button(t.keepRunning, role: .cancel) {}
            Button(t.stopConfirm, role: .destructive) { run.stop() }
        } message: {
            Text(t.stopBody)
        }
    }

    private var bottomPanel: some View {
        VStack(spacing: 12) {
            HStack {
                BigStat(value: t.km(s.progressM), label: t.kmOf, color: .lime)
                BigStat(value: Format.time(s.elapsedSec), label: t.time, color: .white)
                BigStat(value: Format.pace(s.paceSecPerKm), label: t.avgPaceShort, color: .white)
            }
            ProgressView(value: min(s.progressM / s.totalM, 1))
                .tint(Color.berlinBlue)
                .background(Color.navy700)
                .scaleEffect(x: 1, y: 1.6, anchor: .center)
            if let last = s.lastSplit {
                Text("\(t.lastKm) \(t.clock(last.splitSec))")
                    .font(.system(size: 13, weight: .bold)).foregroundStyle(Color.lime)
            }
            Text(s.nextPoiName.map { t.nextPoi($0, t.km(s.nextPoiDistanceM ?? 0)) } ?? t.nextFinish)
                .font(.system(size: 13)).foregroundStyle(Color.muted).multilineTextAlignment(.center)
            if s.demo {
                HStack(spacing: 8) {
                    Text("Demo").font(.system(size: 12)).foregroundStyle(Color.muted)
                    SpeedChips(selected: s.demoSpeed) { run.setDemoSpeed($0) }
                }
            }
            HStack {
                RoundButton(systemImage: "arrow.counterclockwise", label: t.repeat) { run.repeatLast() }
                Spacer()
                Button { run.togglePause() } label: {
                    Image(systemName: s.paused ? "play.fill" : "pause.fill")
                        .font(.system(size: 30, weight: .bold))
                        .foregroundStyle(Color.navy900)
                        .frame(width: 72, height: 72)
                        .background(Color.lime, in: Circle())
                }
                .accessibilityLabel(s.paused ? t.resume : t.pause)
                Spacer()
                RoundButton(systemImage: "forward.end.fill", label: t.skip) { run.skipNarration() }
            }
            .padding(.horizontal, 24)
            Button(t.stopRun) { confirmStop = true }
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color.muted)
        }
        .padding(.horizontal, 14)
        .padding(.top, 16)
        .padding(.bottom, 8)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 26, topTrailingRadius: 26)
                .fill(Color.navy900)
                .ignoresSafeArea(edges: .bottom)
        )
    }
}

private struct BigStat: View {
    let value: String
    let label: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.system(size: 30, weight: .heavy)).foregroundStyle(color).monospacedDigit()
                .lineLimit(1).minimumScaleFactor(0.6)
            Text(label).font(.system(size: 12)).foregroundStyle(Color.muted)
        }
        .frame(maxWidth: .infinity)
    }
}

private struct RoundButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 52, height: 52)
                .background(Color.navy700, in: Circle())
        }
        .accessibilityLabel(label)
    }
}

private struct TurnCard: View {
    let s: RunUiState
    let t: Strings

    var body: some View {
        let (bg, icon, big, small): (Color, String, String, String) = {
            if let d = s.distanceToStartM { return (.lime, "flag.fill", t.shortDistance(d), t.goToStart) }
            if s.offRoute { return (.warn, "arrow.uturn.left", t.offTitle, t.offSub) }
            guard let td = s.turnDistanceM else { return (.lime, "arrow.up", t.shortDistance(s.totalM - s.progressM), t.straightToFinish) }
            return (.lime, turnIcon, t.shortDistance(td), s.turnText)
        }()
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(bg)
                .frame(width: 52, height: 52)
                .background(Color.navy900, in: RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 2) {
                Text(big).font(.system(size: 24, weight: .heavy)).foregroundStyle(Color.navy900)
                Text(small).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.navy900).lineLimit(2)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(bg, in: RoundedRectangle(cornerRadius: 20))
    }

    private var turnIcon: String {
        switch (s.turnDir, s.turnSharp) {
        case (1, true): return "arrow.uturn.right"
        case (1, false): return "arrow.turn.up.right"
        case (-1, true): return "arrow.uturn.left"
        case (-1, false): return "arrow.turn.up.left"
        default: return "arrow.up"
        }
    }
}

private struct SpeakingCard: View {
    let t: Strings
    let sp: Speaking
    let poiCount: Int

    var body: some View {
        let eyebrow: String = {
            switch sp.kind {
            case .narration: return sp.poiNumber.map { t.eyebrowNarrating($0, poiCount) } ?? t.eyebrowFinish
            case .turn: return t.eyebrowGuidance
            case .offRoute: return t.eyebrowOff
            case .km: return t.eyebrowKm
            case .system: return "BERLIN SIGHTRUN"
            }
        }()
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "headphones").foregroundStyle(Color.lime)
                Text(eyebrow).font(.system(size: 12, weight: .bold)).foregroundStyle(Color.lime)
                Spacer()
                Text(t.musicDucked)
                    .font(.system(size: 11.5, weight: .semibold)).foregroundStyle(Color.navy900)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Color.lime, in: RoundedRectangle(cornerRadius: 10))
            }
            Text(sp.title).font(.system(size: 19, weight: .heavy)).foregroundStyle(.white)
            Text(sp.line).font(.system(size: 13)).foregroundStyle(Color.muted).lineLimit(2)
            ProgressView(value: Double(sp.part), total: Double(max(sp.parts, 1))).tint(Color.lime)
        }
        .padding(14)
        .background(Color.navy700, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.berlinBlue, lineWidth: 1))
    }
}

/// Rød cirkel med afstanden, indtil løberen er inden for 50 m af startpunktet ved TU Berlin.
/// Bliver grøn med et flueben, når løbet starter, og forsvinder efter de første 60 m.
private struct StartIndicator: View {
    let t: Strings
    let s: RunUiState

    var body: some View {
        let ok = s.atStart
        let color = ok ? Color.startGreen : Color.startRed
        let title = ok ? t.atStartTitle : (s.distanceToStartM.map { t.toStart(t.shortDistance($0)) } ?? t.searchingGps)
        HStack(spacing: 14) {
            ZStack {
                Circle().stroke(color, lineWidth: 7).frame(width: 52, height: 52)
                Circle().fill(color).frame(width: ok ? 34 : 18, height: ok ? 34 : 18)
                if ok { Image(systemName: "checkmark").font(.system(size: 18, weight: .heavy)).foregroundStyle(Color.navy900) }
            }
            .frame(width: 58, height: 58)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 20, weight: .heavy)).foregroundStyle(.white)
                Text(ok ? t.atStartSub : t.goToStart).font(.system(size: 13)).foregroundStyle(Color.muted)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Color.navy700, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(color, lineWidth: 1.5))
        .accessibilityElement(children: .combine)
    }
}

/// Vises i ca. 300 m efter hver kilometer: samlet tid, seneste kilometer og gennemsnit.
private struct KmSplitCard: View {
    let t: Strings
    let sp: KmSplit

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(t.kmReached(sp.km)).font(.system(size: 12, weight: .bold)).foregroundStyle(Color.lime)
            HStack {
                value(t.clock(sp.totalSec), t.time)
                value(t.clock(sp.splitSec), t.lastKm, highlight: true)
                value(t.clock(sp.avgSec), t.avgPaceShort)
            }
        }
        .padding(14)
        .background(Color.navy700, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.lime, lineWidth: 1.5))
    }

    private func value(_ v: String, _ label: String, highlight: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(v).font(.system(size: 22, weight: .heavy)).foregroundStyle(highlight ? Color.lime : .white).monospacedDigit()
            Text(label).font(.system(size: 11.5)).foregroundStyle(Color.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
