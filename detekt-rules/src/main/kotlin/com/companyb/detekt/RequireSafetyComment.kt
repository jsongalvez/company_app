package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtFile

class RequireSafetyComment(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            javaClass.simpleName,
            Severity.Style,
            "Unsafe `as` cast without a preceding SAFETY: justification.",
            Debt.TEN_MINS,
        )

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
        super.visitBinaryWithTypeRHSExpression(expression)
        if (expression.operationReference.text == UNSAFE_CAST && !hasSafetyComment(expression)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Unsafe `as` needs a preceding `SAFETY:` comment with a #<ticket> link (ref #467).",
                ),
            )
        }
    }

    private fun hasSafetyComment(expression: KtBinaryExpressionWithTypeRHS): Boolean {
        val file = expression.containingFile as? KtFile ?: return false
        val lines = file.text.split('\n')
        val offset = expression.operationReference.textOffset
        val index = file.text.take(offset).count { it == '\n' }
        return SAFETY_MARKER in lines.getOrElse(index) { "" } ||
            (index > 0 && SAFETY_MARKER in lines.getOrElse(index - 1) { "" })
    }

    companion object {
        private const val UNSAFE_CAST = "as"
        private const val SAFETY_MARKER = "SAFETY:"
    }
}
