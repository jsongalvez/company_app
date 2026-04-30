package com.companyb.companyapp.validation

object EmailPolicy {
    fun isValid(email: String): Boolean {
        val atIndex = email.indexOf("@")
        val dotIndex = email.indexOf(".")

        return !(atIndex == -1 || dotIndex == -1 || atIndex + 1 >= dotIndex)
    }
}
