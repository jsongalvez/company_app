package com.companyb.companyapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * #726 — narrow SessionCreate exit-intent seam (not a global draft cache).
 *
 * The SessionCreate entry owns its draft (entry-scoped VM); the shell only needs to know
 * whether that entry is dirty and which destination the user originally chose, so a dirty
 * drawer/section exit can offer Keep editing / Discard and continue instead of silently
 * destroying the draft. Untouched entries navigate immediately; locked submissions keep
 * the stronger disabled rule (no prompt).
 *
 * Memory-only, entry-lifetime: cleared when the SessionCreate entry leaves composition.
 */
class SessionCreateExitGuard {
    val dirty: MutableState<Boolean> = mutableStateOf(false)
    val pendingRoute: MutableState<Route?> = mutableStateOf(null)
}

@Composable
internal fun rememberSessionCreateExitGuard(): SessionCreateExitGuard = remember { SessionCreateExitGuard() }

/**
 * #726 — pure shell-exit decision: intercept a drawer/section navigation only when the
 * current entry is the dirty SessionCreate flow and navigation is otherwise enabled.
 * Locked submissions (and client mutations) disable navigation outright — never prompt.
 */
fun shouldInterceptSessionCreateExit(
    current: Route?,
    dirty: Boolean,
    navigationEnabled: Boolean,
): Boolean = current is Route.SessionCreate && dirty && navigationEnabled

/**
 * #726 — drawer escape from the linked client profile pushed over a dirty intake.
 * The Profile push itself never prompts (it preserves the entry-scoped draft); but a
 * section switch from there would pop both entries via popUpTo(Dashboard) and destroy
 * the draft underneath. Intercept by popping back to the intake first — the retained
 * pendingRoute then offers the same Keep editing / Discard and continue decision there.
 * Previous-is-SessionCreate implies dirty by construction (the intake Profile affordance
 * requires a selected client, which alone marks the entry dirty per #674).
 */
fun shouldInterceptPushedDetailExit(
    current: Route?,
    previous: Route?,
    navigationEnabled: Boolean,
): Boolean = current is Route.ClientDetail && previous is Route.SessionCreate && navigationEnabled
