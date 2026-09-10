package com.companyb.companyapp.proto.desertsand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// #808 — desert-sand prototype entry. Owns the fake repo and roots the shell.

@Composable
fun DesertSandApp(onBack: () -> Unit) {
    val repo = remember { DsFakeRepo() }
    DsRoot { DsShell(repo) }
}
