package com.company_b.company_app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform