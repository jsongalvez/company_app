package com.companyb.companyapp.utils

class Helper {
    fun generateRandomId(length: Int = 16): String {
        val chars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (0..length).map { chars.random() }.joinToString("")
    }
}
