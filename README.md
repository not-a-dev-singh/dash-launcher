# Dash Launcher

A minimalist Android launcher built around **intent-driven interaction**. Instead of hunting through menus, you simply write the app name directly on your screen.

## Philosophy

Modern smartphone launchers often claim to be minimal, yet strip away so much functionality that users end up spending just as much time searching—now with an added layer of UI clutter. **Dash takes a different approach.**

The core premise: **You know what app you want. Start writing.**

No keyboard pop-ups. No search buttons to tap. No animations to wait through. Just your intention, translated directly into action. The launcher respects focused, single-handed use and assumes the user has a goal—not a browsing habit.

Inspired by the original **Nokia Z Launcher**, Dash is built for people who want their phone to get out of the way.

## Features

- **Scribble-to-Search**: Write anywhere on the screen to find apps instantly
- **Single-Hand Optimized**: Search suggestions appear at the bottom, within natural thumb reach
- **No Bloat**: Black full-screen UI with zero animations or decorative elements
- **Smart Filtering**: Prefix and substring matching for intuitive app discovery
- **Auto-Launch**: Apps launch immediately when your handwriting exactly matches an app name
- **Pinned Slots**: Keep your 4 most-used apps always at the bottom
- **Usage Stats**: Recent apps are sorted by actual usage, not arbitrary popularity
- **All Apps View**: Swipe up to see your entire app library, sorted by last used
- **Drag to Reorder**: Organize pinned slots by dragging and dropping

## How It Works

### Home Screen
- **Black, full-screen canvas** — your workspace
- **Top bar zone** — shows your handwritten query as you write
- **App list** — displays suggestions as you scribble, or recent apps when idle
- **Pinned section** — 4 easily accessible slots at the bottom

### Interaction Model

| Gesture | Action |
|---------|--------|
| **Write** | Start writing anywhere; scribble recognition finds matching apps |
| **Backspace** | Swipe right-to-left to delete the last character |
| **Swipe up** | Open All Apps view (from bottom 120 dp zone) |
| **Tap app** | Launch it |
| **Long-press app** | Pin it to one of the 4 slots below |
| **Long-press pinned slot** | Enter edit mode; tap the × to unpin, or drag to reorder |
| **Swipe down** | Close All Apps, return to home screen |

### Smart Filtering
Search runs in two passes:
1. **Prefix match** — apps whose name *starts with* your query appear first
2. **Contains match** — apps whose name *contains* your query appear below

Up to 15 results are shown at any time.

### Always Focused
- Uses ML Kit's Digital Ink Recognition (on-device, no internet)
- No suggestions popup or keyboard interrupt
- Committed text and active recognition kept separate
- 1-second idle timer transitions handwritten text to "locked in"

## Getting Started

### Requirements
- Android 9+ (API 28 and above)
- **Usage Access permission** — required to sort apps by how recently you used them

### Build
```bash
./gradlew build
```

### Run
```bash
./gradlew installDebug
```

Then set Dash as your default launcher in system settings.

### Permissions
On first launch, you'll be prompted to grant **Usage Access** permission. This allows Dash to see which apps you use most, so they appear at the top of your list.

## Architecture

- **Kotlin + Jetpack Compose** — modern, declarative UI
- **MVVM pattern** — state management via `LauncherViewModel`
- **Ink Recognition** — Google ML Kit Digital Ink (English, on-device)
- **Touch interception** — custom `DrawingOverlay` for freehand input
- **Persistent storage** — `SharedPreferences` for pinned app slots and user prefs

**Key files:**
- `MainActivity.kt` — app entry point and permission handling
- `LauncherViewModel.kt` — state, filtering, and business logic
- `DrawingOverlay.kt` — touch capture and ink visualization
- `GestureHandler.kt` — swipe and gesture classification
- `InkRecognitionManager.kt` — ML Kit integration
- `AppRepository.kt` — app loading and usage stats

For full technical details, see [`SPEC.md`](SPEC.md).

## Philosophy in Practice

### Why no animations?
Animations create the illusion of activity but add latency. Dash prioritizes responsiveness over visual polish.

### Why full-screen black?
Reduces cognitive load. There's nothing competing for your attention except your task.

### Why pinned slots and not a dock?
Four slots force intentionality. You can't pin every app; you curate. This keeps the launcher lean and your intent clear.

### Why scribble over typing?
Handwriting recognition removes the friction of finding a keyboard, choosing an input method, and confirming a search. It's one continuous motion from thought to action.

## Status

**Actively used as a daily driver.** Core functionality is stable; see [`SPEC.md`](SPEC.md) for the complete feature list and implementation details.

Contributions, feedback, and forks are welcome.

## License

MIT License — see [`LICENSE`](LICENSE) for details.

---

**Dash is for people who know what they want to do. Let's get there.**
