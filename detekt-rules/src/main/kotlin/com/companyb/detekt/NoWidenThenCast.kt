package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
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
        val ownProperties =
            function
                .collectDescendantsOfType<KtProperty>()
                .filter { it.enclosingNamedFunction() == function }
        val widenedByName = ownProperties.filter { it.isWidenedVal() }.groupBy { it.name }
        if (widenedByName.isEmpty()) return
        val ownParams =
            function
                .collectDescendantsOfType<KtParameter>()
                .filter { it.enclosingNamedFunction() == function }
        function
            .collectDescendantsOfType<KtBinaryExpressionWithTypeRHS>()
            .filter { it.enclosingNamedFunction() == function }
            .forEach { checkCast(it, widenedByName, ownProperties, ownParams) }
    }

    private fun PsiElement.enclosingNamedFunction(): KtNamedFunction? =
        generateSequence(this.parent) { it.parent }
            .filterIsInstance<KtNamedFunction>()
            .firstOrNull()

    private fun KtProperty.isWidenedVal(): Boolean {
        val isVal = valOrVarKeyword.text == VAL_KEYWORD
        val typeName = typeReference?.text
        return isVal && (typeName == ANY_TYPE || typeName == NULLABLE_ANY_TYPE)
    }

    private fun checkCast(
        expression: KtBinaryExpressionWithTypeRHS,
        widenedByName: Map<String?, List<KtProperty>>,
        ownProperties: List<KtProperty>,
        ownParams: List<KtParameter>,
    ) {
        val target = (expression.left as? KtNameReferenceExpression)?.getReferencedName() ?: return
        val candidates = widenedByName[target] ?: return
        if (!resolvesToWidened(expression, target, candidates, ownProperties, ownParams)) return
        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Widen-then-cast on `$target` — keep the specific type instead of widening to Any (ref #467).",
            ),
        )
    }

    // Lexical shadowing heuristic (no binding context): the cast targets the
    // nearest preceding same-name declaration in this function. Out-of-scope
    // sibling declarations with the same name may over-suppress (lenient
    // direction); parenthesized/qualified receivers are not matched.
    private fun resolvesToWidened(
        expression: KtBinaryExpressionWithTypeRHS,
        target: String,
        candidates: List<KtProperty>,
        ownProperties: List<KtProperty>,
        ownParams: List<KtParameter>,
    ): Boolean {
        val castOffset = expression.textOffset
        var nearest: PsiElement? = null
        var nearestOffset = -1
        for (property in ownProperties) {
            if (property.name != target) continue
            val offset = property.textOffset
            if (offset < castOffset && offset > nearestOffset) {
                nearest = property
                nearestOffset = offset
            }
        }
        for (param in ownParams) {
            if (param.name != target) continue
            val offset = param.textOffset
            if (offset < castOffset && offset > nearestOffset) {
                nearest = param
                nearestOffset = offset
            }
        }
        return nearest is KtProperty && candidates.any { it.textOffset == nearest.textOffset }
    }

    companion object {
        private const val VAL_KEYWORD = "val"
        private const val ANY_TYPE = "Any"
        private const val NULLABLE_ANY_TYPE = "Any?"
    }
}
