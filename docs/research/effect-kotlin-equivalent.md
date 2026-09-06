# Effect-TS Equivalent in Kotlin

> Research snapshot (2026-09-04), moved from `docs/specs/` to `docs/research/`
> per map #533 #571: this is **research, not accepted product policy**. No
> dependency, pattern, or recommendation here is adopted until a tracking issue
> says so. Normative module rules live in the `AGENTS.md` routers,
> `docs/architecture.md`, and `docs/adr/`.

## Executive recommendation

Kotlin has no maintained one-to-one port of Effect-TS. The closest mainstream Kotlin
combination is **Arrow plus `kotlinx.coroutines`**, but it is a composition of focused
libraries rather than one replacement runtime:

| Effect concern | Kotlin choice |
|---|---|
| Lazy workflow and execution | `suspend` functions and structured `kotlinx.coroutines` scopes |
| Typed domain failures | Arrow `Either` or `Raise` where the error type matters at the call boundary |
| Resource finalization | `try/finally` for local cases; Arrow `Resource` for reusable compositional resources |
| Parallel work and cancellation | `kotlinx.coroutines` (`CoroutineScope`, `Job`, `Deferred`) |
| Retry and repeat policy | Arrow Resilience `Schedule` |
| DTO encoding/decoding | `kotlinx.serialization` |
| JVM metrics and tracing | Micrometer and OpenTelemetry, independently |
| JVM logging | `kotlin-logging` with SLF4J/Logback, independently |
| JVM configuration | Existing `dotenv-kotlin` plus `AppConfig`; use Hoplite or Lightbend Config only if configuration complexity justifies it |

This is the recommendation for CompanyApp: keep the current coroutine/serialization
foundation, add Arrow primitives only at seams that benefit from typed errors or resource
composition, and do not introduce a new all-in-one effect runtime. Keep JVM operational
libraries in the backend; keep `commonMain` portable.

## What Effect provides

Effect models a computation as `Effect<A, E, R>`: a lazy description whose success value is
`A`, expected failure is `E`, and required environment is `R` ([official Effect type
documentation][effect-type]). Its value is not merely an error wrapper or a coroutine
builder. Effect also supplies a coordinated set of runtime features:

- `Layer` describes and composes services for the environment ([Layers][effect-layers]).
- `Scope` and resource combinators guarantee finalization for scoped resources
  ([resource management][effect-resources]).
- Fibers provide independently controlled concurrent computations
  ([fibers][effect-fibers]); concurrency combinators cover common parallel workflows
  ([basic concurrency][effect-concurrency]).
- `Schedule` represents retry/repeat policies ([retrying][effect-retrying]).
- `ConfigProvider` supplies configuration to Effect programs ([configuration][effect-config]).
- Logging, metrics, and tracing are part of its observability model
  ([logging][effect-logging], [metrics][effect-metrics], [tracing][effect-tracing]).
- Schema handles typed schemas and transformations, including encoding/decoding and
  validation ([Schema introduction][effect-schema]).

Therefore, asking for an "Effect equivalent" has two possible meanings:

1. **Typed-error/resource/concurrency techniques:** Kotlin has good focused equivalents.
2. **One runtime that unifies `R`, `E`, `A`, dependency layers, fibers, scopes, schedules,
   configuration, and observability:** Kotlin has no established mainstream KMP equivalent.

## Feature comparison

| Capability | Effect-TS | Arrow | `kotlinx.coroutines` / Kotlin | CompanyApp fit |
|---|---|---|---|---|
| Computation model | Lazy `Effect<A, E, R>` value interpreted by an Effect runtime | Functional data types and builders such as `Either`/`Raise`; Arrow Fx adds coroutine-based functional utilities | `suspend` functions execute work; coroutine builders create and run jobs | Use coroutines as execution model. Do not wrap every service in an artificial effect value |
| Expected errors | `E` is part of the Effect type; expected failures are distinct from defects | `Either<E, A>` and `Raise<E>` make domain failures explicit | Exceptions are conventional and not encoded in a function's type; `Result` is stdlib but does not model a domain error type as directly as `Either` | Keep ordinary exceptions where existing code uses them. Use `Either`/`Raise` for reusable pure logic or boundaries needing exhaustive error handling |
| Environment / DI | `R` plus `Context` and `Layer` compose service dependencies | No direct equivalent to Effect's integrated `Layer` runtime | Constructor parameters, interfaces, and explicit scopes are idiomatic | Prefer existing constructor wiring. No DI runtime needed for this comparison |
| Resource safety | Scoped resources and acquire/release combinators | Arrow `Resource` composes acquisition and release and accounts for exceptions/cancellation ([resource safety][arrow-resource]) | `try/finally` and coroutine cancellation are the basic tools; `use` covers `Closeable` resources | Use `try/finally` or `use` locally; choose Arrow `Resource` only when resource lifetimes compose across functions |
| Structured concurrency | Effect fibers plus fiber supervision/join/interruption | Arrow Fx uses coroutines and supplies parallel combinators such as `parZip` | `CoroutineScope`, parent/child `Job`, `Deferred`, `async`, and `await` provide structured cancellation and joining ([coroutine basics][kotlin-coroutines], [coroutine API][coroutine-api]) | Existing coroutine model is native to all client targets and sufficient for ViewModels and API calls |
| Retry / repeat | `Schedule` is a first-class policy | Arrow Resilience has `Schedule` for retry/repeat policies ([Arrow retry and repeat][arrow-retry]) | Can be written with loops and delays, but policy composition is less focused | Add Arrow Resilience for non-trivial retry policy; do not build a local scheduler |
| Configuration | Effect `Config` and `ConfigProvider` participate in the environment | No direct counterpart in the cited Arrow modules | No standard typed application-config system | Backend already has `dotenv-kotlin` and manual typed `AppConfig.parse()`; leave configuration at backend boundary |
| Logging | Effect logger with structured levels, annotations, spans, and context | Not an all-in-one logging runtime | No logging implementation in coroutines | Keep `kotlin-logging`/SLF4J/Logback on JVM and existing `expect`/`actual` logging in Compose |
| Metrics | Effect metrics instruments and records metrics in its runtime | No direct all-in-one equivalent in the cited modules | No metrics implementation | Micrometer is the JVM metrics facade; use only if backend operational metrics are required |
| Tracing | Effect tracing spans and annotations integrate with its runtime | No direct all-in-one equivalent in the cited modules | No tracing implementation | OpenTelemetry is the JVM tracing/instrumentation choice; keep it separate from domain effects |
| Schema / serialization | Effect Schema combines schemas, validation, transformations, and codecs | Arrow is not the repository's serialization layer | `kotlinx.serialization` generates serializers through the compiler and supports JSON and other formats | Keep shared DTOs and wire formats on `kotlinx.serialization`; retain shared validation/domain types |

### The important semantic split

Effect's `R`, `E`, and `A` are visible in every `Effect` value. The recommended Kotlin design
puts those concerns in different, idiomatic places:

```text
Effect R  -> constructor parameters and interfaces
Effect E  -> Arrow Either/Raise where typed failure is useful
Effect A  -> ordinary return value (or Either's right side)
Effect runtime -> kotlinx.coroutines and its CoroutineScope/Job tree
Effect Layer -> explicit composition at application startup
Effect Scope -> try/finally, use, or Arrow Resource
Effect Schedule -> Arrow Resilience Schedule
Effect Schema -> kotlinx.serialization plus existing validation
```

This preserves the useful guarantees without forcing every function into a custom wrapper.
It also avoids confusing a `suspend` function with an Effect value: `suspend` describes a
function that may suspend; it does not by itself encode typed errors, dependency requirements,
retry policy, or resource scope.

## Candidate assessment

### Arrow: closest Kotlin analogue

Arrow is the strongest Kotlin answer when the requirement is functional error handling and
composable effects around existing coroutines. Its official documentation covers:

- `Either` and `Raise` for typed errors ([working with typed errors][arrow-typed-errors]);
- resource-safe acquisition and release ([resource safety][arrow-resource]);
- parallel coroutine-based composition ([parallelism][arrow-parallel]); and
- resilience schedules ([retry and repeat][arrow-retry]).

Arrow's current source layouts for Core, Fx Coroutines, and Resilience expose
`commonMain` source sets ([Core build][arrow-core-build], [Fx Coroutines build][arrow-fx-build],
[Resilience build][arrow-resilience-build]). That makes the relevant primitives plausible for
CompanyApp's shared module, subject to the target matrix of the exact version selected.

Arrow is **not** a drop-in replacement for Effect's integrated `Context`/`Layer` runtime,
fiber runtime, configuration service, observability stack, and Schema package. That is a
feature for this repository, not a gap to hide: CompanyApp already has separate transport,
serialization, logging, and backend infrastructure.

Use Arrow selectively:

```kotlin
// Shape only: choose this when callers need to distinguish domain failures.
fun validate(input: Input): Either<ValidationError, ValidInput>

// Shape only: Raise is useful when several typed failures compose in one workflow.
fun Raise<DomainError>.perform(input: Input): Output
```

The exact builder/API should follow the Arrow version adopted; the architectural choice is
typed failure at a meaningful seam, not wholesale conversion of existing services.

### `kotlinx.coroutines`: execution and concurrency foundation

Kotlin's coroutine guide defines structured concurrency around a scope and parent/child
relationships; cancellation propagates through the hierarchy ([official coroutine basics][kotlin-coroutines]).
The API provides `Job`, `Deferred`, `async`, and `await` ([official coroutine API][coroutine-api]).
The `kotlinx.coroutines` repository supports multiplatform targets ([official source][coroutines-source]).

Coroutines cover Effect's practical concurrency needs for this codebase:

- ViewModel work can remain in the existing lifecycle scope.
- Child jobs are cancelled with their parent.
- `Deferred`/`await` handles concurrent results.
- `supervisorScope` can isolate sibling failures where that behavior is intended.

They do not provide typed errors, dependency layers, retry schedules, or application
configuration. Add those concerns explicitly instead of treating coroutine cancellation and
exceptions as a complete Effect substitute.

### Arrow Resilience: focused schedules

Arrow Resilience is the direct candidate for Effect's schedule concern. Its documentation
describes retry/repeat policies, while its source is a KMP module with `commonMain`
([Arrow retry and repeat][arrow-retry], [Resilience build][arrow-resilience-build]). Use it
when backoff, limits, predicates, or composed retry policies are needed. For one simple
one-off retry, a small coroutine loop may remain clearer; do not add policy machinery without
a policy problem.

### Micrometer, OpenTelemetry, logging, and configuration: operational seams

These libraries solve operational concerns, not the computation-model concerns that make
Effect distinctive:

- **Micrometer Observation** uses an `ObservationRegistry` and handlers. Handlers react to
  an observation lifecycle and can create timers, spans, or logs ([official Observation
  introduction][micrometer-observation]). This is useful instrumentation, but it does not
  describe a typed, lazy application workflow or replace coroutine cancellation.
- **OpenTelemetry Java** supplies JVM APIs/SDK and Java instrumentation integration
  ([official Java documentation][otel-java]). It is the right kind of boundary for backend
  traces and context propagation, not a `commonMain` effect abstraction.
- **`kotlin-logging`** provides lazy Kotlin logging calls and its JVM artifact uses SLF4J;
  its own README labels Multiplatform support experimental ([official project source][kotlin-logging]).
  Logback is an SLF4J-backed JVM implementation ([Logback manual][logback]).
- **`dotenv-kotlin`** loads values from `.env` while allowing host environment values to
  override them ([official project source][dotenv]). CompanyApp's `AppConfig.parse()` then
  performs the typed required-value checks. Hoplite and Lightbend Config are optional JVM
  configuration alternatives, not shared-domain dependencies ([Hoplite source][hoplite],
  [Lightbend Config source][lightbend-config]).
- **`kotlinx.serialization`** uses compiler-generated serializers and supports Kotlin's
  multiplatform targets ([Kotlin documentation][serialization-docs], [official source][serialization-source]).
  It is a wire/schema tool, not an effect runtime; that narrower role is exactly what shared
  DTOs need.

### ZIO: conceptual peer, not Kotlin dependency

ZIO is the closest conceptual comparison because its core type is `ZIO[R, E, A]`, and its
ecosystem includes `ZLayer`, fibers, `Scope`, schedules, logging, metrics, and configuration
([ZIO core][zio-core], [ZLayer][zio-layer], [fibers][zio-fibers], [Scope][zio-scope],
[schedules][zio-schedule], [logging][zio-logging], [metrics][zio-metrics],
[configuration][zio-config]).

ZIO is a Scala library. Its official build is Scala/JVM-oriented ([ZIO source][zio-source]),
so it is not a Kotlin Multiplatform `commonMain` replacement and does not fit CompanyApp's
Kotlin/Javalin/Compose module boundaries. Use ZIO as a vocabulary/reference for runtime
design, not as an implementation dependency.

### Direct Kotlin ports

No maintained direct Effect-TS Kotlin port was identified in the official GitHub repository
search performed for this comparison ([repository search][effect-kotlin-search]). Search
results are discovery evidence, not a mathematical proof that no private or experimental
project exists.

`colomboe/KIO` was the notable Kotlin-shaped result, but its repository is archived and its
README directs users toward Arrow ([KIO repository][kio]). It should not be selected as a new
foundation. A direct port would also need to solve the KMP/JVM split, maintenance, compiler/API
compatibility, and integration with the repository's existing coroutine and serialization
contracts.

## KMP versus JVM boundary

The module boundary matters more than API similarity. CompanyApp's `shared` module targets
JVM, Android, and iOS, and its `commonMain` already owns serializable DTOs. The backend is a
JVM-only Javalin/Exposed application.

| Library / concern | `commonMain` / KMP position | JVM backend position |
|---|---|---|
| `kotlinx.coroutines` | Appropriate foundation; coroutine core APIs are multiplatform | Appropriate |
| `kotlinx.serialization` | Appropriate; Kotlin documents JVM, JS, and Native support and compiler-generated serializers ([Kotlin serialization][serialization-docs]) | Appropriate |
| Arrow Core / Fx / Resilience | Relevant modules expose `commonMain`; verify exact artifact targets before adoption ([Arrow source builds][arrow-core-build]) | Appropriate |
| Micrometer | Not a `commonMain` dependency; Micrometer Core is a Kotlin/JVM library targeting Java applications ([Micrometer source][micrometer-source]) | JVM metrics facade |
| OpenTelemetry Java | Not evidence for KMP; Java API/SDK/agent belong at JVM integration seams ([OpenTelemetry Java][otel-java]) | JVM instrumentation/tracing |
| `kotlin-logging` | Multiplatform support is documented as experimental; use existing platform abstraction rather than making it a shared contract ([kotlin-logging source][kotlin-logging]) | JVM facade over SLF4J |
| Logback | JVM logging backend using SLF4J ([Logback manual][logback]) | Backend and existing desktop target |
| `dotenv-kotlin` | Keep out of shared code; current repository uses it in backend startup/configuration ([dotenv source][dotenv]) | Existing environment loader |
| Hoplite / Lightbend Config | JVM configuration options, not needed in shared domain code ([Hoplite source][hoplite], [Lightbend Config source][lightbend-config]) | Optional alternatives to manual backend config |

Do not leak backend types into `shared`:

- no Micrometer, OpenTelemetry Java SDK, SLF4J/Logback, Javalin, JDBC, or HOCON types in
  `commonMain`;
- no backend environment loading from shared DTO/domain code;
- no JVM-only config object as a shared API;
- keep platform logging behind the repository's existing `expect`/`actual` functions.

`kotlinx.serialization` is the safe shared boundary: it supports generated serializers and
multiple Kotlin targets, and the repository already uses it in `shared/src/commonMain`.

### Existing repository evidence

- `shared/build.gradle.kts` puts `kotlinx.serialization.json` in `commonMain` and declares
  JVM, Android, and iOS targets.
- `composeApp/build.gradle.kts` keeps Ktor, JSON serialization, and Compose dependencies in
  `commonMain`; desktop adds Logback and JVM-specific client pieces. The shared logging seam
  is `composeApp/src/commonMain/kotlin/com/companyb/companyapp/util/Log.kt`, with platform
  implementations under `androidMain`, `desktopMain`, and `iosMain`.
- `composeApp/src/commonMain/kotlin/com/companyb/companyapp/viewmodel/ApiCallHandler.kt`
  already centralizes coroutine-backed API-call state and logging for ViewModels.
- `backend/build.gradle.kts` owns `kotlinx.serialization`, `kotlin-logging`, Logback, and
  `dotenv-kotlin` on the JVM backend.
- `backend/src/main/kotlin/com/companyb/companyapp/config/AppConfig.kt` loads dotenv values
  and validates required environment values with typed parsing.

## CompanyApp-specific recommendation

### Keep

1. **Shared:** `kotlinx.coroutines`, `kotlinx.serialization`, existing domain types, route
   constants, and validation.
2. **Compose:** existing ViewModel scopes and `ApiCallHandler`; it already centralizes API
   state and logging behavior.
3. **Backend:** Javalin/Exposed layering, constructor-wired services, `kotlin-logging` with
   Logback, and `dotenv-kotlin` plus typed `AppConfig.parse()`.

### Add only when a concrete requirement appears

- **Arrow Core:** typed `Either`/`Raise` for pure or shared workflows whose callers must
  distinguish domain failure cases. Do not replace every exception or `UiState`.
- **Arrow `Resource`:** a resource lifetime crosses function boundaries or several resources
  must release in a defined order. Otherwise use Kotlin `use`/`try/finally`.
- **Arrow Resilience:** more than a trivial retry needs a named, tested, composable policy.
- **Micrometer:** backend metrics need a vendor-neutral instrumentation facade.
- **OpenTelemetry:** backend traces or standardized context propagation are required.
- **Hoplite or Lightbend Config:** backend configuration grows beyond the current small,
  manually validated environment surface.

### Do not add

- a new all-in-one Effect runtime solely to reproduce Effect-TS's API shape;
- ZIO as a Kotlin/KMP dependency;
- archived KIO as a foundation;
- JVM observability or configuration libraries to `commonMain`;
- a second serialization/schema system for DTOs already covered by `kotlinx.serialization`.

### Adoption rule

Choose the smallest primitive that closes the actual gap:

```text
typed failure       -> Arrow Either/Raise
resource lifetime   -> use/try-finally, then Arrow Resource if composition warrants it
parallel work       -> kotlinx.coroutines structured scope
retry policy        -> Arrow Resilience Schedule
wire schema         -> kotlinx.serialization + shared validation
backend metrics     -> Micrometer
backend tracing     -> OpenTelemetry
backend logs        -> kotlin-logging + SLF4J/Logback
backend config      -> dotenv-kotlin + AppConfig, then a JVM config library if needed
```

This gives CompanyApp most of Effect's useful engineering properties while preserving
multiplatform portability, existing module boundaries, and current operational conventions.

## Sources

All external references below are official project documentation or source repositories.
Repository paths in the recommendation section are local primary sources.

[effect-type]: https://effect.website/docs/v3/getting-started/the-effect-type/
[effect-layers]: https://effect.website/docs/v3/requirements-management/layers/
[effect-resources]: https://effect.website/docs/v3/resource-management/introduction/
[effect-concurrency]: https://effect.website/docs/v3/concurrency/basic-concurrency/
[effect-fibers]: https://effect.website/docs/v3/concurrency/fibers/
[effect-retrying]: https://effect.website/docs/v3/error-management/retrying/
[effect-config]: https://effect.website/docs/v3/configuration/
[effect-logging]: https://effect.website/docs/v3/observability/logging/
[effect-metrics]: https://effect.website/docs/v3/observability/metrics/
[effect-tracing]: https://effect.website/docs/v3/observability/tracing/
[effect-schema]: https://effect.website/docs/v3/schema/introduction/

[arrow-typed-errors]: https://arrow-kt.io/learn/typed-errors/working-with-typed-errors/
[arrow-resource]: https://arrow-kt.io/learn/coroutines/resource-safety/
[arrow-parallel]: https://arrow-kt.io/learn/coroutines/parallel/
[arrow-retry]: https://arrow-kt.io/learn/resilience/retry-and-repeat/
[arrow-core-build]: https://raw.githubusercontent.com/arrow-kt/arrow/main/arrow-libs/core/arrow-core/build.gradle.kts
[arrow-fx-build]: https://raw.githubusercontent.com/arrow-kt/arrow/main/arrow-libs/fx/arrow-fx-coroutines/build.gradle.kts
[arrow-resilience-build]: https://raw.githubusercontent.com/arrow-kt/arrow/main/arrow-libs/resilience/arrow-resilience/build.gradle.kts

[kotlin-coroutines]: https://kotlinlang.org/docs/coroutines-basics.html
[coroutine-api]: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/
[coroutines-source]: https://github.com/Kotlin/kotlinx.coroutines

[micrometer-observation]: https://docs.micrometer.io/micrometer/reference/observation/introduction.html
[micrometer-source]: https://raw.githubusercontent.com/micrometer-metrics/micrometer/main/micrometer-core/build.gradle
[otel-java]: https://opentelemetry.io/docs/languages/java/
[kotlin-logging]: https://github.com/oshai/kotlin-logging
[logback]: https://logback.qos.ch/manual/introduction.html
[dotenv]: https://github.com/cdimascio/dotenv-kotlin
[hoplite]: https://github.com/sksamuel/hoplite
[lightbend-config]: https://github.com/lightbend/config
[serialization-docs]: https://kotlinlang.org/docs/serialization.html
[serialization-source]: https://github.com/Kotlin/kotlinx.serialization

[zio-core]: https://zio.dev/reference/core/zio
[zio-layer]: https://zio.dev/reference/contextual/zlayer
[zio-fibers]: https://zio.dev/reference/fiber
[zio-scope]: https://zio.dev/reference/resource/scope
[zio-schedule]: https://zio.dev/reference/schedule
[zio-logging]: https://zio.dev/reference/observability/logging/
[zio-metrics]: https://zio.dev/reference/observability/metrics/
[zio-config]: https://zio.dev/reference/configuration/
[zio-source]: https://raw.githubusercontent.com/zio/zio/series/2.x/build.sbt

[effect-kotlin-search]: https://api.github.com/search/repositories?q=effect-kt%20language:kotlin&per_page=30
[kio]: https://github.com/colomboe/KIO
