package com.companyb.companyapp

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.companyb.companyapp.app.App
import com.companyb.companyapp.proto.belldesk.ProtoBellDeskApp

private fun detectProjectRoot(dir: java.io.File): String {
    val markers = listOf(".git", "settings.gradle.kts")
    var current = dir
    while (true) {
        if (markers.any { java.io.File(current, it).exists() }) return current.absolutePath
        current = current.parentFile ?: return dir.absolutePath
    }
}

fun main() {
    if (System.getProperty("companyApp.logDir") == null) {
        val root = detectProjectRoot(java.io.File(System.getProperty("user.dir")))
        System.setProperty("companyApp.logDir", "$root/logs/client")
    }
    java.io.File(System.getProperty("companyApp.logDir")).mkdirs()
    application {
        val protoBellDesk = System.getenv("COMPANYAPP_PROTO_BELL_DESK") == "true"
        val windowState =
            rememberWindowState(
                size = if (protoBellDesk) DpSize(1280.dp, 800.dp) else DpSize(1024.dp, 768.dp),
                position = WindowPosition(Alignment.Center),
            )

        Window(
            onCloseRequest = ::exitApplication,
            title = if (protoBellDesk) "CompanyApp — bell-desk prototype" else "CompanyApp",
            state = windowState,
        ) {
            if (protoBellDesk) {
                ProtoBellDeskApp(onBack = ::exitApplication)
            } else {
                App()
            }
        }
    }
}
