package com.companyb.companyapp.identity

import com.companyb.companyapp.contracts.identity.UserStatus
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthStateCacheTest {
    private var now = 0L
    private var loads = 0
    private val user = UUID.randomUUID()
    private val state = AuthState(UserStatus.ACTIVE, null, 0L)

    @BeforeTest
    fun setUp() {
        AuthStateCache.clear()
        AuthStateCache.nanoTime = { now }
    }

    @AfterTest
    fun tearDown() {
        AuthStateCache.clear()
        AuthStateCache.nanoTime = System::nanoTime
    }

    private fun read(
        id: UUID = user,
        value: AuthState? = state,
    ) = AuthStateCache.get(id) {
        loads++
        value
    }

    @Test
    fun `a hit within five seconds skips the load, then it expires`() {
        read()
        now = 4_999_000_000L
        read()
        assertEquals(1, loads)
        now = 5_000_000_000L
        read()
        assertEquals(2, loads)
    }

    @Test
    fun `an unknown user is never cached`() {
        assertNull(read(value = null))
        assertNull(read(value = null))
        assertEquals(2, loads)
    }

    @Test
    fun `invalidate forces the next read to load`() {
        read()
        AuthStateCache.invalidate(user)
        read()
        assertEquals(2, loads)
    }

    @Test
    fun `a load that overlaps an invalidation is not stored`() {
        AuthStateCache.get(user) {
            loads++
            AuthStateCache.invalidate(user)
            state
        }
        read()
        assertEquals(2, loads, "the overlapped read may predate the write, so it must not be kept")
    }

    @Test
    fun `the cache stays bounded`() {
        repeat(AuthStateCache.MAX_ENTRIES + 50) { read(id = UUID.randomUUID()) }
        assertTrue(AuthStateCache.size() <= AuthStateCache.MAX_ENTRIES)
    }
}
