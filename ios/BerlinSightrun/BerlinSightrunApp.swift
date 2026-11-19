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
