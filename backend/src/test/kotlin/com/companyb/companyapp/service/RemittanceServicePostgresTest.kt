package com.companyb.companyapp.service

import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.repository.RemittanceRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class RemittanceServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private var sessionId: UUID? = null
    private var productSaleId: UUID? = null

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertTestUser(callerId, "remittance-caller")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Remittance Branch ${UUID.randomUUID()}")
        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, branchId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `create draft remittance succeeds`() {
        val remittanceId = UUID.randomUUID()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
            )

        assertNotNull(remittance)
        assertEquals(remittanceId, remittance.id)
        assertEquals(RemittanceType.SESSION, remittance.type)
        assertEquals(RemittanceStatus.DRAFT, remittance.status)
        assertEquals(branchId, remittance.branchId)
        assertEquals(RemittanceMethod.BANK_TRANSFER, remittance.method)
        assertEquals(dateRangeStart, remittance.dateRangeStart)
        assertEquals(dateRangeEnd, remittance.dateRangeEnd)
        assertEquals(callerId, remittance.submittedBy)
        assertEquals(1, remittance.version)
    }

    @Test
    fun `create draft PRODUCT type succeeds`() {
        val remittanceId = UUID.randomUUID()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.PRODUCT,
                branchId = branchId,
                method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
            )

        assertEquals(RemittanceType.PRODUCT, remittance.type)
    }

    @Test
    fun `create draft idempotent duplicate returns existing`() {
        val remittanceId = UUID.randomUUID()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val first =
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
            )

        val second =
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
            )

        assertEquals(first.id, second.id)
        assertEquals(first.version, second.version)
    }

    @Test
    fun `create draft without SUBMIT_REMITTANCE is forbidden`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        assertFailsWith<ForbiddenResponse> {
            RemittanceService.createDraft(
                callerId = callerId,
                id = UUID.randomUUID(),
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
            )
        }
    }

    @Test
    fun `create draft with non-existent branch returns not found`() {
        val nonexistentBranchId = UUID.randomUUID()
        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, nonexistentBranchId)

        assertFailsWith<NotFoundResponse> {
            RemittanceService.createDraft(
                callerId = callerId,
                id = UUID.randomUUID(),
                type = RemittanceType.SESSION,
                branchId = nonexistentBranchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
            )
        }
    }

    @Test
    fun `create draft with reversed date range returns bad request`() {
        assertFailsWith<BadRequestResponse> {
            RemittanceService.createDraft(
                callerId = callerId,
                id = UUID.randomUUID(),
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 15),
                dateRangeEnd = LocalDate.of(2026, 7, 1),
            )
        }
    }

    @Test
    fun `create draft writes audit log entry`() {
        val remittanceId = UUID.randomUUID()

        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = LocalDate.of(2026, 7, 15),
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq RemittanceTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    @Test
    fun `submit SESSION remittance succeeds with snapshot and branch day updates`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("500.00"))

        val actualVersion = RemittanceRepository.findById(remittanceId)!!.version

        val (result, duration) =
            measureTimedValue {
                RemittanceService.submit(callerId, remittanceId, actualVersion)
            }
        assertTrue(duration < 15.seconds, "remittance submit regressed: took $duration")

        assertNotNull(result)
        assertEquals(RemittanceStatus.SUBMITTED, result.remittance.status)
        assertTrue(result.remittance.version > 1)
        assertEquals(BigDecimal("500.00"), result.grossIncome)
        assertEquals(BigDecimal.ZERO, result.totalCompensation)
        assertEquals(BigDecimal.ZERO, result.totalExpenses)
        assertEquals(BigDecimal("500.00"), result.netIncome)

        val bd = transaction { BranchDayTable.selectAll().where { BranchDayTable.id eq branchDayId }.single() }
        assertEquals(DayStatus.REMITTED, bd[BranchDayTable.status])

        val snap =
            transaction {
                RemittanceFinancialSnapshotTable
                    .selectAll()
                    .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                    .singleOrNull()
            }
        assertNotNull(snap)
        assertEquals(BigDecimal("500.00"), snap[RemittanceFinancialSnapshotTable.netIncome])
    }

    @Test
    fun `submit PRODUCT remittance succeeds without snapshot`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createDraftProductRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addProductLine(remittanceId, lineId, BigDecimal("200.00"))

        val actualVersion = RemittanceRepository.findById(remittanceId)!!.version

        val result = RemittanceService.submit(callerId, remittanceId, actualVersion)

        assertNotNull(result)
        assertEquals(RemittanceStatus.SUBMITTED, result.remittance.status)
        assertEquals(BigDecimal.ZERO, result.grossIncome)

        val snap =
            transaction {
                RemittanceFinancialSnapshotTable
                    .selectAll()
                    .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                    .singleOrNull()
            }
        assertNull(snap)
    }

    @Test
    fun `submit calculates compensation and expenses correctly`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        val compId = UUID.randomUUID()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("1000.00"))
        addCompensation(compId, branchDayId, BigDecimal("200.00"))
        addExpense(branchDayId, BigDecimal("150.00"))

        val actualVersion = RemittanceRepository.findById(remittanceId)!!.version

        val result = RemittanceService.submit(callerId, remittanceId, actualVersion)

        assertNotNull(result)
        assertEquals(BigDecimal("1000.00"), result.grossIncome)
        assertEquals(BigDecimal("200.00"), result.totalCompensation)
        assertEquals(BigDecimal("150.00"), result.totalExpenses)
        assertEquals(BigDecimal("650.00"), result.netIncome)
    }

    @Test
    fun `submit with version mismatch throws conflict`() {
        val remittanceId = UUID.randomUUID()
        createDraftRemittance(remittanceId)

        assertFailsWith<ConflictResponse> {
            RemittanceService.submit(callerId, remittanceId, 99)
        }
    }

    @Test
    fun `submit without SUBMIT_REMITTANCE capability is forbidden`() {
        val remittanceId = UUID.randomUUID()
        createDraftRemittance(remittanceId)
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        assertFailsWith<ForbiddenResponse> {
            RemittanceService.submit(callerId, remittanceId, 1)
        }
    }

    @Test
    fun `submit non-existent remittance returns not found`() {
        assertFailsWith<NotFoundResponse> {
            RemittanceService.submit(callerId, UUID.randomUUID(), 1)
        }
    }

    @Test
    fun `submit already submitted remittance throws bad request`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("100.00"))
        val v1 = RemittanceRepository.findById(remittanceId)!!.version
        RemittanceService.submit(callerId, remittanceId, v1)

        assertFailsWith<BadRequestResponse> {
            RemittanceService.submit(callerId, remittanceId, 99)
        }
    }

    @Test
    fun `submit writes audit log entries for remittance and branch days`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("300.00"))

        RemittanceService.submit(callerId, remittanceId, 2)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.UPDATE) and
                            (
                                AuditLogTable.auditTableName.eq(RemittanceTable.tableName) or
                                    AuditLogTable.auditTableName.eq(BranchDayTable.tableName)
                            )
                    }.count()
            }
        assertTrue(auditCount >= 2)
    }

    @Test
    fun `submit with no day breakdowns succeeds with zero calculations`() {
        val remittanceId = UUID.randomUUID()
        createDraftRemittance(remittanceId)

        val result = RemittanceService.submit(callerId, remittanceId, 1)

        assertNotNull(result)
        assertEquals(BigDecimal.ZERO, result.grossIncome)
        assertEquals(BigDecimal.ZERO, result.totalCompensation)
        assertEquals(BigDecimal.ZERO, result.totalExpenses)
        assertEquals(BigDecimal.ZERO, result.netIncome)
    }

    private fun createDraftRemittance(remittanceId: UUID) {
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = LocalDate.of(2026, 7, 15),
        )
    }

    private fun createDraftProductRemittance(remittanceId: UUID) {
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = LocalDate.of(2026, 7, 15),
        )
    }

    private fun resolveBranchDay(): UUID {
        val today = LocalDate.of(2026, 7, 10)
        val bd = BranchDayService.resolveOrCreate(branchId, today)
        return bd.id
    }

    private fun addDayBreakdown(
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

    private fun addSessionLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val sId = createSession()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sId,
            productSaleId = null,
            amount = amount,
        )
    }

    private fun addProductLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val psId = createProductSale()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.PRODUCT_SALE,
            sessionId = null,
            productSaleId = psId,
            amount = amount,
        )
    }

    private var sessionCreated = false
    private var productSaleCreated = false

    private fun createSession(): UUID {
        if (sessionCreated) return sessionId!!
        ensureClientExists()
        val sId = UUID.randomUUID()
        val branchDayId = resolveBranchDay()
        DatabaseTestHelper.insertTestSession(
            id = sId,
            clientId = clientId,
            branchDayId = branchDayId,
        )
        sessionId = sId
        sessionCreated = true
        return sId
    }

    private fun createProductSale(): UUID {
        if (productSaleCreated) return productSaleId!!
        ensureClientExists()
        val psId = UUID.randomUUID()
        val productCategoryId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val branchDayId = resolveBranchDay()
        val conn = DatabaseConfig.dataSource.connection
        conn.createStatement().use { stmt ->
            stmt.execute(
                "INSERT INTO product_category (id, name) VALUES ('$productCategoryId', 'Test Cat ps')",
            )
            stmt.execute(
                "INSERT INTO product (id, name, product_category_id, unit_price, commission_amount) " +
                    "VALUES ('$productId', 'Test Prod ps', '$productCategoryId', 100.00, 10.00)",
            )
            stmt.execute(
                "INSERT INTO product_sale (id, branch_day_id, product_id, quantity, is_walk_in, " +
                    "client_id, handled_by, unit_price_at_time, " +
                    "total_amount_at_time, commission_amount_at_time, product_name) " +
                    "VALUES ('$psId', '$branchDayId', '$productId', 1, true, " +
                    "'$clientId', '$callerId', 100.00, 100.00, 10.00, 'Test Product')",
            )
        }
        conn.close()
        productSaleId = psId
        productSaleCreated = true
        return psId
    }

    private fun ensureClientExists() {
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

    @Suppress("SameParameterValue")
    private fun addCompensation(
        id: UUID,
        payingBranchDayId: UUID,
        amount: BigDecimal,
    ) {
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = id
                it[CompensationTable.workBranchDayId] = payingBranchDayId
                it[CompensationTable.payingBranchDayId] = payingBranchDayId
                it[CompensationTable.userId] = callerId
                it[CompensationTable.amount] = amount
                it[CompensationTable.assignedBy] = callerId
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun addExpense(
        branchDayId: UUID,
        amount: BigDecimal,
    ) {
        transaction {
            ExpenseTable.insert {
                it[ExpenseTable.id] = UUID.randomUUID()
                it[ExpenseTable.branchDayId] = branchDayId
                it[ExpenseTable.amount] = amount
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = callerId
            }
        }
    }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere { (AuditLogTable.changedBy eq callerId) }
            UserCapabilityTable.deleteWhere { (UserCapabilityTable.userId eq callerId) }
        }
        disableSnapshotTrigger()
        try {
            transaction {
                RemittanceFinancialSnapshotTable.deleteAll()
            }
        } finally {
            enableSnapshotTrigger()
        }
        transaction {
            RemittanceDayBreakdownTable.deleteAll()
            RemittanceLineTable.deleteAll()
            CompensationTable.deleteWhere { CompensationTable.assignedBy eq callerId }
            ExpenseTable.deleteWhere { ExpenseTable.createdBy eq callerId }
            RemittanceTable.deleteAll()
            ProductSaleTable.deleteAll()
            SessionTable.deleteAll()
            ClientTable.deleteWhere { ClientTable.id eq clientId }
            ProductTable.deleteAll()
            ProductCategoryTable.deleteAll()
            BranchDayTable.deleteWhere { BranchDayTable.branchId eq branchId }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            AppUserTable.deleteWhere { AppUserTable.id eq callerId }
        }
    }

    private fun disableSnapshotTrigger() {
        val conn = DatabaseConfig.dataSource.connection
        conn.createStatement().use { stmt ->
            stmt.execute(
                "ALTER TABLE remittance_financial_snapshot DISABLE TRIGGER trg_remittance_snapshot_immutable",
            )
        }
        conn.close()
    }

    private fun enableSnapshotTrigger() {
        val conn = DatabaseConfig.dataSource.connection
        conn.createStatement().use { stmt ->
            stmt.execute(
                "ALTER TABLE remittance_financial_snapshot ENABLE TRIGGER trg_remittance_snapshot_immutable",
            )
        }
        conn.close()
    }
}
