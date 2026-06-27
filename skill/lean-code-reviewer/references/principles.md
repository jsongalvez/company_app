# Lean Principles — Detection Patterns & Anti-Patterns

This reference file contains detailed detection guidance for each principle,
including Kotlin-specific patterns to watch for.

---

## 1. Mechanical Sympathy
**Core idea:** Code should work *with* the hardware and runtime, not fight it.
Understand the cost model of the platform you're running on.

**What to look for:**
- Allocating objects inside tight loops (GC pressure)
- Using the wrong data structure for the access pattern (e.g., List.contains() in a hot path instead of a Set)
- Ignoring thread/coroutine context — doing CPU-heavy work on the main/UI thread
- Boxing primitives unnecessarily (e.g., `List<Int>` instead of `IntArray` in hot paths)
- String concatenation in loops instead of StringBuilder

**Kotlin-specific:**
- `flow { }.collect { }` chains that do heavy work without `flowOn(Dispatchers.IO)`
- Using `List<Int>` where `IntArray` would avoid boxing
- Creating lambdas inside loops (each lambda is an allocation)

**Consequence if ignored:** Invisible CPU and GC overhead that accumulates. Users feel
it as sluggishness or battery drain, but can't point to why.

---

## 2. The Principle of Refusal
**Core idea:** Before doing any work, ask: does the user benefit from this *right now*?
If not, don't do it. Dave's rule: *"Rare code should be rare not just in usage, but in
cost."* If a feature is only needed sometimes, it shouldn't be in the base cost for
everyone, every time.

**What to look for:**
- Work done in `init {}` blocks or constructors that isn't needed until a specific action
- Loading full datasets when the caller only needs one item
- Running background tasks that produce results nobody has requested yet
- Formatting or serializing data "just in case" it's needed
- Fetching permissions, configs, or metadata before knowing if the feature will be used
- Importing/instantiating heavy dependencies at startup for features that are rarely used
  (the dependency is a roommate that eats your startup time whether the feature runs or not)

**The rare code rule:** If a feature is used rarely, its cost should be paid rarely.
This means not just deferring execution, but deferring the loading of the dependency
itself. A class that's only needed for an edge case shouldn't be in the call graph
of every normal startup path.

**Kotlin-specific:**
- Heavy work in `init {}` of a ViewModel or Repository
- Collecting a Flow in `init {}` before the UI has asked for it
- `runBlocking {}` at startup to pre-warm caches
- Injecting a heavy dependency into a class that only uses it in one rarely-triggered path

**Consequence if ignored:** Slower startup, wasted memory for data that may never be
displayed, and background threads consuming resources while the user waits for the UI.

---

## 3. Lazy Evaluation
**Core idea:** Delay computation until it is strictly necessary, and only compute
what is actually requested — not the whole dataset.

**What to look for:**
- Eagerly computing a full result when only a predicate/check was needed
- `map()` over an entire collection to find one element (use `first {}` or `find {}`)
- Pre-building UI strings, formatted dates, or display names for items that may never be shown
- Computing derived state on every update rather than on demand
- Loading entire paginated datasets when the user hasn't scrolled yet

**Kotlin-specific:**
- `val computed = list.map { expensiveTransform(it) }` at class level instead of `by lazy`
- Eager `.toList()` on a Sequence, killing laziness
- `.stateIn(scope, SharingStarted.Eagerly, ...)` when `WhileSubscribed` would suffice
- Building a full `UiState` object when only one field changed

**Consequence if ignored:** Users pay the cost of work they never see. Memory usage
grows with the dataset even when only a fraction is visible.

---

## 4. Batch Efficiency
**Core idea:** Avoid granular, one-at-a-time calls to expensive systems (database,
network, kernel, IPC). Gather what you need in one trip.

**What to look for:**
- Database queries inside `for` loops (the N+1 query problem)
- Making one network request per item in a list instead of a bulk endpoint
- Calling `notifyItemChanged()` in a loop instead of `notifyDataSetChanged()` or `DiffUtil`
- Repeated `SharedPreferences.edit().putX().apply()` calls in sequence
- Multiple `findViewById()` or `binding` lookups in a loop

**Kotlin-specific:**
- `list.forEach { repository.save(it) }` — save them in bulk
- Multiple `.emit()` calls in quick succession on a StateFlow without coalescing
- Chained `.also {}` / `.let {}` blocks that each make a separate IO call

**Consequence if ignored:** Syscall and IO overhead multiplies with data size. What
works fine at 10 items becomes unacceptable at 1000.

---

## 5. Synchronize, Don't Trash
**Core idea:** Don't destroy and rebuild when you can update what changed. Dave's words:
*"Synchronize. Don't trash. Update. Don't recreate. Respect continuity."* This is the
principle he said he almost wanted to tattoo on product teams.

**What to look for:**
- Clearing and rebuilding an entire list/view when one item changed
- Emitting a new copy of a full state object to change one property
- `notifyDataSetChanged()` when only one item changed (kills animations, causes flicker)
- Re-fetching a full API response to refresh one field
- Tearing down and recreating UI components that could be updated in place

**The dirty bit pattern (what to do instead):** Track exactly which fields changed —
not just "did anything change" but *what* changed. Only update the cells, rows, or
fields that have a change to report. Leave everything else alone.

**Kotlin-specific:**
- `_uiState.value = _uiState.value.copy(isLoading = true)` on every minor update
  triggers full recomposition in Compose or full observer re-notification in LiveData
- Not using `DiffUtil.ItemCallback` in RecyclerView adapters — you're bulldozing the list
- Replacing a `StateFlow<List<Item>>` entirely instead of emitting a targeted delta
- `adapter.notifyDataSetChanged()` in any non-initial-load context is a red flag

**Consequence if ignored:** UI flicker, lost scroll position, dropped animations,
and wasted GPU/CPU cycles re-drawing things that look identical. Users feel it as
jank even when they can't name it.

---

## 6. Graceful Degradation
**Core idea:** Software should scale its ambitions to match available resources.
When constrained, drop non-essentials and protect the critical path. Dave's Task Manager
would instantiate only two tabs under low memory — no charts, no nice-to-haves, just
what was needed to get the machine sorted out.

**What to look for:**
- No handling for low-memory conditions
- Caches with no eviction policy or size bound (unbounded maps used as caches)
- Loading high-resolution assets regardless of available memory
- No fallback when a network call fails — just crashing or showing nothing
- Assuming the coroutine scope will always be active
- Doing full work when the UI isn't even visible (e.g., animating offscreen content)

**The minimized window rule:** If the user can't see the output, don't produce it.
Skip repaints, skip formatting, skip graph updates when the window is minimized or
the component is offscreen. There is no prize for beautifully computing pixels no
human can see.

**Kotlin-specific:**
- `GlobalScope.launch` — ignores lifecycle, can't be cancelled under pressure
- Missing `catch` on IO operations in coroutines
- No error boundary in Flow chains
- Continuing to collect and process a Flow when the UI is in the background

**Consequence if ignored:** The app works fine in development (powerful device, fast
network, plenty of RAM) and degrades badly in the real world where users are on
constrained hardware with background apps competing for resources.

---

## 7. Verify, Don't Assume
**Core idea:** Don't trust that something is alive or healthy just because it appears
to exist. Dave's Task Manager didn't just check if a window handle existed — it sent
private messages and waited for a sane reply before concluding the instance was healthy.
A hung process still has a window. Existence is not liveness.

**What to look for:**
- Assuming a service/connection is healthy because it was healthy last time
- Checking for null but not checking for stale, invalid, or logically corrupt state
- Retry logic that retries blindly without verifying the underlying condition changed
- Caching a result and serving it without checking if the source has been invalidated
- Checking that a coroutine job `isActive` but not whether its output is still valid

**Kotlin-specific:**
- `if (job != null) job.cancel()` — a non-null job is not a running job
- Serving cached `StateFlow` values without a staleness check
- `runCatching {}` that catches exceptions but doesn't verify the happy-path result
  is actually meaningful
- Using `isInitialized` on a lazy property as a health check when the initialized
  value itself might be in a broken state

**Consequence if ignored:** Silent failures. The code thinks it's working because
the structure exists. The user sees wrong data, stale state, or a UI that claims
success when the underlying operation quietly failed.
