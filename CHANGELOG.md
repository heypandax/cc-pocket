# Changelog

## Unreleased

### Added

- Codex native turn steering, context compaction, thread branching, archive/restore, Goals, and provider-native code review.
- In-app Codex Skills, Marketplace Plugins, MCP server, and Apps management.
- Codex account usage now exposes only provider-confirmed limit windows and supports account-provided reset credits/actions when available.

### Changed

- Model, reasoning effort, and execution mode live in the composer status bar and are no longer duplicated in the top-right quick-actions menu.
- Added a GitHub-artifact-first Linux daemon upgrade guide; production servers no longer need to build the daemon from source.

## 1.3.5 — 2026-07-13

Compared with 1.3.4, this release focuses on three-agent consistency, safer remote control, continuity, and mobile responsiveness.

### Added

- Full Cursor Agent sessions, native history discovery, live account model discovery, model variants, usage accounting, and resume support alongside Claude and Codex.
- Permission risk classification, affected-scope summaries, explicit dangerous-action confirmation, and an authorization audit trail.
- A chronological activity timeline for approvals, denials, tool execution, background work, and task completion.
- Codex account-limit snapshots in Token Usage, including weekly remaining allowance, reset time, plan, credits, and live refresh.
- Cross-device takeover guidance, return-to-desktop summaries, and conversation reading-position continuity.
- Chinese agency-agent completion in the `@` composer and clearer starter suggestions for empty sessions.

### Improved

- Claude, Codex, and Cursor now use the same new-session autonomy ladder, card height, spacing, selection state, and full-auto warning.
- The chat model and reasoning controls now match Happy's compact status bar: borderless 24dp labels open a 236dp radio-option panel above the composer.
- Pairing completes automatically after the sixth digit, dismisses the numeric keyboard, and provides clearer camera/error recovery.
- Chats restore the intended reading position, stay pinned correctly around keyboard changes, and keep approvals reachable.
- Long streaming replies render at a controlled cadence to reduce recomposition load and UI stutter.
- Project, offline, empty, attention, navigation, and status states have larger targets and clearer recovery actions.
- Project avatars no longer contain generated letters; Codex uses OpenAI's official Blossom asset; iOS uses the clean native `photo` attachment symbol.
- Desktop switching, recent-session persistence, notifications, prompt delivery, relay reconnects, and replay of long transcripts are more resilient.

### Fixed

- Recent chat content being hidden by the keyboard or reopening at the top.
- Prompt loss and duplicated/replayed output around reconnects, process restarts, and turn acknowledgements.
- Cursor response duplication, model-ID handling, session listing, and picker loading behavior.
- Codex weekly-limit presentation and portable reset-time calculation.

### Compatibility

- App version: **1.3.5**.
- Features listed in this 1.3.5 section require daemon **1.3.5 or newer**. Newer Codex integrations listed under Unreleased require the matching current App and daemon; do not downgrade a newer daemon to match the App marketing version.

## 1.3.4 — 2026-07-10

- Stabilized Chinese IME input and composer clearing.
- Kept long replies above the composer and improved background-task controls.
- Refined usage charts, selectable diffs, soft wrapping, and effective-model feedback.
