# STATE

## Goal
Retheme Octo Jotter to the "Inkwell" warm cream-paper design (from the Figma Make
export the user supplied): amber-on-cream palette, serif titles, monospace-friendly
editor. Implemented at the Material theme layer so it propagates across all screens.

## Now
DONE — Inkwell theme shipped at the theme layer (Color.kt / Theme.kt / Type.kt) plus a
monospace markdown editor body in NoteApp.kt. Verified on Pixel_7 emulator across home,
settings, editor, preview: cream bg, serif Baskerville titles, amber accents, mono body.

## Next
- Optional follow-ups: bundle real Libre Baskerville / JetBrains Mono TTFs under res/font
  for a pixel-exact match (currently system Serif/Monospace stand in).
- Optional: version bump + signed release if the user wants to ship this.

## Constraints
(none stated by user beyond "implement this design")

## Design source
scratchpad/design/ — Inkwell palette in src/index.css @theme block:
cream #F5F0E8 / cream-dark #EBE5D8 / cream-darker #DDD6C8 / ink #1C1814 /
ink-light #4A443C / ink-faint #8C8278 / amber #8B6E3C / amber-light #B8924F /
border #CECABF. Editor paper #FEFCF9, toolbar #F0EBE2, dark drawer #1C1814,
drawer active amber text #D4AB6E, saved-dot green #7A9B7A.
