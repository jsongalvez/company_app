package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeReference

class NoRuntimeTypeCheck(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            javaClass.simpleName,
            Severity.Style,
            "Ad-hoc runtime type checks on weak types belong in named boundary parsers.",
            Debt.TEN_MINS,
        )

    override fun visitIsExpression(expression: KtIsExpression) {
        super.visitIsExpression(expression)
        val root = expression.typeReference?.weakRoot()
        if (root != null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Ad-hoc `is` check on `$root` — move it behind a named boundary parser (ref #468).",
                ),
            )
        }
    }

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
        super.visitBinaryWithTypeRHSExpression(expression)
        val root =
            expression.right
                .takeIf { expression.operationReference.text in CAST_OPERATORS }
                ?.weakRoot()
        if (root != null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Ad-hoc `as` cast to `$root` — move it behind a named boundary parser (ref #468).",
                ),
            )
        }
    }

    override fun visitClassLiteralExpression(expression: KtClassLiteralExpression) {
        super.visitClassLiteralExpression(expression)
        if (expression.receiverExpression?.isInstanceReceiver() == true) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Instance `::class` narrowing — move it behind a named boundary parser (ref #468).",
                ),
            )
        }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (expression.isJavaClassAccess()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Runtime `javaClass` narrowing — move it behind a named boundary parser (ref #468).",
                ),
            )
        }
    }

    override fun visitSafeQualifiedExpression(expression: KtSafeQualifiedExpression) {
        super.visitSafeQualifiedExpression(expression)
        if (expression.isJavaClassAccess()) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(expression),
                    "Runtime `javaClass` narrowing — move it behind a named boundary parser (ref #468).",
                ),
            )
        }
    }

    private fun KtTypeReference.weakRoot(): String? {
        var text = text.substringAfterLast(DOT).trim()
        while (text.endsWith(NULLABLE_SUFFIX)) text = text.dropLast(1).trim()
        val generic = text.indexOf(GENERIC_OPEN)
        if (generic >= 0) text = text.substring(0, generic).trim()
        return text.takeIf { it in WEAK_ROOTS }
    }

    private fun KtExpression.isInstanceReceiver(): Boolean =
        when (this) {
            is KtNameReferenceExpression -> {
                val name = getReferencedName()
                name == THIS_LITERAL || name.firstOrNull()?.isLowerCase() == true
            }

            is KtThisExpression -> {
                true
            }

            is KtDotQualifiedExpression -> {
                selectorExpression?.isInstanceReceiver() ?: true
            }

            is KtSafeQualifiedExpression -> {
                selectorExpression?.isInstanceReceiver() ?: true
            }

            else -> {
                true
            }
        }

    private fun KtExpression.isJavaClassAccess(): Boolean =
        (this as? KtDotQualifiedExpression)?.selectorExpression?.selectorName() == JAVA_CLASS ||
            (this as? KtSafeQualifiedExpression)?.selectorExpression?.selectorName() == JAVA_CLASS

    private fun KtExpression.selectorName(): String? = (this as? KtNameReferenceExpression)?.getReferencedName()

    companion object {
        private val WEAK_ROOTS = setOf("Any", "Map", "MutableMap")
        private val CAST_OPERATORS = setOf("as", "as?")
        private const val DOT = "."
        private const val GENERIC_OPEN = '<'
        private const val NULLABLE_SUFFIX = "?"
        private const val THIS_LITERAL = "this"
        private const val JAVA_CLASS = "javaClass"
    }
}
