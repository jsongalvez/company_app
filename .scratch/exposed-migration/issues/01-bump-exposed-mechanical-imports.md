# EM-01 — Bump Exposed + mechanical import migration

**What to build:** Update the Exposed version from 0.61.0 to 1.3.1 in `gradle/libs.versions.toml`, then apply all mechanical import renames so the project compiles. JetBrains provides a Claude Code migration skill that handles ~90% of this automatically. The remaining ~10% are `SqlExpressionBuilder` lambda blocks where method calls now need top-level function imports (e.g. `eq`, `and`, `less`, `greaterEq`, `like`, `neq`, `count`, `plus`, `concat`, `Case`).

**Key blast radius:** ~512 import lines across ~100 files in `backend/`. Package renames: `org.jetbrains.exposed.sql.*` → `org.jetbrains.exposed.v1.*`. Top-level query functions (`selectAll`, `insert`, `update`, `deleteWhere`, `andWhere`, `exists`) move from `exposed-core` to `exposed-jdbc`. `Database` and `TransactionManager` also move to `exposed-jdbc`.

**Reference:** [Migration guide](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html) and [Claude Code skill](https://github.com/JetBrains/Exposed/tree/main/.claude/skills/migrate-to-1.0).

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Update `gradle/libs.versions.toml`: `exposed = "1.3.1"`
- [ ] Update `backend/build.gradle.kts` artifact coordinates if needed (`exposed-core` → new coordinates)
- [ ] Run Claude Code migration skill or apply mechanical import renames manually
- [ ] Fix `SqlExpressionBuilder` lambda blocks: add `import org.jetbrains.exposed.v1.core.*` (or individual imports) to every file with `where`, `having`, `update`, `Case`, `Op.build` usages
- [ ] Fix `ForUpdateOption` import paths (moved to vendor packages)
- [ ] Compilation passes: `./gradlew :backend:compileKotlin`
