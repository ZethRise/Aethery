# Halo Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the main screen, the connect button and every popup of the Aethery Android app with the approved "Halo" design: a glass orb over a sky that warms when connected, spring motion with haptics, and full-page screens with a top-left back button.

**Architecture:** New package `studio.cluvex.aethery.ui` holds pure logic (colour maths, orb state model, stage text, page stack model, sky maths) that is unit-tested on the JVM, plus the custom Views (`HaloSky`, `HaloOrb`, page chrome, `ChoicePage`, `EditorPage`, `UpdatePage`). `MainActivity` keeps all tunnel and preference logic; its UI builders and its five `Dialog` sites call into `ui`. Existing full-page screens adopt the shared back button, transition, background, title and label helpers through mechanical codemods.

**Tech Stack:** Kotlin, classic Android Views (no Compose), AGSL `RuntimeShader` (API 33+) with a layered-gradient fallback, `androidx.dynamicanimation` `SpringAnimation`, JUnit 4 for JVM unit tests, Gradle 8.13 / AGP 8.13.2.

**Spec:** `docs/superpowers/specs/2026-10-16-halo-redesign-design.md`

Two small deviations from the spec text, both implementation choices to confirm in review: (1) the font ships in `assets/fonts` instead of `res/font`, because variable-font axes (`wght`, `wdth`) can only be set through `Typeface.Builder` on API 26+; (2) existing full-page screens are "moved onto the scaffold" by sharing its back button, transition, background, title and label helpers rather than being rebuilt structurally, which gives the same look and behaviour at far lower regression risk.

## Global Constraints

- Tunnel, core, JNI, `AetherVpnService` behaviour, stored preference keys and defaults do not change (only the default theme for installs with no saved theme becomes Halo, and one new broadcast is added in Task 3).
- minSdk 26, targetSdk 36. AGSL only on API 33+; every state also works on the gradient fallback.
- Gesture stays a single tap on the orb. No hold-to-connect.
- Sentence case everywhere; no tracked ALL-CAPS labels; no `A · B · C` strings.
- Halo palette (exact): Midnight `#0B0D24`, Dusk `#1B1A45`, Periwinkle `#9DB0FF`, Dawn `#FFC58A` (core `#FFE2B8`, glow `#FF9F6B`), Linen `#F2EEFF`, Mist `#A9A7C9`, warning `#FFB24A`, error `#FF8FA3`.
- Glass panels: white at 6% over the sky, 1 dp hairline at 10%, 20 dp corners.
- Orb sizes and timings: orb 248 dp diameter; press stiffness 800, damping 0.6; release stiffness 400, damping 0.5; bloom ring 700 ms; sky warm-up 1200 ms; drain 600 ms; breathing 6 s, scale 1.00 to 1.03; reduced motion replaces springs with 150 ms cross-fades.
- Pages: 44 dp glass back button top-left below the status-bar inset; title 28 sp; page springs stiffness 350, damping 0.85; page underneath scales to 0.96 and dims.
- Fonts bundled must be SIL OFL. Downloading the font file needs the owner's explicit OK first (Task 2).
- System dialogs (VPN consent, notification permission, package installer) are not restyled.
- Build env for every Gradle command: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=$HOME/Android/Sdk`. Use `-PtargetAbi=x86_64` for day-to-day builds; the last task builds all ABIs.
- The Aliyun Google-Maven mirror init script is already installed at `~/.gradle/init.d/google-mirror.gradle`; new Google-hosted dependencies resolve through it.

## Review Focus

Failure modes the spec implies but no feature task would otherwise test, most likely first. Each has a pinned test or check in the task named.

1. Reduced motion (animator scale 0, or "remove animations"): no infinite animators run and the orb is static. Task 1 (`MotionTest`), Task 5.
2. Rapid state flips (connecting, failed, connecting within 100 ms; double tap during a transition): the last state wins, no stacked animators, no stale warmth, no repeated haptic. Task 3 (`OrbModelTest`), Task 5.
3. Back handling with the page stack above existing pages and the keyboard open: one back press pops exactly the top page, never two. Task 7 (`PageStackModelTest`).
4. Activity recreation (rotation, light/dark switch, theme change) while connected or connecting: orb and sky come back in the right state. Task 11.
5. Large font scale (1.5) and a 360x640 screen: nothing on the main screen or a converted page is clipped or overlaps the orb. Task 11.
6. Stale stage text: after the state leaves connecting, the last stage line must not show. Task 3 (`StageTextTest` plus the receiver rule).

---

### Task 1: Test scaffold, dependencies, colour maths, motion basics

**Files:**
- Modify: `app/build.gradle.kts` (dependencies and test options)
- Create: `app/src/main/java/studio/cluvex/aethery/ui/HaloColors.kt`
- Create: `app/src/main/java/studio/cluvex/aethery/ui/Motion.kt`
- Test: `app/src/test/java/studio/cluvex/aethery/ui/HaloColorsTest.kt`
- Test: `app/src/test/java/studio/cluvex/aethery/ui/MotionTest.kt`

**Interfaces:**
- Produces: `object HaloColors { fun mix(a: Int, b: Int, t: Float): Int; fun withAlpha(color: Int, alpha: Float): Int }` (ARGB ints, per-channel linear mix with `roundToInt`, `t` clamped to 0..1).
- Produces: `object Motion { fun reducedFromScale(animatorDurationScale: Float): Boolean; fun reduced(context: Context): Boolean }` (`reduced` reads `Settings.Global.ANIMATOR_DURATION_SCALE`, default 1f).
- Produces: `class SpringValue(initial: Float, stiffness: Float, damping: Float, onUpdate: (Float) -> Unit)` with `val value: Float`, `fun animateTo(target: Float, velocity: Float = 0f)`, `fun snapTo(v: Float)`, `fun cancel()`; wraps `androidx.dynamicanimation` `SpringAnimation(FloatValueHolder)`; when `Motion.reduced` is unknown to it, callers decide, so it never reads settings itself.
- Produces: `object Haptics { fun tick(v: View); fun confirm(v: View); fun reject(v: View); fun click(v: View) }` using `HapticFeedbackConstants` GESTURE_START/CONFIRM/REJECT/CONTEXT_CLICK on API 30+, falling back to CONTEXT_CLICK / LONG_PRESS below.

- [ ] **Step 1: Add dependencies and test options in `app/build.gradle.kts`**: `implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")`, `testImplementation("junit:junit:4.13.2")`, and `testOptions { unitTests.isReturnDefaultValues = true }` inside `android {}`.

- [ ] **Step 2: Write `HaloColorsTest`** with these assertions: `mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f) == 0xFF808080.toInt()`; `mix(a, b, 0f) == a` and `mix(a, b, 1f) == b` for `a = 0xFF102030`, `b = 0x80A0B0C0`; `mix(0x00FF0000, 0xFF0000FF.toInt(), 0.5f) == 0x80800080.toInt()`; `withAlpha(0xFFFF0000.toInt(), 0.5f) == 0x80FF0000.toInt()`; `withAlpha` clamps below 0 and above 1.

- [ ] **Step 3: Write `MotionTest`**: `reducedFromScale(0f)` is true; `reducedFromScale(1f)` and `reducedFromScale(0.5f)` are false.

- [ ] **Step 4: Run the tests to see them fail**
Run: `./gradlew :app:testDebugUnitTest -PtargetAbi=x86_64 --tests 'studio.cluvex.aethery.ui.*'`
Expected: compile FAIL (`HaloColors`, `Motion` unresolved).

- [ ] **Step 5: Implement `HaloColors`, `Motion`, `SpringValue`, `Haptics`** as specified above in the two new files.

- [ ] **Step 6: Run the tests to see them pass**
Run: the same command. Expected: `BUILD SUCCESSFUL`, 2 test classes passing.

- [ ] **Step 7: Commit**
```bash
git add app/build.gradle.kts app/src/main/java/studio/cluvex/aethery/ui app/src/test
git commit -m "ui: add colour maths, motion helpers and unit-test scaffold"
```

---

### Task 2: Halo theme, tokens, type and label helpers

**Files:**
- Modify: `app/src/main/java/studio/cluvex/aethery/AppAppearance.kt`
- Create: `app/src/main/java/studio/cluvex/aethery/ui/HaloType.kt`
- Create: `app/src/main/java/studio/cluvex/aethery/ui/SkyMath.kt`
- Create: `app/src/main/assets/fonts/bricolage_grotesque.ttf` (after owner approval)
- Modify: `app/src/main/java/studio/cluvex/aethery/MainActivity.kt` (helpers near `label()` at line 3112; `TypefaceStyle` at line 3629)
- Test: `app/src/test/java/studio/cluvex/aethery/ui/SkyMathTest.kt`

**Interfaces:**
- Consumes: `HaloColors.mix`.
- Produces: `AppAppearance.Palette` gains `skyTop: Int = canvas`, `skyBottom: Int = surface`, `dawn: Int = connected`, `warning: Int = 0xFFFFB24A`, `error: Int = 0xFFFF8FA3` (defaults so existing themes keep working); `AppAppearance.Theme.HALO` with the Global Constraints palette (`canvas` Midnight, `surface` Dusk, `surfaceVariant` `0xFF26245C`, `ink` Linen, `muted` Mist, `divider` `0xFF3A3870`, `primary` Periwinkle, `primaryContainer` `0xFF2B3470`, `connected` Dawn, `connectedContainer` `0xFF5A3A1E`, `skyTop` Midnight, `skyBottom` Dusk); `AppAppearance.current()` returns `HALO` when no theme is saved.
- Produces: `object SkyMath { fun colors(warmth: Float, top: Int, bottom: Int, dawn: Int): Pair<Int, Int>; data class Star(val x: Float, val y: Float, val size: Float, val phase: Float); fun stars(seed: Long, count: Int): List<Star> }`. `colors` returns `(mix(top, dawn, warmth * 0.12f), mix(bottom, dawn, warmth * 0.6f))`; `stars` is deterministic for a seed, with x, y, phase in 0..1 and size in 0.6..1.6.
- Produces: `object HaloType { fun display(context: Context): Typeface; fun body(context: Context, medium: Boolean): Typeface }` (variable font with `wght` 500 and `wdth` 92 for display, 400/500 for body; falls back to `Typeface.create("sans-serif-medium", NORMAL)` / `"sans"` if the asset is missing or throws).
- Produces in `MainActivity`: `private fun sectionLabel(text: String): TextView` (13 sp, `MUTED`, `HaloType.body`, no letter spacing) and `private fun pageTitle(text: String): TextView` (28 sp display, `INK`); `label()` now sets `typeface` from `HaloType.body`.

- [ ] **Step 1: Write `SkyMathTest`**: `colors(0f, T, B, D) == (T, B)`; `colors(1f, T, B, D) == (mix(T, D, 0.12f), mix(B, D, 0.6f))`; `stars(7L, 36)` has 36 entries, every `x`, `y`, `phase` within 0..1 and `size` within 0.6..1.6, and `stars(7L, 36) == stars(7L, 36)`; `stars(8L, 36) != stars(7L, 36)`.

- [ ] **Step 2: Run to see it fail**
Run: `./gradlew :app:testDebugUnitTest -PtargetAbi=x86_64 --tests 'studio.cluvex.aethery.ui.SkyMathTest'` Expected: compile FAIL.

- [ ] **Step 3: Implement `SkyMath`**, then rerun: Expected: PASS.

- [ ] **Step 4: Extend `AppAppearance`** (new `Theme` constructor parameters `skyTop`, `skyBottom` defaulting to 0L, new `Palette` fields, `load`/`systemPalette` pass them, `Theme.HALO` listed first, `current()` default `HALO`). Existing theme names and saved values keep working.

- [ ] **Step 5: STOP and ask the owner** for permission to download the font: "Download Bricolage Grotesque variable TTF (SIL OFL, about 0.4 MB) from the google/fonts GitHub repository, path `ofl/bricolagegrotesque/`, into `app/src/main/assets/fonts/bricolage_grotesque.ttf`?" Only on a yes, download it and add `app/src/main/assets/fonts/OFL.txt` with the licence. On a no, skip the file; `HaloType` already falls back to the system font.

- [ ] **Step 6: Implement `HaloType`, `sectionLabel`, `pageTitle`, and the `label()` typeface change.**

- [ ] **Step 7: Build and check the theme list still renders**
Run: `./gradlew :app:assembleDebug -PtargetAbi=x86_64` Expected: `BUILD SUCCESSFUL`. Install and open Settings, Theme on the emulator: Halo is listed first and selectable; the other five still apply.

- [ ] **Step 8: Commit**
```bash
git add -A && git commit -m "ui: add Halo theme tokens, type and sky maths"
```

---

### Task 3: Orb state model, stage text, and the stage broadcast

**Files:**
- Create: `app/src/main/java/studio/cluvex/aethery/ui/OrbModel.kt`
- Create: `app/src/main/java/studio/cluvex/aethery/ui/StageText.kt`
- Modify: `app/src/main/java/studio/cluvex/aethery/AetherVpnService.kt` (the `"log"` branch of `onEvent` at lines 134-137; constants block at lines 686-707)
- Modify: `app/src/main/java/studio/cluvex/aethery/MainActivity.kt` (`statusReceiver` at lines 149-178; `onStart` filter at lines 273-282)
- Test: `app/src/test/java/studio/cluvex/aethery/ui/OrbModelTest.kt`
- Test: `app/src/test/java/studio/cluvex/aethery/ui/StageTextTest.kt`

**Interfaces:**
- Produces: `enum class OrbState { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, FAILED }`, `enum class OrbEvent { IGNITE, BLOOM, DRAIN, SHAKE, HAPTIC_CONFIRM, HAPTIC_REJECT, HAPTIC_CLICK }`, `data class OrbLook(val warmth: Float, val rings: Boolean, val flicker: Boolean)`, `object OrbModel { fun lookFor(state: OrbState): OrbLook; fun transition(from: OrbState, to: OrbState): List<OrbEvent> }`.
- Produces: `object StageText { fun plain(coreLine: String): String? }`.
- Produces in `AetherVpnService`: `const val ACTION_STAGE = "studio.cluvex.aethery.STAGE"` and `const val EXTRA_STAGE = "stage"`; every `"log"` core event is also broadcast (package-restricted) as `ACTION_STAGE` with the raw message.

Rules the tests pin (spec section 4): `lookFor` warmth is 0 for DISCONNECTED and FAILED, 0.25 for CONNECTING, 1 for CONNECTED, 0.45 for DEGRADED; `rings` only for CONNECTING; `flicker` only for DEGRADED. `transition`: same state gives an empty list; to CONNECTING gives `[IGNITE]`; CONNECTING to CONNECTED gives `[BLOOM, HAPTIC_CONFIRM]`; DISCONNECTED or FAILED to CONNECTED gives `[BLOOM]` (a restored session is silent); DEGRADED to CONNECTED gives `[]`; any state to FAILED gives `[SHAKE, HAPTIC_REJECT]`; CONNECTED or DEGRADED to DISCONNECTED gives `[DRAIN, HAPTIC_CLICK]`; CONNECTING to DISCONNECTED gives `[HAPTIC_CLICK]`; FAILED to DISCONNECTED gives `[]`; CONNECTED to DEGRADED gives `[]`.

- [ ] **Step 1: Write `OrbModelTest`** with one test per rule above, plus `rapidFlipsKeepLastStateOnly`: folding `transition` over `[DISCONNECTED, CONNECTING, FAILED, CONNECTING, CONNECTED]` ends with the BLOOM/CONFIRM pair only for the last hop and never emits an event for a repeated state.

- [ ] **Step 2: Write `StageTextTest`** using real core lines: `"Finding a verified MASQUE gateway"` gives `"Finding a verified gateway"`; `"HTTP/3 scan running for up to 240s"` gives `"Scanning over HTTP/3"`; `"HTTP/3 found no gateway; retrying HTTP/2 with TLS fragmentation"` gives `"HTTP/3 is blocked here, trying HTTP/2"`; `"Checking 3 cached MASQUE gateway(s)"` gives `"Checking saved gateways"`; `"Cached gateways did not respond; starting a fresh scan"` gives `"Saved gateways did not answer, scanning fresh"`; `"Selected 162.159.198.221:443 (1.470953645s)"` gives `"Gateway found"`; `"Opening local SOCKS5 and HTTP CONNECT proxies"` gives `"Opening local proxies"`; `"Native tunnel started (MASQUE)"` gives `"Starting MASQUE"`; an unknown line such as `"socks5 listening on 127.0.0.1:1819"` gives `null`.

- [ ] **Step 3: Run to see them fail**
Run: `./gradlew :app:testDebugUnitTest -PtargetAbi=x86_64 --tests 'studio.cluvex.aethery.ui.OrbModelTest' --tests 'studio.cluvex.aethery.ui.StageTextTest'` Expected: compile FAIL.

- [ ] **Step 4: Implement `OrbModel` and `StageText`**; rerun. Expected: PASS.

- [ ] **Step 5: Add the stage broadcast** in the service (`sendBroadcast(Intent(ACTION_STAGE).setPackage(packageName).putExtra(EXTRA_STAGE, message))` next to `ConnectionLog.record(message)`), and register `ACTION_STAGE` in `MainActivity`'s receiver filter. In `onReceive`, for `ACTION_STAGE` return early after: if `visualState == CONNECTING`, run `StageText.plain(line)?.let { connectionDetail.text = it }`. Every `show*` function that leaves CONNECTING already overwrites `connectionDetail`, which is what clears stale text (Review Focus 6).

- [ ] **Step 6: Build** `./gradlew :app:assembleDebug -PtargetAbi=x86_64` Expected: success. Commit:
```bash
git add -A && git commit -m "ui: add orb state model, stage text and stage broadcast"
```

---

### Task 4: Sky backdrop and tilt

**Files:**
- Create: `app/src/main/java/studio/cluvex/aethery/ui/HaloSky.kt`
- Create: `app/src/main/java/studio/cluvex/aethery/ui/Tilt.kt`

**Interfaces:**
- Consumes: `SkyMath.colors/stars`, `HaloColors`, `Motion.reduced`, `SpringValue`, `AppAppearance.Palette`.
- Produces: `class HaloSky(context: Context, palette: AppAppearance.Palette) : View` with `var warmth: Float` (setter invalidates), `fun animateWarmth(target: Float, durationMs: Long = 1200)` (a `ValueAnimator` with the existing `PathInterpolator(0.2f, 0f, 0f, 1f)`; snaps when `Motion.reduced`), `fun setTilt(x: Float, y: Float)` (−1..1, shifts stars up to 6 dp), `fun pause()` and `fun resume()` (stop and restart the star-twinkle clock).
- Produces: `class Tilt(context: Context, private val onTilt: (Float, Float) -> Unit)` with `fun start()` and `fun stop()`; uses `Sensor.TYPE_GAME_ROTATION_VECTOR` through `SensorManager`, low-pass filtered (alpha 0.12), does nothing when `Motion.reduced` or when the sensor is absent.

Behaviour: `onDraw` paints the vertical gradient from `SkyMath.colors`, then a radial dawn glow centred at 58% of the height whose alpha is `warmth`, then the stars (36, `SkyMath.stars(seed = 11, 36)`), each twinkling by `phase` on a 9 s loop at low alpha (0.25 to 0.7).

- [ ] **Step 1: Implement both classes.**
- [ ] **Step 2: Verify on the emulator** (done in Task 6 once the sky is placed behind the main screen; here only compile): `./gradlew :app:assembleDebug -PtargetAbi=x86_64` Expected: success.
- [ ] **Step 3: Commit** `git add -A && git commit -m "ui: add Halo sky and tilt"`

---

### Task 5: The orb

**Files:**
- Create: `app/src/main/java/studio/cluvex/aethery/ui/HaloOrb.kt`
- Create: `app/src/main/java/studio/cluvex/aethery/ui/OrbShader.kt`
- Modify: `app/src/main/java/studio/cluvex/aethery/MainActivity.kt` (delete `ConnectionControl` at lines 3717-3909; mechanical rename of `ConnectionControl.State` and `ConnectionControl(` to `HaloOrb.State` and `HaloOrb(`)

**Interfaces:**
- Consumes: `OrbModel`, `OrbEvent`, `SpringValue`, `Haptics`, `Motion.reduced`, `HaloColors`, `AppAppearance.Palette`.
- Produces: `class HaloOrb(context: Context, palette: AppAppearance.Palette) : View` with `enum class State { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, FAILED }` (same names as the old control so `MainActivity` call sites change by rename only), `var state: State`, `var onWarmthTarget: ((Float) -> Unit)?` (called with `OrbModel.lookFor(...).warmth` on each state change so the sky can follow), `fun pauseAnimation()`, `fun resumeAnimation()`, `fun setTilt(x: Float, y: Float)`, `fun wake()` (entrance: from alpha 0 and scale 0.6 to rest with a spring), `var forceFallback: Boolean`.
- Produces: `object OrbShader { const val SRC: String; fun supported(): Boolean }` where `supported()` is `Build.VERSION.SDK_INT >= 33`. The AGSL source takes uniforms `iResolution` (float2), `iTime`, `iWarmth`, `iPress`, `iTilt` (float2), `iColdA`, `iColdB`, `iWarmA`, `iWarmB` (float3) and draws a lit glass sphere with a soft rim and caustic swirl; the Kotlin fallback draws the same look from a `RadialGradient` pair plus a rim stroke.

Behaviour (spec section 4): 248 dp, centred; touch down springs the scale to 0.94 and calls `Haptics.tick`, release springs to 1.06 then settles to 1.0 and calls `performClick`; `state` changes run `OrbModel.transition`, mapping IGNITE to the rings animation start, BLOOM to one expanding ring (700 ms) plus warmth to 1, DRAIN to a 600 ms warmth drain and a collapse-and-settle spring, SHAKE to a damped x-offset, and the HAPTIC events to `Haptics`. Looks per `OrbModel.lookFor`: breathing 6 s when DISCONNECTED, three inhaling rings when CONNECTING, desaturated with an amber (`palette.warning`) ring and irregular flicker when DEGRADED, dim `palette.error` tint when FAILED. Content description is "Connect" (DISCONNECTED, FAILED), "Connecting", "Disconnect" (CONNECTED, DEGRADED); keyboard centre/enter activates, with a visible focus ring. When `Motion.reduced(context)`: no springs and no breathing, tilt off, states cross-fade over 150 ms. Animators stop in `pauseAnimation` and `onDetachedFromWindow`.

- [ ] **Step 1: Implement `OrbShader` and `HaloOrb`**, including the `forceFallback` path sharing one state machine.

- [ ] **Step 2: Rename the old control in `MainActivity`** and delete `ConnectionControl`. Set `connectionControl.onWarmthTarget` later in Task 6 once the sky exists.

- [ ] **Step 3: Add a debug-only state preview** in `MainActivity.onCreate` after `restoreTunnelUi()`: when `applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0` and the launch intent has string extra `preview_state` (one of `disconnected`, `connecting`, `connected`, `degraded`, `failed`) call the matching `show*()`, and when it has `preview_fallback` set `connectionControl.forceFallback = true`. This lets every state be screenshotted without a live tunnel.

- [ ] **Step 4: Verify each state on the emulator** (the screen layout is still the old one, so only the orb is judged here)
Run, per state: `adb shell am start -n studio.cluvex.aethery/.MainActivity --es preview_state connected` then `adb exec-out screencap -p > /tmp/orb-connected.png`. Expected per state: DISCONNECTED dormant periwinkle; CONNECTING bright core with rings; CONNECTED gold orb; DEGRADED amber ring; FAILED dim rose. Repeat once with `--ez preview_fallback true` and confirm the same five states render. Tap the orb once: it squashes and overshoots.

- [ ] **Step 5: Reduced-motion check**
Run: `adb shell settings put global animator_duration_scale 0`, relaunch, then `adb shell dumpsys gfxinfo studio.cluvex.aethery | head -5` twice 5 s apart. Expected: the frame count does not climb in the disconnected state (no infinite animator). Restore with `adb shell settings put global animator_duration_scale 1`.

- [ ] **Step 6: Commit** `git add -A && git commit -m "ui: replace the connect control with the Halo orb"`

---

### Task 6: Main screen layout and status copy

**Files:**
- Modify: `app/src/main/java/studio/cluvex/aethery/MainActivity.kt` (`onCreate` lines 180-271, `showOpeningOverlay` 337-382, `createHeader` 428-456, `createConnectionConsole` 458-497, `createIpAddressCard` 499-523, `refreshPublicIp` 525-569, the four selector builders 648-732, `showConnecting`...`showDisconnected` 2965-3040)
- Create: `app/src/main/java/studio/cluvex/aethery/ui/GlassPanel.kt`

**Interfaces:**
- Consumes: `HaloSky`, `HaloOrb`, `Tilt`, `HaloType`, `sectionLabel`, `HaloColors`, `StageText` wiring from Task 3.
- Produces: `fun glassBackground(palette: AppAppearance.Palette, radiusDp: Int, density: Float): GradientDrawable` (white at 6% fill, 1 dp stroke white at 10%); `fun glassCircle(palette: AppAppearance.Palette, sizeDp: Int, density: Float): GradientDrawable` (oval, same fill and stroke).

Layout (top to bottom, spec section 3): `HaloSky` fills `mainRoot`; header with logo left and a 44 dp glass settings button right; the orb centred at about 42% of the height; status headline (`HaloType.display`, 34 sp, colour per state from the palette); detail line (15 sp, `MUTED`); an IP row without a card (flag, address, refresh icon; label "Your IP" when disconnected or failed, "VPN IP" when connected; tap refreshes); the latency graph, shown only when `visualState` is CONNECTED or DEGRADED; one glass panel with two rows, "Route" (existing `modeSelector` target, `openModeScreen`) and "Network" (existing `scannerSelector` target, `openScannerScreen`), each with its current value and a chevron (the Route row's second line shows the connection type, "VPN" or "Proxy"; changing the type stays in Settings as today); two small glass chips "Log" and "Performance" (existing `openLogsScreen` and `choosePerfProfile`). All existing fields (`modeValue`, `scanValue`, `ipAddressValue`, `perfSelector` child text) keep their roles so `updateConnectionMode`, `refreshPublicIp` and `setModeEnabled` work unchanged. Status copy: "Disconnected" with detail "Tap the orb to connect", "Connecting" / "Starting {protocol} tunnel" (replaced live by stage text), "Protected" / "{protocol} tunnel is active" (restored: "{protocol} tunnel recovered"), "Connection unstable" / "The tunnel is up but a health check failed", "Couldn't connect" / the failure detail or "Check your network and try again". Wiring: `connectionControl.onWarmthTarget = { sky.animateWarmth(it) }`; `Tilt(this) { x, y -> sky.setTilt(x, y); connectionControl.setTilt(x, y) }` started in `onStart`, stopped in `onStop`, with `sky.resume()/pause()` alongside.

Entrance: `showOpeningOverlay` keeps the logo overlay but, instead of fading out last, scales the logo to 0.45 and fades it while `connectionControl.wake()` runs, so the star appears to become the orb; skipped (instant) when `Motion.reduced`.

- [ ] **Step 1: Implement `GlassPanel.kt` and the new layout builders**, keeping the old builders only until the new ones are wired, then delete `createConnectionConsole`, `createLogSelector`, and the replaced selector builders.

- [ ] **Step 2: Update status copy and colours** in the five `show*` functions and the "PUBLIC IP" strings in `refreshPublicIp`.

- [ ] **Step 3: Wire sky, tilt, warmth and the entrance.**

- [ ] **Step 4: Verify** with the preview extras for all five states on the Halo theme at default size. Expected: layout matches the spec section 3 order; orb, headline and panel never overlap; the sky is cold for disconnected and failed and warm for connected. Also check the Dynamic theme (set from Settings, Theme) in light and dark: sky and orb take its colours and stay legible.

- [ ] **Step 5: Commit** `git add -A && git commit -m "ui: Halo main screen, status copy and entrance"`

---

### Task 7: Page stack, page motion, shared page chrome

**Files:**
- Create: `app/src/main/java/studio/cluvex/aethery/ui/PageStack.kt`
- Create: `app/src/main/java/studio/cluvex/aethery/ui/PageChrome.kt`
- Modify: `app/src/main/java/studio/cluvex/aethery/MainActivity.kt` (`animatePageOpen`/`animatePageClose` at lines 886-933, `createHeaderBackButton` at 3087-3099, `handleBack` at 2876-2893)
- Modify: `app/src/main/AndroidManifest.xml` (`android:windowSoftInputMode="adjustResize"` on `MainActivity`)
- Test: `app/src/test/java/studio/cluvex/aethery/ui/PageStackModelTest.kt`

**Interfaces:**
- Produces: `class PageStackModel<T> { val size: Int; fun push(page: T); fun pop(): T?; fun top(): T? }` (LIFO; `pop` on empty returns null).
- Produces: `class PageStack(private val host: FrameLayout, private val palette: AppAppearance.Palette)` with `fun push(page: View)`, `fun pop(): Boolean` (true when a page was removed), `val isEmpty: Boolean`; owns a `PageStackModel<View>`; push adds the view to `host` and runs `PageMotion.open(page, behind)`, pop runs `PageMotion.close(page, behind) { host.removeView(page) }`.
- Produces: `object PageMotion { fun open(page: View, behind: View); fun close(page: View, behind: View, onEnd: () -> Unit) }` using springs (stiffness 350, damping 0.85): the page slides in from 28% of its width on x while fading in, the page behind scales to 0.96 and dims to alpha 0.6; close reverses; with `Motion.reduced` both become 150 ms cross-fades.
- Produces: `fun haloBackButton(context: Context, palette: AppAppearance.Palette, onClick: () -> Unit): ImageView` (44 dp glass circle using `R.drawable.ic_back` tinted `palette.ink`, content description "Back", press springs scale to 0.9, focus ring) and `fun haloPageBackground(palette: AppAppearance.Palette): Drawable` (vertical gradient `skyTop` to `skyBottom`, cold).
- In `MainActivity`: `animatePageOpen(page)` and `animatePageClose(page, onEnd)` become one-line calls to `PageMotion` (so existing pages and new pages move identically); `createHeaderBackButton(onClick)` returns `haloBackButton(this, palette, onClick)`; `private lateinit var pageStack: PageStack` created next to `pageHost` and `handleBack()` starts with `if (pageStack.pop()) return true`.

- [ ] **Step 1: Write `PageStackModelTest`**: pushing a, b, c then popping returns c, b, a then null; `top()` tracks the last push and is null when empty; `size` follows; popping twice after a single push returns the page once and null second (the "never two pages on one back press" rule, Review Focus 3).

- [ ] **Step 2: Run to see it fail**, implement `PageStackModel`, rerun to see it pass:
`./gradlew :app:testDebugUnitTest -PtargetAbi=x86_64 --tests 'studio.cluvex.aethery.ui.PageStackModelTest'`

- [ ] **Step 3: Implement `PageStack`, `PageMotion`, `haloBackButton`, `haloPageBackground`**, and wire `MainActivity` as described. Add the manifest attribute.

- [ ] **Step 4: Verify on the emulator**: open Settings, Theme, Logs. Expected: each page slides in with a soft overshoot, the page beneath dims and shrinks, the back button is the same glass circle top-left on all three, and the system back gesture closes one page at a time.

- [ ] **Step 5: Commit** `git add -A && git commit -m "ui: add page stack, spring page motion and glass back button"`

---

### Task 8: ChoicePage and EditorPage, convert the Dialog popups

**Files:**
- Create: `app/src/main/java/studio/cluvex/aethery/ui/ChoicePage.kt`
- Create: `app/src/main/java/studio/cluvex/aethery/ui/EditorPage.kt`
- Modify: `app/src/main/java/studio/cluvex/aethery/MainActivity.kt` (`showConnectionTypeSheet` 1217-1258 and `createConnectionTypeOption` 1260-1291, `chooseObfuscation` 1698-1753, `editManualEndpoint` 1837-1893, `showTextSettingsSheet` 1917-1969, `showChoiceSheet` 2192-2255)
- Test: `app/src/test/java/studio/cluvex/aethery/ui/EditorModelTest.kt`

**Interfaces:**
- Consumes: `PageStack`, `haloBackButton`, `haloPageBackground`, `glassBackground`, `HaloType`, `SpringValue`, `Haptics`.
- Produces: `fun <T> buildChoicePage(context: Context, palette: AppAppearance.Palette, title: String, subtitle: String, options: List<T>, selected: T, label: (T) -> String, description: (T) -> String, onBack: () -> Unit, onSelected: (T) -> Unit): View` — a full page: back button, 28 sp title, subtitle, one glass row per option with a radio mark that springs when selected; choosing calls `onSelected(item)` then, after 220 ms, `onBack()`.
- Produces: `class EditorField(val name: String, val input: EditText)`, `object EditorModel { fun firstError(values: List<String>, validator: ((List<String>) -> Pair<Int, String>?)?): Pair<Int, String>? }`, and `fun buildEditorPage(context: Context, palette: AppAppearance.Palette, title: String, subtitle: String, fields: List<EditorField>, validator: ((List<String>) -> Pair<Int, String>?)?, secondaryAction: Pair<String, () -> Unit>?, onBack: () -> Unit, onSave: (List<String>) -> Unit): View` — scrolling page, a sentence-case caption above each field, the error for a failing field shown inline under it in `palette.error` with a damped shake, a bottom bar with Save (and the optional secondary action) that stays above the keyboard and navigation bar.
- `MainActivity` keeps every existing function signature (`showChoiceSheet(title, subtitle, options, selected, label, description, onSelected)`, `showTextSettingsSheet(title, subtitle, fields: List<Pair<String, EditText>>, validator, onSave)`, `chooseObfuscation()`, `editManualEndpoint()`, `showConnectionTypeSheet()`), but their bodies now call `pageStack.push(buildChoicePage(...))` or `buildEditorPage(...)`; no `Dialog` remains in `MainActivity` (remove the unused imports). The text-sheet's save callback still runs `closeTunnelControlsScreen(false); openTunnelControlsScreen()` after `onSave`. `showConnectionTypeSheet` keeps its early return while the tunnel is connecting or connected, and its selection still updates `connectionTypeValue` and refreshes Settings via `openSettingsScreen(animate = false)` when `showingSettings`. `editManualEndpoint` supplies the "Clear" secondary action and the existing IP:port regex as the validator, returning `0 to "Use numeric IP:port"`.

- [ ] **Step 1: Write `EditorModelTest`**: `firstError(listOf("a", "b"), null)` is null; a validator returning `1 to "bad"` yields `Pair(1, "bad")`; a validator returning null yields null; a validator is called once with the exact values list.

- [ ] **Step 2: Run to see it fail**, implement `EditorModel` (and the two builders), rerun to see `EditorModelTest` pass.

- [ ] **Step 3: Convert the six `MainActivity` functions** as described and delete the Dialog code.

- [ ] **Step 4: Verify every converted popup on the emulator**: Settings, Connection type; Settings, Advanced, TLS fingerprint, Performance, Log level, Gateway cache, HTTP/3 and HTTP/2, Obfuscation; DNS resolvers, Destination routing, Advanced obfuscation (WireGuard), Manual endpoint (type `1.2.3.4` and press Save: inline "Use numeric IP:port" appears under the field, no popup). Expected for each: a full page with the glass back button top-left; picking an option applies it and returns after about 220 ms; with the keyboard open the Save bar stays visible; the back gesture closes only the page.

- [ ] **Step 5: Commit** `git add -A && git commit -m "ui: turn the popup dialogs into full pages"`

---

### Task 9: Update pages

**Files:**
- Create: `app/src/main/java/studio/cluvex/aethery/ui/UpdatePage.kt`
- Modify: `app/src/main/java/studio/cluvex/aethery/AppUpdater.kt` (dialog sites at lines 54-61, 78-95, 184-214)
- Modify: `app/src/main/java/studio/cluvex/aethery/MainActivity.kt` (the `AppUpdater(this)` construction at line 196)

**Interfaces:**
- Produces: `sealed class UpdateUi { object Checking; data class Available(val version: String); data class Downloading(val version: String, val percent: Int?) ; object NeedsPermission; data class Message(val title: String, val body: String) }`.
- Produces: `interface UpdatePages { fun show(state: UpdateUi, actions: UpdateActions); fun dismiss() }` and `data class UpdateActions(val primary: Pair<String, () -> Unit>? = null, val secondary: Pair<String, () -> Unit>? = null)`; `MainActivity` implements it with one reusable page pushed on `pageStack` (re-rendered in place when the state changes, so Checking to Available to Downloading does not stack pages) and a determinate progress bar drawn in `palette.primary`.
- `AppUpdater(activity: Activity, pages: UpdatePages)` replaces `MaterialAlertDialogBuilder` and `AlertDialog` usage: "Checking for updates" (indeterminate), "Update available" with "Download" and "Not now", "Downloading {version}" with live percent, "Allow update installs" with "Open settings" and "Cancel", and the message states ("You're up to date", "Update check failed", "Download failed") with an "OK" action. Copy keeps the current wording; `ThemeOverlay_Aethery_AlertDialog` becomes unused and is removed from `styles.xml` with its `materialAlertDialogTheme` item.

- [ ] **Step 1: Implement `UpdatePage` and the `MainActivity` adapter**, then change `AppUpdater` to render through `UpdatePages`.
- [ ] **Step 2: Verify** from Settings, Check for updates on the emulator (offline GitHub is fine): Expected: a full page "Checking for updates" with an indeterminate bar, then a result page ("You're up to date" or "Update check failed") with an OK action that returns to Settings; no `AlertDialog` appears.
- [ ] **Step 3: Build and commit** `./gradlew :app:assembleDebug -PtargetAbi=x86_64`, then `git add -A && git commit -m "ui: move updater dialogs onto full pages"`

---

### Task 10: Restyle existing pages with the shared helpers

**Files:**
- Modify: `app/src/main/java/studio/cluvex/aethery/MainActivity.kt` (mechanical codemods across the file)
- Create: `scripts/halo_codemod.py` (one-off, deleted at the end of the task)

**Interfaces:**
- Consumes: `haloPageBackground`, `sectionLabel`, `pageTitle`, `haloBackButton`.

Codemods (each a deterministic regex run over `MainActivity.kt`, listed with the counts measured before this plan):

1. 17 sites `setBackgroundColor(CANVAS)` on a page `FrameLayout` become `background = haloPageBackground(palette)`. Do not touch `mainRoot`/`pageHost` (they are replaced in Task 6).
2. 35 ALL-CAPS labels `label("TEXT", 12f|11f, ...).apply { letterSpacing = ... }` become `sectionLabel("Text")` for section headers and `label("Selected"|"Current"|"Desktop only", 12f, primary|MUTED, TypefaceStyle.MEDIUM)` for the row indicators. Conversion rule: first word capitalised, the rest lower case, except the tokens `MASQUE`, `HTTP/3`, `HTTP/2`, `IP`, `VPN`, `DNS`, `CIDR`, `SOCKS5`, which stay upper case ("HTTP/3 AND HTTP/2" becomes "HTTP/3 and HTTP/2", "NATIVE SPLIT TUNNELING" becomes "Native split tunneling").
3. Page-title labels `label(<expr>, 22f, INK, TypefaceStyle.MEDIUM)` inside page headers become `pageTitle(<expr>)`.
4. Remaining `createHeaderBackButton { ... }` call sites already use the new glass button (Task 7); only their `LayoutParams(dp(48), dp(56))` sizes change to `dp(44)` square with a `dp(4)` end margin, so the button is 44 dp everywhere.

- [ ] **Step 1: Write `scripts/halo_codemod.py`** implementing the four rules, run it, and print a before/after count per rule. Expected: rule 1 reports 17, rule 2 reports 35 (minus the sites already removed in Tasks 6 and 8), rule 3 reports every page title, rule 4 reports 17.
- [ ] **Step 2: Build** `./gradlew :app:assembleDebug -PtargetAbi=x86_64`; fix any compile errors the codemod introduced by hand.
- [ ] **Step 3: Verify** every existing page on the emulator (Settings, Theme, Logs, Scanner, Mode, Tunnel controls, Zero Trust, Widget controls and transport, Traffic monitor, Split tunnel and its apps list): Expected: same cold sky background, same glass back button position, sentence-case section labels with no tracked ALL-CAPS text, titles in the display face, unchanged content and behaviour.
- [ ] **Step 4: Delete the script and commit** `git rm -f scripts/halo_codemod.py 2>/dev/null; git add -A && git commit -m "ui: restyle existing pages with the Halo chrome"`

---

### Task 11: Verification matrix, polish, final build

**Files:**
- Modify: whatever the matrix finds; `README.md` (screenshot line only if one exists for the main screen)

- [ ] **Step 1: Start the emulator** `~/Android/Sdk/emulator/emulator -avd Medium_Phone_API_37.0 -no-window -no-audio -no-snapshot-save -gpu swiftshader_indirect &`, install `app-x86_64-debug.apk`, and capture the full state matrix with `preview_state`: five states on Halo, then the same five on Dynamic light and Dynamic dark (`adb shell cmd uimode night yes|no`). Save the screenshots under the session scratchpad and read each one.
- [ ] **Step 2: Layout stress (Review Focus 5)**: `adb shell wm size 360x640`, `adb shell settings put system font_scale 1.5`; capture connected and disconnected, plus one converted page with the keyboard open. Expected: no clipped text, headline and detail never overlap the orb, the panel scrolls or wraps instead of overflowing. Reset with `adb shell wm size reset` and `font_scale 1.0`.
- [ ] **Step 3: Recreation (Review Focus 4)**: start a real connection from the UI, then `adb shell cmd uimode night yes` and rotate with `adb shell settings put system user_rotation 1`; Expected: orb returns to Connected with the warm sky, no crash (`adb logcat -d | grep -E 'FATAL|AndroidRuntime'` is empty).
- [ ] **Step 4: Live connect and disconnect** end to end on the emulator (accept the VPN prompt on the throwaway emulator): stage text changes during connecting, the bloom plays, the IP row changes to the VPN IP, disconnect drains the warmth. Compare the status shown with `adb shell run-as studio.cluvex.aethery tail -5 files/aether-core.log`.
- [ ] **Step 5: Fix what the matrix found**, rebuilding and re-capturing only the affected screens.
- [ ] **Step 6: Final build of all ABIs and full unit-test run**
Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug` Expected: `BUILD SUCCESSFUL`; APKs for arm64-v8a, armeabi-v7a and x86_64 in `app/build/outputs/apk/debug/`; `zipalign -c -P 16 4` passes for each.
- [ ] **Step 7: Shut the emulator down** (`adb emu kill`) and commit `git add -A && git commit -m "ui: Halo redesign polish and verification fixes"`

---

## Self-review notes

- Spec coverage: palette and existing themes (Task 2), type (Task 2), main screen (Task 6), orb states, haptics, entrance, shader and fallback, accessibility and reduced motion (Tasks 3 and 5), page scaffold, every popup and the updater (Tasks 7 to 9), existing pages (Task 10), architecture files (`HaloTheme` is split across `AppAppearance`, `HaloColors`, `HaloType`, `GlassPanel`; `Pages.kt` is split into `PageStack`, `PageChrome`, `ChoicePage`, `EditorPage`, `UpdatePage`), dependency and font handling (Tasks 1 and 2), testing matrix (Task 11).
- Type consistency: `HaloOrb.State` mirrors the old `ConnectionControl.State` names so Task 5's rename is mechanical; `OrbState` in the model is mapped from it inside `HaloOrb`; `PageStack` is the single owner of pushed pages and `handleBack` consults it first.
- No tests exist for the Views themselves; the pure model, maths, stack and editor logic carry the JVM tests, and every View task has an emulator verification step with an expected visual result.
