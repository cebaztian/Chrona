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

- **System font** (`FontFamily.Default`, SamsungOne on the target phone). No bundled font, no new
  dependency.
- **Tabular figures for every time and counter**
  (`fontFeatureSettings = "tnum"`), so digits don't jitter as a countdown or step count changes.

| Use | Style | Size / weight |
|---|---|---|
| Ringing screen time | `displayLarge` override | 88 sp, Light |
| Alarm time in the list | `displayMedium` | 48 sp, Normal |
| Mission counter (steps, shakes) | `displayMedium` | 48 sp, Medium, Accent |
| Math problem | `headlineLarge` | 32 sp, Medium |
| Screen titles | `titleLarge` | 22 sp |
| Body, settings | `bodyLarge` | 16 sp |
| Labels, repeat days | `labelLarge` | 14 sp, Second |

Text must survive the system font scale up to 200 %: no fixed-height text containers.

---

## 5. Shape, spacing and touch

- **Spacing:** a 4 dp grid; 16 dp screen margins; 12 dp between cards.
- **Corners:** cards 16 dp, buttons and chips fully rounded, sheets 28 dp top.
- **Touch targets:** at least 48 dp everywhere. On the **ringing and mission screens, at least
  64 dp**, with 8 dp gaps: the user is half asleep.
- **Numpad:** a 3 × 4 grid of 72 dp keys on `surfaceContainerHigh`; the confirm key is the screen's
  only Accent fill.

---

## 6. Screens

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

- Functional only: state changes (expand, toggle, progress) at about 200 ms with the standard M3
  easing.
- No decorative motion on the ringing screen; the only motion there is mission progress.
- Respect the system's "Remove animations" setting.

## 8. Launcher icon

An adaptive icon: Main background, Accent glyph (a minimal clock or sunrise mark). It includes a
monochrome layer for themed icons. Designed in M4.
