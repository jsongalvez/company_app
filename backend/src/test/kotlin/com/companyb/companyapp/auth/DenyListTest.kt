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
        assertTrue(DenyList.isDenied(userId, Instant.EPOCH))
    }

    @Test
    fun tokenIssuedAfterDenyIsAllowed() {
        val userId = UUID.randomUUID()
        DenyList.denyAt(userId, base)
        assertFalse(DenyList.isDeniedAt(userId, base.plusSeconds(1), base.plusSeconds(3600)))
    }

    @Test
    fun unknownUserIsNotDenied() {
        assertFalse(DenyList.isDenied(UUID.randomUUID(), Instant.EPOCH))
    }

    @Test
    fun entryIsKeptJustBeforeEviction() {
        val userId = UUID.randomUUID()
        DenyList.denyAt(userId, base)
        val almostExpired = base.plus(Duration.ofHours(24)).plusSeconds(59)
        assertTrue(DenyList.isDeniedAt(userId, base, almostExpired))
        // A token issued after the deny is allowed even while the entry lives.
        assertFalse(DenyList.isDeniedAt(userId, base.plusSeconds(1), almostExpired))
    }

    @Test
    fun entryIsEvictedAfterTokenMaxAgePlusLeeway() {
        val userId = UUID.randomUUID()
        DenyList.denyAt(userId, base)
        // An entry must outlive the last pre-deny token: 24h JWT max age + the 60s
        // acceptLeeway JwtService applies to exp validation.
        val expired = base.plus(Duration.ofHours(24)).plusSeconds(60)
        assertFalse(DenyList.isDeniedAt(userId, base, expired))
        // Lazy eviction removes the stale entry on read.
        assertEquals(0, DenyList.size())
    }

    @Test
    fun evictExpiredRemovesOnlyStaleEntries() {
        val fresh = UUID.randomUUID()
        val stale = UUID.randomUUID()
        DenyList.denyAt(fresh, base.plus(Duration.ofHours(23)))
        DenyList.denyAt(stale, base)
        val now = base.plus(Duration.ofHours(24)).plusSeconds(61)

        DenyList.evictExpiredAt(now)

        assertEquals(1, DenyList.size())
        assertTrue(DenyList.isDeniedAt(fresh, base, now))
        assertFalse(DenyList.isDeniedAt(stale, base, now))
    }
}
