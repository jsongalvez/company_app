package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table

object ActiveSessionVoidsView : Table("active_session_voids") {
    val id = uuid("id")
    val sessionId = uuid("session_id")
}
