package com.companyb.companyapp.session

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object ActiveSessionVoidsView : Table("active_session_voids") {
    val sessionId = javaUUID("session_id")
}
