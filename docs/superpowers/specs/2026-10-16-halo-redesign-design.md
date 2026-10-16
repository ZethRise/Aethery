# Halo redesign: main screen, connect orb, full-page popups

Date: 2026-10-16. Status: design approved in chat, awaiting written-spec review.

## 1. Intent

The owner wants the main page redesigned to feel premium and polished, "good enough to stare at for ten minutes". Three concrete asks:

1. A new main screen.
2. A better connect button that reacts to the user's touch through native Kotlin motion.
3. Every popup replaced by a full page with a back button in the top corner.

Chosen direction: **Halo**. The connect control is a glass orb that holds the connection state, and the whole screen's colour temperature follows it: cold dusk when disconnected, warm dawn when connected. Research showed the same principles: a huge central control, states that look completely different, and a status that never lies.

Sources: NordVPN mobile redesign (2026), the VPN connect-switch teardown at vp0.com, Material 3 Expressive, the AGSL and SpringAnimation Android docs.

### Out of scope

- No change to the tunnel, the core, the JNI layer, `AetherVpnService`, or any stored setting, key or default (except the new Halo default theme for fresh installs).
- No Compose migration.
- System-owned UI cannot be restyled: the VPN consent dialog, the notification permission prompt, and the system package installer.
- No new features. Controls that exist today stay; some move.
- Gesture stays a single tap. Hold-to-connect was rejected: slower, not discoverable, no supporting evidence.

### Success

Nothing on the main screen or any converted page looks like a stock Material View. Every state of the orb is distinct without relying on colour alone. Every popup is a page with a top-left back button that behaves identically everywhere. All three ABIs build and every state has been checked on the emulator.

## 2. Visual system

### Halo theme (new, default for fresh installs)

| Token | Value | Use |
|---|---|---|
| canvas | Midnight `#0B0D24` | top of the sky, page background |
| surface | Dusk `#1B1A45` | bottom of the sky, panel base |
| primary | Periwinkle `#9DB0FF` | idle orb, links, focus (the logo's hue) |
| connected | Dawn `#FFC58A` (core `#FFE2B8`, glow `#FF9F6B`) | connected orb, warm sky |
| ink | Linen `#F2EEFF` | primary text |
| muted | Mist `#A9A7C9` | secondary text |
| warning | `#FFB24A` | degraded ring |
| error | Rose `#FF8FA3` | failed state |

Panels are glass: white at 6% over the sky, 1 dp hairline at 10%, 20 dp corners, no heavy shadows. Structure is information, not decoration: no tracked ALL-CAPS labels and no `A · B · C` strings. Sentence case everywhere.

### Existing themes

Dynamic, Forest, Blue/black, Blue/gold and Gold/black stay. The orb and sky read the existing `AppAppearance.Palette` tokens (`primary` for the idle orb, `connected` for the warm state, `canvas` and `surface` for the sky), so they work in any theme. Users keep their saved theme. Halo is added to the theme list and is the default only when no theme is saved.

### Type

One bundled variable family, Bricolage Grotesque (SIL OFL), via `res/font`. Status headline 34 sp, weight 500, width 92. Body 15 sp. Section titles 28 sp on pages. IP and time use tabular figures. If the font is unavailable the app falls back to the system sans without breaking layout.

## 3. Main screen

Portrait layout, top to bottom:

1. Header: logo (left), settings (right). No title.
2. The orb, about 248 dp, centred near 42% of the height, over the sky.
3. Status headline (Disconnected, Connecting, Protected, Connection unstable, Couldn't connect).
4. One line of live detail. While connecting this is the real stage text from the core (`Finding a verified MASQUE gateway`, `HTTP/3 found no gateway; retrying HTTP/2`). When connected it is the gateway and protocol.
5. IP row: flag, address, refresh. Label is sentence case ("Your IP" when disconnected, "VPN IP" when connected).
6. Latency sparkline, visible only when connected; tapping pings (existing behaviour).
7. One glass panel with two rows: **Route** (protocol, opens the mode page) and **Network** (scan summary, opens the scanner page). Connection type stays reachable from Route.
8. Two small chips: Log and Performance, each opening its choice page.

Every existing control keeps its behaviour and preference key.

## 4. The connect orb

A custom View (`HaloOrb`) over a `HaloSky` backdrop. Single tap toggles the tunnel, as today.

| State | Look | Motion | Haptic |
|---|---|---|---|
| Disconnected | Dormant periwinkle orb, cold sky | 6 s breathing (scale 1.00 to 1.03), highlight follows device tilt, stars drift slowly | none |
| Pressed | Squashed to 0.94 | Spring down; on release overshoot to 1.06 and settle | tick (gesture start) |
| Connecting | Core brightens, three rings inhale toward the core | Looping spring-eased rings, stage text cross-fades | none |
| Connected | Gold orb, dawn glow rising behind it | One shockwave ring leaves the orb (700 ms), sky warms over 1200 ms, headline morphs | confirm |
| Degraded | Desaturated orb, amber ring | Slow irregular flicker | none |
| Failed | Dim rose orb | One damped horizontal shake | reject |
| Disconnecting | Warmth drains over 600 ms, orb collapses and settles to dormant | Reverse of bloom | click |

Springs use `SpringForce`; starting values: press stiffness 800 and damping 0.6, release stiffness 400 and damping 0.5. These are tuned on the emulator against screenshots and feel, not fixed by this spec.

Entrance: the splash logo morphs into the orb, replacing the current fade-out overlay. This is the only orchestrated entrance motion.

The orb is drawn with an AGSL `RuntimeShader` on Android 13 and newer. Below 13 it is drawn from layered radial gradients with the same states. Both paths share one state machine.

### Accessibility and comfort

- Content description changes with state ("Connect", "Connecting", "Disconnect"); the keyboard and TV focus ring is kept.
- Every state is also distinguished by the headline text, not colour alone.
- Reduced motion: when the system animator scale is 0, the orb is static, springs become 150 ms cross-fades, tilt and star drift stop.
- Haptics use the platform constants, so they follow system settings.
- Animators, the shader clock and the tilt sensor run only while the activity is resumed and the screen is on.

## 5. Full-page popups

`PageScaffold` is the one container for every secondary screen:

- 44 dp glass back button, top-left, below the status bar inset.
- Large title (28 sp) under it; optional trailing action.
- Scrolling content area.
- Optional bottom bar for the primary action (Save, Apply).
- Push animation: the page slides in from the right with a spring and fades; the page below scales to 0.96 and dims. Pop reverses it. System back and the back gesture pop the top page.

Pages built on it:

| Replaces | Page |
|---|---|
| `showConnectionTypeSheet` | `ChoicePage` with two explained cards (VPN, Proxy) |
| `showChoiceSheet` callers: TLS curve, performance, HTTP/2 fragmentation, MASQUE transport, log level, gateway cache | generic `ChoicePage`: options with radio state and a description each; choosing applies and pops after a short settle |
| `chooseObfuscation` | `ChoicePage` plus link to the advanced editor |
| `showTextSettingsSheet` callers: DNS servers, routing rules, advanced obfuscation | `EditorPage`: fields, inline validation errors, Save in the bottom bar, keyboard-aware |
| `editManualEndpoint` | `EditorPage` |
| `AppUpdater` alert and progress dialogs | `UpdatePage` (available, downloading with progress, permission needed, ready to install) |

The existing full-page screens (settings, mode, scanner, logs, theme, traffic, widget controls, split tunnel, Zero Trust) are moved onto `PageScaffold` so back button, title and transition are identical. Their content and behaviour are unchanged.

The existing page bookkeeping in `MainActivity` (`showingSettings` and friends) is replaced by one page stack, so `handleBack` and predictive back work the same everywhere.

## 6. Architecture

New package `studio.cluvex.aethery.ui`:

- `HaloTheme.kt`: tokens from `AppAppearance.Palette`, plus Halo, type styles, dp helpers.
- `HaloSky.kt`: gradient sky, stars, warmth level.
- `HaloOrb.kt` and `OrbShader.kt`: the orb view, the state machine, the AGSL source and the gradient fallback.
- `PageScaffold.kt` and `Pages.kt`: scaffold, page stack, `ChoicePage`, `EditorPage`, `UpdatePage`.
- `Motion.kt`: spring helpers, reduced-motion check, haptics wrapper.
- `Tilt.kt`: low-pass-filtered rotation-vector parallax.

`MainActivity` keeps tunnel and preference logic and calls into these. `ConnectionControl` is replaced by `HaloOrb`; `AppUpdater` renders through `UpdatePage`.

Dependency added: `androidx.dynamicanimation:dynamicanimation:1.0.0` (resolved through the Google Maven mirror already configured). Asset added: the Bricolage Grotesque variable font file; fetching it is a download that needs the owner's explicit OK at that point.

## 7. Testing and acceptance

- `./gradlew :app:assembleDebug` succeeds for arm64-v8a, armeabi-v7a, x86_64.
- On the emulator, screenshots of: disconnected, connecting (with stage text), connected, degraded, failed; Halo and Dynamic (light and dark); a 360x640 screen; font scale 1.3.
- Every converted popup opened once and closed by the back button and by the back gesture.
- A real connect and disconnect on the emulator still works end to end and the tunnel status shown matches the core.
- Reduced motion verified by setting the animator scale to 0.
- The app has no JVM or instrumentation tests today, so the checks above are the acceptance suite. The core's 398 library tests are unaffected (no core change).

## 8. Risks

- `MainActivity.kt` is 4,048 lines and edits touch many call sites; mitigated by moving UI into `ui/` and changing call sites mechanically.
- AGSL behaviour differs between emulator and devices; the gradient fallback is the safety net and is tested on an API below 33 if an image is available.
- Fidelity of motion cannot be judged from still screenshots; the owner should review it on a device.
- Bundled font adds roughly 0.3 to 0.5 MB.
