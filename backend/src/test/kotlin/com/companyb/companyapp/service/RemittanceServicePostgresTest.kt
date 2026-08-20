package com.companyb.companyapp.service
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class RemittanceServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private var sessionId: UUID? = null
    private var productSaleId: UUID? = null

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "remittance-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)

        DatabaseTestHelper.insertTestBranch(branchId, "Test Remittance Branch ${TestFixtures.uuid()}")
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, branchId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `create draft remittance succeeds`() {
        val remittanceId = TestFixtures.uuid()
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

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
    }

    @Test
    fun `create draft PRODUCT type succeeds`() {
        val remittanceId = TestFixtures.uuid()
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

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
    }

    @Test
    fun `create draft idempotent duplicate returns existing`() {
        val remittanceId = TestFixtures.uuid()
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

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
    }

    @Test
    fun `create draft without SUBMIT_REMITTANCE is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                id = TestFixtures.uuid(),
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
            )

        assertNotNull(remittance)
        trackOwned(RemittanceTable, RemittanceTable.id, remittance.id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittance.id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittance.id)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittance.id)
    }

    @Test
    fun `create draft with non-existent branch returns not found`() {
        val nonexistentBranchId = TestFixtures.uuid()
        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, nonexistentBranchId)

        assertFailsWith<NotFoundException> {
            RemittanceService.createDraft(
                callerId = callerId,
                id = TestFixtures.uuid(),
                type = RemittanceType.SESSION,
                branchId = nonexistentBranchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
            )
        }
    }

    @Test
    fun `create draft writes audit log entry`() {
        val remittanceId = TestFixtures.uuid()

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

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
    }

    @Test
    fun `submit SESSION remittance succeeds with snapshot and branch day updates`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("500.00"))

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)

        val actualVersion = RemittanceService.getRemittance(remittanceId).remittance.version

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
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftProductRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addProductLine(remittanceId, lineId, BigDecimal("200.00"))

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(ProductSaleTable, ProductSaleTable.id, productSaleId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)

        val actualVersion = RemittanceService.getRemittance(remittanceId).remittance.version

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
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        val compId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("1000.00"))
        addCompensation(compId, branchDayId, BigDecimal("200.00"))
        addExpense(branchDayId, BigDecimal("150.00"))

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(CompensationTable, CompensationTable.assignedBy, callerId)
        trackOwned(ExpenseTable, ExpenseTable.createdBy, callerId)

        val actualVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val result = RemittanceService.submit(callerId, remittanceId, actualVersion)

        assertNotNull(result)
        assertEquals(BigDecimal("1000.00"), result.grossIncome)
        assertEquals(BigDecimal("200.00"), result.totalCompensation)
        assertEquals(BigDecimal("150.00"), result.totalExpenses)
        assertEquals(BigDecimal("650.00"), result.netIncome)
    }

    @Test
    fun `submit with version mismatch throws conflict`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)

        assertFailsWith<ConflictException> {
            RemittanceService.submit(callerId, remittanceId, 99)
        }
    }

    @Test
    fun `submit without SUBMIT_REMITTANCE is allowed at service layer`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)

        val result = RemittanceService.submit(callerId, remittanceId, 1)

        assertNotNull(result)
        assertEquals(RemittanceStatus.SUBMITTED, result.remittance.status)
    }

    @Test
    fun `submit non-existent remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.submit(callerId, TestFixtures.uuid(), 1)
        }
    }

    @Test
    fun `submit already submitted remittance throws version mismatch`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("100.00"))

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)

        val v1 = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, v1)

        assertFailsWith<VersionMismatchException> {
            RemittanceService.submit(callerId, remittanceId, 99)
        }
    }

    @Test
    fun `submit writes audit log entries for remittance and branch days`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("300.00"))

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(SessionTable, SessionTable.id, sessionId!!)
        trackOwned(ClientTable, ClientTable.id, clientId)

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

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
    fun `submit branch day audit preserves lazy past before status`() {
        val remittanceId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.UPDATE) and
                            (AuditLogTable.auditTableName eq BranchDayTable.tableName) and
                            (AuditLogTable.recordId eq branchDayId)
                    }.single()
            }
        assertEquals("OPEN", DatabaseTestHelper.extractJsonField(audit[AuditLogTable.oldValue].orEmpty(), "status"))
        assertEquals(
            "REMITTED",
            DatabaseTestHelper.extractJsonField(audit[AuditLogTable.newValue].orEmpty(), "status"),
        )
    }

    @Test
    fun `submit branch day audit preserves current open before status`() {
        val remittanceId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveCurrentBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.UPDATE) and
                            (AuditLogTable.auditTableName eq BranchDayTable.tableName) and
                            (AuditLogTable.recordId eq branchDayId)
                    }.single()
            }
        assertEquals("OPEN", DatabaseTestHelper.extractJsonField(audit[AuditLogTable.oldValue].orEmpty(), "status"))
        assertEquals(
            "REMITTED",
            DatabaseTestHelper.extractJsonField(audit[AuditLogTable.newValue].orEmpty(), "status"),
        )
    }

    @Test
    fun `submit with no day breakdowns succeeds with zero calculations`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)

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

    private fun resolveCurrentBranchDay(): UUID {
        val bd = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
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
        val sId = TestFixtures.uuid()
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
        val psId = TestFixtures.uuid()
        val catId = TestFixtures.uuid()
        val prodId = TestFixtures.uuid()
        val branchDayId = resolveBranchDay()
        DatabaseTestHelper.insertTestCategory(catId, "Cat ${psId.toString().take(8)}")
        DatabaseTestHelper.insertTestProduct(prodId, "Prod ${psId.toString().take(8)}", catId)
        DatabaseTestHelper.insertTestProductSale(
            id = psId,
            branchDayId = branchDayId,
            productId = prodId,
            handledBy = callerId,
        )
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, catId)
        trackOwned(ProductTable, ProductTable.id, prodId)
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
                it[ExpenseTable.id] = TestFixtures.uuid()
                it[ExpenseTable.branchDayId] = branchDayId
                it[ExpenseTable.amount] = amount
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = callerId
            }
        }
    }
}
