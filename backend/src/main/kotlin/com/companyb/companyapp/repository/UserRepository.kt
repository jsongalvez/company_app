package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.AppUser
import com.companyb.companyapp.repository.model.AppUserTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object UserRepository {
    fun findByUsername(username: String): AppUser? =
        transaction {
            AppUserTable
                .selectAll()
                .where { AppUserTable.username eq username }
                .map { row ->
                    AppUser(
                        id = row[AppUserTable.id].toString(),
                        username = row[AppUserTable.username],
                        passwordHash = row[AppUserTable.passwordHash],
                    )
                }.singleOrNull()
        }
}
