# PRD: Client Search Improvements

## Introduction

The client search is wired up (pg_trgm fuzzy matching on backend; 300ms debounced search bar on client) but feels clunky. Two bugs and several UX gaps make typing through a search feel like fighting the UI instead of flowing through it. The backend supports full fuzzy matching on first name, last name, and phone prefix — the client just isn't delivering that experience cleanly.

Key issues observed:
- **Cursor loss (fixed):** TextField was disabled during `UiState.Loading`, killing keyboard focus every time the debounce fired. Removed the `enabled` gating.
- **URL encoding broken:** `get("/api/clients?q=$query")` uses Kotlin string interpolation, which does not encode spaces or special characters. Multi-word queries (e.g., "John Smith", "de la Cruz") silently fail or return partial results because the URL is malformed.
- **Loading replaces all content:** When a search fires, the entire results area is replaced by a full-screen `CircularProgressIndicator`. Previous results vanish. The user sees a disorienting flash of nothing → spinner → new results.
- **Generic empty states:** "No clients found" and "Type a name or phone number to search" tell the user nothing about what they typed or what happened.

## Goals

- Multi-word and special-character queries work correctly via proper URL encoding
- Search feels continuous: previous results stay visible with a subtle loading cue, no full-screen spinner replacement
- Every state communicates context: idle shows "Start typing to search", error shows the query, empty shows "No results for '<query>'", success shows "N results for '<query>'"

## User Stories

### US-059: Fix URL encoding for multi-word queries
**Description:** As a user, I want to search for "John Smith" or "de la Cruz" and get results, so that multi-word names and names with special characters work correctly.

**Acceptance Criteria:**
- [ ] `ClientViewModel.search()` uses Ktor's `parameter("q", query)` or `URLBuilder.parameters.append()` instead of string interpolation
- [ ] Typing "John Smith" in the search bar produces the URL `/api/clients?q=John%20Smith`
- [ ] Typing "de la Cruz" returns results matching across first and last names
- [ ] Phone-only searches with dashes (`0917-555-1234`) still work
- [ ] Typecheck passes

### US-060: Keep previous results visible during loading
**Description:** As a user, I want to see my previous results while a new search loads, so that the UI doesn't flash blank between keystrokes.

**Acceptance Criteria:**
- [ ] When a new search fires (debounced), the previous search results stay on screen
- [ ] A subtle loading cue (small inline spinner or "Searching..." text) appears near the search bar, not replacing the content area
- [ ] Results update atomically when the new response arrives — no intermediate blank state
- [ ] If the user clears the search bar, results clear and idle state shows
- [ ] Typecheck passes

### US-061: Add result count and search term header
**Description:** As a user, I want to see how many results matched and what I searched for, so that I know my query was processed correctly.

**Acceptance Criteria:**
- [ ] When results arrive, show "N results for '<query>'" header above the results list
- [ ] When zero results, show "No results for '<query>'" with the actual query
- [ ] Header updates when new search results arrive
- [ ] Typecheck passes

### US-062: Improve idle and empty states
**Description:** As a user, I want meaningful prompts at every stage so that I know what the system expects and what happened.

**Acceptance Criteria:**
- [ ] Idle state (no query entered) shows "Start typing to search by name or phone" — more action-oriented than the current passive message
- [ ] Empty results state shows "No results for '<query>'" with suggestion to try different spelling
- [ ] Error state shows the query that failed so user doesn't retype it
- [ ] After the user clears the search bar, idle state reappears with results cleared
- [ ] Typecheck passes

### US-063: Add subtle inline loading indicator
**Description:** As a user, I want a loading cue that doesn't interrupt my view so that I know the search is processing without losing context.

**Acceptance Criteria:**
- [ ] During a search request, a small `CircularProgressIndicator` (18dp) or pulse animation appears inline next to the search bar — not a full-screen replacement
- [ ] Search bar text "Searching..." label or similar hint appears while request is in flight
- [ ] Indicator disappears when results (or empty/error) arrive
- [ ] Typecheck passes

## Functional Requirements

- FR-1: `ClientViewModel.search()` must use Ktor's type-safe query parameter API (`parameter()`, `URLBuilder`) instead of string interpolation, ensuring all characters are properly URL-encoded
- FR-2: The search results area must not be replaced or cleared during loading — previous results persist until the new response arrives
- FR-3: A result count and search term header must be displayed above the results list (e.g., "3 results for 'Smith'")
- FR-4: Idle state must show an action-oriented prompt: "Start typing to search by name or phone"
- FR-5: Empty results state must include the search query: "No results for 'Smith'. Try a different spelling."
- FR-6: Error state must include the search query that failed
- FR-7: Loading state must be indicated inline (near the search bar, small spinner) rather than replacing the content area

## Non-Goals

- No search suggestions or autocomplete dropdown
- No search history or recent searches
- No server-side result ranking changes (pg_trgm ordering is sufficient)
- No highlighting of matched text in results
- No pagination (current `SEARCH_LIMIT` from backend is adequate)
- No debounce tuning (300ms is fine)

## Design Considerations

- The search bar should never lose focus during typing — no state change should disable or re-focus it (already fixed in US-040 follow-up)
- Loading indicator should be in the search bar area, not replacing results — this is the single biggest UX win
- The UX pattern to follow: search bar at top (always enabled), results area below (never cleared during loading), status line between them showing result count or loading state
- Reference: Android Material 3 search bar pattern, Apple Spotlight search, any modern search that keeps results visible during refinement

## Technical Considerations

- Ktor 3.x `HttpRequestBuilder` supports `parameter(key, value)` which properly URL-encodes values — use this over string interpolation
- The `ApiClient` already sets a `defaultRequest { url(baseUrl) }` so relative paths like `/api/clients` work
- `UiState.Loading` should not be treated as "clear everything and show spinner" — it should be "keep what's visible and add a loading cue"
- The `LaunchedEffect(query)` with `delay(300)` pattern stays unchanged — debounce timing is not the issue
- Backend `ClientRepository.search()` already supports first name, last name, and phone prefix via `LIKE` with pg_trgm ILIKE — no backend changes needed

## Success Metrics

- User can type "John Smith" and get results matching either first or last name without losing cursor focus
- Results list does not flash blank between keystrokes
- User always knows what they searched for (query shown in result header and empty state)
