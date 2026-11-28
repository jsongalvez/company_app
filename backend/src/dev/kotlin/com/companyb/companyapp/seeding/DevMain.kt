package com.companyb.companyapp.seeding

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.branchday.SiteHub
import com.companyb.companyapp.branchday.SiteHubCerts
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.main

fun main() {
    val config = AppConfig.parse()
    val fixtures = DevFixtureConfig.parse()

    JwtService.init(config)
    SiteHub.configure(config.jwtSecret)
    Password.init(config.authDummyPassword)

    DatabaseConfig.initialize(config)
    DevSeeder.seed(fixtures)
    if (config.cloudflareDnsToken == null) SiteHubCerts.seedDevCert()
    main(config)
}
