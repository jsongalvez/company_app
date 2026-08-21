package com.companyb.companyapp.test

import io.javalin.Javalin
import io.javalin.testtools.HttpClient
import org.junit.rules.ExternalResource
import java.net.http.HttpClient as JavaHttpClient

class JavalinTestServerRule(
    private val appFactory: () -> Javalin,
) : ExternalResource() {
    private var app: Javalin? = null

    lateinit var client: HttpClient
        private set

    override fun before() {
        app = appFactory()
        app!!.start(0)
        client = HttpClient(app!!, JavaHttpClient.newHttpClient())
    }

    override fun after() {
        app?.stop()
    }
}
