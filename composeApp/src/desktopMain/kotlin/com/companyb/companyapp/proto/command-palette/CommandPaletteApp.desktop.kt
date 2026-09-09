package com.companyb.companyapp.proto.commandpalette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CommandPaletteApp() {
    CpTheme {
        val store = remember { seedCpStore() }
        val user = store.currentUser()
        when {
            user == null -> CpLogin(store)
            user.role == CpRole.ONBOARDING -> CpOnboardingLocked(store, user)
            store.currentBranch() == null -> CpBranchSelect(store, user)
            else -> CpShell(store, user)
        }
    }
}

@Composable
private fun CpLogin(store: CpStore) {
    Box(
        Modifier.fillMaxSize().background(CpNight.Canvas).padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(560.dp)) {
            Text(
                "Compass",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = CpNight.Accent,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Sign in to the calm dark workspace",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = CpNight.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Fake users only — no network calls. Press Ctrl/⌘+K anywhere inside to jump.",
                fontSize = 14.sp,
                color = CpNight.Muted,
            )
            Spacer(Modifier.height(24.dp))
            store.users.forEach { user ->
                CpCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    onClick = {
                        store.currentUserId.value = user.id
                        store.currentBranchId.value = null
                        store.screen.value = CpScreen.HOME
                        store.audit(user.name, "SIGN_IN", "users:${user.id}", "Signed in as ${user.role.label}.")
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(user.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
                            Text(user.role.label, fontSize = 13.sp, color = CpNight.Muted)
                        }
                        CpChip(if (user.role == CpRole.ONBOARDING) "Locked" else "Sign in", CpNight.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun CpOnboardingLocked(
    store: CpStore,
    user: CpUser,
) {
    Box(
        Modifier.fillMaxSize().background(CpNight.Canvas).padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(560.dp)) {
            CpChip("ONBOARDING — locked", CpNight.Amber)
            Spacer(Modifier.height(14.dp))
            Text("Welcome, ${user.name}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CpNight.Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your account holds an empty capability bundle, so every surface stays locked " +
                    "until a manager grants a real role. This is the expected locked state, not an error.",
                fontSize = 14.sp,
                color = CpNight.Muted,
            )
            Spacer(Modifier.height(20.dp))
            CpCard(Modifier.fillMaxWidth()) {
                CpStatRow("Role", CpRole.ONBOARDING.label)
                CpStatRow("Capabilities", "none — bundle is empty")
                CpStatRow("Next step", "Ask a MANAGER for a role grant")
            }
            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick = {
                    store.audit(user.name, "SIGN_OUT", "users:${user.id}", "Signed out from locked onboarding screen.")
                    store.currentUserId.value = null
                },
            ) {
                Text("Sign out", color = CpNight.Accent)
            }
        }
    }
}

@Composable
private fun CpBranchSelect(
    store: CpStore,
    user: CpUser,
) {
    Box(
        Modifier.fillMaxSize().background(CpNight.Canvas).padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(640.dp)) {
            Text(
                "Pick a branch, ${user.name.split(" ").firstOrNull() ?: user.name}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = CpNight.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your home branch opens fully. Any other branch opens as relief duty with view-only access " +
                    "until a relief grant lands.",
                fontSize = 14.sp,
                color = CpNight.Muted,
            )
            Spacer(Modifier.height(22.dp))
            store.branches.forEach { branch ->
                val isHome = branch.id == user.homeBranchId
                val openDay = store.days.firstOrNull { it.branchId == branch.id && it.state == CpDayState.OPEN }
                CpCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    onClick = {
                        store.currentBranchId.value = branch.id
                        store.screen.value = CpScreen.HOME
                        store.selectedSessionId.value = null
                        store.selectedClientId.value = null
                        store.dayFilter.value = openDay?.date
                            ?: store.days.firstOrNull { it.branchId == branch.id }?.date
                            ?: store.dayFilter.value
                        store.reliefEdit.value = isHome
                        store.touchRecent(
                            CpRecent("Branch", branch.name, branch.kind.label, CpScreen.HOME, branch.id),
                        )
                        store.audit(
                            user.name,
                            "BRANCH_OPEN",
                            "branches:${branch.id}",
                            if (isHome) "Opened home branch." else "Clocked in as relief — view-only until grant.",
                        )
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(branch.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
                            Text(branch.kind.label, fontSize = 13.sp, color = CpNight.Muted)
                        }
                        if (openDay != null) CpChip(openDay.state.label, openDay.state.tint())
                        Spacer(Modifier.width(8.dp))
                        CpChip(if (isHome) "Home" else "Relief", if (isHome) CpNight.Accent else CpNight.Blue)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { store.currentUserId.value = null }) {
                Text("Back to sign in", color = CpNight.Muted)
            }
        }
    }
}

@Composable
private fun CpShell(
    store: CpStore,
    user: CpUser,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(CpNight.Canvas)
            .onPreviewKeyEvent { event ->
                handleCpGlobalKey(
                    store,
                    event.key,
                    event.type,
                    event.isCtrlPressed || event.isMetaPressed,
                    event.isAltPressed,
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            CpTopBar(store)
            CpDayBanner(store)
            Row(Modifier.weight(1f)) {
                CpSidebar(store, user)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (store.screen.value) {
                            CpScreen.HOME -> CpHomeView(store, user)
                            CpScreen.SESSIONS -> CpSessionsView(store, user)
                            CpScreen.CLIENTS -> CpClientsView(store, user)
                            CpScreen.FINANCE -> CpFinanceView(store, user)
                            CpScreen.TEAM -> CpTeamView(store, user)
                            CpScreen.MAIL -> CpMailView(store, user)
                            CpScreen.AUDIT -> CpAuditView(store)
                            CpScreen.PROFILE -> CpProfileView(store, user)
                        }
                    }
                }
            }
            CpStatusBar(store, user)
        }
    }
    if (store.paletteOpen.value) CpPaletteOverlay(store, user)
}

private fun handleCpGlobalKey(
    store: CpStore,
    key: Key,
    type: KeyEventType,
    mod: Boolean,
    alt: Boolean,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    if (mod && key == Key.K) {
        store.paletteOpen.value = !store.paletteOpen.value
        if (store.paletteOpen.value) {
            store.paletteQuery.value = ""
            store.paletteIndex.value = 0
        }
        return true
    }
    if (store.paletteOpen.value) return false
    if (alt) {
        val target =
            when (key) {
                Key.One -> CpScreen.HOME
                Key.Two -> CpScreen.SESSIONS
                Key.Three -> CpScreen.CLIENTS
                Key.Four -> CpScreen.FINANCE
                Key.Five -> CpScreen.TEAM
                Key.Six -> CpScreen.MAIL
                Key.Seven -> CpScreen.AUDIT
                Key.Eight -> CpScreen.PROFILE
                else -> null
            }
        if (target != null) {
            store.screen.value = target
            return true
        }
    }
    return false
}

@Composable
private fun CpTopBar(store: CpStore) {
    val branch = store.currentBranch()
    Row(
        Modifier
            .fillMaxWidth()
            .background(CpNight.Sidebar)
            .border(0.dp, Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Compass", fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = CpNight.Accent)
        Spacer(Modifier.width(20.dp))
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(CpNight.Panel)
                .border(1.dp, CpNight.Hairline, RoundedCornerShape(10.dp))
                .clickable {
                    store.paletteOpen.value = true
                    store.paletteQuery.value = ""
                    store.paletteIndex.value = 0
                }.padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Jump to a screen, session, client, or branch…",
                    fontSize = 14.sp,
                    color = CpNight.Faint,
                    modifier = Modifier.weight(1f),
                )
                CpKeyHint("Ctrl/⌘ K")
            }
        }
        Spacer(Modifier.width(16.dp))
        if (branch != null) {
            val branchDays = store.days.filter { it.branchId == branch.id }
            branchDays.forEach { day ->
                val selected = store.dayFilter.value == day.date
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) CpNight.Accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { store.dayFilter.value = day.date }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        day.date.takeLast(5),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) CpNight.Accent else CpNight.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun CpDayBanner(store: CpStore) {
    val branch = store.currentBranch() ?: return
    val day = store.branchDay(branch.id, store.dayFilter.value)
    Row(
        Modifier
            .fillMaxWidth()
            .background(CpNight.PanelSoft)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(branch.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
        Spacer(Modifier.width(10.dp))
        Text(store.dayFilter.value, fontSize = 13.sp, color = CpNight.Muted)
        Spacer(Modifier.width(10.dp))
        if (day != null) CpChip("Branch day ${day.state.label}", day.state.tint())
        Spacer(Modifier.width(12.dp))
        Text(
            "Boundary 04:00 Asia/Manila — OPEN stays editable until 04:00 the next morning, then turns PAST lazily.",
            fontSize = 12.sp,
            color = CpNight.Faint,
        )
    }
}

@Composable
private fun CpSidebar(
    store: CpStore,
    user: CpUser,
) {
    val branch = store.currentBranch()
    Column(
        Modifier
            .width(264.dp)
            .fillMaxHeight()
            .background(CpNight.Sidebar)
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (branch != null) {
            Text(branch.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
            Text(branch.kind.label, fontSize = 12.sp, color = CpNight.Faint)
            TextButton(
                onClick = {
                    store.currentBranchId.value = null
                    store.audit(user.name, "BRANCH_SWITCH", "branches:${branch.id}", "Opened branch switcher.")
                },
            ) {
                Text("Switch branch", fontSize = 12.sp, color = CpNight.Accent)
            }
            Spacer(Modifier.height(8.dp))
        }
        CpSectionTitle("Workspace")
        val screens = listOf(CpScreen.HOME, CpScreen.SESSIONS, CpScreen.CLIENTS, CpScreen.FINANCE)
        screens.forEachIndexed { index, screen ->
            val badge =
                when (screen) {
                    CpScreen.SESSIONS -> store.pendingCount().takeIf { it > 0 }?.toString()
                    else -> null
                }
            CpNavRow(
                label = screen.label,
                hint = "${screen.hint}  ·  Alt+${index + 1}",
                active = store.screen.value == screen,
                badge = badge,
                onClick = { store.screen.value = screen },
            )
        }
        Spacer(Modifier.height(14.dp))
        CpSectionTitle("Team")
        listOf(CpScreen.TEAM, CpScreen.MAIL, CpScreen.AUDIT).forEachIndexed { index, screen ->
            val badge =
                when (screen) {
                    CpScreen.MAIL -> store.unreadCount().takeIf { it > 0 }?.toString()
                    else -> null
                }
            CpNavRow(
                label = screen.label,
                hint = "${screen.hint}  ·  Alt+${index + 5}",
                active = store.screen.value == screen,
                badge = badge,
                onClick = { store.screen.value = screen },
            )
        }
        Spacer(Modifier.height(14.dp))
        CpSectionTitle("Me")
        CpNavRow(
            label = CpScreen.PROFILE.label,
            hint = "${CpScreen.PROFILE.hint}  ·  Alt+8",
            active = store.screen.value == CpScreen.PROFILE,
            onClick = { store.screen.value = CpScreen.PROFILE },
        )
        Spacer(Modifier.height(14.dp))
        CpSectionTitle("Recent", trailing = "auto-kept")
        if (store.recent.isEmpty()) {
            Text(
                "Nothing yet — open a session, client, or branch and it lands here.",
                fontSize = 12.sp,
                color = CpNight.Faint,
            )
        } else {
            store.recent.take(5).forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { openCpRecent(store, item) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontSize = 13.sp, color = CpNight.Ink, maxLines = 1)
                        Text("${item.kind} · ${item.detail}", fontSize = 11.sp, color = CpNight.Faint, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CpNight.Panel)
                .border(1.dp, CpNight.Hairline, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Column {
                Text(user.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
                Text(user.role.label, fontSize = 12.sp, color = CpNight.Muted)
                Spacer(Modifier.height(6.dp))
                CpChip(
                    if (store.clockedIn.value) "Clocked in" else "Clocked out",
                    if (store.clockedIn.value) CpNight.Green else CpNight.Faint,
                )
            }
        }
    }
}

private fun openCpRecent(
    store: CpStore,
    item: CpRecent,
) {
    when (item.kind) {
        "Session" -> {
            store.screen.value = CpScreen.SESSIONS
            store.sessionFilter.value = null
            store.selectedSessionId.value = item.targetId
        }

        "Client" -> {
            store.screen.value = CpScreen.CLIENTS
            store.selectedClientId.value = item.targetId
        }

        "Branch" -> {
            store.currentBranchId.value = item.targetId
            store.screen.value = CpScreen.HOME
            store.selectedSessionId.value = null
            store.selectedClientId.value = null
        }

        else -> {
            store.screen.value = item.targetScreen
        }
    }
}

@Composable
private fun CpStatusBar(
    store: CpStore,
    user: CpUser,
) {
    val branch = store.currentBranch()
    Row(
        Modifier
            .fillMaxWidth()
            .background(CpNight.Sidebar)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            listOfNotNull(
                branch?.name,
                store.dayFilter.value,
                user.name,
                if (store.clockedIn.value) "clocked in" else "clocked out",
            ).joinToString("  ·  "),
            fontSize = 12.sp,
            color = CpNight.Faint,
        )
        Spacer(Modifier.weight(1f))
        Text("Ctrl/⌘K palette", fontSize = 12.sp, color = CpNight.Faint)
        Spacer(Modifier.width(12.dp))
        Text("Alt+1…8 navigate", fontSize = 12.sp, color = CpNight.Faint)
        Spacer(Modifier.width(12.dp))
        Text("↑↓ + Enter select", fontSize = 12.sp, color = CpNight.Faint)
    }
}

private data class CpPaletteEntry(
    val kind: String,
    val title: String,
    val detail: String,
    val tint: Color,
    val run: () -> Unit,
)

private fun fuzzyHits(
    query: String,
    text: String,
): List<Int>? {
    val q = query.lowercase()
    val t = text.lowercase()
    if (q.isEmpty()) return emptyList()
    val hits = mutableListOf<Int>()
    var ti = 0
    for (ch in q) {
        var found = -1
        var i = ti
        while (i < t.length) {
            if (t[i] == ch) {
                found = i
                break
            }
            i += 1
        }
        if (found < 0) return null
        hits.add(found)
        ti = found + 1
    }
    return hits
}

@Composable
private fun CpPaletteOverlay(
    store: CpStore,
    user: CpUser,
) {
    val query = store.paletteQuery.value
    val entries = buildCpEntries(store, user, query)
    val index = store.paletteIndex.value.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(CpNight.Scrim)
            .clickable { store.paletteOpen.value = false }
            .padding(top = 110.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .width(600.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CpNight.Panel)
                .border(1.dp, CpNight.Hairline, RoundedCornerShape(16.dp))
                .clickable { }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            store.paletteOpen.value = false
                            true
                        }

                        Key.DirectionDown -> {
                            if (entries.isNotEmpty()) store.paletteIndex.value = (index + 1) % entries.size
                            true
                        }

                        Key.DirectionUp -> {
                            if (entries.isNotEmpty()) {
                                store.paletteIndex.value = (index - 1 + entries.size) % entries.size
                            }
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            entries.getOrNull(index)?.run?.invoke()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.padding(18.dp),
        ) {
            TextField(
                value = query,
                onValueChange = {
                    store.paletteQuery.value = it
                    store.paletteIndex.value = 0
                },
                placeholder = { Text("Type to fuzz — try “m Vivi”, “s10”, or “remit”…", color = CpNight.Faint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Text(
                    "No match — fuzzy search spans screens, sessions, clients, and branches.",
                    fontSize = 13.sp,
                    color = CpNight.Muted,
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    itemsIndexed(entries, key = { i, e -> "${e.kind}:${e.title}:$i" }) { i, entry ->
                        val active = i == index
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) CpNight.Accent.copy(alpha = 0.14f) else Color.Transparent)
                                .clickable {
                                    store.paletteIndex.value = i
                                    entry.run()
                                }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CpChip(entry.kind, entry.tint)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                CpFuzzyText(entry.title, query)
                                Text(entry.detail, fontSize = 12.sp, color = CpNight.Muted, maxLines = 1)
                            }
                            if (active) CpKeyHint("Enter")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CpKeyHint("↑↓")
                Spacer(Modifier.width(6.dp))
                Text("move", fontSize = 11.sp, color = CpNight.Faint)
                Spacer(Modifier.width(12.dp))
                CpKeyHint("Enter")
                Spacer(Modifier.width(6.dp))
                Text("open", fontSize = 11.sp, color = CpNight.Faint)
                Spacer(Modifier.width(12.dp))
                CpKeyHint("Esc")
                Spacer(Modifier.width(6.dp))
                Text("dismiss", fontSize = 11.sp, color = CpNight.Faint)
            }
        }
    }
}

@Composable
private fun CpFuzzyText(
    title: String,
    query: String,
) {
    val hits = fuzzyHits(query.trim(), title)
    if (hits == null || hits.isEmpty()) {
        Text(title, fontSize = 14.sp, color = CpNight.Ink, maxLines = 1)
        return
    }
    val hitSet = hits.toSet()
    Text(
        buildAnnotatedString {
            title.forEachIndexed { i, ch ->
                if (hitSet.contains(i)) {
                    withStyle(SpanStyle(color = CpNight.Accent, fontWeight = FontWeight.Bold)) { append(ch.toString()) }
                } else {
                    append(ch.toString())
                }
            }
        },
        fontSize = 14.sp,
        color = CpNight.Ink,
        maxLines = 1,
    )
}

private fun buildCpEntries(
    store: CpStore,
    user: CpUser,
    rawQuery: String,
): List<CpPaletteEntry> {
    val query = rawQuery.trim()
    val out = mutableListOf<Pair<Int, CpPaletteEntry>>()

    fun close() {
        store.paletteOpen.value = false
    }

    fun add(
        haystack: String,
        make: () -> CpPaletteEntry,
    ) {
        if (query.isEmpty()) {
            out.add(0 to make())
            return
        }
        val score = fuzzyScore(query, haystack) ?: return
        out.add(score to make())
    }
    CpScreen.entries.forEach { screen ->
        add("${screen.label} ${screen.hint} go to open ${screen.label}") {
            CpPaletteEntry(
                kind = "Go to",
                title = screen.label,
                detail = screen.hint,
                tint = CpNight.Accent,
                run = {
                    store.screen.value = screen
                    close()
                },
            ).let { entry -> entry }
        }
    }
    store.sessions.forEach { session ->
        val branchName = store.branches.firstOrNull { it.id == session.branchId }?.name ?: session.branchId
        add("${session.id} ${session.clientName} ${session.type} session ${session.status.label}") {
            CpPaletteEntry(
                kind = "Session",
                title = "${session.id} · ${session.clientName}",
                detail = "${session.type} · ${session.status.label} · $branchName ${session.dayDate}",
                tint = session.status.tint(),
                run = {
                    store.screen.value = CpScreen.SESSIONS
                    store.sessionFilter.value = null
                    store.selectedSessionId.value = session.id
                    store.touchRecent(
                        CpRecent(
                            "Session",
                            "${session.id} · ${session.clientName}",
                            session.status.label,
                            CpScreen.SESSIONS,
                            session.id,
                        ),
                    )
                    close()
                },
            ).let { entry -> entry }
        }
    }
    store.clients.forEach { client ->
        add("${client.name} client ${client.age} ${client.gender}") {
            CpPaletteEntry(
                kind = "Client",
                title = client.name,
                detail =
                    if (client.anonymized) {
                        "Anonymized record — gender + age only"
                    } else {
                        "Age ${client.age} · ${client.gender}"
                    },
                tint = CpNight.Blue,
                run = {
                    store.screen.value = CpScreen.CLIENTS
                    store.selectedClientId.value = client.id
                    store.touchRecent(
                        CpRecent(
                            "Client",
                            client.name,
                            "Age ${client.age} · ${client.gender}",
                            CpScreen.CLIENTS,
                            client.id,
                        ),
                    )
                    close()
                },
            ).let { entry -> entry }
        }
    }
    store.branches.forEach { branch ->
        add("${branch.name} ${branch.kind.label} branch relief") {
            CpPaletteEntry(
                kind = "Branch",
                title = branch.name,
                detail = branch.kind.label + if (branch.id == user.homeBranchId) " · home" else " · opens as relief",
                tint = CpNight.Violet,
                run = {
                    store.currentBranchId.value = branch.id
                    store.screen.value = CpScreen.HOME
                    store.selectedSessionId.value = null
                    store.selectedClientId.value = null
                    val openDay = store.days.firstOrNull { it.branchId == branch.id && it.state == CpDayState.OPEN }
                    store.dayFilter.value = openDay?.date
                        ?: store.days.firstOrNull { it.branchId == branch.id }?.date
                        ?: store.dayFilter.value
                    store.reliefEdit.value = branch.id == user.homeBranchId
                    store.touchRecent(CpRecent("Branch", branch.name, branch.kind.label, CpScreen.HOME, branch.id))
                    close()
                },
            ).let { entry -> entry }
        }
    }
    if (query.isEmpty()) {
        val recentsFirst = mutableListOf<Pair<Int, CpPaletteEntry>>()
        val rest = mutableListOf<Pair<Int, CpPaletteEntry>>()
        out.forEach { (score, entry) ->
            if (entry.kind == "Go to") rest.add(score to entry) else rest.add(score to entry)
        }
        store.recent.forEach { item ->
            val target: CpPaletteEntry? =
                when (item.kind) {
                    "Session" -> {
                        store.sessions.firstOrNull { it.id == item.targetId }?.let { s ->
                            CpPaletteEntry(
                                "Recent",
                                "${s.id} · ${s.clientName}",
                                "Session · ${s.status.label}",
                                s.status
                                    .tint(),
                                run = {
                                    store.screen.value = CpScreen.SESSIONS
                                    store.sessionFilter.value = null
                                    store.selectedSessionId.value = s.id
                                    close()
                                },
                            )
                        }
                    }

                    "Client" -> {
                        store.clients.firstOrNull { it.id == item.targetId }?.let { c ->
                            CpPaletteEntry("Recent", c.name, "Client", CpNight.Blue, run = {
                                store.screen.value = CpScreen.CLIENTS
                                store.selectedClientId.value = c.id
                                close()
                            })
                        }
                    }

                    "Branch" -> {
                        store.branches.firstOrNull { it.id == item.targetId }?.let { b ->
                            CpPaletteEntry("Recent", b.name, "Branch · ${b.kind.label}", CpNight.Violet, run = {
                                store.currentBranchId.value = b.id
                                store.screen.value = CpScreen.HOME
                                close()
                            })
                        }
                    }

                    else -> {
                        null
                    }
                }
            if (target != null) recentsFirst.add(-100 to target)
        }
        return (recentsFirst + rest).map { it.second }.take(14)
    }
    return out.sortedBy { it.first }.map { it.second }.take(14)
}

@Composable
private fun CpHomeView(
    store: CpStore,
    user: CpUser,
) {
    val branch = store.currentBranch() ?: return
    val isHome = branch.id == user.homeBranchId
    val daySessions = store.sessions.filter { it.branchId == branch.id && it.dayDate == store.dayFilter.value }
    CpPageTitle(
        "Good evening, ${user.name.split(" ").firstOrNull() ?: user.name}",
        "${branch.name} · ${store.dayFilter.value}",
    )
    CpTwoCol(
        left = {
            CpCard(Modifier.fillMaxWidth()) {
                CpSectionTitle("Clock status")
                CpStatRow("Branch", branch.name + if (isHome) " (home)" else " (relief duty)")
                CpStatRow(
                    "Access",
                    if (isHome ||
                        store.reliefEdit.value
                    ) {
                        "Full access"
                    } else {
                        "View-only until relief grant"
                    },
                )
                CpStatRow("State", if (store.clockedIn.value) "Clocked in" else "Clocked out")
                CpVSpace(10)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!store.clockedIn.value) {
                        CpPrimaryButton("Clock in") {
                            store.clockedIn.value = true
                            if (isHome) store.reliefEdit.value = true
                            store.audit(
                                user.name,
                                "CLOCK_IN",
                                "attendance:${branch.id}",
                                "Clocked in at ${branch.name}.",
                            )
                            store.notify(
                                "Clocked in",
                                "${user.name} clocked in at ${branch.name}.",
                                branch.id,
                                store.dayFilter.value,
                            )
                        }
                    } else {
                        CpPrimaryButton("Clock out") {
                            store.clockedIn.value = false
                            store.reliefEdit.value = isHome
                            store.audit(user.name, "CLOCK_OUT", "attendance:${branch.id}", "Clocked out.")
                        }
                    }
                }
                if (!isHome && !store.reliefEdit.value) {
                    CpVSpace(10)
                    Text(
                        "Relief duty starts view-only. Request edit access, then simulate the branch grant " +
                            "to continue the flow.",
                        fontSize = 13.sp,
                        color = CpNight.Muted,
                    )
                    CpVSpace(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CpSecondaryButton("Request relief edit") {
                            store.relief.add(
                                CpRelief(
                                    "r-${store.relief.size + 1}-${store.dayFilter.value}",
                                    CpReliefKind.REQUEST,
                                    user.name,
                                    branch.id,
                                    store.dayFilter.value,
                                    "Pending branch grant",
                                ),
                            )
                            store.audit(
                                user.name,
                                "INSERT",
                                "relief:${branch.id}",
                                "Relief request broadcast to ${branch.name}.",
                            )
                            store.notify(
                                "Relief request sent",
                                "${user.name} asked for relief edit at ${branch.name}.",
                                branch.id,
                                store.dayFilter.value,
                            )
                        }
                        CpSecondaryButton("Simulate branch grant") {
                            store.reliefEdit.value = true
                            store.audit(
                                user.name,
                                "UPDATE",
                                "relief:${branch.id}",
                                "Branch member granted relief edit access.",
                            )
                            store.notify(
                                "Relief request approved",
                                "A branch member granted relief edit at ${branch.name}.",
                                branch.id,
                                store.dayFilter.value,
                            )
                        }
                    }
                }
            }
            CpVSpace()
            CpCard(Modifier.fillMaxWidth()) {
                CpSectionTitle("Relief board", trailing = "duty · requests · invites")
                if (store.relief.isEmpty()) {
                    Text("No relief activity yet.", fontSize = 13.sp, color = CpNight.Muted)
                } else {
                    store.relief.forEach { item ->
                        val itemBranch = store.branches.firstOrNull { it.id == item.branchId }?.name ?: item.branchId
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${item.person} — ${item.kind.label}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CpNight.Ink,
                                )
                                Text(
                                    "$itemBranch · ${item.dayDate} · ${item.state}",
                                    fontSize = 12.sp,
                                    color = CpNight.Muted,
                                )
                            }
                            CpReliefActions(store, user, item)
                        }
                    }
                }
                CpVSpace(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("New request for day:", fontSize = 12.sp, color = CpNight.Muted)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        TextField(
                            value = store.reliefNote.value.ifEmpty { store.dayFilter.value },
                            onValueChange = { store.reliefNote.value = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    CpSecondaryButton("Send") {
                        val date = store.reliefNote.value.ifEmpty { store.dayFilter.value }
                        store.relief.add(
                            0,
                            CpRelief(
                                "r-new-${store.relief.size}",
                                CpReliefKind.REQUEST,
                                user.name,
                                branch.id,
                                date,
                                "Pending branch grant",
                            ),
                        )
                        store.reliefNote.value = ""
                        store.audit(user.name, "INSERT", "relief:${branch.id}", "Relief request for $date.")
                    }
                }
            }
        },
        right = {
            CpCard(Modifier.fillMaxWidth()) {
                CpSectionTitle("Branch day at a glance", trailing = "${daySessions.size} sessions")
                val active = daySessions.filter { !it.voided }
                CpSessionStatus.entries.forEach { status ->
                    val count = active.count { it.status == status }
                    CpStatRow(status.label, count.toString())
                }
                CpStatRow("Voided (kept for record)", daySessions.count { it.voided }.toString())
                CpVSpace(8)
                CpSecondaryButton("Open sessions") { store.screen.value = CpScreen.SESSIONS }
            }
            CpVSpace()
            CpCard(Modifier.fillMaxWidth()) {
                CpSectionTitle("Mailbox preview", trailing = "${store.unreadCount()} unread")
                store.notifications.take(3).forEach { note ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                markCpRead(store, note.id)
                                if (note.branchId != null) {
                                    store.currentBranchId.value = note.branchId
                                    if (note.dayDate != null) store.dayFilter.value = note.dayDate
                                    store.screen.value = CpScreen.HOME
                                } else {
                                    store.screen.value = CpScreen.MAIL
                                }
                            }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .padding(end = 10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (note.read) Color.Transparent else CpNight.Accent)
                                .padding(4.dp),
                        ) {}
                        Column {
                            Text(note.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CpNight.Ink)
                            Text(note.body, fontSize = 12.sp, color = CpNight.Muted, maxLines = 1)
                        }
                    }
                }
                CpVSpace(6)
                CpSecondaryButton("Open mailbox") { store.screen.value = CpScreen.MAIL }
            }
        },
    )
}

@Composable
private fun CpReliefActions(
    store: CpStore,
    user: CpUser,
    item: CpRelief,
) {
    when {
        item.kind == CpReliefKind.REQUEST && item.state == "Pending branch grant" -> {
            CpSmallButton("Grant") {
                val i = store.relief.indexOfFirst { it.id == item.id }
                if (i >= 0) store.relief[i] = item.copy(state = "Granted — edit access")
                store.audit(user.name, "UPDATE", "relief:${item.id}", "Granted relief request.")
                store.notify(
                    "Relief request approved",
                    "Your relief request at ${item.branchId} for ${item.dayDate} was granted.",
                    item.branchId,
                    item.dayDate,
                )
            }
            Spacer(Modifier.width(6.dp))
            CpSmallButton("Decline") {
                val i = store.relief.indexOfFirst { it.id == item.id }
                if (i >= 0) store.relief[i] = item.copy(state = "Declined")
                store.audit(user.name, "UPDATE", "relief:${item.id}", "Declined relief request.")
            }
            if (item.person == user.name) {
                Spacer(Modifier.width(6.dp))
                CpSmallButton("Withdraw") {
                    store.relief.removeAll { it.id == item.id }
                    store.audit(user.name, "DELETE", "relief:${item.id}", "Withdrew own relief request.")
                }
            }
        }

        item.kind == CpReliefKind.INVITE && item.state == "Invite sent" -> {
            CpSmallButton("Accept") {
                val i = store.relief.indexOfFirst { it.id == item.id }
                if (i >= 0) store.relief[i] = item.copy(kind = CpReliefKind.DUTY, state = "Edit access")
                store.audit(user.name, "UPDATE", "relief:${item.id}", "Accepted relief invite — day grant written.")
            }
            Spacer(Modifier.width(6.dp))
            CpSmallButton("Decline") {
                val i = store.relief.indexOfFirst { it.id == item.id }
                if (i >= 0) store.relief[i] = item.copy(state = "Declined")
                store.audit(user.name, "UPDATE", "relief:${item.id}", "Declined relief invite.")
            }
        }

        item.kind == CpReliefKind.DUTY -> {
            CpSmallButton("Revoke") {
                val i = store.relief.indexOfFirst { it.id == item.id }
                if (i >= 0) store.relief[i] = item.copy(state = "Revoked by branch")
                store.audit(
                    user.name,
                    "DELETE",
                    "relief:${item.id}",
                    "Revoked accepted relief duty; day grant removed.",
                )
                store.notify(
                    "Relief duty revoked",
                    "Your duty at ${item.branchId} for ${item.dayDate} was revoked.",
                    item.branchId,
                    item.dayDate,
                )
            }
        }

        else -> {
            Text(item.state, fontSize = 12.sp, color = CpNight.Faint)
        }
    }
}

internal fun markCpRead(
    store: CpStore,
    id: String,
) {
    val i = store.notifications.indexOfFirst { it.id == id }
    if (i >= 0 && !store.notifications[i].read) {
        store.notifications[i] = store.notifications[i].copy(read = true)
    }
}
