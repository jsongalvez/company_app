package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class NoWidenThenCast(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            javaClass.simpleName,
            Severity.Style,
            "Same-function val widened to Any then cast back with as / as?.",
            Debt.TEN_MINS,
        )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val widened =
            function
                .collectDescendantsOfType<KtProperty>()
                .filter { it.isWidenedVal() }
                .mapNotNull { it.name }
                .toSet()
        function.collectDescendantsOfType<KtBinaryExpressionWithTypeRHS>().forEach { checkCast(it, widened) }
    }

    private fun KtProperty.isWidenedVal(): Boolean {
        val isVal = valOrVarKeyword.text == VAL_KEYWORD
        val typeName = typeReference?.text
        return isVal && (typeName == ANY_TYPE || typeName == NULLABLE_ANY_TYPE)
    }

    private fun checkCast(
        expression: KtBinaryExpressionWithTypeRHS,
        widened: Set<String>,
    ) {
        val target = (expression.left as? KtNameReferenceExpression)?.getReferencedName()
        if (target != null && widened.contains(target)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Widen-then-cast on `$target` — keep the specific type instead of widening to Any (ref #467).",
                ),
            )
        }
    }

    companion object {
        private const val VAL_KEYWORD = "val"
        private const val ANY_TYPE = "Any"
        private const val NULLABLE_ANY_TYPE = "Any?"
    }
}
