package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList

/**
 * A function that opens a database transaction or sends an HTTP request has its whole body wrapped
 * in `traced("Owner.function") { ... }`, so every such call shows up as a span under its function's
 * name. A route handler lambda (`config.get(path, gate) { ... }`) needs none: the request's root span
 * covers it.
 */
class TracedAtDefinition(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            javaClass.simpleName,
            Severity.Maintainability,
            "A function that reads the database or calls out wraps its whole body in traced(\"Owner.function\").",
            Debt.FIVE_MINS,
        )

    private val outCalls = setOf("transaction", "newSuspendedTransaction", "suspendTransaction")
    private val httpSends = setOf("send", "sendAsync")
    private val routeHelpers = setOf("get", "post", "put", "patch", "delete")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!callsOut(expression)) return
        val function = owningFunction(expression) ?: return
        val expected = expectedName(function)
        val wrap = wrapper(function)
        when {
            wrap == null -> {
                report(function, "Wrap the body of ${function.name} in traced(\"$expected\") { ... }.")
            }

            wrap != expected -> {
                report(function, "traced(\"$wrap\") names another function: use traced(\"$expected\").")
            }
        }
    }

    private val reported = HashSet<KtNamedFunction>()

    private fun report(
        function: KtNamedFunction,
        message: String,
    ) {
        if (reported.add(function)) report(CodeSmell(issue, Entity.atName(function), message))
    }

    private fun callsOut(call: KtCallExpression): Boolean {
        val name = call.calleeExpression?.text ?: return false
        if (name in outCalls) return true
        return name in httpSends && call.valueArguments.any { "BodyHandlers" in it.text }
    }

    /** The named function this call runs in, or null when a route handler lambda holds it. */
    private fun owningFunction(call: KtCallExpression): KtNamedFunction? =
        generateSequence(call.parent) { it.parent }
            .firstOrNull { it is KtNamedFunction || (it is KtLambdaExpression && isRouteHandler(it)) }
            as? KtNamedFunction

    private fun isRouteHandler(lambda: KtLambdaExpression): Boolean {
        val argument = lambda.parent as? KtLambdaArgument ?: return false
        val call = argument.parent as? KtCallExpression ?: return false
        return call.calleeExpression?.text in routeHelpers && call.valueArguments.any { "Gate." in it.text }
    }

    private fun expectedName(function: KtNamedFunction): String {
        val owner =
            generateSequence(function.parent) { it.parent }
                .filterIsInstance<KtClassOrObject>()
                .firstOrNull { !(it is KtObjectDeclaration && it.isCompanion()) }
                ?.name
                ?: function.containingKtFile.name.removeSuffix(".kt")
        return "$owner.${function.name}"
    }

    /**
     * The span name when the body is one `traced("...") { }` call, after guard clauses at most
     * (`if (x) throw/return`, `val y = z ?: throw/return`, `require`, `check`), else null.
     */
    private fun wrapper(function: KtNamedFunction): String? {
        val body = function.bodyExpression
        val statements = (body as? KtBlockExpression)?.statements
        val last =
            when {
                statements == null -> {
                    body
                }

                statements.dropLast(1).all(::isGuard) -> {
                    statements.lastOrNull()?.let { (it as? KtReturnExpression)?.returnedExpression ?: it }
                }

                else -> {
                    null
                }
            }
        val call = (last as? KtCallExpression)?.takeIf { it.calleeExpression?.text == "traced" }
        val args = call?.children?.filterIsInstance<KtValueArgumentList>()?.singleOrNull()
        val literal = args?.arguments?.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
        return literal?.takeUnless { it.hasInterpolation() }?.text?.trim('"')
    }

    private fun isGuard(statement: KtExpression): Boolean =
        when (statement) {
            is KtIfExpression -> {
                statement.`else` == null && exits(statement.then)
            }

            is KtProperty -> {
                val elvis = statement.initializer as? KtBinaryExpression
                elvis?.operationToken == KtTokens.ELVIS && exits(elvis.right)
            }

            is KtCallExpression -> {
                statement.calleeExpression?.text in guardCalls
            }

            else -> {
                false
            }
        }

    private fun exits(branch: KtExpression?): Boolean {
        val only = (branch as? KtBlockExpression)?.statements?.singleOrNull() ?: branch
        return only is KtThrowExpression || only is KtReturnExpression
    }

    private val guardCalls = setOf("require", "check", "requireNotNull", "checkNotNull")
}
