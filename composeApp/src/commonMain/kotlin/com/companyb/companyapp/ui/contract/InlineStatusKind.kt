package com.companyb.companyapp.ui.contract

/**
 * #670 — shared inline status: updating, last-updated/stale, failure + Retry, success.
 *
 * Never replaces a populated region for a background refresh and never steals focus:
 * the Retry control is reachable by keyboard but never auto-focused. Status always pairs
 * text (or a distinct icon + text) — never color alone — and success is a short nonmodal
 * note with no automatic navigation.
 *
 * No composed entrance animation is added; the UPDATING spinner is essential progress
 * indication (not decorative motion), so reduced motion has nothing to suppress here.
 */
enum class InlineStatusKind {
    UPDATING,
    STALE,
    FAILURE,
    SUCCESS,
    INFO,
}
