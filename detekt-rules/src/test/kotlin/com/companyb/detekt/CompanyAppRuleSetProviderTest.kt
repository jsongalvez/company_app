package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompanyAppRuleSetProviderTest {
    @Test
    fun `empty CompanyApp ruleset registers with no rules`() {
        val provider = CompanyAppRuleSetProvider()
        assertEquals("CompanyApp", provider.ruleSetId)
        assertTrue(provider.instance(Config.empty).rules.isEmpty())
    }

    @Test
    fun `CompanyApp provider is ServiceLoader-discoverable`() {
        val ids = ServiceLoader.load(RuleSetProvider::class.java).map { it.ruleSetId }
        assertTrue(ids.contains("CompanyApp"), "expected CompanyApp in $ids")
    }
}
