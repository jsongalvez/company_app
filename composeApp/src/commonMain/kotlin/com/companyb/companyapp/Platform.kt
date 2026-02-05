package com.companyb.companyapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform