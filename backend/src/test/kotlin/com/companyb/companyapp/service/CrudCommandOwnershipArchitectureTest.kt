package com.companyb.companyapp.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #323 batches 1–6 ownership seam (ADR-0024). Cheap source-level assertions that the migrated
 * modules (Branch, ProductCategory, Product, Client, Allowance, Compensation, session cluster,
 * user/access cluster, inventory/product-sale cluster, commission cluster) keep their shape:
 * mutating store operations are `*InTransaction` with no `auditFn` coordination and no nested
 * write transactions, and each public command owns exactly one transaction while persistence-table
 * knowledge stays behind the feature/audit seam. Mechanical enforcement for the whole backend
 * arrives with #324; this pins these migration batches until then.
 */
class CrudCommandOwnershipArchitectureTest {
    private fun mainSource(relative: String): String =
        File("backend/src/main/kotlin/com/companyb/companyapp/$relative").readText()

    @Test
    fun `crud stores have no nested write transactions or audit callbacks`() {
        // (file, allowed read-wrapper transaction blocks)
        val files =
            mapOf(
                "branch/BranchRepository.kt" to 3,
                "commerce/ProductCategoryRepository.kt" to 2,
                "commerce/ProductRepository.kt" to 4,
                // Three read wrappers after the authoritative session-count query.
                "client/ClientRepository.kt" to 3,
                "repository/AllowanceRepository.kt" to 1,
                "repository/CompensationRepository.kt" to 3,
                // Five read-only wrappers: #453 dropped the audit-read createdBy
                // wrapper (ownership is now transaction-local created_by).
                "session/SessionRepository.kt" to 5,
                "session/SessionVoidRepository.kt" to 2,
                "session/SessionPractitionerRepository.kt" to 2,
                "session/ConcernRepository.kt" to 3,
                "session/SessionBaseRateRepository.kt" to 1,
                // Batch 4 — user/access cluster.
                // Six read wrappers after #492 (removed findJwtRevocationBoundaries startup
                // scan; authorize stays one query, advanceRevocationBoundary is InTransaction)
                // plus the #537 display-name read (merged from the retired UserDisplayNames helper).
                "identity/UserRepository.kt" to 7,
                "workforce/UserBranchAssignmentRepository.kt" to 3,
                // 7 = five request/read blocks + hasActiveClockIn + isActiveUser read wrappers
                "workforce/relief/ReliefAccessRepository.kt" to 7,
                // 14 regex hits = thirteen read wrappers + the #374 scan + the #401 by-date read
                // plus one "transaction (" prose match in the accept comment (pin counts hits, not blocks).
                "workforce/relief/ReliefInviteRepository.kt" to 14,
                // findById + branch list read wrappers; mutation stores remain in-transaction.
                "workforce/relief/MedicalMissionDelegateRepository.kt" to 2,
                // Batch 5 — inventory/product-sale cluster.
                "commerce/ProductSaleRepository.kt" to 2,
                "commerce/BranchInventoryRepository.kt" to 4,
                // Batch 6 — commission cluster.
                "commission/CommissionSplitRepository.kt" to 1,
                "commission/CommissionManualInclusionRepository.kt" to 3,
            )
        files.forEach { (file, expectedBlocks) ->
            val source = mainSource(file)

            assertFalse(source.contains("auditFn"), "$file: auditFn coordination must be retired (#323 deletion test)")
            assertTrue(source.contains("InTransaction("), "$file: must expose in-transaction store operations")
            val blocks = Regex("""\btransaction\s*[(\{]""").findAll(source).count()
            assertEquals(expectedBlocks, blocks, "$file: read-only wrappers only")
        }
    }

    @Test
    fun `each crud mutation command owns exactly one transaction`() {
        val commands =
            mapOf(
                "branch/BranchService.kt" to listOf("create"),
                "commerce/ProductCategoryService.kt" to listOf("create"),
                "commerce/ProductService.kt" to listOf("create", "update"),
                "client/ClientService.kt" to listOf("create", "update", "anonymize"),
                "service/AllowanceService.kt" to listOf("create"),
                "service/CompensationService.kt" to listOf("create", "update"),
                // Session cluster (batch 3): pass-through delegates in SessionService are not
                // commands; the transaction-owning mutations live in these four files.
                "session/SessionService.kt" to
                    listOf("create", "updateStatus", "updateFinalPrice", "voidSession", "unvoidSession"),
                "session/SessionPractitionerService.kt" to
                    listOf("addPractitioner", "updatePractitionerRemarks", "removePractitioner"),
                "session/SessionConcernService.kt" to
                    listOf("addToSession", "removeFromSession", "promoteConcern"),
                "session/SessionBaseRateService.kt" to listOf("setRate"),
                // Batch 4 — user/access cluster.
                "identity/UserService.kt" to listOf("deactivate", "reactivate"),
                "workforce/UserBranchAssignmentService.kt" to
                    listOf("create", "remove", "updateSlot", "swapSlots"),
                "workforce/relief/ReliefAccessService.kt" to
                    listOf("grantAccess", "denyAccess", "requestReliefAccess", "cancelRequest"),
                "workforce/relief/ReliefInviteService.kt" to
                    listOf("createInvite", "acceptInvite", "declineInvite", "retractInvite", "revokeInvite"),
                "workforce/relief/MedicalMissionDelegateService.kt" to listOf("assignDelegate", "revokeDelegate"),
                // Batch 5 — inventory/product-sale cluster.
                "commerce/ProductSaleService.kt" to listOf("sell"),
                "commerce/InventoryService.kt" to listOf("recordMovement", "ensureCard"),
                // Batch 6 — commission cluster. recalculate remains the standalone entry point
                // (manualRecalculate); sell/clock-in/clock-out call recalculateInTransaction
                // inside their own commands instead of nesting this module's write block.
                "commission/CommissionService.kt" to
                    listOf("createManualInclusion", "recalculate"),
            )
        commands.forEach { (file, names) ->
            val source = mainSource(file)
            assertFalse(source.contains("auditFn"), "$file: commands call the audit seam directly")

            names.forEach { command ->
                val body =
                    commandBody(source, command)
                        ?: error("command $command not found in $file")
                val blocks = Regex("""\btransaction\s*[(\{]""").findAll(body).count()
                assertEquals(1, blocks, "$file: command $command must open exactly one transaction, found $blocks")
            }
        }
    }

    @Test
    fun `public crud command surfaces hold no persistence-table knowledge`() {
        // The `<Feature>Audit` seam objects own the *Table imports; the public service object body
        // between its declaration and the seam must not touch persistence tables.
        val seams =
            mapOf(
                "branch/BranchService.kt" to "BranchAudit",
                "commerce/ProductCategoryService.kt" to "ProductCategoryAudit",
                "commerce/ProductService.kt" to "ProductAudit",
                "client/ClientService.kt" to "ClientAudit",
                "service/AllowanceService.kt" to "AllowanceAudit",
                "service/CompensationService.kt" to "CompensationAudit",
                "session/SessionService.kt" to "SessionAudit",
                "session/SessionPractitionerService.kt" to "SessionPractitionerAudit",
                "session/SessionConcernService.kt" to "SessionConcernAudit",
                "session/SessionBaseRateService.kt" to "SessionBaseRateAudit",
                // Batch 4 — user/access cluster.
                "identity/UserService.kt" to "UserAudit",
                "workforce/UserBranchAssignmentService.kt" to "UserBranchAssignmentAudit",
                "workforce/relief/ReliefAccessService.kt" to "ReliefAccessAudit",
                "workforce/relief/ReliefInviteService.kt" to "ReliefInviteAudit",
                "workforce/relief/MedicalMissionDelegateService.kt" to "MedicalMissionDelegateAudit",
                // Batch 5 — inventory/product-sale cluster.
                "commerce/ProductSaleService.kt" to "ProductSaleAudit",
                "commerce/InventoryService.kt" to "BranchInventoryAudit",
                // Batch 6 — commission cluster.
                "commission/CommissionService.kt" to "CommissionAudit",
            )
        seams.forEach { (file, seam) ->
            val source = mainSource(file)
            val serviceBody = source.substringBefore("internal object $seam")

            assertTrue(source.contains("internal object $seam"), "$file: audit seam $seam missing")
            assertFalse(
                Regex("""\b\w+Table\b""").containsMatchIn(serviceBody.substringAfter("object ")),
                "$file: persistence-table knowledge leaked into the public command surface (#323)",
            )
        }
    }

    private fun commandBody(
        source: String,
        command: String,
    ): String? {
        val marker = "fun $command("
        val start = source.indexOf(marker).takeIf { it >= 0 } ?: return null
        val nextFun = source.indexOf("\n    fun ", start + marker.length).takeIf { it >= 0 } ?: source.length
        return source.substring(start, nextFun)
    }
}
