# Map #180 Candidate: Remove Credential-Bearing HTTP Header Logging

## Question

Should client HTTP logging avoid `LogLevel.HEADERS` so Authorization bearer
tokens cannot enter desktop rolling logs or captured output?

## Scope

Change the existing Ktor logging configuration to a credential-safe level or
verified authorization-header sanitization. Preserve request lifecycle logs.
Add focused regression evidence that Authorization values are not emitted.

## Evidence

- `composeApp/src/commonMain/kotlin/com/companyb/companyapp/network/ApiClient.kt:59-61`
  enables `LogLevel.HEADERS`.
- `composeApp/src/desktopMain/resources/logback.xml:8-19` persists desktop logs.
- JWT handling is security-sensitive in `docs/architecture.md:247-253`.

## Verifier packet

- mode: structured
- model: GPT-5.6 Luna
- blind position: ALPHA
- L1 fact integrity: pass
- L2 domain coherence: pass with HARD credential-minimization breach
- L3 long-term architecture: pass; narrow existing client seam
- L4 adversarial falsification: pass; bearer leakage reaches files/capture sinks
- L5 comprehension: pass
- deterministic gate: fail; header logging remains enabled without sanitization
- HARD findings: zero after safe logging level or proven sanitizer
- SOFT findings: zero
- confidence: high
- artifact: Session 344 Compose audit; `ApiClient.kt:59-61`
