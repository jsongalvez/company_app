package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanyAppRuleSetProviderTest {
    @Test
    fun `CompanyApp ruleset registers SuppressRequiresTicket`() {
        val provider = CompanyAppRuleSetProvider()
        assertEquals("CompanyApp", provider.ruleSetId)
        val rules = provider.instance(Config.empty).rules
        assertEquals(1, rules.size)
        assertTrue(rules.single() is SuppressRequiresTicket)
    }

    @Test
    fun `CompanyApp provider is ServiceLoader-discoverable`() {
        val ids = ServiceLoader.load(RuleSetProvider::class.java).map { it.ruleSetId }
        assertTrue(ids.contains("CompanyApp"), "expected CompanyApp in $ids")
    }
}
