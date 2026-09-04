package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class CompanyAppRuleSetProvider : RuleSetProvider {
    override val ruleSetId = "CompanyApp"

    override fun instance(config: Config): RuleSet = RuleSet(ruleSetId, emptyList())
}
