package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AppUserTable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

// #358 — display names for notification copy: relief broadcast messages name the people in
// them (owner rule "let ben know carla's response"). Read helper keeps its transaction
// wrapper (ADR-0024 rule 6); called inside a command it joins the ambient transaction.
fun findDisplayNamesByIds(ids: Collection<UUID>): Map<UUID, String> {
    if (ids.isEmpty()) return emptyMap()
    return transaction {
        AppUserTable
            .selectAll()
            .where { AppUserTable.id inList ids }
            .associate { it[AppUserTable.id] to it[AppUserTable.displayName] }
    }
}
