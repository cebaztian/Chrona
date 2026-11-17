# Chrona — Design System (for Stitch)

Chrona is an ad-free Android alarm clock. To stop an alarm the user must complete a mission: solve
math problems, shake the phone, or walk a number of steps. The app is used at night and at dawn by
someone half asleep: calm, dark, precise, never glaring. Mobile only, portrait, **dark theme only**.

## 1. Visual theme

Premium, quiet and tactile, like a machined instrument at night. The base is a very dark teal; sage
is used for lines and secondary type; a single saturated orange marks the one action that matters
on each screen ("wake up"). Generous breathing room, but dense enough to scan a list of alarms at a
glance. No glassmorphism, no blur, no grain, no gradients, no illustrations, no photos.

## 2. Color palette and roles (60-30-10)

| Role | Hex | Use |
|---|---|---|
| Main (background, 60 %) | `#16292B` | Screen background |
| Surface low | `#1E3133` | Card outer shell |
| Surface | `#243738` | Card inner core, sheets, dialogs |
| Surface high | `#2B3E3E` | Pressed cards, numpad keys, selected chips |
| Surface highest | `#344747` | Switch track (off), progress track |
| Second (30 %) | `#9FB3AB` | Secondary text, icons, outlines, labels |
| On surface | `#E6EEEB` | Primary text (off-white) |
| Accent (10 %) | `#FF8A1F` | The single primary action per screen, enabled switches, progress, key numbers |
| On accent | `#16292B` | Text and icons on orange (never white) |
| Hairline | `#4D605E` | Decorative dividers and card hairlines only |
| Error | `#FFB4AB` | Errors, always with an icon |

Hard rules:
- Never place orange on sage or sage on orange (their contrast is 1.07).
- Text on an orange fill is `#16292B`, never white.
- At most one orange-filled action per screen; switches and progress may also be orange.
- Status is never color alone: always add an icon or a label.

## 3. Typography

- Family: **Plus Jakarta Sans** (provisional). Every time and counter uses **tabular figures**.
- Ringing screen time: 88 sp, light weight.
- Alarm time in the list: 48 sp, regular.
- Mission counter: 48 sp, medium, orange.
- Math problem: 32 sp, medium.
- Screen title: 22 sp.
- Body: 16 sp.
- Labels and eyebrows: 12–14 sp, sage.
- **Eyebrow tags:** small uppercase pill labels with wide letter spacing above sections, for example
  "PRÓXIMA ALARMA" or "MISIÓN".

## 4. Components

- **Double-bezel card.** An outer shell (`#1E3133`, hairline `#4D605E`, 6 dp padding, 28 dp radius)
  holds an inner core (`#243738`, 22 dp radius, a subtle inner top highlight). Use it for alarm
  cards, health items and the mission panel.
- **Primary CTA.** A fully rounded orange pill with `#16292B` text. Its trailing icon sits inside its
  own small circular wrapper, flush right ("button-in-button").
- **Secondary button.** A rounded pill outlined in sage, with sage text.
- **Switch.** Orange track with a `#16292B` thumb when on; `#344747` track with a sage outline when
  off.
- **Chips** (repeat days L M X J V S D). Selected: `#2B3E3E` fill with off-white text. Unselected:
  sage outline.
- **Icons:** thin-line, precise, 1.5 px stroke (Phosphor Light style). On ringing and mission
  screens, use one weight heavier for readability. Never Material filled icons.
- **Shadows:** none, or extremely soft and diffused. Depth comes from tonal surfaces.

## 5. Layout and spacing

- 4 dp grid, 20 dp screen margins, 12 dp between cards.
- The alarm list must show at least 4 alarms on a tall phone (1440 × 3120) without scrolling.
- Touch targets are at least 48 dp; on the ringing and mission screens, at least 64 dp, with 8 dp
  gaps.
- No tab bar. The top area holds a large title or the next-alarm summary. Floating action button
  bottom-right on the list.

## 6. Language

All UI copy is in **Spanish** (for example "Alarmas", "Posponer", "Iniciar misión", "Resuelve para
detener").
