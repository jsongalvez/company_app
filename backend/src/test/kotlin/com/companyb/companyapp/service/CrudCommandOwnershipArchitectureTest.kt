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
                "repository/ProductCategoryRepository.kt" to 2,
                "repository/ProductRepository.kt" to 4,
                // Three read wrappers after the authoritative session-count query.
                "repository/ClientRepository.kt" to 3,
                "repository/AllowanceRepository.kt" to 1,
                "repository/CompensationRepository.kt" to 3,
                // Five read-only wrappers: #453 dropped the audit-read createdBy
                // wrapper (ownership is now transaction-local created_by).
                "repository/SessionRepository.kt" to 5,
                "repository/SessionVoidRepository.kt" to 2,
                "repository/SessionPractitionerRepository.kt" to 2,
                "repository/ConcernRepository.kt" to 3,
                "repository/SessionBaseRateRepository.kt" to 1,
                // Batch 4 — user/access cluster.
                // Six read wrappers after #492 (removed findJwtRevocationBoundaries startup
                // scan; authorize stays one query, advanceRevocationBoundary is InTransaction).
                "repository/UserRepository.kt" to 6,
                "repository/UserBranchAssignmentRepository.kt" to 3,
                // 7 = five request/read blocks + hasActiveClockIn + isActiveUser read wrappers
                "repository/ReliefAccessRepository.kt" to 7,
                // 13 = twelve find/isActive read wrappers + the #374 accepted-duty scan + the #401 by-date read
                "repository/ReliefInviteRepository.kt" to 13,
                // findById + branch list read wrappers; mutation stores remain in-transaction.
                "repository/MedicalMissionDelegateRepository.kt" to 2,
                // Batch 5 — inventory/product-sale cluster.
                "repository/ProductSaleRepository.kt" to 2,
                "repository/BranchInventoryRepository.kt" to 4,
                // Batch 6 — commission cluster.
                "repository/CommissionSplitRepository.kt" to 1,
                "repository/CommissionManualInclusionRepository.kt" to 3,
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
                "service/ProductCategoryService.kt" to listOf("create"),
                "service/ProductService.kt" to listOf("create", "update"),
                "service/ClientService.kt" to listOf("create", "update", "anonymize"),
                "service/AllowanceService.kt" to listOf("create"),
                "service/CompensationService.kt" to listOf("create", "update"),
                // Session cluster (batch 3): pass-through delegates in SessionService are not
                // commands; the transaction-owning mutations live in these four files.
                "service/session/SessionService.kt" to
                    listOf("create", "updateStatus", "updateFinalPrice", "voidSession", "unvoidSession"),
                "service/session/SessionPractitionerService.kt" to
                    listOf("addPractitioner", "updatePractitionerRemarks", "removePractitioner"),
                "service/session/SessionConcernService.kt" to
                    listOf("addToSession", "removeFromSession", "promoteConcern"),
                "service/session/SessionBaseRateService.kt" to listOf("setRate"),
                // Batch 4 — user/access cluster.
                "service/UserService.kt" to listOf("deactivate", "reactivate"),
                "service/UserBranchAssignmentService.kt" to
                    listOf("create", "remove", "updateSlot", "swapSlots"),
                "service/ReliefAccessService.kt" to
                    listOf("grantAccess", "denyAccess", "requestReliefAccess", "cancelRequest"),
                "service/ReliefInviteService.kt" to
                    listOf("createInvite", "acceptInvite", "declineInvite", "retractInvite", "revokeInvite"),
                "service/MedicalMissionDelegateService.kt" to listOf("assignDelegate", "revokeDelegate"),
                // Batch 5 — inventory/product-sale cluster.
                "service/ProductSaleService.kt" to listOf("sell"),
                "service/inventory/InventoryService.kt" to listOf("recordMovement", "ensureCard"),
                // Batch 6 — commission cluster. recalculate remains the standalone entry point
                // (manualRecalculate); sell/clock-in/clock-out call recalculateInTransaction
                // inside their own commands instead of nesting this module's write block.
                "service/finance/commission/CommissionService.kt" to
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
                "service/ProductCategoryService.kt" to "ProductCategoryAudit",
                "service/ProductService.kt" to "ProductAudit",
                "service/ClientService.kt" to "ClientAudit",
                "service/AllowanceService.kt" to "AllowanceAudit",
                "service/CompensationService.kt" to "CompensationAudit",
                "service/session/SessionService.kt" to "SessionAudit",
                "service/session/SessionPractitionerService.kt" to "SessionPractitionerAudit",
                "service/session/SessionConcernService.kt" to "SessionConcernAudit",
                "service/session/SessionBaseRateService.kt" to "SessionBaseRateAudit",
                // Batch 4 — user/access cluster.
                "service/UserService.kt" to "UserAudit",
                "service/UserBranchAssignmentService.kt" to "UserBranchAssignmentAudit",
                "service/ReliefAccessService.kt" to "ReliefAccessAudit",
                "service/ReliefInviteService.kt" to "ReliefInviteAudit",
                "service/MedicalMissionDelegateService.kt" to "MedicalMissionDelegateAudit",
                // Batch 5 — inventory/product-sale cluster.
                "service/ProductSaleService.kt" to "ProductSaleAudit",
                "service/inventory/InventoryService.kt" to "BranchInventoryAudit",
                // Batch 6 — commission cluster.
                "service/finance/commission/CommissionService.kt" to "CommissionAudit",
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
