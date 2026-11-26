package com.companyb.companyapp.remittance
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Ids, seed rows and helpers shared by the remittance draft and submit suites. */
abstract class RemittanceServicePostgresBase : BasePostgresTest() {
    protected val callerId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()
    protected val clientId = TestFixtures.uuid()
    protected var sessionId: UUID? = null
    protected var productSaleId: UUID? = null

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "remittance-caller")

        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Remittance Branch ${TestFixtures.uuid()}")
    }

    protected fun createDraftRemittance(remittanceId: UUID) {
        RemittanceService.createDraft(
            callerId = callerId,
            NewRemittance(
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                // #483 — covers both the fixed July fixtures and the operational-today breakdown.
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = TestFixtures.today,
            ),
        )
    }

    protected fun createDraftProductRemittance(remittanceId: UUID) {
        RemittanceService.createDraft(
            callerId = callerId,
            NewRemittance(
                id = remittanceId,
                type = RemittanceType.PRODUCT,
                branchId = branchId,
                method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
                // #483 — same widened range as the SESSION draft helper above.
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = TestFixtures.today,
            ),
        )
    }

    protected fun resolveBranchDay(): UUID {
        val today = LocalDate.of(2026, 7, 10)
        val bd = BranchDayService.resolveOrCreate(branchId, today)
        return bd.id
    }

    protected fun addDayBreakdown(
        remittanceId: UUID,
        breakdownId: UUID,
        branchDayId: UUID,
    ) {
        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittanceId,
            id = breakdownId,
            branchDayId = branchDayId,
        )
    }

    protected fun addSessionLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val sId = createSession()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            NewRemittanceLine(
                id = lineId,
                type = RemittanceLineType.SESSION,
                sessionId = sId,
                productSaleId = null,
                amount = amount,
            ),
        )
    }

    protected fun addProductLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val psId = createProductSale()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            NewRemittanceLine(
                id = lineId,
                type = RemittanceLineType.PRODUCT_SALE,
                sessionId = null,
                productSaleId = psId,
                amount = amount,
            ),
        )
    }

    protected var sessionCreated = false
    protected var productSaleCreated = false

    protected fun createSession(): UUID {
        if (sessionCreated) return sessionId!!
        ensureClientExists()
        val sId = TestFixtures.uuid()
        val branchDayId = resolveBranchDay()
        SessionClientFixtures.insertTestSession(
            id = sId,
            clientId = clientId,
            branchDayId = branchDayId,
            // #857 — only COMPLETED sessions are remittable; the submit happy paths
            // exercise the guarded (allowed) state.
            sessionStatus = SessionStatus.COMPLETED,
        )
        sessionId = sId
        sessionCreated = true
        return sId
    }

    protected fun createProductSale(): UUID {
        if (productSaleCreated) return productSaleId!!
        val psId = TestFixtures.uuid()
        val catId = TestFixtures.uuid()
        val prodId = TestFixtures.uuid()
        val branchDayId = resolveBranchDay()
        CommerceFinanceFixtures.insertTestCategory(catId, "Cat ${psId.toString().take(8)}")
        CommerceFinanceFixtures.insertTestProduct(prodId, "Prod ${psId.toString().take(8)}", catId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = psId,
            branchDayId = branchDayId,
            productId = prodId,
            handledBy = callerId,
        )
        productSaleId = psId
        productSaleCreated = true
        return psId
    }

    protected fun ensureClientExists() {
        transaction {
            val exists = ClientTable.selectAll().where { ClientTable.id eq clientId }.count() > 0
            if (!exists) {
                ClientTable.insert {
                    it[ClientTable.id] = clientId
                    it[ClientTable.firstName] = "Test"
                    it[ClientTable.lastName] = "Client"
                    it[ClientTable.gender] = "M"
                    it[ClientTable.age] = 30
                }
            }
        }
    }

    /** A remittance for [branchId]. */
    protected fun draft(
        id: UUID,
        dateRangeStart: LocalDate,
        dateRangeEnd: LocalDate,
        type: RemittanceType = RemittanceType.SESSION,
        method: RemittanceMethod = RemittanceMethod.BANK_TRANSFER,
    ) = NewRemittance(id, type, branchId, method, dateRangeStart, dateRangeEnd)
}
