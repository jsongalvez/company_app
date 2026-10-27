package com.companyb.companyapp.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.FileAppender
import kotlin.test.Test
import kotlin.test.assertTrue

class LogbackConfigTest {
    private fun shippedContext(): LoggerContext {
        val context = LoggerContext()
        val config = checkNotNull(javaClass.classLoader.getResource("logback.xml")) { "logback.xml missing" }
        JoranConfigurator().apply { this.context = context }.doConfigure(config)
        return context
    }

    @Test
    fun `server logs go to stdout only, never to a second copy in a file`() {
        val context = shippedContext()
        try {
            val appenders = context.loggerList.flatMap { it.iteratorForAppenders().asSequence().toList() }
            assertTrue(appenders.any { it is ConsoleAppender<*> }, "stdout appender missing")
            assertTrue(appenders.none { it is FileAppender<*> }, "a file appender doubles every log write")
        } finally {
            context.stop()
        }
    }

    @Test
    fun `request lines stay on at INFO, the line perf-record and the log keeper read`() {
        val context = shippedContext()
        try {
            assertTrue(context.getLogger(RequestLog::class.java.name).isInfoEnabled)
        } finally {
            context.stop()
        }
    }
}
