# Snapshot Convention

This document is the working baseline for the current `baserefactor` branch.
It captures the existing behavior and the rules we should preserve unless a
change is explicitly requested.

## Purpose

- Keep the current implementation easy to refer back to during refactors.
- Record the key flows, rules, and file locations that define the app today.
- Distinguish baseline behavior from new behavior so we can refactor safely.

## Working Terms

- `baseline` = the current code and behavior as documented here.
- `refactor` = the new version we build from this branch forward.
- `notes` = short running observations about edge cases and decisions.
- If a request is unclear, ask clarifying questions with at least 2 options,
  include reasoning for each, and recommend one option.
- Continue asking focused follow-up questions until the feature request is
  clear enough to implement safely.

## Core Baseline

### Product

- Android launcher called zHome Launcher.
- Full-screen, black, minimalist launcher built around scribble-to-search.
- Primary input method is freehand handwriting recognition, not typing.

### Main Flow

- `MainActivity` starts the ML Kit Digital Ink manager.
- `LauncherViewModel` owns state and business logic.
- `AppRepository` loads installed apps, usage stats, and pinned slots.
- `InkRecognitionManager` manages handwriting model download and recognition.
- `LauncherRoot` wires the UI together and routes user actions to the ViewModel.

### State Shape

`LauncherState` currently tracks:

- `recentApps`
- `pinnedApps`
- `allApps`
- `suggestions`
- `recognizedText`
- `showAllApps`
- `isModelReady`
- `modelDownloadStatus`
- `isEditMode`
- `draggingApp`

### App Loading

- Installed launcher apps are queried from the package manager.
- Apps are sorted by last-used timestamp from `UsageStatsManager`.
- Pinned apps are stored in `SharedPreferences` under `pinned_apps_v2`.
- There are 4 fixed pinned slots.
- Recent apps are the first 20 non-pinned apps.

### Search / Recognition

- Handwriting input is recognized with ML Kit Digital Ink using `en-US`.
- Recognition results update `recognizedText` and search suggestions.
- Filtering is two-pass:
  - prefix matches first
  - contains matches second
- Suggestions are capped at 15 results.
- Auto-launch happens only when there is exactly one match and its label
  exactly equals the query, ignoring case.

### Gestures

- Swipe up opens All Apps.
- Right-to-left swipe backspaces the current query.
- Swipe down closes All Apps.
- Long-press on an app begins the pinning flow.
- Long-press on a pinned slot enables edit mode.
- Dragging a pinned slot swaps pinned positions on drop.

### UI Layout

- A fixed 56 dp top bar zone is reserved for recognized text or model status.
- The main list shows either suggestions or recent apps.
- The pinned section is always visible at the bottom.
- All Apps is shown as an overlay with animated vertical enter/exit.

## Behavioral Details to Preserve

### `MainActivity`

- Registers a package-change receiver in `onStart` and unregisters it in `onStop`.
- On first launch without Usage Access, shows an in-app intro screen before
  sending the user to system settings.
- Also refreshes app state in `onResume` so missed install/uninstall changes are
  caught when the launcher becomes active again.
- Refreshes the app list when apps are added, removed, or replaced.
- Checks for Usage Access permission and sends the user to system settings when
  it is missing.
- Closes the ink manager in `onDestroy`.

### `LauncherViewModel`

- Keeps `committedText` separate from the currently visible query state.
- `committedText` is intentionally private to `LauncherViewModel` and is not
  part of `LauncherState`, so future refactors should not promote it unless the
  state model is deliberately being redesigned.
- App refresh is built as one modular pipeline in the ViewModel: build the app
  snapshot once, then apply it to `allApps`, `recentApps`, `pinnedApps`, and
  `suggestions` from the same source data.
- `backspace()` always derives from the visible `recognizedText`.
- `commitActiveScribble()` snapshots the visible query into `committedText`.
- `clearScribble()` clears the query and suggestions.
- `pinApp`, `unpinApp`, and `swapPinnedSlots` always reload state afterward.
- Refreshing apps also removes stale pinned packages, so uninstalled apps do not
  stay visible in pinned slots or stale search results.
- `launchApp()` clears the scribble first, then opens the app launch intent.

### `DrawingOverlay`

- Uses an 8 dp touch slop before it starts intercepting a gesture.
- Debounces recognition by 150 ms after the last stroke.
- Commits the scribble after 1 second of inactivity.
- Cancels recognition and idle timers when the query is cleared.
- Suspends overlay interception while pinned-slot drag is active so drag
  gestures are not stolen by the scribble layer.

### `AppGrid`

- `AppList` uses `reverseLayout = true` and bottom anchoring for thumb reach.
- App rows support tap and long-press.
- Pinned slots support edit mode, remove badges, and drag-to-reorder.

### `AllAppsScreen`

- Swipe down dismisses the overlay when the drag amount is greater than 30 px.
- Each row includes an inline `Pin` or `Unpin` action.
- Tapping an app launches it and dismisses the overlay.
- Pinning from All Apps routes back to the home screen and uses the same
  pin-selection flow as the rest of the launcher.
- Unpinning from All Apps resolves the slot by package name in `LauncherRoot`
  and then delegates to the ViewModel.

### `InkRecognitionManager`

- Checks whether the model is already downloaded before requesting download.
- Emits a download-status stream while the model is preparing or downloading.
- Uses a sequence number to discard stale recognition results that arrive late.
- Formats the ETA text from elapsed time using a simple minute/second display.

## File Reference Map

- `/app/src/main/java/io/github/zHomeLauncher/MainActivity.kt`
  - app entry point, permission checks, lifecycle registration
- `/app/src/main/java/io/github/zHomeLauncher/LauncherViewModel.kt`
  - launcher state, filtering, pinning, launching, scribble handling
- `/app/src/main/java/io/github/zHomeLauncher/data/AppRepository.kt`
  - installed-app loading, usage stats, pinned slot persistence
- `/app/src/main/java/io/github/zHomeLauncher/recognition/InkRecognitionManager.kt`
  - ML Kit model download and handwriting recognition
- `/app/src/main/java/io/github/zHomeLauncher/ui/LauncherRoot.kt`
  - top-level UI composition and interaction routing
- `/app/src/main/java/io/github/zHomeLauncher/ui/AppGrid.kt`
  - app list and pinned-slot rendering
- `/app/src/main/java/io/github/zHomeLauncher/ui/DrawingOverlay.kt`
  - gesture interception, ink capture, and recognition debounce
- `/app/src/main/java/io/github/zHomeLauncher/ui/AllAppScreen.kt`
  - All Apps overlay and inline pin/unpin actions
- `/SPEC.md`
  - detailed feature specification and interaction rules
- `/README.md`
  - product overview and user-facing summary

## Refactor Rules

- Prefer the simplest correct solution first.
- Keep code clean, readable, and easy to reason about.
- Avoid over-engineering, unnecessary abstraction, and premature optimization.
- Keep solutions reusable when reuse is actually beneficial.
- Keep performance and security in mind throughout the refactor.
- Preserve the baseline behavior unless a change is explicitly requested.
- When changing logic, note the old behavior and the new behavior together.
- Prefer small, traceable refactors over broad rewrites.
- If a feature is removed or reworked, update this snapshot so it still reflects
  the current intended baseline.

## Notes Log

Use this section for short running notes during the refactor.

- Worked areas:
  - `LauncherRoot` top bar made pluggable
  - main layout updated with reserved top zone and swipe-up hint
  - `committedText` documented as private ViewModel state

- `YYYY-MM-DD`:
  - note
