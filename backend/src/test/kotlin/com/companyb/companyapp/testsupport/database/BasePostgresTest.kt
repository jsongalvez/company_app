package com.companyb.companyapp.testsupport.database

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * #494 — test fixture lifecycle owns no rows. Each worker JVM owns its whole mutable
 * fixture namespace (#493); per-test isolation is one RESTRICT truncate of the owned
 * schema before setup and after the test. Seed reference rows survive; snapshot
 * immutability is never toggled (TRUNCATE fires no ON DELETE trigger).
 *
 * #552 — lifecycle delegates to [TestDatabaseLifecycle]; scenario fixtures live in
 * `testsupport.fixtures` families.
 */
abstract class BasePostgresTest {
    protected abstract fun initTestData()

    @BeforeTest
    fun setUpBase() {
        TestDatabaseLifecycle.ensureDatabase()
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        TestDatabaseLifecycle.resetWorkerSchema()
        initTestData()
    }

    @AfterTest
    open fun tearDownBase() {
        if (TestDatabaseLifecycle.isDatabaseReady()) {
            TestDatabaseLifecycle.resetWorkerSchema()
        }
    }
}
