import SwiftUI
import WidgetKit

private let appGroup = "group.com.panda.ccpocket"

struct UsageEntry: TimelineEntry {
    let date: Date
    let tokens: Int64
    let requests: Int64
    let weeklyRemaining: Double?
    let plan: String
    let updatedAt: Date?

    static func load() -> UsageEntry {
        let defaults = UserDefaults(suiteName: appGroup)
        return UsageEntry(
            date: Date(),
            tokens: Int64(defaults?.string(forKey: "tokensToday") ?? "0") ?? 0,
            requests: Int64(defaults?.string(forKey: "requestsToday") ?? "0") ?? 0,
            weeklyRemaining: defaults?.object(forKey: "weeklyRemaining") == nil
                ? nil : defaults?.double(forKey: "weeklyRemaining"),
            plan: defaults?.string(forKey: "planType") ?? "",
            updatedAt: (defaults?.double(forKey: "updatedAt") ?? 0) > 0
                ? Date(timeIntervalSince1970: defaults!.double(forKey: "updatedAt")) : nil
        )
    }
}

struct UsageProvider: TimelineProvider {
    func placeholder(in context: Context) -> UsageEntry {
        UsageEntry(date: Date(), tokens: 12_800_000, requests: 86, weeklyRemaining: 66, plan: "plus", updatedAt: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (UsageEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : .load())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<UsageEntry>) -> Void) {
        let entry = UsageEntry.load()
        completion(Timeline(entries: [entry], policy: .after(Date().addingTimeInterval(15 * 60))))
    }
}

private let ink = Color(red: 0.93, green: 0.94, blue: 0.95)
private let muted = Color(red: 0.60, green: 0.63, blue: 0.66)
private let accent = Color(red: 0.85, green: 0.47, blue: 0.34)
private let teal = Color(red: 0.25, green: 0.71, blue: 0.67)
private let canvas = Color(red: 0.055, green: 0.059, blue: 0.067)

struct UsageWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: UsageEntry

    var body: some View {
        Group {
            if #available(iOSApplicationExtension 17.0, *) {
                content.containerBackground(canvas, for: .widget)
            } else {
                content.background(canvas)
            }
        }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: family == .systemSmall ? 8 : 10) {
            HStack(spacing: 6) {
                Circle().fill(accent).frame(width: 7, height: 7)
                Text("CC Pocket").font(.caption.weight(.semibold)).foregroundColor(ink)
                Spacer(minLength: 2)
                if !entry.plan.isEmpty {
                    Text(entry.plan.uppercased()).font(.system(size: 8, weight: .bold, design: .rounded))
                        .foregroundColor(teal).padding(.horizontal, 5).padding(.vertical, 3)
                        .background(teal.opacity(0.13)).clipShape(Capsule())
                }
            }

            VStack(alignment: .leading, spacing: 1) {
                Text("TODAY TOKENS").font(.system(size: 9, weight: .medium, design: .monospaced)).foregroundColor(muted)
                Text(shortTokens(entry.tokens)).font(.system(size: family == .systemSmall ? 27 : 31, weight: .bold, design: .rounded))
                    .foregroundColor(ink).minimumScaleFactor(0.7).lineLimit(1)
            }

            if let remaining = entry.weeklyRemaining {
                VStack(alignment: .leading, spacing: 5) {
                    HStack {
                        Text("CODEX WEEKLY").font(.system(size: 9, weight: .medium, design: .monospaced)).foregroundColor(muted)
                        Spacer()
                        Text("\(Int(remaining.rounded()))% left").font(.caption2.weight(.semibold)).foregroundColor(ink)
                    }
                    GeometryReader { proxy in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.white.opacity(0.08))
                            Capsule().fill(teal).frame(width: proxy.size.width * max(0, min(remaining / 100, 1)))
                        }
                    }.frame(height: 5)
                }
            } else {
                Text("Open CC Pocket to refresh limits")
                    .font(.system(size: 9)).foregroundColor(muted).lineLimit(1)
            }

            HStack {
                Label("\(entry.requests) requests", systemImage: "arrow.up.circle")
                Spacer()
                Text(entry.updatedAt == nil ? "No data" : "Updated")
            }.font(.system(size: 9)).foregroundColor(muted)
        }
        .padding(14)
        // The snapshot is aggregate usage, not private message content. Keep the placeholder readable
        // while WidgetKit is refreshing instead of showing an unexplained blurred/black rectangle.
        .unredacted()
    }

    private func shortTokens(_ value: Int64) -> String {
        if value >= 1_000_000 { return String(format: "%.1fM", Double(value) / 1_000_000) }
        if value >= 1_000 { return String(format: "%.1fK", Double(value) / 1_000) }
        return "\(value)"
    }

}

@main
struct CCPocketUsageWidget: Widget {
    let kind = "CCPocketUsageWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: UsageProvider()) { entry in
            UsageWidgetView(entry: entry)
        }
        .configurationDisplayName("Token Usage")
        .description("Today’s token usage and Codex weekly allowance.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
