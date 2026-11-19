import SwiftUI

// Samme farver som ui/theme/Theme.kt på Android.
extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }

    static let navy900 = Color(hex: 0x0A1A33)
    static let navy800 = Color(hex: 0x0E2140)
    static let navy700 = Color(hex: 0x13294B)
    static let navy600 = Color(hex: 0x1A3560)
    static let berlinBlue = Color(hex: 0x2F6FDB)
    static let lime = Color(hex: 0xB5E61D)
    static let muted = Color.white.opacity(0.72)
    static let warn = Color(hex: 0xFFB547)
    static let startRed = Color(hex: 0xFF5A5F)
    static let startGreen = Color(hex: 0x22C55E)
}

struct StatTile: View {
    let value: String
    let label: String
    var highlight = false

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 24, weight: .heavy))
                .foregroundStyle(highlight ? Color.lime : .white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(Color.muted)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.navy700, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 17, weight: .heavy))
            .foregroundStyle(Color.navy900)
            .frame(maxWidth: .infinity, minHeight: 58)
            .background(Color.lime.opacity(configuration.isPressed ? 0.8 : 1), in: Capsule())
    }
}

struct OutlineButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .bold))
            .foregroundStyle(Color.lime)
            .frame(maxWidth: .infinity, minHeight: 48)
            .overlay(Capsule().stroke(Color.navy600, lineWidth: 1.5))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

struct Chip: View {
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(selected ? Color.navy900 : Color.muted)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Color.lime : Color.navy700, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct SpeedChips: View {
    let speeds = [1, 5, 10, 30]
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(speeds, id: \.self) { sp in
                Button { onSelect(sp) } label: {
                    Text("\(sp)×")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(selected == sp ? .white : Color.muted)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(selected == sp ? Color.berlinBlue : Color.navy700, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct PoiBadge: View {
    let n: Int
    let played: Bool
    var size: CGFloat = 26

    var body: some View {
        Text("\(n)")
            .font(.system(size: size * 0.46, weight: .heavy))
            .foregroundStyle(played ? Color.navy900 : .white)
            .frame(width: size, height: size)
            .background(played ? Color.lime : Color.berlinBlue, in: Circle())
            .overlay(Circle().stroke(Color.navy900, lineWidth: size * 0.08))
    }
}
