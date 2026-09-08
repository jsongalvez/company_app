# Dead-code gate (map #529, ref #530; zero-debt since Phase C)

One reproducible command runs the same authoritative analysis CI uses:

```bash
./gradlew deadCodeCheck
```

## Files

| File | Lifetime | Semantics |
|---|---|---|
| `entry-points.txt` | Permanent by design | Narrow implicit entry points the gate cannot see used, same key format plus ` # reason`. Each line names its verified reason. |
| `known-unanalyzed.txt` | Permanent (usually empty) | Files the analyzer could not parse. The gate fails when the actual set differs. |

## Gate policy

A finding passes iff its key is in `entry-points.txt`.
The gate fails on any unlisted finding, on any stale line in `entry-points.txt`
(the declaration was deleted or became visible — remove the line), and on any
change to the unanalyzed-file set. Exit 0 clean, 1 gate failure, 2 tool error.

There is no baseline and no grandfathering: a newly introduced dead
declaration fails with no write-baseline path. The analyzer's former
`--baseline` / `--write-baseline` flags are rejected as unknown arguments.

## Scope

Candidates (reported when dead): `shared/commonMain`, `composeApp/commonMain`,
`composeApp/desktopMain`, `backend/main` — non-private, non-local Kotlin
functions, properties, classes, objects, typealiases, secondary constructors.

Consumers (references counted, never reported): `backend/test`, `backend/dev`,
`composeApp/commonTest`, `composeApp/desktopTest`, `shared/commonTest`.

`androidMain`/`iosMain` are excluded: no Android SDK is available locally and
analysis inputs must stay identical between local runs and CI (determinism).
Declarations used only from platform mains live in `entry-points.txt` with
their verified platform caller named. Moving a platform root into common code
retires its exemptions through the stale check.

## Analysis notes

The engine (`:detekt-rules`, `com.companyb.detekt.deadcode`) binds all inputs
in one Kotlin compiler invocation and matches references to declarations
through the binding — never through names or text. Liveness is a closure over
attributed references, so dead reference cycles and dead classes with dead
members report together. Overrides stay live while their base is live;
`expect`/`actual`, companions, enum entries, and `main` functions are roots.

Known narrow gaps (cleanup-time revalidation covers them): member extensions
invoked through `with`/`run`/`apply` dispatch scope; custom-delegate
`getValue`/`setValue`/`provideDelegate` used only through `by`; Java/JMH-only
usages. K1-frontend deprecation warnings are expected: the engine pins the
repo's own `kotlin-compiler-embeddable` version and migrates only if binding
breaks.

## Zero-debt policy (Phase C)

The migration baseline is deleted and stays deleted. Adding an unused
declaration fails the gate until the declaration is removed or a narrow
`entry-points.txt` line with a verified reason is added in the same reviewed
commit. Never grandfather new debt.
