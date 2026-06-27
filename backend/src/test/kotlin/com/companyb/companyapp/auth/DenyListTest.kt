package com.companyb.companyapp.auth

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DenyListTest {
    private val base: Instant = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeTest
    fun setUp() = DenyList.clear()

    @AfterTest
    fun tearDown() = DenyList.clear()

    @Test
    fun deniedUserIsBlocked() {
        val userId = UUID.randomUUID()
        DenyList.deny(userId)
        assertTrue(DenyList.isDenied(userId))
    }

    @Test
    fun unknownUserIsNotDenied() {
        assertFalse(DenyList.isDenied(UUID.randomUUID()))
    }

    @Test
    fun entryIsKeptJustBefore24Hours() {
        val userId = UUID.randomUUID()
        DenyList.denyAt(userId, base)
        val almostExpired = base.plus(Duration.ofHours(24)).minusSeconds(1)
        assertTrue(DenyList.isDeniedAt(userId, almostExpired))
    }

    @Test
    fun entryIsEvictedAfter24Hours() {
        val userId = UUID.randomUUID()
        DenyList.denyAt(userId, base)
        val expired = base.plus(Duration.ofHours(24))
        assertFalse(DenyList.isDeniedAt(userId, expired))
        // Lazy eviction removes the stale entry on read.
        assertEquals(0, DenyList.size())
    }

    @Test
    fun evictExpiredRemovesOnlyStaleEntries() {
        val fresh = UUID.randomUUID()
        val stale = UUID.randomUUID()
        DenyList.denyAt(fresh, base.plus(Duration.ofHours(23)))
        DenyList.denyAt(stale, base)
        val now = base.plus(Duration.ofHours(24))

        DenyList.evictExpiredAt(now)

        assertEquals(1, DenyList.size())
        assertTrue(DenyList.isDeniedAt(fresh, now))
        assertFalse(DenyList.isDeniedAt(stale, now))
    }
}
