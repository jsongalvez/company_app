package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class RequireSafetyComment(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            javaClass.simpleName,
            Severity.Style,
            "Unsafe `as` cast without a preceding SAFETY: + #<ticket> justification.",
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
        val offset = expression.operationReference.textOffset
        val index = file.text.take(offset).count { it == '\n' }
        return file
            .collectDescendantsOfType<PsiComment>()
            .filter { SAFETY_MARKER in it.text && TICKET_REF.containsMatchIn(it.text) }
            .any { comment ->
                val start = file.text.take(comment.textRange.startOffset).count { it == '\n' }
                val end = start + comment.text.count { it == '\n' }
                index in start..end || index == end + 1
            }
    }

    companion object {
        private const val UNSAFE_CAST = "as"
        private const val SAFETY_MARKER = "SAFETY:"
        private val TICKET_REF = Regex("#\\d+")
    }
}
