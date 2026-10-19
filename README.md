# Dial Pilot

Turns the **Smart Dial on the CMF Buds Pro 2 case** into a remote for your phone screen: next or
previous reel in Instagram, scrolling in Chrome and PDF readers, zoom, and more. You can pick an
action per app, tune each action, and add new actions as plug-ins.

Android 8.0+ (API 26), built against Android 15 (API 35).

## Install

Download the latest APK on your phone:
**https://github.com/Karabiner07/dial-pilot/releases/latest/download/dial-pilot.apk**

- **Google Play Protect may block it** in regions with enhanced fraud protection (e.g. India),
  because the app uses an accessibility service. Turn off *Play Store → Play Protect → ⚙ → Scan
  apps with Play Protect* while installing, then turn it back on.
- New builds install over the old one: every build is signed with the same key.

## Setup

1. **Nothing X** → CMF Buds Pro 2 → Smart Dial: set **double-click → Skip forward** and
   **triple-click → Skip back**.
2. Open **Dial Pilot** → **Turn on**, and enable the *Dial Pilot* accessibility service.
   - Android 13+ may call this a "Restricted setting" for sideloaded apps. Go to **Settings →
     Apps → Dial Pilot → ⋮ → Allow restricted settings**, then try again.
3. Open Instagram Reels and double- or triple-click the dial.
4. Optional: add the **Dial scroll** Quick Settings tile to pause or resume quickly.

## How it works

The Nothing X app is the only thing that configures the dial, and Nothing publishes no API for it.
Dial Pilot works with the signals that do reach the phone.

**Presses (default).** A press set to *Skip forward / back* reaches the phone as a Bluetooth media
key. Android sends media keys to the app that most recently started playing audio. While the dial
is captured, `MediaKeyDialSource` takes that role: it holds a `MediaSession` and loops a silent
`AudioTrack`, and claims the role back whenever another app starts playing. Next becomes +1 step
and previous becomes -1.

**Rotation (opt-in).** Rotation can only be set to *Volume* in Nothing X. On many phones the buds
apply it to their own volume and never tell the phone, so no app can see it. Where the phone's
volume slider does move when you turn the dial, turn on **Use dial rotation**. `VolumeDialSource`
then holds the media volume at an anchor level, reads each change as a step, and restores it.

The engine runs the action chosen for the foreground app, usually as an injected touch gesture.
The dial is left alone when the screen is off or locked, when the buds are disconnected, in apps
set to *Don't capture*, and when the master switch or tile is off.

## Built-in actions

| Action | Use for | Settings |
|---|---|---|
| Scroll up / down | Web, PDFs, feeds, chats | distance per step, swipe speed, stop exactly (no fling) |
| Next / previous item | Reels, Shorts, TikTok, stories | vertical/horizontal, cooldown |
| Page by page | Chrome, lists (accessibility scroll, not touches) | none |
| Scroll left / right | Carousels, horizontal PDFs | same as vertical |
| Zoom | PDFs, maps, photos | zoom per step |
| Next / previous track | Music | cooldown |
| Don't capture | Apps where the dial should behave normally | none |

Out of the box, Instagram uses **Next / previous item** and every other app uses **Scroll up / down**.

## Building

Push to `main` and GitHub Actions runs the unit tests and Android Lint, builds a shrunk release
APK, and publishes it as a release. Locally, use Android Studio or:

```bash
./gradlew testDebugUnitTest lintRelease assembleRelease
```

Stack: Kotlin 2.1, Jetpack Compose (Material 3), Hilt, DataStore, coroutines/Flow, version catalog
(`gradle/libs.versions.toml`).

## Architecture

```
com.dialpilot
├── core/                 # Framework. No feature-specific code.
│   ├── action/           # DialAction (plug-in API), ActionSetting schema, ActionContext, ActionRegistry
│   ├── input/            # DialInputSource (plug-in API): MediaKeyDialSource, VolumeDialSource
│   ├── engine/           # DialEngine + CaptureRules: decide what captures the dial, coalesce input, run it
│   ├── monitor/          # Headset and screen/lock state as Flows
│   └── settings/         # DialConfig snapshot + DataStore repository
├── actions/              # Features: one class per action + ActionsModule (Hilt multibinding)
├── service/              # AccessibilityService host, gesture-backed ActionContext, QS tile
├── di/                   # Hilt modules for system services and input sources
└── ui/                   # Compose screen, ViewModel, reusable components
```

```
Dial press ─► buds ─► AVRCP media key ─► MediaKeyDialSource ─┐
Dial turn  ─► buds ─► (if reported) volume ─► VolumeDialSource ─┤
                                                               ▼
               DialConfig + foreground app ─► DialEngine ─► DialAction.perform(ActionContext)
                                                  │                    │
                                           EngineStatus (UI)    gestures, nodes, media keys
```

Design choices:
- **Plug-ins through Hilt `@IntoSet` multibindings.** The engine, storage and UI only know the
  `DialAction` and `DialInputSource` interfaces.
- **Schema-driven settings.** An action declares `ActionSetting`s (slider / toggle / choice). They
  are stored under namespaced keys and rendered by the UI automatically.
- **Input coalescing.** Steps that arrive while a gesture is running are summed into the next call,
  so gestures never pile up or cancel each other.
- **Accessibility service as the only long-running component.** It needs no foreground
  notification, and it is the only unprivileged way to inject gestures.

## Adding a feature

1. Create `actions/MyAction.kt`:

   ```kotlin
   class MyAction @Inject constructor() : DialAction {
       override val id = "my_action"                 // persisted, never rename
       override val titleRes = R.string.action_my
       override val descriptionRes = R.string.action_my_desc
       override val settings = listOf(
           ActionSetting.Slider("strength", R.string.setting_strength, 1..10, default = 5),
       )

       override suspend fun perform(steps: Int, context: ActionContext) {
           val strength = context.settings.int("strength")
           // Use context.dispatchGesture(...), context.findPrimaryScrollable(),
           // context.dispatchMediaKey(...), or the helpers in Gestures.kt.
       }
   }
   ```

2. Register it in `actions/ActionsModule.kt`:

   ```kotlin
   @Binds @IntoSet @Singleton
   abstract fun myAction(action: MyAction): DialAction
   ```

3. Add its strings to `res/values/strings.xml`.

It then appears in the default-action picker, the per-app rule picker and the action settings list,
with its settings stored automatically. New input sources work the same way: implement
`DialInputSource` and bind it in `di/InputModule.kt`.

## Limitations

- **Single-click (play/pause) is ignored** in apps where the dial is captured. There is no way to
  pass a media key on to the app that would normally receive it.
- **Rotation is phone-dependent.** It only works where the phone's volume slider moves when you
  turn the dial. While it is on, the media volume stays between min and max and can't reach 0.
- **A silent audio stream stays active while the dial is captured.** That is how the app receives
  presses, and it keeps the Bluetooth audio link awake, at a small battery cost.
