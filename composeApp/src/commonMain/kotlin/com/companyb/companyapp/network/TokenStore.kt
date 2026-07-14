package com.companyb.companyapp.network

interface TokenStore {
    fun saveToken(token: String)

    fun getToken(): String?

    fun clearToken()
}

expect fun createTokenStore(): TokenStore
