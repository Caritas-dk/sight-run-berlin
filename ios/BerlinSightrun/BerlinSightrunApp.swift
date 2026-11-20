import SwiftUI

@main
struct BerlinSightrunApp: App {
    @StateObject private var language = LanguageStore.shared
    @StateObject private var run = RunController()

    var body: some Scene {
        WindowGroup {
            ContentView(run: run, language: language)
                .preferredColorScheme(.dark)
                .tint(Color.lime)
                .onAppear(perform: startDemoFromLaunchArguments)
        }
    }

    /// Til skærmbilleder og test: `-demoStartKm 16` starter et demo-løb ved km 16 med det samme,
    /// `-lang de` vælger sprog.
    private func startDemoFromLaunchArguments() {
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-lang"), i + 1 < args.count, let l = Lang.fromCode(args[i + 1]) {
            language.lang = l
        }
        if let i = args.firstIndex(of: "-demoStartKm"), i + 1 < args.count, let km = Double(args[i + 1]) {
            run.start(demo: true, speed: 10, startKm: km, walkToStart: false)
        }
    }
}

struct ContentView: View {
    @ObservedObject var run: RunController
    @ObservedObject var language: LanguageStore

    var body: some View {
        switch run.state.phase {
        case .idle: OverviewView(run: run, language: language)
        case .running: RunView(run: run, language: language)
        case .finished: SummaryView(run: run, language: language)
        }
    }
}
