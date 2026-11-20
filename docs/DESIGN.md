# Chrona — Design Guide

Visual rules for the Compose UI. Colors are tokens: code uses the theme (`MaterialTheme.colorScheme`),
never hex values in composables. Decision record: [ADR-020](ADR.md#adr-020--brand-palette-dark-only-no-dynamic-color).

---

## 1. Palette

| Role | Hex | HSL | Share | Character |
|---|---|---|---|---|
| **Main** | `#16292B` | 186°, 32 %, 13 % | 60 % | Very dark teal: night, calm, low glare |
| **Second** | `#9FB3AB` | 156°, 12 %, 66 % | 30 % | Muted sage: quiet, readable on Main |
| **Accent** | `#FF8A1F` | 29°, 100 %, 56 % | 10 % | Saturated orange: sunrise, "wake up", action |

**Reading.** Main and Second are low-saturation cool colors; the Accent is their warm complement and
the only saturated color. That maps well onto an alarm clock: a dark, nocturnal app that does not
dazzle at 6 a.m., where orange always means "this is what you do now". Because Main is nearly black,
the app is **dark only** (ADR-020): there is no light theme, whatever the system setting.

### Contrast (WCAG 2.x, computed)

| Pair | Ratio | Text (AA 4.5) | Large text / UI (3.0) |
|---|---|---|---|
| Main ↔ white | 15.15 | ✅ | ✅ |
| Main ↔ Second | 6.86 | ✅ | ✅ |
| Main ↔ Accent | 6.43 | ✅ | ✅ |
| Accent ↔ white | 2.36 | ❌ | ❌ |
| Second ↔ white | 2.21 | ❌ | ❌ |
| **Second ↔ Accent** | **1.07** | ❌ | ❌ |

### Rules that follow from the numbers

1. **Never put Accent on Second, or Second on Accent.** Their luminance is almost identical (1.07):
   only the hue tells them apart, which fails for color-blind users and in sunlight.
2. **Text on an Accent fill is Main, never white** (6.43 vs 2.36). Orange buttons have dark text.
3. **Accent and Second work as text on Main** (6.43 / 6.86): orange for actionable text, sage for
   secondary text.
4. **Accent and Second never work as text on light backgrounds** (2.36 / 2.21 on white). That is
   one more reason the app is dark only; it matters for any light element, such as a snackbar.
5. **Never rely on color alone.** Error red and the Accent are close in hue; every status also has an
   icon or a label.

---

## 2. The 60-30-10 split, applied

| Share | Dark theme | Where |
|---|---|---|
| **60 % Main** | Background and surfaces (Main, plus tonal steps of Main toward Second) | Screen backgrounds, alarm cards, sheets, ringing screen |
| **30 % Second** | Secondary text, icons, outlines, inactive controls, chips | Repeat days, labels, section headers, switch tracks off, editor fields |
| **10 % Accent** | The single primary action and "active" state per screen | FAB, "Start mission", enabled switch, next-alarm highlight, mission progress, focus ring |

The 30 % is mostly **lines and type**, not large sage fills. Big sage panels would glare at night and
break rule 1 next to any orange element.

**Accent discipline:** at most one Accent-filled **action** per screen (FAB, "Start mission", Save).
State indicators (enabled switches, progress) may repeat. If two actions compete for orange, one of
them isn't the primary action.

---

## 3. Tokens

Tonal surfaces are Main mixed toward Second by the percentage shown, so they stay in the same hue.

**Every role of `ColorScheme` is set explicitly.** Any role left out falls back to Material's
baseline purple, and some components read roles that are easy to forget. The FAB uses
`primaryContainer`; switch tracks use `surfaceContainerHighest`; the time picker uses `tertiary*`.
Verify these on screen in M2.

### Dark theme (reference)

| Role | Hex | Notes |
|---|---|---|
| `primary`, `primaryContainer` | `#FF8A1F` | Accent; ≥ 4.8 on every surface below |
| `onPrimary`, `onPrimaryContainer` | `#16292B` | Main on Accent: 6.43 |
| `inversePrimary` | `#A84F00` | Snackbar action on `inverseSurface`: 4.70 |
| `secondary`, `tertiary` | `#9FB3AB` | Second (the palette has no third hue) |
| `onSecondary`, `onTertiary` | `#16292B` | |
| `secondaryContainer`, `tertiaryContainer` | `#2B3E3E` | Selected chips, time-picker selection |
| `onSecondaryContainer`, `onTertiaryContainer` | `#E6EEEB` | |
| `background`, `surface`, `surfaceDim` | `#16292B` | Main |
| `onBackground`, `onSurface` | `#E6EEEB` | Off-white tinted toward Second; 12.8 on Main |
| `surfaceVariant` | `#2B3E3E` | |
| `onSurfaceVariant` | `#9FB3AB` | Second; ≥ 5.1 on every container up to `High` |
| `surfaceContainerLowest` | `#101F20` | Main darkened 25 % |
| `surfaceContainerLow` | `#1E3133` | Main + 6 % Second: alarm cards |
| `surfaceContainer` | `#243738` | Main + 10 %: sheets, dialogs |
| `surfaceContainerHigh` | `#2B3E3E` | Main + 15 %: pressed cards, numpad keys |
| `surfaceContainerHighest`, `surfaceBright` | `#344747` | Main + 22 %: switch track off |
| `surfaceTint` | `#9FB3AB` | Tonal elevation lightens toward Second, not orange |
| `inverseSurface` | `#E6EEEB` | Snackbars |
| `inverseOnSurface` | `#16292B` | |
| `outline` | `#9FB3AB` | Input borders, switch outline: component boundaries need ≥ 3:1 |
| `outlineVariant` | `#4D605E` | Decorative dividers only (2.3, not a boundary) |
| `error` | `#FFB4AB` | 8.9 on Main; always paired with an icon (rule 5) |
| `onError` | `#16292B` | |
| `errorContainer` | `#5C1A16` | |
| `onErrorContainer` | `#FFDAD6` | 10.1 on `errorContainer` |
| `scrim` | `#000000` | |

```kotlin
private val ChronaDark = darkColorScheme(
    primary = Color(0xFFFF8A1F), onPrimary = Color(0xFF16292B),
    primaryContainer = Color(0xFFFF8A1F), onPrimaryContainer = Color(0xFF16292B),
    inversePrimary = Color(0xFFA84F00),
    secondary = Color(0xFF9FB3AB), onSecondary = Color(0xFF16292B),
    secondaryContainer = Color(0xFF2B3E3E), onSecondaryContainer = Color(0xFFE6EEEB),
    tertiary = Color(0xFF9FB3AB), onTertiary = Color(0xFF16292B),
    tertiaryContainer = Color(0xFF2B3E3E), onTertiaryContainer = Color(0xFFE6EEEB),
    background = Color(0xFF16292B), onBackground = Color(0xFFE6EEEB),
    surface = Color(0xFF16292B), onSurface = Color(0xFFE6EEEB),
    surfaceVariant = Color(0xFF2B3E3E), onSurfaceVariant = Color(0xFF9FB3AB),
    surfaceTint = Color(0xFF9FB3AB),
    inverseSurface = Color(0xFFE6EEEB), inverseOnSurface = Color(0xFF16292B),
    error = Color(0xFFFFB4AB), onError = Color(0xFF16292B),
    errorContainer = Color(0xFF5C1A16), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9FB3AB), outlineVariant = Color(0xFF4D605E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF344747), surfaceDim = Color(0xFF16292B),
    surfaceContainerLowest = Color(0xFF101F20), surfaceContainerLow = Color(0xFF1E3133),
    surfaceContainer = Color(0xFF243738), surfaceContainerHigh = Color(0xFF2B3E3E),
    surfaceContainerHighest = Color(0xFF344747),
)
```

Dynamic color (Material You) is **off**: the wallpaper would replace the brand palette.

There is **no light theme** (ADR-020).

---

## 4. Typography

Visual direction: the **A+B hybrid** (ADR-024). The editorial serif gives the titles character,
and a geometric sans with tabular figures carries every number.

- **Fonts.** Two bundled OFL fonts in `res/font`. They are assets, not dependencies, and need no
  network.
  - **Newsreader** (variable serif): titles, labels, dates and body copy. Its figures are tabular by
    default.
  - **Plus Jakarta Sans** (variable sans): every time, counter and number input, always with
    `fontFeatureSettings = "tnum"`.
  - Both were checked from the font files on 2026-11-17: Newsreader's default digits share one width;
    Plus Jakarta's are proportional but it has the `tnum` feature.
- **No italics** for times, and 24-hour format without AM/PM.

| Use | Font | Size / weight |
|---|---|---|
| Ringing screen time | Plus Jakarta Sans, `tnum` | 88 sp, Light |
| Editor time | Plus Jakarta Sans, `tnum` | 64 sp, Light |
| Alarm time in the list | Plus Jakarta Sans, `tnum` | 48 sp, Light |
| Mission counter (steps, shakes) | Plus Jakarta Sans, `tnum` | 56 sp, Medium, Accent; the total ("/ 30") 24 sp, Regular, Second, on the same baseline |
| Math problem and answer | Plus Jakarta Sans, `tnum` | 32 sp, Medium |
| Screen titles | Newsreader | 28 sp, Regular |
| Onboarding headline | Newsreader | 32 sp, Regular |
| Alarm label on the ringing screen | Newsreader | 22 sp, Regular, Second |
| Body, settings rows, button text | Newsreader | 16–18 sp |
| Labels, repeat days, hints | Newsreader | 14 sp, Second |
| Eyebrow tags | Plus Jakarta Sans | 11 sp, SemiBold, uppercase, tracking 0.16 em |

- **No italics anywhere**, not only on times.
- **Icons:** Phosphor (MIT license). Light weight in general; Regular on the ringing and mission
  screens. Only the icons actually used are imported, as vector drawables, tinted Second at full
  alpha (some Stitch renders draw them fainter; the renders are not the reference for icon contrast).

Text must survive the system font scale up to 200 %: no fixed-height text containers.

---

## 5. Shape, spacing and touch

- **Spacing:** a 4 dp grid; 20 dp screen margins; 12 dp between cards. The alarm list still shows at
  least 4 alarms on the S25 Ultra without scrolling.
- **Corners:** double-bezel cards 28 dp outer / 22 dp inner; text fields 16 dp; numpad keys 20 dp;
  buttons, chips and tags fully rounded; sheets 28 dp top.
- **Touch targets:** at least 48 dp everywhere. On the **ringing and mission screens, at least
  64 dp**, with 8 dp gaps: the user is half asleep.
- **Numpad:** a 3 × 4 grid of 72 dp keys on `surfaceContainerHigh`, 8 dp gaps; the confirm key is the
  screen's only Accent fill.
- **Elevation:** tonal only. No shadows, blur, glow, gradients or grain.

### Components

| Component | Spec |
|---|---|
| **Double-bezel card** | Outer shell `surfaceContainerLow`, 1 dp border Second at 18 % alpha (decorative), 6 dp padding, 28 dp corners. Inner core `surfaceContainer`, 22 dp corners, a 1 dp top highlight `onSurface` at 6 %. Used for alarm cards, settings and health groups, the mission panel, permission lists. Rows inside are divided by `outlineVariant` at 50 %. |
| **Pill CTA** (the one Accent action) | Accent fill, Main text in Newsreader 18 sp Medium, 56 dp tall (64 dp on ringing and mission screens). A trailing 32 dp circle in Main at 12 % holds the icon (arrow or check), flush with the right padding. Pressed: scale 0.98. |
| **Secondary pill** | Transparent, 1 dp Second outline, Second text. "Snooze", "Configurar", "Probar misión". |
| **Disabled pill** (gated "Cambiar a matemáticas", ADR-019) | Dashed 1 dp Second at 40 %; lock icon and text in Second at 45 %, lock leading. One hint line below in Newsreader 14 sp ("Disponible tras 2 min sin pasos" / "… 30 s sin agitar"). |
| **Eyebrow tag** | Pill with 1 dp Second at 40 % outline, Plus Jakarta 11 sp SemiBold uppercase, tracking 0.16 em, Second. Above section groups and on mission screens ("MISIÓN · PASOS"). |
| **Chips** (repeat days, difficulty) | Selected `secondaryContainer` with `onSecondaryContainer`; unselected 1 dp Second outline with Second text. Never Accent. |
| **Switch** | On: Accent track, Main thumb. Off: `surfaceContainerHighest` track, Second outline and thumb. |
| **Progress ring** (shake, steps) | 8 dp stroke, Accent arc with round caps on a `surfaceContainerHigh` track; mission icon and counter centered inside. Math uses a 4 dp bar instead. |
| **Selected option** (mission picker) | 1.5 dp Accent outline and an Accent outline check; no Accent fill. |
| **Bottom sheet** | `surfaceContainer`, 28 dp top corners, sage drag handle; flat black scrim at 40 %, no blur. |
| **Floating action bar** (editor) | A detached pill bar in `surfaceContainerLow` with a hairline border, 16 dp from the screen edges, holding the Save pill CTA. |
| **Volume note** (mission screens) | Speaker-low icon + "Volumen reducido mientras …" in Newsreader 14 sp Second, at the bottom (PLAN §3.6). |

---

## 6. Screens

**Reference screens** (D2, approved direction): [`docs/design/final/`](design/final/) `01`–`11`,
generated in the Stitch project `12862449765537649811` (preview links have the form
`https://stitch.google.com/preview/12862449765537649811?node-id=<screen id>`; the final screen ids are
listed in [journal/D2.md](journal/D2.md), "D2 — Final screens"). They are a visual reference
only; M2 and M3 build them in Compose from this guide and do not convert the Stitch HTML. Where a
render and this guide disagree, this guide wins. The D1 structure screens in `docs/design/screens/`
are superseded.

Known render quirks, not to copy: faint icons on 06 and 10; the action pills on 06 render at 40 dp
(build them at ≥ 48 dp); settings shows "1 aviso" while the
health screen shows two issues, and two version formats ("1.0.0", "Chrona 1.0"). Counts and the
version come from real data.

**Alarm list.** Background Main; cards on `surfaceContainerLow`. Time in `onSurface`; label and
repeat days in Second. The enabled switch is the Accent; the disabled one has a Second outline. The
"rings in 7 h 12 min" header uses Accent text. The FAB is Accent with a Main icon. A ringing or
snoozed alarm shows a lock icon and "Solve to stop".

**Editor.** Fields outlined in Second; selected repeat days use `secondaryContainer`; the mission
selector shows the mission icon in Second and the selected option outlined in Accent. Save is the
only Accent fill.

**Ringing screen** (it appears in the dark and must not dazzle). Main background, huge time in `onSurface`, label in Second, "Start mission" as a full-width
Accent button with Main text, "Snooze" as an outlined Second button below it.

**Mission screens.** Progress (ring or bar) in Accent on a `surfaceContainerHigh` track; counter
numbers in Accent; instructions in Second. Wrong math answer: a short shake of the input plus the
error color *and* an icon (rule 5).

**Health screen.** The traffic light is never color alone:

| State | Color | Icon |
|---|---|---|
| OK | Second | check |
| Needs attention | Accent | warning triangle |
| Blocking | `error` | error circle |

---

## 7. Motion

- Functional only: state changes (expand, toggle, progress) at about 200 ms with
  `CubicBezierEasing(0.32f, 0.72f, 0f, 1f)`; nothing linear. Presses scale to 0.98 with
  `spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)`.
- No scroll-entry animations: lists appear instantly.
- No decorative motion on the ringing screen; the only motion there is mission progress.
- Respect the system's "Remove animations" setting.

### Haptics and list actions

- Every tappable goes through `hapticClickable` (Compose's `clickable` is silent): `ContextClick` for taps,
  `Confirm` for pill CTAs, FAB and the numpad confirm key, `VirtualKey` for numpad digits, `ToggleOn`/`ToggleOff`
  for switches and weekday chips, `SegmentFrequentTick` for the time picker and the volume slider, `Reject` for a
  wrong math answer, `LongPress` for a long press. They follow the system's touch-feedback setting.
- Alarm list: a long press on an unlocked alarm opens a menu (Duplicate, Select, Delete). Duplicate opens the
  editor on a copy that is a new alarm until saved. Select starts multi-select: the top bar shows the count, close and
  delete, taps toggle the check marks, system back exits, and one Undo restores every deleted alarm. Ringing and
  snoozed alarms are never selectable (ADR-016).

## 8. Launcher icon

An adaptive icon: Main background, Accent glyph (a minimal clock or sunrise mark). It includes a
monochrome layer for themed icons. Designed in M4.
