package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object ActiveSessionVoidsView : Table("active_session_voids") {
    val id = javaUUID("id")
    val sessionId = javaUUID("session_id")
}
