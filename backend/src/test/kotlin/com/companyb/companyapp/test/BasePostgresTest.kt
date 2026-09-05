package com.companyb.companyapp.test

import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * #494 — test fixture lifecycle owns no rows. Each worker JVM owns its whole mutable
 * fixture namespace (#493); per-test isolation is one RESTRICT truncate of the owned
 * schema before setup and after the test. Seed reference rows survive; snapshot
 * immutability is never toggled (TRUNCATE fires no ON DELETE trigger).
 */
abstract class BasePostgresTest {
    protected abstract fun initTestData()

    @BeforeTest
    fun setUpBase() {
        DatabaseTestHelper.ensureDatabase()
        val config = AppConfig.parse()
        JwtService.init(config)
        Password.init(config.authDummyPassword)
        DatabaseTestHelper.resetWorkerSchema()
        initTestData()
    }

    @AfterTest
    open fun tearDownBase() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            DatabaseTestHelper.resetWorkerSchema()
        }
    }
}
