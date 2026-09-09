package com.companyb.companyapp.proto.glasspanels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GpLogin(onLogin: () -> Unit, onOnboarding: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassSheet(modifier = Modifier.width(460.dp)) {
            Column {
                FaintLabel("companyapp / frosted prototype")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Step through", style = MaterialTheme.typography.displayLarge, color = FrostText)
                Text(text = "the glass.", style = MaterialTheme.typography.displayLarge, color = AquaGlow)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A translucent daybook floating over a deep-sea gradient. Fake data only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FrostDim,
                )
                Spacer(modifier = Modifier.height(18.dp))
                GlassField(name, { name = it }, "Name (anything works)")
                Spacer(modifier = Modifier.height(10.dp))
                GlassField(pin, { pin = it }, "PIN (anything works)")
                Spacer(modifier = Modifier.height(16.dp))
                GlassButton("Drift in  →", onLogin, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                GhostButton("View onboarding pane", onOnboarding, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun GpOnboardingLocked(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassSheet(modifier = Modifier.width(520.dp)) {
            Column {
                FaintLabel("onboarding / locked pane")
                Spacer(modifier = Modifier.height(8.dp))
                SheetTitle("Still frosted over", "ONBOARDING practitioners carry an empty capability bundle.")
                Spacer(modifier = Modifier.height(12.dp))
                PaneRow("Status", "LOCKED — no branch-day actions")
                PaneRow("Capabilities", "none until a Manager clears the pane")
                PaneRow("Sessions", "cannot own, void, or remit yet")
                Spacer(modifier = Modifier.height(16.dp))
                GhostButton("← Back to login", onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun GpBranchSelect(repo: GlassPanelsFakeRepo, onPick: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
        FaintLabel("branch select / pick a sheet")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Which water are you in today?", style = MaterialTheme.typography.headlineMedium, color = FrostText)
        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (branch in repo.branches) {
                val picked = repo.branchId.value == branch.id
                GlassSheet(modifier = Modifier.weight(1f)) {
                    Column {
                        StatusPill(if (picked) "CURRENT" else branch.kind, if (picked) AquaGlow else VioletGlow)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = branch.name, style = MaterialTheme.typography.titleLarge, color = FrostText)
                        Text(text = branch.shimmer, style = MaterialTheme.typography.bodyMedium, color = FrostDim)
                        Spacer(modifier = Modifier.height(14.dp))
                        if (picked) {
                            GlassButton("Float here  →", onPick, modifier = Modifier.fillMaxWidth())
                        } else {
                            GhostButton(
                                "Rest on this sheet",
                                { repo.branchId.value = branch.id },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        GhostButton("← Back", onBack)
    }
}

@Composable
fun GpHome(repo: GlassPanelsFakeRepo) {
    var inviteWho by remember { mutableStateOf("") }
    var requestNote by remember { mutableStateOf("") }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassSheet(modifier = Modifier.weight(1.2f)) {
            Column {
                FaintLabel("home / clock pane")
                Spacer(modifier = Modifier.height(8.dp))
                SheetTitle(repo.branchName(repo.branchId.value), if (repo.clockedIn.value) "Clocked in — pane is warm" else "Not clocked in — pane is cool")
                Spacer(modifier = Modifier.height(12.dp))
                StatusPill(if (repo.clockedIn.value) "● CLOCKED IN" else "○ CLOCKED OUT", if (repo.clockedIn.value) MintGlow else FrostDim)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (repo.clockedIn.value) {
                        GhostButton("Clock out", { repo.clockOut() }, modifier = Modifier.weight(1f))
                    } else {
                        GlassButton("Clock in", { repo.clockIn() }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                FaintLabel("relief — invite help")
                Spacer(modifier = Modifier.height(6.dp))
                GlassField(inviteWho, { inviteWho = it }, "Who to invite (e.g. L. Tan)")
                Spacer(modifier = Modifier.height(8.dp))
                GlassButton(
                    "Send invite",
                    {
                        if (inviteWho.isBlank()) return@GlassButton
                        repo.reliefBoard.add(
                            GpReliefItem("r${repo.reliefBoard.size + 1}", "Invite", inviteWho, repo.branchName(repo.branchId.value), "Today", "Invited from home pane"),
                        )
                        repo.stamp(repo.me.value, "INSERT", "relief invite", inviteWho)
                        inviteWho = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                FaintLabel("relief — request cover")
                Spacer(modifier = Modifier.height(6.dp))
                GlassField(requestNote, { requestNote = it }, "Why you need cover")
                Spacer(modifier = Modifier.height(8.dp))
                GhostButton(
                    "Broadcast request",
                    {
                        if (requestNote.isBlank()) return@GhostButton
                        repo.reliefBoard.add(
                            GpReliefItem("r${repo.reliefBoard.size + 1}", "Request", repo.me.value, repo.branchName(repo.branchId.value), "Today", requestNote),
                        )
                        repo.stamp(repo.me.value, "INSERT", "relief request", requestNote)
                        requestNote = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        GlassSheet(modifier = Modifier.weight(1f)) {
            Column {
                FaintLabel("relief board / duty · invite · request")
                Spacer(modifier = Modifier.height(8.dp))
                SheetTitle("Beside you", "${repo.reliefBoard.size} floating cards")
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(repo.reliefBoard) { item ->
                        GlassSheet(radius = 16.dp) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusPill(
                                        item.kind.uppercase(),
                                        when (item.kind) {
                                            "Invite" -> AquaGlow
                                            "Request" -> PeachGlow
                                            else -> VioletGlow
                                        },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = item.day, fontSize = 12.sp, color = FrostDim)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = item.who, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FrostText)
                                Text(text = "${item.branch} — ${item.note}", fontSize = 12.sp, color = FrostDim)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GhostButton("Grant", { repo.dropRelief(item.id, "Granted") }, modifier = Modifier.weight(1f))
                                    GhostButton("Deny", { repo.dropRelief(item.id, "Denied") }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GpSessions(repo: GlassPanelsFakeRepo) {
    var filter by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var walkName by remember { mutableStateOf("") }
    var voidReason by remember { mutableStateOf("") }
    val shown = repo.sessions.filter { filter == null || it.status.name == filter }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassSheet(modifier = Modifier.weight(1.2f)) {
            Column {
                FaintLabel("sessions / daybook panes")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("All", filter == null) { filter = null }
                    GlassChip("Pending", filter == "PENDING") { filter = "PENDING" }
                    GlassChip("Completed", filter == "COMPLETED") { filter = "COMPLETED" }
                    GlassChip("No-show", filter == "NO_SHOW") { filter = "NO_SHOW" }
                    GlassChip("Cancelled", filter == "CANCELLED") { filter = "CANCELLED" }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Walk-ins never take NO_SHOW or CANCELLED — they simply dissolve.",
                    fontSize = 12.sp,
                    color = FrostDim,
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown) { s ->
                        GlassSheet(radius = 16.dp) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = s.clientName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = FrostText)
                                        Text(text = "${s.type} · ${s.time} · P${s.price}${if (s.walkIn) " · walk-in" else ""}", fontSize = 12.sp, color = FrostDim)
                                    }
                                    StatusPill(
                                        if (s.voided) "VOIDED" else s.status.name,
                                        when {
                                            s.voided -> RoseGlow
                                            s.status == GpSessionStatus.COMPLETED -> MintGlow
                                            s.status == GpSessionStatus.PENDING -> AquaGlow
                                            else -> PeachGlow
                                        },
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                GhostButton(
                                    if (openId == s.id) "Close file" else "Open file",
                                    { openId = if (openId == s.id) null else s.id },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        GlassSheet(modifier = Modifier.weight(1f)) {
            val s = repo.sessions.firstOrNull { it.id == openId }
            if (s == null) {
                Column {
                    FaintLabel("session file")
                    Spacer(modifier = Modifier.height(8.dp))
                    SheetTitle("No pane open", "Tap “Open file” on any session card.")
                }
            } else {
                Column {
                    FaintLabel("session file / ${s.id}")
                    Spacer(modifier = Modifier.height(8.dp))
                    SheetTitle(s.clientName, "${s.type} · ${repo.branchName(s.branchId)}")
                    Spacer(modifier = Modifier.height(10.dp))
                    PaneRow("Status", if (s.voided) "VOIDED (${s.voidReason})" else s.status.name)
                    PaneRow("Kind", if (s.walkIn) "Walk-in" else "Booked")
                    PaneRow("Price", "P${s.price}")
                    PaneRow("Time", s.time)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            "Complete",
                            { repo.updateSession(s.id, { it.copy(status = GpSessionStatus.COMPLETED) }, "session ${s.id}", "Marked completed") },
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            "No-show",
                            {
                                if (s.walkIn) return@GhostButton
                                repo.updateSession(s.id, { it.copy(status = GpSessionStatus.NO_SHOW) }, "session ${s.id}", "Marked no-show")
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            "Cancel",
                            {
                                if (s.walkIn) return@GhostButton
                                repo.updateSession(s.id, { it.copy(status = GpSessionStatus.CANCELLED) }, "session ${s.id}", "Cancelled")
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (s.voided) {
                            GhostButton(
                                "Unvoid",
                                { repo.updateSession(s.id, { it.copy(voided = false, voidReason = "") }, "session ${s.id}", "Unvoided") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (!s.voided) {
                        Spacer(modifier = Modifier.height(8.dp))
                        GlassField(voidReason, { voidReason = it }, "Void reason (required)")
                        Spacer(modifier = Modifier.height(8.dp))
                        GhostButton(
                            "Confirm void",
                            {
                                if (voidReason.isBlank()) return@GhostButton
                                repo.updateSession(s.id, { it.copy(voided = true, voidReason = voidReason) }, "session ${s.id}", "Voided: $voidReason")
                                voidReason = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            FaintLabel("log a walk-in")
            Spacer(modifier = Modifier.height(6.dp))
            GlassField(walkName, { walkName = it }, "Walk-in name")
            Spacer(modifier = Modifier.height(8.dp))
            GlassButton(
                "Add walk-in",
                {
                    if (walkName.isBlank()) return@GlassButton
                    repo.addWalkIn(walkName, "Checkup", 500)
                    walkName = ""
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
