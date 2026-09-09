package com.companyb.companyapp.validation

object EmailPolicy {
    fun isValid(email: String): Boolean {
        val atIndex = email.indexOf("@")
        val dotIndex = email.indexOf(".", atIndex + 1)

        return !(atIndex == -1 || dotIndex == -1 || atIndex + 1 >= dotIndex)
    }
}
