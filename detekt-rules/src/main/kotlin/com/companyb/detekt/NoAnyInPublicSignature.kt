package com.companyb.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

class NoAnyInPublicSignature(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            javaClass.simpleName,
            Severity.Style,
            "Public signatures must not use `Any`, `Any?`, or `object` as parameter or return types.",
            Debt.TEN_MINS,
        )

    private val bannedSimpleNames = setOf("Any", "object")

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.isPublic() || function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
        val typeAliasMap = (function.containingFile as? KtFile)?.typeAliasMap() ?: emptyMap()
        function.valueParameters.forEach { param ->
            param.typeReference?.let { checkType(it, typeAliasMap) }
        }
        function.typeReference?.let { checkType(it, typeAliasMap) }
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        val owner =
            generateSequence(property.parent) { it.parent }
                .filterIsInstance<KtClass>()
                .firstOrNull()
        if (owner?.isInterface() != true) return
        val typeAliasMap = (property.containingFile as? KtFile)?.typeAliasMap() ?: emptyMap()
        property.typeReference?.let { checkType(it, typeAliasMap) }
    }

    private fun KtNamedFunction.isPublic(): Boolean {
        val ml = modifierList ?: return true
        return !ml.hasModifier(KtTokens.PRIVATE_KEYWORD) &&
            !ml.hasModifier(KtTokens.INTERNAL_KEYWORD) &&
            !ml.hasModifier(KtTokens.PROTECTED_KEYWORD)
    }

    private fun checkType(
        typeRef: KtTypeReference,
        typeAliasMap: Map<String, String>,
    ) {
        val name = typeRef.bannedSimpleName(typeAliasMap)
        if (name != null) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(typeRef),
                    "Public signature uses `$name` — use a specific type instead (ref #466).",
                ),
            )
        }
    }

    private fun KtTypeReference.bannedSimpleName(typeAliasMap: Map<String, String>): String? {
        val direct =
            (typeElement as? KtUserType)?.referencedName
                ?: ((typeElement as? KtNullableType)?.innerType as? KtUserType)?.referencedName
        return direct?.let { resolveBanned(it, typeAliasMap) }
    }

    private fun resolveBanned(
        name: String,
        typeAliasMap: Map<String, String>,
    ): String? {
        if (name in bannedSimpleNames) return name
        val underlying = typeAliasMap[name] ?: return null
        return if (underlying in bannedSimpleNames) underlying else null
    }

    private fun KtFile.typeAliasMap(): Map<String, String> =
        declarations
            .filterIsInstance<KtTypeAlias>()
            .mapNotNull { alias ->
                val name = alias.name ?: return@mapNotNull null
                val underlying =
                    (alias.getTypeReference()?.typeElement as? KtUserType)?.referencedName
                        ?: return@mapNotNull null
                name to underlying
            }.toMap()
}
