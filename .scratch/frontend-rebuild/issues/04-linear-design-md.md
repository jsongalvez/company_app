# 04 — Install Linear DESIGN.md + composeApp theme

**What to build:** Drop the Linear DESIGN.md into the project root and create a `LinearTheme` composable with the design tokens: dark canvas, surface ladder, lavender accent, Inter font, rounded corners scale, spacing scale. This is the visual foundation all screens build on.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `DESIGN.md` downloaded from `https://raw.githubusercontent.com/VoltAgent/awesome-design-md/main/design-md/linear.app/DESIGN.md` and placed in project root
- [ ] `LinearTheme.kt` in `composeApp/src/commonMain/.../ui/theme/` wrapping `MaterialTheme` with Linear color scheme:
  - Canvas: `#010102`, surface ladder: `#0f1011`/`#141516`/`#18191a`/`#191a1b`
  - Primary (lavender): `#5e6ad2`, on-primary: `#ffffff`
  - Ink: `#f7f8f8`, ink-muted: `#d0d6e0`, ink-subtle: `#8a8f98`
  - Hairline: `#23252a`
- [ ] `Inter` font added as dependency or bundled (system-ui fallback acceptable for desktop; Android bundling via font resource)
- [ ] `App.kt` wraps content in `LinearTheme { }`
- [ ] `:composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid` passes
