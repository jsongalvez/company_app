package com.companyb.companyapp.utils

object RandomIdGenerator {
    fun generate(length: Int = 16): String {
        val chars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (0 until length).map { chars.random() }.joinToString("")
    }
}
