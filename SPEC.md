# Dash Launcher — Feature Specification

A minimal Android home-screen launcher whose primary interaction model is **freehand scribble-to-search** rather than typing. The entire UI is full-screen black, content-free except for the elements described below.

---

## 1. App Loading & Sorting

**Source:** `AppRepository.getAllApps()` + `LauncherViewModel.loadApps()`

- Queries the package manager for every app that has a `CATEGORY_LAUNCHER` intent (self excluded).
- Reads 30-day usage stats via `UsageStatsManager` and attaches a `lastUsed` timestamp to each app.
- Sorts the full list **by most-recently-used** descending.
- Loads pinned-slot assignments from `SharedPreferences` (`pinned_apps_v2`).
- Splits the result into three lists held in `LauncherState`:
  - `allApps` — complete sorted list.
  - `recentApps` — top 20 non-pinned apps.
  - `pinnedApps` — 4 fixed slots; empty slots are `null`.
- **Usage-access permission:** `MainActivity` checks `AppOpsManager` on start; if missing, sends the user to the system Usage Access settings screen.

---

## 2. Home Screen Layout

**Source:** `LauncherRoot.kt`, `AppGrid.kt`

The home screen has three vertical zones, all anchored to the bottom of the screen:

```
┌──────────────────────────┐
│  Top Bar Zone  (56 dp)   │  ← always present; see §2.1
├──────────────────────────┤
│                          │
│  App List (scrollable)   │  ← fills remaining space
│  (recent or suggestions) │
│                          │
├──────────────────────────┤
│  Pinned Apps (1×4 row)   │  ← always visible
│                          │
└──────────────────────────┘
```

- The app list uses `reverseLayout = true` with `Arrangement.Bottom` so the most relevant results sit closest to the user's thumb.
- No wallpaper, widgets, or status-bar decorations are shown.

### 2.1 Top Bar Zone

**Height:** 56 dp — fixed on both the home screen and the All Apps screen so all screens share a common visual rhythm.

**Purpose:**
- Provides clearance from the system status bar.
- Acts as a permanent reserved slot for a future title, settings button, or any persistent chrome — these can be added without affecting the rest of the layout.
- On the **home screen**: displays `RecognizedTextBar` (the live scribble query + clear button) when a scribble is active. When idle the zone is visually empty. Because the box height is constant, the app list and pinned grid never shift when text appears or disappears.
- On the **All Apps screen**: displays the static "All Apps" label left-aligned and vertically centred.

The `RecognizedTextBar` composable fills the zone with a semi-transparent pill background, large light-weight text (22 sp, 4 sp letter-spacing), and a × clear button.

---

## 3. Scribble Recognition (Core Interaction)

**Source:** `DrawingOverlay.kt`, `GestureHandler.kt`, `InkRecognitionManager.kt`

This is the primary way to interact with the launcher. The user writes directly on the screen anywhere (no text field).

### 3a. Touch Capture
- `DrawingOverlay` wraps all content in a `pointerInput(Unit)` scope.
- A touch is ignored until it moves beyond an 8 dp slop threshold; at that point the overlay *intercepts* the event stream so child views do not receive it.
- Raw `MotionEvent` objects are forwarded to `GestureHandler`.

### 3b. Gesture Classification (`GestureHandler`)
Every touch sequence is classified once, at the first moment movement exceeds 30 px:

| Gesture | Condition | Action |
|---|---|---|
| **Swipe Up** | Touch started in bottom 120 dp zone; vertical displacement > horizontal × 1.5 | `onSwipeUp` → open All Apps |
| **Backspace swipe** | Rightward-to-left; horizontal displacement > ~29 dp (density-scaled); horizontal dominance > 2× vertical | `onBackspace` → delete last character of committed text |
| **Ink stroke** | Everything else | Points accumulated into `Ink.Stroke` |

- Swipe-Up fires only if the finger's upward velocity at `ACTION_UP` exceeds 200 px/s (via `VelocityTracker`).
- Classification is decided once and locked for the rest of the touch; no re-evaluation on subsequent move events.

### 3c. Ink Accumulation
- Each completed ink stroke is appended to a shared `inkBuilder` inside `GestureHandler`.
- The full accumulated `Ink` (all strokes so far) is passed to `onInkStrokeAdded`.

### 3d. Recognition (`InkRecognitionManager`)
- Initialized at app start with the `en-US` ML Kit Digital Ink model (downloaded on-device).
- `DrawingOverlay` waits **150 ms** after the last stroke before calling `recognize()` — lets multi-stroke characters (e.g. "t", "i") finish before recognition fires.
- Recognition calls are sequence-numbered; out-of-order async results (from earlier strokes arriving late) are silently discarded.
- Results (ordered list of candidate strings) are delivered to `LauncherViewModel.onRecognizedResults()`.

### 3e. Committed vs. Active Text (`LauncherViewModel`)
- `committedText` — text from strokes that have been finalized (after the 1-second idle timer fires).
- `recognizedText` (state) — `committedText + current active recognition candidate`.
- When the 1-second idle timer fires in `DrawingOverlay`: canvas is cleared, `GestureHandler` is reset, and `commitActiveScribble()` is called → the active candidate becomes the new `committedText`.
- When `recognizedText` is externally cleared (e.g. app launch or clear button), the idle timer is also cancelled.

### 3f. Ink Visualization
- A `Canvas` composable overlaid on all content draws white semi-transparent lines (alpha 0.6, stroke width 8 px, round caps) between each consecutive `DrawPoint`.
- Points marked `isStart = true` (first point of a new stroke) are not connected to the previous point, so separate strokes appear visually disconnected.

---

## 4. Search / Filtering

**Source:** `LauncherViewModel.filterApps()`

- Runs on every recognition result update.
- Two-pass filter, both case-insensitive:
  1. **Prefix match** — app label starts with the query.
  2. **Contains match** — app label contains the query (deduped against pass 1).
- Returns up to 15 results.
- Results replace `recentApps` in the app list display whenever `suggestions` is non-empty.

---

## 5. Auto-Launch

**Source:** `LauncherViewModel.onRecognizedResults()`

- If the filtered result set contains exactly **one** app AND its label exactly matches the current query (case-insensitive), the app is launched immediately with no further user input.
- Auto-launch calls `clearScribble()` first, then fires the system launch intent with `FLAG_ACTIVITY_NEW_TASK`.

---

## 6. Recognized Text Bar

**Source:** `RecognizedTextBar.kt`

- Lives inside the fixed 56 dp top bar zone (§2.1); the zone is always present so the layout never shifts when text appears.
- Displays the current query string in large, light-weight text with wide letter-spacing.
- Contains a **×** (close) icon button:
  - Calls `clearScribble()` → empties `committedText` and `recognizedText`, clears suggestions.
  - Exits edit mode, clears any pending pin operation.

---

## 7. App List (Recent & Suggestions)

**Source:** `AppGrid.AppList`, `AppGrid.AppRow`

- Shows `suggestions` when non-empty, otherwise `recentApps` (top 20 by last-used).
- Each row: 32 dp app icon + label in white, 18 sp medium weight.
- **Tap** → launch app (or, if a pin operation is pending, cancel it).
- **Long-press** → sets `appToPin` to the selected `AppInfo` and enters edit mode. A banner appears: *"Tap a slot below to pin \<label\>"*.

---

## 8. Pinned Apps Section

**Source:** `AppGrid.PinnedAppsSection`, `AppGrid.PinnedAppSlot`

- Fixed 1-row × 4-column section at the bottom of the home screen.
- Each slot shows a 48 dp icon and a 9 sp truncated label. Empty slots render as a faint circle placeholder.
- **Tap (normal mode)** → launch app.
- **Long-press** → enter edit mode (shows remove badges on all occupied slots).
- **Tap slot (while `appToPin` is set)** → pin the pending app into that slot (removes it from its previous slot if it was already pinned elsewhere).
- **Long-press slot (while `appToPin` is set)** → same as tap-slot: pins the pending app.
- **× badge (edit mode)** → unpin the app from that slot (slot becomes empty).
- Persistence: slot assignments are stored as a comma-separated string in `SharedPreferences` under key `pinned_apps_v2` (4 comma-separated package names, empty string for empty slots). Older saved values with more than 4 slots are trimmed to the visible slots when read.

---

## 9. All Apps Screen

**Source:** `AllAppsScreen.kt`, `LauncherRoot.kt`

- Triggered by a swipe-up gesture from the bottom zone of the home screen.
- Slides in from the bottom via `AnimatedVisibility` + `slideInVertically`.
- Shows every installed app sorted by last-used, in a scrollable `LazyColumn` with icon + label + inline Pin/Unpin text button per row.
- **Swipe down** anywhere on the screen → dismiss (back to home screen).
- **Back button** (top-right) → dismiss.
- **Tap app** → launch and dismiss All Apps.
- **Long-press app** → sets `appToPin`, dismisses All Apps, returns to home screen in pin-selection mode.
- Pin/Unpin inline button: does not require entering edit mode; toggles pinned state directly (unpin removes from slot; pin requires selecting a slot on the home screen after dismissal — this flow is currently incomplete: the unpin here calls a package-based `onUnpinApp(String)` callback that has no implementation in `LauncherRoot`/`ViewModel`).

---

## 10. Backspace

**Source:** `LauncherViewModel.backspace()`

- Triggered by the right-to-left swipe gesture.
- If `committedText` is non-empty: drops its last character.
- Else if `recognizedText` is non-empty: clears all text (full reset).
- Re-runs `filterApps` against the new `committedText` and updates `suggestions` and `recognizedText` in state.

---

## 11. State Model Summary

```
LauncherState
├── allApps          List<AppInfo>       all installed apps, sorted by last-used
├── recentApps       List<AppInfo>       top 20 non-pinned from allApps
├── pinnedApps       List<AppInfo?>      4 slots, null = empty
├── suggestions      List<AppInfo>       current filter results (up to 15)
├── recognizedText   String              committedText + active recognition candidate
├── showAllApps      Boolean             All Apps overlay visibility
├── isModelReady     Boolean             ML Kit model downloaded and ready
├── isEditMode       Boolean             pinned grid shows remove badges
└── draggingApp      AppInfo?            (declared but unused in current UI)
```
