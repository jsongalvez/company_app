package com.companyb.companyapp.proto.searchonly

// #855 — search-only query routing. One box, two hit kinds: instant commands
// (verb → run) and object jumps (match → open detail). No nav chrome anywhere.

data class SoHit(
    val group: String,
    val glyph: String,
    val title: String,
    val sub: String,
    val tone: SoTone,
    val sel: SoSel,
    val runNow: Boolean = false,
)

private fun cmd(
    title: String,
    sub: String,
    sel: SoSel,
    glyph: String = "⌁",
    tone: SoTone = SoTone.LIME,
): SoHit = SoHit("COMMANDS", glyph, title, sub, tone, sel, runNow = true)

fun soSearch(repo: SearchOnlyFakeRepo, raw: String): List<SoHit> {
    val q = raw.trim().lowercase()
    val out = mutableListOf<SoHit>()
    val loggedIn = repo.actor != null
    val locked = repo.actor?.role == SoRole.ONBOARDING

    fun has(vararg words: String): Boolean = words.any { q.contains(it) }

    // ——— command catalog (filtered by query; state-aware) ———
    val catalog = mutableListOf<SoHit>()
    if (!loggedIn) {
        repo.users.forEach { u ->
            catalog += SoHit(
                "COMMANDS", "⛉", "Login as ${u.name}", "${u.role.label} · home ${u.homeBranch}",
                if (u.role == SoRole.ONBOARDING) SoTone.AMBER else SoTone.LIME,
                SoSel("login", u.id), runNow = true,
            )
        }
    } else {
        if (!repo.clockedIn) catalog += cmd("Clock in", "${repo.actorName()} · ${repo.currentBranch.name}", SoSel("clockin"))
        else catalog += cmd("Clock out", "${repo.actorName()} · end this stint", SoSel("clockout"))
        catalog += SoHit("COMMANDS", "◈", "Switch branch", "QC Central · Laguna Tour Stop 3 · Tondo Medical Mission", SoTone.CYAN, SoSel("branches"))
        catalog += SoHit("COMMANDS", "＋", "Book walk-in session", "new PENDING session · cash client at this branch", SoTone.LIME, SoSel("walkin"))
        catalog += SoHit("COMMANDS", "◉", "Clock & relief", "relief invites · cover asks · grant / withdraw", SoTone.CYAN, SoSel("home"))
        catalog += SoHit("COMMANDS", "₱", "Finance & remittance", "SESSION + PRODUCT drafts · submit · snapshot · Undo 48h", SoTone.VIOLET, SoSel("finance"))
        catalog += SoHit("COMMANDS", "⛉", "Team & roles", "practitioners · coordinators · grant ONBOARDING", SoTone.VIOLET, SoSel("team"))
        catalog += SoHit("COMMANDS", "✉", "Notifications", "${repo.notices.count { !it.read }} unread · read / unread mailbox", SoTone.AMBER, SoSel("notices"))
        catalog += SoHit("COMMANDS", "≣", "Audit log", "${repo.audits.size} entries · who did what", SoTone.GREY, SoSel("audit"))
        catalog += SoHit("COMMANDS", "☺", "Profile & logout", "${repo.actorName()} · clock-out · sign out", SoTone.GREY, SoSel("profile"))
        if (locked) {
            catalog += SoHit("COMMANDS", "⚿", "Unlock onboarding", "zero capabilities → grant Practitioner via MANAGE_USERS", SoTone.RED, SoSel("onboarding"))
        }
        if (has("grant", "unlock", "onboard") && repo.users.any { it.role == SoRole.ONBOARDING }) {
            catalog += SoHit("COMMANDS", "⚿", "Grant Practitioner to onboarding user", "MANAGE_USERS · unlocks the branch", SoTone.RED, SoSel("onboarding"))
        }
        if (has("remit", "submit", "draft", "undo", "snapshot", "₱", "finance", "money")) {
            catalog += SoHit("COMMANDS", "₱", "Open finance & remittance", "draft → submit → snapshot → Undo 48h", SoTone.VIOLET, SoSel("finance"))
        }
        if (has("void", "unvoid")) {
            repo.sessions.filter { it.status == SoSessionStatus.PENDING }.forEach { s ->
                catalog += SoHit(
                    "COMMANDS", "⊘", if (s.voided) "Unvoid ${s.id}" else "Void ${s.id}",
                    "${s.client} · reason recorded in audit", SoTone.RED, SoSel("session", s.id),
                )
            }
        }
        if (has("read", "unread", "notif", "mail", "✉")) {
            catalog += SoHit("COMMANDS", "✉", "Mark all notifications read", "mailbox sweep", SoTone.AMBER, SoSel("notices"))
        }
        if (has("logout", "sign out", "exit")) catalog += cmd("Logout", "back to the login box", SoSel("logout"), "⏻", SoTone.GREY)
        if (has("day", "advance", "open", "past", "remit-day", "04:00", "boundary")) {
            catalog += SoHit(
                "COMMANDS", "◐", "Advance branch day", "now ${repo.dayState.label} · boundary 04:00 Asia/Manila",
                SoTone.CYAN, SoSel("branches"),
            )
        }
    }
    out += if (q.isBlank()) catalog else catalog.filter {
        it.title.lowercase().contains(q) || it.sub.lowercase().contains(q) ||
            q.split(" ").filter { w -> w.length > 1 }.any { w -> it.title.lowercase().contains(w) }
    }

    // ——— object jumps ———
    if (loggedIn) {
        fun match(vararg bits: String): Boolean {
            if (q.isBlank()) return true
            val hay = bits.joinToString(" ").lowercase()
            return q.split(" ").filter { it.length > 1 }.all { hay.contains(it) }
        }
        repo.sessions.filter { match(it.id, it.client, it.service, it.status.label, it.branch) }.forEach { s ->
            out += SoHit(
                "SESSIONS", if (s.walkIn) "⚑" else "◉", "${s.id} · ${s.client}", "${s.service} · ${s.status.label}${if (s.voided) " · VOID" else ""}",
                when (s.status) {
                    SoSessionStatus.PENDING -> SoTone.LIME
                    SoSessionStatus.COMPLETED -> SoTone.GREEN
                    SoSessionStatus.NO_SHOW -> SoTone.AMBER
                    SoSessionStatus.CANCELLED -> SoTone.GREY
                },
                SoSel("session", s.id),
            )
        }
        repo.clients.filter { match(it.id, it.name) }.forEach { c ->
            out += SoHit(
                "CLIENTS", "☺", if (c.anonymized) "${c.id} · anonymized" else c.name,
                "${c.gender} · ${c.age} · ${repo.pendingCount(c.id)} pending · global record",
                SoTone.CYAN, SoSel("client", c.id),
            )
        }
        repo.users.filter { match(it.id, it.name, it.role.label) }.forEach { u ->
            out += SoHit(
                "PEOPLE", "⛉", u.name, "${u.role.label} · home ${u.homeBranch}${if (u.clockedIn) " · clocked in" else ""}",
                if (u.role == SoRole.ONBOARDING) SoTone.RED else SoTone.VIOLET, SoSel("team", u.id),
            )
        }
        repo.branches.filter { match(it.id, it.name, it.kind) }.forEach { b ->
            out += SoHit(
                "BRANCHES", "◈", b.name, "${b.kind} · ${b.dayDate} · ${repo.dayState.label}",
                SoTone.CYAN, SoSel("branches", b.id),
            )
        }
        if (q.isBlank() || has("remit", "r-", "snap", "₱", "finance")) {
            repo.remittances.forEach { r ->
                if (q.isBlank() || match(r.id, r.kind.label, r.state.label)) {
                    out += SoHit(
                        "MONEY", "₱", "${r.id} · ${r.kind.label} ${soPeso(r.amount)}",
                        "${r.branchDay} · ${r.state.label}${r.snapshotId?.let { " · $it" } ?: ""}",
                        when (r.state) {
                            SoRemitState.DRAFT -> SoTone.AMBER
                            SoRemitState.SUBMITTED -> SoTone.GREEN
                            SoRemitState.UNDONE -> SoTone.GREY
                        },
                        SoSel("finance", r.id),
                    )
                }
            }
        }
    }
    return out.take(40)
}

// Suggested verbs shown under the bar when the query is empty.
fun soVerbs(repo: SearchOnlyFakeRepo): List<String> {
    return if (repo.actor == null) {
        listOf("login", "sam", "ann", "joy", "mia", "rob")
    } else {
        buildList {
            add(if (!repo.clockedIn) "clock in" else "clock out")
            add("book walk-in")
            add("s-101")
            add("remit")
            add("void")
            add("team")
            add("notifications")
            add("audit")
            add("switch branch")
            add("logout")
        }
    }
}
