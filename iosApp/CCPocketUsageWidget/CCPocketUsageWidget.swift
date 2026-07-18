import SwiftUI
import WidgetKit

private let appGroup = "group.com.panda.ccpocket"

struct UsageEntry: TimelineEntry {
    let date: Date
    let tokens: Int64
    let requests: Int64
    let weeklyRemaining: Double?
    let limitWindowMinutes: Int
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
            limitWindowMinutes: defaults?.integer(forKey: "limitWindowMinutes") ?? 0,
            plan: defaults?.string(forKey: "planType") ?? "",
            updatedAt: (defaults?.double(forKey: "updatedAt") ?? 0) > 0
                ? Date(timeIntervalSince1970: defaults!.double(forKey: "updatedAt")) : nil
        )
    }
}

struct UsageProvider: TimelineProvider {
    func placeholder(in context: Context) -> UsageEntry {
        UsageEntry(date: Date(), tokens: 12_800_000, requests: 86, weeklyRemaining: 66,
                   limitWindowMinutes: 10_080, plan: "plus", updatedAt: Date())
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
        VStack(alignment: .leading, spacing: family == .systemSmall ? 7 : 10) {
            HStack(spacing: 6) {
                Circle().fill(accent).frame(width: 7, height: 7)
                Text("CC Pocket")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(ink).lineLimit(1).minimumScaleFactor(0.85)
                Spacer(minLength: 2)
                if family == .systemMedium && !entry.plan.isEmpty {
                    Text(entry.plan.uppercased()).font(.system(size: 8, weight: .bold, design: .rounded))
                        .foregroundColor(teal).padding(.horizontal, 5).padding(.vertical, 3)
                        .background(teal.opacity(0.13)).clipShape(Capsule())
                }
            }

            if family == .systemMedium {
                HStack(alignment: .top, spacing: 28) {
                    metric(title: text("今日 TOKEN", "TODAY TOKENS"), value: shortTokens(entry.tokens))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    metric(title: text("今日请求", "TODAY REQUESTS"), value: "\(entry.requests)")
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                metric(title: text("今日 TOKEN", "TODAY TOKENS"), value: shortTokens(entry.tokens))
            }

            if let remaining = entry.weeklyRemaining {
                VStack(alignment: .leading, spacing: 5) {
                    HStack {
                        Text(limitTitle)
                            .font(.system(size: 9, weight: .medium, design: .monospaced)).foregroundColor(muted)
                        Spacer()
                        Text(text("已用 \(usedPercent(remaining))%", "\(usedPercent(remaining))% used"))
                            .font(.caption2.weight(.semibold)).foregroundColor(ink)
                    }
                    GeometryReader { proxy in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.white.opacity(0.08))
                            Capsule().fill(teal).frame(
                                width: proxy.size.width * max(0, min((100 - remaining) / 100, 1))
                            )
                        }
                    }.frame(height: 5)
                }
            } else {
                Text(text("打开 App 刷新周额度", "Open app to refresh weekly limit"))
                    .font(.system(size: 9)).foregroundColor(muted).lineLimit(1).minimumScaleFactor(0.8)
            }

            HStack {
                if family == .systemSmall {
                    Label(text("\(entry.requests) 次请求", "\(entry.requests) requests"), systemImage: "arrow.up.circle")
                        .lineLimit(1).minimumScaleFactor(0.75)
                }
                Spacer()
                Text(entry.updatedAt == nil ? text("暂无数据", "No data") : text("已更新", "Updated"))
            }.font(.system(size: 9)).foregroundColor(muted)
        }
        .padding(14)
        // The snapshot is aggregate usage, not private message content. Keep the placeholder readable
        // while WidgetKit is refreshing instead of showing an unexplained blurred/black rectangle.
        .unredacted()
    }

    private func metric(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(title).font(.system(size: 9, weight: .medium, design: .monospaced)).foregroundColor(muted)
            Text(value).font(.system(size: family == .systemSmall ? 27 : 29, weight: .bold, design: .rounded))
                .foregroundColor(ink).minimumScaleFactor(0.7).lineLimit(1)
        }
    }

    private func text(_ zh: String, _ en: String) -> String {
        Locale.preferredLanguages.first?.hasPrefix("zh") == true ? zh : en
    }

    private var limitTitle: String {
        entry.limitWindowMinutes >= 7 * 24 * 60
            ? text("CODEX 周用量", "CODEX WEEKLY")
            : text("CODEX 5 小时用量", "CODEX 5-HOUR")
    }

    private func usedPercent(_ remaining: Double) -> Int {
        Int(max(0, min(100 - remaining, 100)).rounded())
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
