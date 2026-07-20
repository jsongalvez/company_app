# ADR-0012: Domain exception hierarchy for service-layer error signaling

**Status:** Accepted
**Date:** 2026-07-17

## Context

The service layer previously threw Javalin HTTP exceptions (`BadRequestResponse`, `NotFoundResponse`,
`ConflictResponse`, `ForbiddenResponse`) directly. This coupled business logic to the web framework:
if the app ever needed a different transport (gRPC, CLI, event-driven), the service layer could not
be reused without modification.

The documented standard (`backend/AGENTS.md`) explicitly prescribed this pattern:
> Signal HTTP errors by throwing Javalin's built-in response exceptions from the service layer.
> There is no custom exception hierarchy.

## Decision

Introduce a domain-specific exception hierarchy in `com.companyb.companyapp.exception`:

| Exception | HTTP Status | Replaces |
|---|---|---|
| `ValidationException` | 400 | `BadRequestResponse` |
| `NotFoundException` | 404 | `NotFoundResponse` |
| `ConflictException` | 409 | `ConflictResponse` |
| `ForbiddenException` | 403 | `ForbiddenResponse` |

All exceptions are `open class` extending `RuntimeException`, allowing future subclassing if needed
(e.g., `InsufficientStockException : ValidationException`).

A centralized Javalin exception handler in `Main.kt` (`registerExceptionHandlers`) maps each domain
exception to its HTTP status code via `config.routes.exception()`. This is the single point in the
codebase where domain→HTTP mapping lives.

Route handlers in `api/routes/` may still throw Javalin HTTP exceptions for request-validation
concerns (e.g., missing query params), but the mapping for business-logic errors is centralized.

## Consequences

**Positive:**
- Service layer is now transport-agnostic — it throws domain exceptions with no knowledge of HTTP
- Exception→HTTP mapping is in one place, not scattered across 29 service files
- Route handlers remain thin; no route files were modified
- Tests assert domain exceptions instead of Javalin exceptions, making them transport-agnostic too

**Negative:**
- One extra level of indirection: services throw domain exceptions → handler maps to HTTP codes.
  Previously, services threw HTTP exceptions directly.

## Alternatives considered

1. **Keep Javalin exceptions (status quo)** — simplest, but couples business logic to Javalin.
   Rejected: the coupling was flagged as a concern in CR-036.

2. **Sealed class hierarchy with Result return types** — services return `Result<T, Error>` instead
   of throwing. Rejected: would require rewriting every service method signature and every caller;
   the exception-throwing pattern is idiomatic Kotlin and already familiar to the team.

3. **Only replace BadRequest/NotFound/Conflict, leave Forbidden** — would leave some service-layer
   coupling. Rejected: `ForbiddenResponse` was thrown in 5 service files for authorization failures;
   replacing it was necessary for full decoupling. `InternalServerErrorResponse` was replaced with
   Kotlin's `IllegalStateException` (standard library, no Javalin dependency), since these represent
   unexpected system-state errors (missing seed data, failed write-backs) rather than business-logic
   decisions.
