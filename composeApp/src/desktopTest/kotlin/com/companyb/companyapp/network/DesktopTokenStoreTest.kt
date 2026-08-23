package com.companyb.companyapp.network

import com.companyb.companyapp.config.TOKEN_STORE_KEY
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DesktopTokenStoreTest {
    private val baseDir: Path = Files.createTempDirectory("tokenstore-test")
    private val tokenPath: Path = baseDir.resolve(TOKEN_STORE_KEY)
    private val store = DesktopTokenStore(baseDir.toFile())

    @BeforeTest
    fun setUp() {
        store.clearToken()
    }

    @AfterTest
    fun tearDown() {
        store.clearToken()
        Files.deleteIfExists(baseDir)
    }

    private fun ownerOnly(): Set<PosixFilePermission> = Files.getPosixFilePermissions(tokenPath)

    @Test
    fun savedTokenRoundTrips() {
        store.saveToken("jwt-value")
        assertEquals("jwt-value", store.getToken())
    }

    @Test
    fun tokenFileIsOwnerOnlyAfterSave() {
        store.saveToken("jwt-value")
        assertEquals(PosixFilePermissions.fromString("rw-------"), ownerOnly())
    }

    @Test
    fun legacyWorldReadableFileIsRepairedOnNextSave() {
        tokenPath.toFile().writeText("stale")
        Files.setPosixFilePermissions(tokenPath, PosixFilePermissions.fromString("rw-rw-rw-"))
        store.saveToken("fresh")
        assertEquals(PosixFilePermissions.fromString("rw-------"), ownerOnly())
        assertEquals("fresh", store.getToken())
    }

    @Test
    fun missingOrEmptyFileYieldsNull() {
        assertNull(store.getToken())
        store.saveToken("")
        assertNull(store.getToken())
    }

    @Test
    fun clearRemovesTheFile() {
        store.saveToken("jwt-value")
        store.clearToken()
        assertFalse(Files.exists(tokenPath))
        assertNull(store.getToken())
    }
}
