# Dial Pilot

Turns the **Smart Dial on the CMF Buds Pro 2 case** into a scroll wheel for your phone: web pages,
PDFs, Instagram Reels, YouTube Shorts and more. You can pick an action per app, tune each action,
and add new actions as plug-ins.

Targets Android 8.0+ (API 26) and is built and tested against Android 14/15 APIs.

## How it works

The Nothing X app is the only thing that talks to the dial, and Nothing publishes no API for it.
What *is* visible to other apps: when the dial is set to **Volume**, every click makes the buds
report a new volume over Bluetooth, and Android changes the media volume.

Dial Pilot runs as an accessibility service and:

1. **Pins the media volume at an "anchor" level** while it's capturing the dial. The anchor is never
   0 or max, so it can detect both directions.
2. **Reads each change away from the anchor as dial clicks**, then sets the volume straight back.
3. **Runs the action you picked for the app in front**, usually as an injected touch gesture.

The dial goes back to being a normal volume control when the screen is off or locked, when your
buds aren't connected, in apps you set to "Volume", or when you switch it off with the master
switch or the Quick Settings tile.

## Setup

1. **Nothing X app** → CMF Buds Pro 2 → Controls → Smart Dial → set **rotate** to **Volume**.
2. Build and install (see below), then open **Dial Pilot**.
3. Tap **Turn on** and enable the *Dial Pilot* accessibility service.
   - Android 13+ blocks this for sideloaded apps ("Restricted setting"). Go to
     **Settings → Apps → Dial Pilot → ⋮ → Allow restricted settings**, then try again.
4. Optional: add the **Dial scroll** Quick Settings tile (edit your quick settings panel).
5. Open Chrome, a PDF or Instagram and turn the dial.

## Built-in actions

| Action | Use for | Settings |
|---|---|---|
| Scroll up / down | Web, PDFs, feeds, chats | distance per click, swipe speed, stop exactly (no fling) |
| Next / previous item | Reels, Shorts, TikTok, stories | vertical/horizontal, cooldown |
| Page by page | Chrome, lists (uses the accessibility scroll action, not touches) | none |
| Scroll left / right | Carousels, horizontal PDFs | same as vertical |
| Zoom | PDFs, maps, photos | zoom per click |
| Next / previous track | Music | cooldown |
| Volume (don't capture) | Apps where you want normal volume | none |

General options: reverse direction, vibrate on each click, let the phone's volume keys scroll too,
and only capture while a headset whose name matches (default `CMF`) is connected.

Out of the box, Instagram has a rule set to **Next / previous item** and every other app uses
**Scroll up / down**.

## Building

Open the folder in Android Studio (Ladybug or newer) and press Run, or:

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

Stack: Kotlin 2.1, Jetpack Compose (Material 3), Hilt, DataStore, coroutines/Flow, version catalog
(`gradle/libs.versions.toml`).

## Architecture

```
com.dialpilot
├── core/                       # Framework. No feature-specific code.
│   ├── action/                 # DialAction (plug-in API), ActionSetting schema, ActionContext, ActionRegistry
│   ├── input/                  # DialInputSource (plug-in API) + VolumeDialSource
│   ├── engine/                 # DialEngine: picks the active action, coalesces clicks, runs it
│   ├── monitor/                # Headset and screen/lock state as Flows
│   └── settings/               # DialConfig snapshot + DataStore repository
├── actions/                    # Features: one class per action + ActionsModule (Hilt multibinding)
├── service/                    # AccessibilityService host, gesture-backed ActionContext, QS tile
├── di/                         # Hilt modules for system services and input sources
└── ui/                         # Compose screen, ViewModel, reusable components
```

Data flow:

```
Dial ─► buds ─► AVRCP volume ─► VolumeDialSource ─► DialEngine ─► DialAction.perform()
                                     ▲                  │  ▲             │
               phone volume keys ────┘                  │  └── DialConfig (DataStore) + foreground app
                                                        ▼                ▼
                                              EngineStatus (UI)   ActionContext (gestures, nodes, media keys)
```

Design choices:
- **Plug-in actions via a Hilt `@IntoSet` multibinding.** The engine, settings storage and UI only
  know the `DialAction` interface.
- **Schema-driven settings.** An action declares `ActionSetting`s (slider / toggle / choice). They're
  stored under namespaced keys, and the UI renders them automatically.
- **Click coalescing.** Clicks that arrive while a gesture is still running are summed into the next
  call, so fast turns never pile up gestures or cancel each other.
- **Accessibility service as the only long-running component.** No foreground-service notification
  is needed, and it's the only unprivileged way to inject gestures.

## Adding a feature

Example: "Rotate to switch browser tabs" or anything else the dial should do.

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

3. Add the strings to `res/values/strings.xml`.

It then appears in the default-action picker, the per-app rule picker and the action settings list,
with its settings stored automatically. If an action needs a new capability (for example a global
action like "back"), add a method to `ActionContext` and implement it in `ServiceActionContext`.

New **input sources** work the same way: implement `DialInputSource` and bind it in
`di/InputModule.kt`.

## When the phone never sees dial rotation

On some phones (seen on a Xiaomi HyperOS device) the CMF buds apply dial rotation to their own
volume and never report it, even with absolute volume toggled. The status card's
"changes seen" counter shows this: it stays the same while you turn the dial. No app can read
rotation in that case.

Dial **presses** still reach the phone. In Nothing X, set double-click to *Skip forward* and
triple-click to *Skip back*. While the dial is captured, `MediaKeyDialSource` claims the media
keys (a `MediaSession` plus a looping silent `AudioTrack`, re-claimed whenever another app starts
playing), so those presses become +1 / -1 steps: next or previous reel, scroll down or up.
Switch this off with **Use dial presses**.

## Limitations

- **The dial must be set to Volume in Nothing X.** Dial presses (play/pause, ANC) go straight to the
  media player or the buds and can't be intercepted by a normal app.
- **Android may flash the volume slider on each click.** The Bluetooth stack shows it, and only a
  system app can hide it.
- **Some clicks can need two turns.** Bluetooth volume has 128 levels and the phone has about 15–25
  steps, so a click doesn't always land on a new phone step. Turn up *Distance per click* to make up
  for it.
- **The volume slider counts as a dial turn.** If you drag the media volume slider while the dial is
  captured, that is read as dial input too. The phone's volume keys are handled correctly.
- **Muting isn't possible while capturing.** The media volume can't sit at 0 while the dial is
  captured, because the anchor stays between min and max.
