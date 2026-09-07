package com.companyb.detekt.deadcode

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.storage.StorageManager
import java.io.File

data class DeadCodeFinding(
    val path: String,
    val kind: String,
    val signature: String,
    val line: Int,
) {
    // Single-line, whitespace-normalized: renderer output occasionally spans
    // lines (long function types), and baseline entries must stay one per line.
    fun key(): String = "$path|$kind|${signature.normalize()}"

    private fun String.normalize(): String = split(Regex("\\s+")).joinToString(" ").trim()
}

data class DeadCodeResult(
    val findings: List<DeadCodeFinding>,
    val analyzedFiles: Int,
    val failedFiles: List<String>,
    val unresolvedDeclarations: Int,
)

private data class Candidate(
    val declaration: KtDeclaration,
    val kind: String,
    val file: KtFile,
    val sourceFile: File,
    val entryPoint: Boolean,
)

// #530: whole-project semantic unused-declaration analysis. Every input file is
// bound in ONE compiler invocation, so a reference in any file resolves to a
// declaration in any other file. This is what per-file lint (Detekt rules,
// grep occurrence counts) cannot do, and why the detector lives here instead
// of as a Detekt rule (whose findings are harvested per file).
object DeadCodeAnalyzer {
    private const val DEADCODE_MODULE_NAME = "deadcode"

    fun analyze(
        root: File,
        candidateFiles: List<File>,
        consumerFiles: List<File>,
        classpath: List<File>,
    ): DeadCodeResult {
        val disposable = Disposer.newDisposable()
        try {
            return analyzeWithDisposable(root, candidateFiles, consumerFiles, classpath, disposable)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun analyzeWithDisposable(
        root: File,
        candidateFiles: List<File>,
        consumerFiles: List<File>,
        classpath: List<File>,
        disposable: Disposable,
    ): DeadCodeResult {
        val configuration =
            CompilerConfiguration().apply {
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                put(CommonConfigurationKeys.MODULE_NAME, DEADCODE_MODULE_NAME)
                addJvmClasspathRoots(classpath.filter { it.exists() })
            }
        val environment =
            KotlinCoreEnvironment.createForProduction(
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
        val factory = KtPsiFactory(environment.project, markGenerated = false)
        val allFiles = (candidateFiles + consumerFiles).distinct().sorted()
        val ktFiles = allFiles.associateWith { factory.createFile(it.absolutePath, it.readText()) }
        val failed = ktFiles.filter { hasSyntaxErrors(it.value) }.keys
        val failedPaths = failed.map { relativize(root, it) }.sorted()

        val trace = NoScopeRecordCliBindingTrace(environment.project)
        val result =
            TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                environment.project,
                ktFiles.values,
                trace,
                configuration,
                { scope: GlobalSearchScope -> environment.createPackagePartProvider(scope) },
                { storageManager: StorageManager, files: Collection<KtFile> ->
                    FileBasedDeclarationProviderFactory(storageManager, files)
                },
            )
        val binding = result.bindingContext

        val candidates = collectCandidates(ktFiles, failed, candidateFiles.toSet())
        val descriptorByCandidate = matchDescriptors(binding, candidates)
        val unresolved = candidates.count { descriptorByCandidate[it] == null }
        val inputs = AnalysisInputs(binding, ktFiles, failed, candidateFiles.toSet())
        val markers = collectMarkers(inputs, candidates, descriptorByCandidate)
        propagateOverrideBases(descriptorByCandidate, markers)
        val live = computeLive(candidates, descriptorByCandidate, markers)

        val findings =
            candidates
                .filter { it !in live }
                .map { candidate ->
                    val descriptor = requireNotNull(descriptorByCandidate[candidate]).original
                    DeadCodeFinding(
                        path = relativize(root, candidate.sourceFile),
                        kind = candidate.kind,
                        signature = DescriptorRenderer.FQ_NAMES_IN_TYPES.render(descriptor),
                        line = lineNumber(candidate.file, candidate.declaration),
                    )
                }.sortedWith(compareBy({ it.path }, { it.kind }, { it.signature }))

        return DeadCodeResult(
            findings = findings,
            analyzedFiles = ktFiles.size - failed.size,
            failedFiles = failedPaths,
            unresolvedDeclarations = unresolved,
        )
    }

    private fun hasSyntaxErrors(file: KtFile): Boolean = file.collectDescendantsOfType<PsiErrorElement>().any()

    private fun collectCandidates(
        ktFiles: Map<File, KtFile>,
        failed: Set<File>,
        candidateRoots: Set<File>,
    ): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for ((source, ktFile) in ktFiles) {
            if (source in failed || source !in candidateRoots) continue
            for (declaration in ktFile.collectDescendantsOfType<KtDeclaration>()) {
                toCandidate(declaration, ktFile, source)?.let { out.add(it) }
            }
        }
        return out
    }

    // Non-null return = reportable candidate (entry-point ones included, flagged).
    // Null = out of this gate's scope (Detekt/compiler own private/local/synthesized).
    private fun toCandidate(
        declaration: KtDeclaration,
        ktFile: KtFile,
        source: File,
    ): Candidate? {
        val kind = candidateKind(declaration)
        if (kind == null || !declaration.hasReportableName(kind)) return null
        return Candidate(
            declaration = declaration,
            kind = kind,
            file = ktFile,
            sourceFile = source,
            entryPoint = isEntryPoint(declaration),
        )
    }

    private fun candidateKind(declaration: KtDeclaration): String? {
        if (KtPsiUtil.isLocal(declaration) || declaration.isEffectivelyPrivate()) return null
        return when (declaration) {
            is KtEnumEntry,
            is KtPropertyAccessor,
            is KtPrimaryConstructor,
            is KtTypeParameter,
            -> null

            is KtSecondaryConstructor -> "constructor"

            is KtNamedFunction -> "function"

            is KtProperty -> "property"

            is KtTypeAlias -> "typealias"

            is KtObjectDeclaration -> "object"

            is KtClassOrObject -> "class"

            else -> null
        }
    }

    private fun KtDeclaration.hasReportableName(kind: String): Boolean =
        when {
            kind == "constructor" -> true
            this is KtNamedFunction -> !name.isNullOrEmpty()
            this is KtProperty -> !name.isNullOrEmpty()
            this is KtTypeAlias -> !name.isNullOrEmpty()
            this is KtClassOrObject -> !name.isNullOrEmpty()
            else -> false
        }

    private fun KtDeclaration.isEffectivelyPrivate(): Boolean {
        if ((this as? KtModifierListOwner)?.hasModifier(KtTokens.PRIVATE_KEYWORD) == true) return true
        return generateSequence(parent) { it.parent }
            .takeWhile { it !is KtFile }
            .filterIsInstance<KtClassOrObject>()
            .any { it.hasModifier(KtTokens.PRIVATE_KEYWORD) }
    }

    // Implicit entry points: the compiler/framework guarantees invocation without
    // a statically visible source reference, so absence of references proves nothing.
    // Sealed heirs are deliberately NOT entry points: live heirs are marked by
    // `is`-checks, instantiation, and registrations, while entry-pointing them
    // would immortalize whole dead hierarchies (the sealed parent is always
    // named by its heirs, so it can never report either).
    private fun isEntryPoint(declaration: KtDeclaration): Boolean {
        if (declaration.inExpectOrActualScope()) return true
        if ((declaration as? KtModifierListOwner)?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true) return true
        if (declaration is KtObjectDeclaration && declaration.isCompanion()) return true
        return declaration is KtNamedFunction && declaration.isMainEntry()
    }

    private fun KtDeclaration.inExpectOrActualScope(): Boolean {
        var owner: KtModifierListOwner? = this as? KtModifierListOwner ?: modifierListOwnerParent()
        while (owner != null) {
            if (owner.hasModifier(KtTokens.EXPECT_KEYWORD) || owner.hasModifier(KtTokens.ACTUAL_KEYWORD)) return true
            owner =
                generateSequence(owner.parent) { it.parent }
                    .takeWhile { it !is KtFile }
                    .filterIsInstance<KtModifierListOwner>()
                    .firstOrNull()
        }
        return false
    }

    private fun KtDeclaration.modifierListOwnerParent(): KtModifierListOwner? =
        generateSequence(parent) { it.parent }
            .takeWhile { it !is KtFile }
            .filterIsInstance<KtModifierListOwner>()
            .firstOrNull()

    private fun KtNamedFunction.isMainEntry(): Boolean {
        if (name != "main" || parent !is KtFile) return false
        return valueParameters.size <= 1
    }

    private fun matchDescriptors(
        binding: BindingContext,
        candidates: List<Candidate>,
    ): Map<Candidate, DeclarationDescriptor?> =
        candidates.associateWith { binding[BindingContext.DECLARATION_TO_DESCRIPTOR, it.declaration] }

    // Marker attribution for the liveness closure below. A reference is
    // attributed to its innermost owning candidate (null = persistent: test
    // code, private members, failed files — referrers this gate never
    // deletes, so their targets stay live). A candidate is live when any
    // marker on it is persistent or live; otherwise it cascades dead with its
    // markers. Self-markers (recursion, delegation, `this`) can never confer
    // liveness, and dead reference cycles report together instead of hiding.
    private data class AnalysisInputs(
        val binding: BindingContext,
        val ktFiles: Map<File, KtFile>,
        val failed: Set<File>,
        val candidateRoots: Set<File>,
    )

    private fun collectMarkers(
        inputs: AnalysisInputs,
        candidates: List<Candidate>,
        descriptorByCandidate: Map<Candidate, DeclarationDescriptor?>,
    ): Map<Candidate, MutableSet<Candidate?>> {
        val markers = candidates.associateWith { mutableSetOf<Candidate?>() }
        val candidateByPsi = candidates.associateBy { it.declaration }
        val reverse = reverseDescriptors(descriptorByCandidate)
        for ((source, ktFile) in inputs.ktFiles) {
            val reliable = source in inputs.candidateRoots && source !in inputs.failed
            DeadCodeReferences.collect(inputs.binding, ktFile, reliable) { marker, target ->
                val owner = marker?.let { candidateByPsi[it] }
                propagate(markers, reverse, owner, target)
            }
        }
        return markers
    }

    private fun reverseDescriptors(descriptorByCandidate: Map<Candidate, DeclarationDescriptor?>): DescriptorIndex {
        val byInstance = mutableMapOf<DeclarationDescriptor, Candidate>()
        val bySignature = mutableMapOf<String, Candidate>()
        for ((candidate, descriptor) in descriptorByCandidate) {
            val original = descriptor?.original ?: continue
            byInstance[original] = candidate
            bySignature[DescriptorRenderer.FQ_NAMES_IN_TYPES.render(original)] = candidate
        }
        return DescriptorIndex(byInstance, bySignature)
    }

    private data class DescriptorIndex(
        val byInstance: Map<DeclarationDescriptor, Candidate>,
        val bySignature: Map<String, Candidate>,
    ) {
        // Instance identity is the fast path; the signature fallback covers
        // descriptors the compiler materializes per use-site (member-scope
        // extensions resolve to copies whose identity differs from the
        // declaration-site descriptor while rendering identically).
        fun resolve(descriptor: DeclarationDescriptor): Candidate? {
            val original = descriptor.original
            return byInstance[original]
                ?: bySignature[DescriptorRenderer.FQ_NAMES_IN_TYPES.render(original)]
        }
    }

    private fun propagate(
        markers: Map<Candidate, MutableSet<Candidate?>>,
        reverse: DescriptorIndex,
        marker: Candidate?,
        target: DeclarationDescriptor,
    ) {
        var current: DeclarationDescriptor? = target.original
        while (current != null && current !is ModuleDescriptor) {
            reverse.resolve(current)?.let { markers.getValue(it).add(marker) }
            current = current.containingDeclaration
        }
    }

    // An override implementation is only reachable while its overridden base
    // is: the base's markers keep the override alive, so deleting a dead
    // hierarchy takes its implementations with it instead of stranding
    // unresolvable findings on live interfaces.
    private fun propagateOverrideBases(
        descriptorByCandidate: Map<Candidate, DeclarationDescriptor?>,
        markers: Map<Candidate, MutableSet<Candidate?>>,
    ) {
        val reverse = reverseDescriptors(descriptorByCandidate)
        for ((candidate, descriptor) in descriptorByCandidate) {
            val member = descriptor as? CallableMemberDescriptor ?: continue
            for (overridden in member.overriddenDescriptors) {
                reverse.resolve(overridden.original)?.let { base ->
                    markers.getValue(candidate).addAll(markers.getValue(base))
                }
            }
        }
    }

    private fun computeLive(
        candidates: List<Candidate>,
        descriptorByCandidate: Map<Candidate, DeclarationDescriptor?>,
        markers: Map<Candidate, MutableSet<Candidate?>>,
    ): Set<Candidate> {
        val live = mutableSetOf<Candidate>()
        for (candidate in candidates) {
            if (candidate.entryPoint || descriptorByCandidate[candidate] == null) live.add(candidate)
        }
        var grew = true
        while (grew) {
            grew = false
            for (candidate in candidates) {
                if (candidate !in live && markers.getValue(candidate).any { it == null || it in live }) {
                    live.add(candidate)
                    grew = true
                }
            }
        }
        return live
    }

    private fun relativize(
        root: File,
        file: File,
    ): String = runCatching { file.relativeTo(root).path }.getOrDefault(file.absolutePath)

    private fun lineNumber(
        file: KtFile,
        declaration: KtDeclaration,
    ): Int {
        val text = file.text
        val anchor = (declaration as? KtNamedDeclaration)?.nameIdentifier ?: declaration
        return text.lineNumberUpTo(anchor.textRange.startOffset)
    }

    private fun String.lineNumberUpTo(offset: Int): Int {
        var line = 1
        val end = offset.coerceIn(0, length)
        for (index in 0 until end) {
            if (this[index] == '\n') line++
        }
        return line
    }
}
