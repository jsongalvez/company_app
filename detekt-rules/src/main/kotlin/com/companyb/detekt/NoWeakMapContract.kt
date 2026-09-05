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
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType

class NoWeakMapContract(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            javaClass.simpleName,
            Severity.Style,
            "Public signatures must not use Map<String, Any?> / Any / object values.",
            Debt.TEN_MINS,
        )

    private val bannedSimpleNames = setOf("Any", "object")
    private val mapTypes = setOf("Map", "MutableMap")

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
        val userType = typeRef.typeElement as? KtUserType
        if (userType != null && userType.isWeakMapContract(typeAliasMap)) {
            report(
                CodeSmell(
                    issue,
                    Entity.from(typeRef),
                    "Weak map `Map<String, Any?>` in public signature — use a specific type (ref #466).",
                ),
            )
        }
    }

    private fun KtUserType.isWeakMapContract(typeAliasMap: Map<String, String>): Boolean {
        if ((typeAliasMap[referencedName] ?: referencedName) !in mapTypes) return false
        if (typeArguments.size != 2) return false
        return hasStringKey(typeArguments[0]) && hasBannedValue(typeArguments[1], typeAliasMap)
    }

    private fun hasStringKey(arg: KtTypeProjection): Boolean =
        (arg.typeReference?.typeElement as? KtUserType)?.referencedName == "String"

    private fun hasBannedValue(
        arg: KtTypeProjection,
        typeAliasMap: Map<String, String>,
    ): Boolean {
        val elem = arg.typeReference?.typeElement
        val valueName =
            (elem as? KtUserType)?.referencedName
                ?: ((elem as? KtNullableType)?.innerType as? KtUserType)?.referencedName
        return isBannedValueName(valueName, typeAliasMap)
    }

    private fun isBannedValueName(
        valueName: String?,
        typeAliasMap: Map<String, String>,
    ): Boolean {
        if (valueName == null) return false
        if (valueName in bannedSimpleNames) return true
        return typeAliasMap[valueName] in bannedSimpleNames
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
