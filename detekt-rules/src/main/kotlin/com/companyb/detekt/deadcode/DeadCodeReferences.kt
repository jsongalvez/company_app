package com.companyb.detekt.deadcode

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext

// Reference collection for #530: every probe below resolves through the
// compiler binding, never through names or text. Each reference is attributed
// to a marker — its innermost owning declaration, or null when the referrer
// persists outside this gate (test code, private members, failed files).
// Imports are excluded from the walk: an unused import must not keep a dead
// declaration alive (Detekt's UnusedImports owns the import itself).
// Custom-delegate getValue/setValue/provideDelegate operators have no
// element-keyed binding slice, so an operator used only through `by` relies on
// cleanup-time revalidation rather than this walk.
private typealias Mark = (KtDeclaration?, DeclarationDescriptor) -> Unit

object DeadCodeReferences {
    fun collect(
        binding: BindingContext,
        file: KtFile,
        reliable: Boolean,
        mark: Mark,
    ) {
        for (root in scopeRoots(file)) {
            probe(binding, root, markerOf(root, reliable), mark)
            for (element in root.collectDescendantsOfType<KtElement>()) {
                probe(binding, element, markerOf(element, reliable), mark)
            }
        }
    }

    private fun scopeRoots(file: KtFile): List<KtElement> =
        file.children.filter { it !is KtImportList }.filterIsInstance<KtElement>()

    // Transparent scopes never own a reference: accessors die with their
    // property, initializers with their class, parameters with their owner,
    // and locals with their enclosing unit. Everything else that cannot be a
    // candidate (private members, test declarations, failed files) persists,
    // so it marks null and keeps its targets live.
    private fun markerOf(
        element: KtElement,
        reliable: Boolean,
    ): KtDeclaration? {
        if (!reliable) return null
        var node: PsiElement? = element
        while (node != null && node !is KtFile) {
            if (node is KtDeclaration && node.isOwningScope()) return node
            node = node.parent
        }
        return null
    }

    private fun KtDeclaration.isOwningScope(): Boolean {
        if (this is KtPropertyAccessor || this is KtAnonymousInitializer) return false
        if (this is KtParameter || this is KtTypeParameter) return false
        // Enum entries die with their enum: attribute entry arguments to the
        // enum class, otherwise self-construction (USERNAME_TAKEN(label = …))
        // marks the enum through a referrer the gate never deletes.
        if (this is KtEnumEntry) return false
        return !KtPsiUtil.isLocal(this)
    }

    private fun probe(
        binding: BindingContext,
        element: KtElement,
        marker: KtDeclaration?,
        mark: Mark,
    ) {
        probeReference(binding, element, marker, mark)
        probeCall(binding, element, marker, mark)
        probeType(binding, element, marker, mark)
    }

    private fun probeReference(
        binding: BindingContext,
        element: KtElement,
        marker: KtDeclaration?,
        mark: Mark,
    ) {
        if (element is KtSimpleNameExpression && element !is KtLabelReferenceExpression) {
            binding[BindingContext.REFERENCE_TARGET, element]?.let { mark(marker, it) }
        }
        if (element is KtConstructorCalleeExpression) {
            binding[BindingContext.REFERENCE_TARGET, element.constructorReferenceExpression]?.let {
                mark(marker, it)
            }
        }
        if (element is KtExpression) {
            binding[BindingContext.AMBIGUOUS_REFERENCE_TARGET, element]?.forEach { mark(marker, it) }
            loopRange(binding, element, marker, mark)
        }
    }

    private fun loopRange(
        binding: BindingContext,
        element: KtExpression,
        marker: KtDeclaration?,
        mark: Mark,
    ) {
        binding[BindingContext.LOOP_RANGE_ITERATOR_RESOLVED_CALL, element]?.resultingDescriptor?.let {
            mark(marker, it)
        }
        binding[BindingContext.LOOP_RANGE_HAS_NEXT_RESOLVED_CALL, element]?.resultingDescriptor?.let {
            mark(marker, it)
        }
        binding[BindingContext.LOOP_RANGE_NEXT_RESOLVED_CALL, element]?.resultingDescriptor?.let {
            mark(marker, it)
        }
    }

    private fun probeCall(
        binding: BindingContext,
        element: KtElement,
        marker: KtDeclaration?,
        mark: Mark,
    ) {
        binding[BindingContext.CALL, element]
            ?.let { binding[BindingContext.RESOLVED_CALL, it] }
            ?.resultingDescriptor
            ?.let { mark(marker, it) }
        if (element is KtDestructuringDeclarationEntry) {
            binding[BindingContext.COMPONENT_RESOLVED_CALL, element]?.resultingDescriptor?.let {
                mark(marker, it)
            }
        }
    }

    private fun probeType(
        binding: BindingContext,
        element: KtElement,
        marker: KtDeclaration?,
        mark: Mark,
    ) {
        if (element is KtTypeReference) {
            binding[BindingContext.TYPE, element]?.constructor?.declarationDescriptor?.let { mark(marker, it) }
        }
    }
}
