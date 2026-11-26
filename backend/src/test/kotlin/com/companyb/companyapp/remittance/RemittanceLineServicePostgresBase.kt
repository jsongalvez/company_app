package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.NewProductSale
import com.companyb.companyapp.commerce.ProductCategoryRepository
import com.companyb.companyapp.commerce.ProductCreateParams
import com.companyb.companyapp.commerce.ProductRepository
import com.companyb.companyapp.commerce.ProductSaleService
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.remittance.RemittanceLineTable
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.session.NewSession
import com.companyb.companyapp.session.SessionCreateContext
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Ids, seed rows and helpers shared by the remittance line and day breakdown suites. */
abstract class RemittanceLineServicePostgresBase : BasePostgresTest() {
    protected companion object {
        const val CONCURRENT_BREAKDOWNS = 2
        const val CONCURRENT_LINES = 2
        const val EXECUTOR_TERMINATION_SECONDS = 10L
    }

    protected val callerId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()
    protected val clientId = TestFixtures.uuid()
    protected val sessionId = TestFixtures.uuid()
    protected val productCategoryId = TestFixtures.uuid()
    protected val productId = TestFixtures.uuid()
    protected val productSaleId = TestFixtures.uuid()
    protected lateinit var branchDayId: UUID
    protected val rateId = TestFixtures.uuid()
    protected val secondSessionRateId = TestFixtures.uuid()
    protected val subsequentRateId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "rl")

        BranchWorkforceFixtures.insertTestBranch(branchId)

        SessionClientFixtures.insertTestClient(clientId)

        SessionClientFixtures.insertTestBaseRate(rateId, branchId, callerId)
        SessionClientFixtures.insertTestBaseRate(
            secondSessionRateId,
            branchId,
            callerId,
            SessionType.SECOND_SESSION,
        )
        SessionClientFixtures.insertTestBaseRate(
            subsequentRateId,
            branchId,
            callerId,
            SessionType.SUBSEQUENT,
        )

        ensureBranchDay()
    }

    protected fun createDraftRemittance(type: RemittanceType = RemittanceType.SESSION) =
        RemittanceService.createDraft(
            callerId = callerId,
            NewRemittance(
                id = TestFixtures.uuid(),
                type = type,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                // #483 — the range must cover the operational today the session/sale fixtures
                // land on; out-of-range writes are rejected, so a fixed July window would fail.
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = TestFixtures.today,
            ),
        )

    protected fun createSession(sid: UUID = sessionId) {
        SessionService.create(
            callerId = callerId,
            NewSession(
                id = sid,
                clientId = clientId,
                branchId = branchId,
                isWalkIn = false,
                requestedPractitionerId = null,
                finalPrice = BigDecimal("2500.00"),
                remarks = null,
                otherConcerns = null,
                nextAppointmentDate = null,
            ),
            context =
                SessionCreateContext(requireClockIn = false),
        )
        // #857 — only COMPLETED sessions are remittable; complete the fixture so the
        // legacy happy-path tests exercise the guarded (allowed) state.
        completeSession(sid)
    }

    protected fun createProductSale() {
        insertProductCategory()
        insertProduct()
        ensureBranchInventory()
        ProductSaleService.sell(
            callerId = callerId,
            NewProductSale(
                id = productSaleId,
                branchDayId = branchDayId,
                sessionId = sessionId,
                clientId = null,
                isWalkIn = false,
                productId = productId,
                quantity = 1,
            ),
            expectedVersion = 2,
        )
    }

    protected fun ensureBranchInventory() {
        val card = InventoryService.ensureCard(callerId, branchId, productId)
        transaction {
            BranchInventoryTable.update({
                (BranchInventoryTable.branchId eq branchId) and
                    (BranchInventoryTable.productId eq productId)
            }) {
                it[BranchInventoryTable.currentStock] = 20
                it[BranchInventoryTable.version] = card.version + 1
            }
        }
    }

    protected fun insertProductCategory() {
        transaction {
            ProductCategoryRepository.createInTransaction(
                id = productCategoryId,
                name = "Test Category $productCategoryId",
            )
        }
    }

    protected fun insertProduct() {
        transaction {
            ProductRepository.createInTransaction(
                ProductCreateParams(
                    id = productId,
                    name = "Test Product $productId",
                    productCategoryId = productCategoryId,
                    unitPrice = BigDecimal("500.00"),
                    commissionAmount = BigDecimal("50.00"),
                    changedBy = callerId,
                ),
            )
        }
    }

    protected fun ensureBranchDay() {
        val day = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        branchDayId = day.id
    }

    protected fun completeSession(sid: UUID) {
        transaction {
            SessionTable.update({ SessionTable.id eq sid }) {
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
            }
        }
    }

    protected fun markRemittanceSubmitted(remittanceId: UUID) {
        transaction {
            RemittanceTable.update({ RemittanceTable.id eq remittanceId }) {
                it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
            }
        }
    }

    protected fun remittanceLineAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.auditTableName eq RemittanceLineTable.tableName }
                .count()
        }

    /** A line remitting [sessionId]. */
    protected fun sessionLine(
        id: UUID = TestFixtures.uuid(),
        amount: BigDecimal = BigDecimal("1500.00"),
    ) = NewRemittanceLine(id, RemittanceLineType.SESSION, sessionId, null, amount)
}
