package com.companyb.companyapp.proto.ganttbranch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// #815 — gantt-branch prototype entry. Owns the fake repo and roots the shell.

@Composable
fun GanttBranchApp(onBack: () -> Unit) {
    val repo = remember { GbFakeRepo() }
    GbRoot { GbShell(repo, onBack) }
}
