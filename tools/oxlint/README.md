# oxlint + anti-slop (vendored)

Vendored lint tooling for the repo's small JS surface (`scripts/gate-check.mjs` +
`tests/`). Kotlin modules stay under their own gates (ktlint/detekt); this covers the
scripts that tooling agents write.

- `oxlint.config.json` — enables the 15 `anti-slop` rules (error) + oxlint core.
- `tools/oxlint/anti-slop/` — vendored from `dmmulroy/anti-slop` (MIT; LICENSE ships
  alongside). Keep byte-identical to upstream `src/`; re-copy on upgrade.
- `package.json` — `lint:js` script runs `oxlint scripts tests/gates`.

**`@oxlint/binding-linux-arm64-gnu` is pinned on purpose**: npm 9 has an
optional-dependency bug that skips the native binding on aarch64 (oxlint then dies with
"Cannot find native binding"). The pin is the workaround for this VPS; on other hosts the
binding resolves via oxlint's own optional deps.

Run: `npm run lint:js` (needs `npm install` once).