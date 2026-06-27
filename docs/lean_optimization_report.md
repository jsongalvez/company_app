# Lean Optimization Report: Authentication System

This report details the architectural and mechanical optimizations applied to the `company-app` authentication and rate-limiting subsystems to eliminate waste and prevent system instability.

## 1. RateLimiter: Solving the Unbounded State (Memory Leak)

### The Problem
Previously, `RateLimiter` used a `ConcurrentHashMap` to store IP-based request counts. This map grew indefinitely with every unique IP address that accessed the server. On the open internet, this is an "unbounded state" violation—a memory leak that eventually leads to a JVM crash (`OutOfMemoryError`).

### The Solution: LruCache Pattern
We replaced the map with a synchronized `LinkedHashMap` configured with an LRU (Least Recently Used) eviction policy.
- **Max Entries:** The cache is now capped at 1,000 entries.
- **Eviction:** When the 1,001st unique IP arrives, the least recently seen IP is dropped from memory.
- **Thread Safety:** We used `Collections.synchronizedMap` and a `synchronized(list)` block to ensure that the "Read-Modify-Write" cycle (checking the window and incrementing the count) is atomic.

**Impact:** Memory usage is now constant and predictable, regardless of traffic volume.

---

## 2. JwtService: Mechanical Sympathy & Allocation Reduction

### The Problem
On every single request, `JwtService.verifyToken` was calling `JWT.require(algorithm).build()`. This instructed the library to re-allocate and re-configure a `JWTVerifier` object every time. 

### The Solution: Component Reuse
The `JWTVerifier` is thread-safe and depends only on static configuration (Secret, Issuer, Audience). 
- We moved the verifier to a `private val` property within the `JwtService` singleton.
- It is now instantiated exactly **once** when the application starts.

**Impact:** Reduced CPU cycles and GC (Garbage Collection) pressure on the authentication hot-path.

---

## 3. AuthService: Pre-calculating Security Overhead

### The Problem
To prevent timing attacks (where an attacker can guess usernames by how fast the server responds), we use a "dummy hash" when a user isn't found. Previously, we were storing a raw string and running a full `BCrypt.verify` on it.

### The Solution: Eager Initialization of Constant State
- The `dummyHash` is now pre-calculated once during the singleton's initialization.
- We standardized the "cost" factor (12) to ensure the hardware performs the same amount of work for both real and dummy users.

**Impact:** Constant-time security is maintained without redundant string-to-hash conversions on every failed login.

---

## 4. Global: Hot-Path Log Pruning

### The Problem
The codebase was "chatty," logging successful events (e.g., "[RATE-LIMITER] Request count: 5", "[VERIFY-TOKEN] Verifying token") on every request. Logging involves string formatting and disk I/O, which are significantly slower than memory operations.

### The Solution: The Principle of Refusal
- **Silence on Success:** Removed `info` level logs from the authentication hot-path.
- **Noisy on Failure:** Kept `warn` and `error` logs for rate-limiting triggers and invalid tokens.

**Impact:** Drastically reduced disk I/O and log noise. The hardware now spends its time on business logic rather than writing "Everything is fine" to a text file.

---

## 5. New Lean Review Findings (April 2026)

This section details recent findings that identify further opportunities for optimization across the backend and shared components.

### 🔴 Critical: Wasteful Random ID Generation
**Principle violated:** Mechanical Sympathy
**Observation:** `Helper.generateRandomId` (used in every request's `before` hook) is a textbook example of heap churn. It creates a `List<Char>` of 62 elements on every call, then maps a range to another `List<Char>` before finally joining it to a string. 
**Cost:** For every single request, the system is performing multiple allocations and iterations just to generate a trace ID.
**Proposed Fix:** Use a static `CharArray` or a constant string and a `StringBuilder` or `Random.nextBytes` approach to avoid intermediate list allocations. Make the function a top-level property or a singleton object.

### 🟡 Notable: Eager Heavy Computation in AuthService
**Principle violated:** The Principle of Refusal
**Observation:** `AuthService` eagerly computes `dummyHash` using BCrypt (cost 12) at class initialization. While this provides timing protection, it forces the system to pay a heavy CPU tax as soon as the class is loaded, regardless of whether the login feature is actually used.
**Cost:** Significant latency in the startup path if `AuthService` is loaded during a cold start.
**Proposed Fix:** Use `by lazy` for `dummyHash` so the cost is only paid when the first login attempt occurs.

### 🟡 Notable: Regex-based String Masking
**Principle violated:** Mechanical Sympathy
**Observation:** `maskUUID` uses `Regex.replace` with a complex regex pattern on every logging call that involves a UUID. This is unnecessary overhead for a known string format.
**Cost:** CPU cycles wasted on regex engine execution where simple indexing would suffice.
**Proposed Fix:** Use simple string manipulation (e.g., `take` and `takeLast`) after a basic length check.

### 🔵 Minor: Redundant Helper Instantiation
**Principle violated:** The Principle of Refusal
**Observation:** In `Main.kt`, `Helper().generateRandomId()` instantiates a new `Helper` object on every single request.
**Cost:** Minor heap allocation on every request.
**Proposed Fix:** Make `Helper` an `object` or move the function to a top-level utility.

### 🔵 Minor: Redundant Database Lookups
**Principle violated:** Batch Efficiency
**Observation:** `AuthService.register` method first calls `UserRepository.findByUsername` and then `UserRepository.createUser`.
**Cost:** Two round-trips to the database instead of one.
**Proposed Fix:** Rely on the database's unique constraint for the username. Attempt the insert and catch the "Unique constraint violation" to return `UsernameTaken`.

### 🔵 Minor: Eager JWT Configuration
**Principle violated:** The Principle of Refusal
**Observation:** `JwtService` uses `run { ... }` blocks to eagerly read environment variables and initialize the `Algorithm` and `Verifier` at class load time.
**Cost:** Premature allocation and computation for a component that might not be used in certain startup paths.
**Proposed Fix:** Use `by lazy` for the `algorithm`, `verifier`, `issuer`, and `audience` properties.

---

## Summary of Files Modified
- `backend/src/main/kotlin/com/companyb/companyapp/auth/RateLimiter.kt`
- `backend/src/main/kotlin/com/companyb/companyapp/auth/JwtService.kt`
- `backend/src/main/kotlin/com/companyb/companyapp/service/AuthService.kt`
- `backend/src/main/kotlin/com/companyb/companyapp/utils/Helper.kt` (Candidate for optimization)
- `backend/src/main/kotlin/com/companyb/companyapp/logging/LoggingExtensions.kt` (Candidate for optimization)
- `backend/src/main/kotlin/com/companyb/companyapp/Main.kt` (Candidate for optimization)
