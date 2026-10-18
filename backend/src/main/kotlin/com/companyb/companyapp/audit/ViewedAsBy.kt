package com.companyb.companyapp.audit

import java.util.UUID

/**
 * The superuser behind the current request while they "view as" someone (feedback 378fdb6d).
 * Set per request by the auth filter; history rows written during the request carry it in
 * `viewed_as_by`, so a change made while viewing as Pat reads "Pat (twisty viewing)".
 */
object ViewedAsBy {
    private val current = ThreadLocal<UUID?>()

    fun get(): UUID? = current.get()

    fun set(userId: UUID?) = if (userId == null) current.remove() else current.set(userId)
}
