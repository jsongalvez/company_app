package com.companyb.companyapp.proto.metricsmosaic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// #779 — team, mailbox, audit log, profile. Quiet tiles, dense rows.

@Composable
fun MmTeam(repo: MmRepo, viewer: MmUser) {
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        MmSection("Team · users + roles")
        MmTitle("Who can do what")
        MmNote("MANAGER holds the superset. ONBOARDING holds nothing until granted (MANAGER view can grant).")
        Spacer(Modifier.height(8.dp))
        repo.users.forEach { u ->
            MmTile {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmDot(if (u.locked) MmColors.Faint else MmColors.Teal)
                    Spacer(Modifier.width(8.dp))
                    MmText("${u.name} · ${u.role}", bold = true)
                }
                MmNote("@${u.login} · home ${repo.branchById(u.homeBranchId).name}")
                val caps = if (u.capabilities.isEmpty()) "none (ONBOARDING)" else u.capabilities.joinToString()
                MmNote("Capabilities: $caps")
                if (u.locked && viewer.role == "MANAGER") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MmButton("Grant Practitioner") {
                            repo.log(viewer.login + " granted Practitioner to " + u.login)
                        }
                        MmGhost("Deactivate") {
                            repo.log(viewer.login + " deactivated " + u.login)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun MmMail(repo: MmRepo) {
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        MmSection("Notifications · mailbox")
        MmTitle("Inbox")
        MmNote("${repo.unreadCount()} unread. Relief events name branch + day.")
        Spacer(Modifier.height(8.dp))
        MmGhost("Mark all read") {
            repo.notices.forEach { it.read = true }
        }
        Spacer(Modifier.height(8.dp))
        repo.notices.forEach { n ->
            MmTile(alert = !n.read) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MmDot(if (n.read) MmColors.Edge else MmColors.Amber)
                    Spacer(Modifier.width(8.dp))
                    MmText(n.title, bold = !n.read)
                }
                MmNote(n.body)
                if (!n.read) {
                    MmGhost("Mark read") { n.read = true }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun MmAudit(repo: MmRepo) {
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        MmSection("Audit log")
        MmTitle("Every action, newest first")
        MmNote("Clock, sessions, voids, finance, relief — all append here.")
        Spacer(Modifier.height(8.dp))
        repo.audit.forEach { entry ->
            MmTile {
                MmText(entry, size = 13)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun MmProfile(repo: MmRepo, user: MmUser, branch: MmBranch, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        MmSection("Profile")
        MmTitle(user.name)
        MmNote("@${user.login} · ${user.role} · home ${repo.branchById(user.homeBranchId).name}")
        Spacer(Modifier.height(8.dp))
        MmTile {
            MmSection("Role bundle")
            MmNote(if (user.capabilities.isEmpty()) "Empty (ONBOARDING)" else user.capabilities.joinToString())
        }
        Spacer(Modifier.height(8.dp))
        MmTile {
            MmSection("Shift")
            MmText(if (repo.clockedIn) "Clocked in at ${branch.name}" else "Clocked out", bold = true)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repo.clockedIn) {
                    MmButton("Clock out") {
                        repo.clockedIn = false
                        repo.log(user.login + " clocked out")
                    }
                    MmGhost("Clock out + logout") {
                        repo.clockedIn = false
                        repo.log(user.login + " clocked out")
                        onLogout()
                    }
                } else {
                    MmButton("Logout") { onLogout() }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MmNote("Branch Day boundary 04:00 Asia/Manila · fake-data build, no network.")
    }
}
