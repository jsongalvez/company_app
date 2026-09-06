package com.companyb.companyapp.seeding

import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.identity.JwtService
import com.companyb.companyapp.identity.Password
import com.companyb.companyapp.main

fun main() {
    val config = AppConfig.parse()

    JwtService.init(config)
    Password.init(config.authDummyPassword)

    DatabaseConfig.initialize(config)
    DevSeeder.seed(config)
    main(config)
}
