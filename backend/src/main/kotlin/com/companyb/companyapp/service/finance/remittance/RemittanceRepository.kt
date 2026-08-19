package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshot
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotCreateParams
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class CreateDraftParams(
    val id: UUID,
    val type: RemittanceType,
    val branchId: UUID,
    val method: RemittanceMethod,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
    val submittedDate: LocalDate,
    val submittedBy: UUID,
)

data class RemittanceCreateResult(
    val remittance: Remittance,
    val created: Boolean,
)

data class UndoParams(
    val remittanceId: UUID,
    val expectedVersion: Int,
    val reason: String,
    val now: OffsetDateTime? = null,
)

data class UpdateHeaderParams(
    val remittanceId: UUID,
    val expectedVersion: Int,
    val type: RemittanceType,
    val method: RemittanceMethod,
    val dateRangeStart: LocalDate,
    val dateRangeEnd: LocalDate,
)

data class RemittanceWithNet(
    val remittance: Remittance,
    val netIncome: BigDecimal?,
)

data class RemittanceSessionPickerEntry(
    val id: UUID,
    val clientName: String?,
    val bookedAt: OffsetDateTime?,
    val sessionStatus: SessionStatus,
    val finalPrice: BigDecimal,
)

data class RemittanceProductSalePickerEntry(
    val id: UUID,
    val productName: String,
    val quantity: Int,
    val totalAmountAtTime: BigDecimal,
    val soldAt: OffsetDateTime,
)

@Suppress("TooManyFunctions")
internal object RemittanceRepository {
    fun findById(id: UUID): Remittance? =
        transaction {
            RemittanceTable
                .selectAll()
                .where { RemittanceTable.id eq id }
                .singleOrNull()
                ?.toRemittance()
        }

    fun findByBranchId(
        branchId: UUID,
        status: RemittanceStatus?,
    ): List<RemittanceWithNet> =
        transaction {
            val query =
                RemittanceTable
                    .leftJoin(
                        RemittanceFinancialSnapshotTable,
                        { RemittanceTable.id },
                        { RemittanceFinancialSnapshotTable.remittanceId },
                    ).selectAll()
            val rows =
                if (status == null) {
                    query.where { RemittanceTable.branchId eq branchId }
                } else {
                    query.where {
                        (RemittanceTable.branchId eq branchId) and (RemittanceTable.status eq status)
                    }
                }
            rows
                .orderBy(
                    RemittanceTable.createdAt to SortOrder.DESC,
                    RemittanceTable.id to SortOrder.DESC,
                ).map { row ->
                    RemittanceWithNet(
                        remittance = row.toRemittance(),
                        netIncome = row.getOrNull(RemittanceFinancialSnapshotTable.netIncome),
                    )
                }
        }

    fun findSessionsInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceSessionPickerEntry> =
        transaction {
            SessionTable
                .innerJoin(BranchDayTable, { SessionTable.branchDayId }, { BranchDayTable.id })
                .innerJoin(ClientTable, { SessionTable.clientId }, { ClientTable.id })
                .leftJoin(ActiveSessionVoidsView, { SessionTable.id }, { ActiveSessionVoidsView.sessionId })
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date greaterEq from) and
                        (BranchDayTable.date lessEq to) and
                        (ActiveSessionVoidsView.sessionId.isNull())
                }.orderBy(
                    BranchDayTable.date to SortOrder.ASC,
                    SessionTable.createdAt to SortOrder.ASC,
                    SessionTable.id to SortOrder.ASC,
                ).map { row ->
                    RemittanceSessionPickerEntry(
                        id = row[SessionTable.id],
                        clientName =
                            buildClientName(
                                firstName = row[ClientTable.firstName],
                                middleName = row[ClientTable.middleName],
                                lastName = row[ClientTable.lastName],
                                suffix = row[ClientTable.suffix],
                            ),
                        bookedAt = row[SessionTable.bookedAt],
                        sessionStatus = row[SessionTable.sessionStatus],
                        finalPrice = row[SessionTable.finalPrice],
                    )
                }
        }

    fun findProductSalesInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<RemittanceProductSalePickerEntry> =
        transaction {
            ProductSaleTable
                .innerJoin(BranchDayTable, { ProductSaleTable.branchDayId }, { BranchDayTable.id })
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date greaterEq from) and
                        (BranchDayTable.date lessEq to)
                }.orderBy(
                    BranchDayTable.date to SortOrder.ASC,
                    ProductSaleTable.soldAt to SortOrder.ASC,
                    ProductSaleTable.id to SortOrder.ASC,
                ).map { row ->
                    RemittanceProductSalePickerEntry(
                        id = row[ProductSaleTable.id],
                        productName = row[ProductSaleTable.productName],
                        quantity = row[ProductSaleTable.quantity],
                        totalAmountAtTime = row[ProductSaleTable.totalAmountAtTime],
                        soldAt = row[ProductSaleTable.soldAt],
                    )
                }
        }

    fun findBranchDaysInRange(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<BranchDay> =
        transaction {
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date greaterEq from) and
                        (BranchDayTable.date lessEq to)
                }.orderBy(
                    BranchDayTable.date to SortOrder.ASC,
                    BranchDayTable.id to SortOrder.ASC,
                ).map { it.toBranchDay() }
        }

    private fun buildClientName(
        firstName: String?,
        middleName: String?,
        lastName: String?,
        suffix: String?,
    ): String? {
        val parts = listOfNotNull(firstName, middleName, lastName).filter { it.isNotBlank() }
        val suffixPart = suffix?.takeIf { it.isNotBlank() }
        val joined = parts.joinToString(" ").ifBlank { null }
        return when {
            joined == null -> suffixPart
            suffixPart == null -> joined
            else -> "$joined $suffixPart"
        }
    }

    fun createDraft(
        params: CreateDraftParams,
        auditFn: (Remittance) -> Unit = {},
    ): RemittanceCreateResult =
        transaction {
            val existing = findByIdInTransaction(params.id)
            if (existing != null) {
                return@transaction RemittanceCreateResult(existing, created = false)
            }

            RemittanceTable.insertIgnore {
                it[RemittanceTable.id] = params.id
                it[RemittanceTable.type] = params.type
                it[RemittanceTable.branchId] = params.branchId
                it[RemittanceTable.method] = params.method
                it[RemittanceTable.dateRangeStart] = params.dateRangeStart
                it[RemittanceTable.dateRangeEnd] = params.dateRangeEnd
                it[RemittanceTable.submittedDate] = params.submittedDate
                it[RemittanceTable.submittedBy] = params.submittedBy
            }

            val created =
                findByIdInTransaction(params.id) ?: error("remittance not found after insert for ${params.id}")

            auditFn(created)
            RemittanceCreateResult(created, created = true)
        }.also { result ->
            logger.info {
                "[CREATE-REMITTANCE-DRAFT] Remittance ${result.remittance.id.toString().maskUUID()}" +
                    " created=${result.created}"
            }
        }

    private fun findByIdInTransaction(id: UUID): Remittance? =
        RemittanceTable
            .selectAll()
            .where { RemittanceTable.id eq id }
            .singleOrNull()
            ?.toRemittance()

    private const val SERIALIZABLE_ISOLATION = Connection.TRANSACTION_SERIALIZABLE

    @Suppress("ReturnCount", "ComplexMethod", "LongMethod")
    fun submit(
        remittanceId: UUID,
        expectedVersion: Int,
        callerId: UUID,
        auditFn: (SubmitAuditContext) -> Unit = {},
    ): RemittanceSubmissionResult? =
        transaction(transactionIsolation = SERIALIZABLE_ISOLATION) {
            val remittanceRow =
                RemittanceTable
                    .selectAll()
                    .where { RemittanceTable.id eq remittanceId }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull() ?: return@transaction null

            if (remittanceRow[RemittanceTable.version] != expectedVersion) {
                throw VersionMismatchException(RemittanceTable.tableName, remittanceId)
            }
            if (remittanceRow[RemittanceTable.status] != RemittanceStatus.DRAFT) {
                throw ValidationException("Can only submit DRAFT remittances")
            }

            val remittanceBefore = remittanceRow.toRemittance()
            val remittanceType = remittanceRow[RemittanceTable.type]

            val breakdownIds =
                RemittanceDayBreakdownTable
                    .selectAll()
                    .where { RemittanceDayBreakdownTable.remittanceId eq remittanceId }
                    .map { it[RemittanceDayBreakdownTable.branchDayId] }

            val grossIncome = calculateGrossIncome(remittanceId)
            val totalCompensation = calculateCompensationSum(breakdownIds)
            val totalExpenses = calculateExpenseSum(breakdownIds)
            val netIncome = netOf(grossIncome, totalCompensation, totalExpenses)

            val branchDayBeforeRows =
                breakdownIds.map { bdId ->
                    BranchDayTable
                        .selectAll()
                        .where { BranchDayTable.id eq bdId }
                        .single()
                        .toBranchDay()
                }

            writeFinancialSnapshot(
                remittanceType,
                remittanceId,
                grossIncome,
                totalCompensation,
                totalExpenses,
                netIncome,
            )
            updateRemittanceToSubmitted(remittanceId, expectedVersion, callerId)
            updateBranchDayStatuses(breakdownIds)

            val remittanceAfter =
                findByIdInTransaction(remittanceId)
                    ?: error("remittance not found after submit for $remittanceId")

            val branchDayPairs =
                branchDayBeforeRows.map { before ->
                    val after =
                        BranchDayTable
                            .selectAll()
                            .where { BranchDayTable.id eq before.id }
                            .single()
                            .toBranchDay()
                    before to after
                }

            auditFn(
                SubmitAuditContext(
                    remittanceBefore = remittanceBefore,
                    remittanceAfter = remittanceAfter,
                    branchDayPairs = branchDayPairs,
                ),
            )

            RemittanceSubmissionResult(
                remittance = remittanceAfter,
                grossIncome = grossIncome,
                totalCompensation = totalCompensation,
                totalExpenses = totalExpenses,
                netIncome = netIncome,
            )
        }.also { result ->
            logger.info {
                "[SUBMIT-REMITTANCE] Remittance ${remittanceId.toString().maskUUID()}" +
                    " submitted=${result != null} gross=${result?.grossIncome}"
            }
        }

    private const val UNDO_WINDOW_HOURS = 48L

    private fun currentDatabaseTime(): OffsetDateTime =
        RemittanceTable
            .select(CurrentTimestampWithTimeZone)
            .first()[CurrentTimestampWithTimeZone]

    /**
     * Reverts a SUBMITTED remittance to DRAFT within the 48h undo window (server-enforced).
     * Days unlock (status re-derived from their calendar date), the snapshot row is deleted
     * (V13 trigger carve-out: allowed when the parent remittance is DRAFT), version bumps,
     * submitted_at cleared (a reverted draft has no submission instant).
     *
     * The production window comparison uses the same DB clock that stamps `submitted_at`.
     * [UndoParams.now] remains an explicit test seam for deterministic boundary tests.
     *
     * Accepted per #103 D10: monthly aggregate views may shift while the window is open —
     * they are views over live rows and re-compute.
     */
    @Suppress("ReturnCount", "ComplexMethod", "LongMethod")
    fun undo(
        params: UndoParams,
        auditFn: (UndoAuditContext) -> Unit = {},
    ): Remittance? =
        transaction(transactionIsolation = SERIALIZABLE_ISOLATION) {
            val remittanceRow =
                RemittanceTable
                    .selectAll()
                    .where { RemittanceTable.id eq params.remittanceId }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull() ?: return@transaction null

            if (remittanceRow[RemittanceTable.status] != RemittanceStatus.SUBMITTED) {
                throw ValidationException("Can only undo SUBMITTED remittances")
            }
            if (remittanceRow[RemittanceTable.version] != params.expectedVersion) {
                throw VersionMismatchException(RemittanceTable.tableName, params.remittanceId)
            }

            val submissionInstant =
                remittanceRow[RemittanceTable.submittedAt]
                    ?: RemittanceFinancialSnapshotTable
                        .selectAll()
                        .where { RemittanceFinancialSnapshotTable.remittanceId eq params.remittanceId }
                        .singleOrNull()
                        ?.get(RemittanceFinancialSnapshotTable.snapshottedAt)
                    ?: throw ValidationException("No submission timestamp — cannot undo this remittance")
            val comparisonInstant = params.now ?: currentDatabaseTime()
            if (comparisonInstant.isAfter(submissionInstant.plusHours(UNDO_WINDOW_HOURS))) {
                throw ValidationException("Undo window of 48 hours has expired")
            }

            val remittanceBefore = remittanceRow.toRemittance()

            val breakdownDayRows =
                RemittanceDayBreakdownTable
                    .selectAll()
                    .where { RemittanceDayBreakdownTable.remittanceId eq params.remittanceId }
                    .map { it[RemittanceDayBreakdownTable.branchDayId] }
                    .map { bdId ->
                        BranchDayTable
                            .selectAll()
                            .where { BranchDayTable.id eq bdId }
                            .single()
                    }

            val updated =
                RemittanceTable.update({
                    (RemittanceTable.id eq params.remittanceId) and
                        (RemittanceTable.version eq params.expectedVersion) and
                        (RemittanceTable.status eq RemittanceStatus.SUBMITTED)
                }) {
                    it[RemittanceTable.status] = RemittanceStatus.DRAFT
                    it[RemittanceTable.version] = params.expectedVersion + 1
                    it[RemittanceTable.submittedAt] = null
                }
            if (updated == 0) {
                throw VersionMismatchException(RemittanceTable.tableName, params.remittanceId)
            }

            val snapshotBefore = RemittanceFinancialSnapshotRepository.deleteByRemittanceId(params.remittanceId)

            val today = LocalDate.now(BranchDayService.manilaZone)
            val branchDayPairs =
                breakdownDayRows.map { beforeRow ->
                    val afterStatus =
                        BranchDayService.evaluateStatus(DayStatus.OPEN, beforeRow[BranchDayTable.date], today)
                    BranchDayTable.update({ BranchDayTable.id eq beforeRow[BranchDayTable.id] }) {
                        it[BranchDayTable.status] = afterStatus
                    }
                    val afterRow =
                        BranchDayTable
                            .selectAll()
                            .where { BranchDayTable.id eq beforeRow[BranchDayTable.id] }
                            .single()
                    beforeRow.toBranchDay() to afterRow.toBranchDay()
                }

            val remittanceAfter =
                findByIdInTransaction(params.remittanceId)
                    ?: error("remittance not found after undo for ${params.remittanceId}")

            auditFn(
                UndoAuditContext(
                    remittanceBefore = remittanceBefore,
                    remittanceAfter = remittanceAfter,
                    branchDayPairs = branchDayPairs,
                    snapshotBefore = snapshotBefore,
                ),
            )

            remittanceAfter
        }.also { remittance ->
            logger.info {
                "[UNDO-REMITTANCE] Remittance ${params.remittanceId.toString().maskUUID()}" +
                    " undone=${remittance != null} reason=${params.reason}"
            }
        }

    @Suppress("ReturnCount")
    fun updateHeader(
        params: UpdateHeaderParams,
        auditFn: (Remittance, Remittance) -> Unit = { _, _ -> },
    ): Remittance? =
        transaction {
            val existing =
                RemittanceTable
                    .selectAll()
                    .where { RemittanceTable.id eq params.remittanceId }
                    .singleOrNull() ?: return@transaction null

            if (existing[RemittanceTable.status] != RemittanceStatus.DRAFT) {
                throw ValidationException("Can only update header of DRAFT remittances")
            }
            if (existing[RemittanceTable.version] != params.expectedVersion) {
                throw VersionMismatchException(RemittanceTable.tableName, params.remittanceId)
            }

            val tupleOccupied =
                RemittanceTable
                    .selectAll()
                    .where {
                        (RemittanceTable.branchId eq existing[RemittanceTable.branchId]) and
                            (RemittanceTable.type eq params.type) and
                            (RemittanceTable.submittedDate eq existing[RemittanceTable.submittedDate]) and
                            (RemittanceTable.id neq params.remittanceId)
                    }.empty()
                    .not()
            if (tupleOccupied) {
                throw ConflictException(
                    "A remittance of type ${params.type.name} already exists " +
                        "for ${existing[RemittanceTable.submittedDate]}",
                )
            }

            val updated =
                RemittanceTable.update({
                    (RemittanceTable.id eq params.remittanceId) and
                        (RemittanceTable.version eq params.expectedVersion) and
                        (RemittanceTable.status eq RemittanceStatus.DRAFT)
                }) {
                    it[RemittanceTable.type] = params.type
                    it[RemittanceTable.method] = params.method
                    it[RemittanceTable.dateRangeStart] = params.dateRangeStart
                    it[RemittanceTable.dateRangeEnd] = params.dateRangeEnd
                    it[RemittanceTable.version] = params.expectedVersion + 1
                }
            if (updated == 0) {
                throw VersionMismatchException(RemittanceTable.tableName, params.remittanceId)
            }

            val before = existing.toRemittance()
            val after =
                findByIdInTransaction(params.remittanceId)
                    ?: error("remittance not found after header update for ${params.remittanceId}")
            auditFn(before, after)
            after
        }.also { remittance ->
            logger.info {
                "[UPDATE-REMITTANCE-HEADER] Remittance ${params.remittanceId.toString().maskUUID()}" +
                    " header updated=${remittance != null}"
            }
        }

    private fun updateRemittanceToSubmitted(
        remittanceId: UUID,
        expectedVersion: Int,
        callerId: UUID,
    ) {
        val today = LocalDate.now(java.time.ZoneId.of("Asia/Manila"))
        val updated =
            RemittanceTable.update({
                (RemittanceTable.id eq remittanceId) and
                    (RemittanceTable.version eq expectedVersion)
            }) {
                it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
                it[RemittanceTable.version] = expectedVersion + 1
                it[RemittanceTable.submittedDate] = today
                it[RemittanceTable.submittedAt] = CurrentTimestampWithTimeZone
                it[RemittanceTable.submittedBy] = callerId
            }

        if (updated == 0) {
            throw VersionMismatchException(RemittanceTable.tableName, remittanceId)
        }
    }

    @Suppress("LongParameterList")
    private fun writeFinancialSnapshot(
        remittanceType: RemittanceType,
        remittanceId: UUID,
        grossIncome: BigDecimal,
        totalCompensation: BigDecimal,
        totalExpenses: BigDecimal,
        netIncome: BigDecimal,
    ) {
        if (remittanceType == RemittanceType.SESSION) {
            RemittanceFinancialSnapshotRepository.insert(
                RemittanceFinancialSnapshotCreateParams(
                    remittanceId = remittanceId,
                    grossIncome = grossIncome,
                    totalCompensation = totalCompensation,
                    totalExpenses = totalExpenses,
                    netIncome = netIncome,
                ),
            )
        }
    }

    private fun updateBranchDayStatuses(breakdownIds: List<UUID>) {
        for (bdId in breakdownIds) {
            BranchDayTable.update({ BranchDayTable.id eq bdId }) {
                it[BranchDayTable.status] = DayStatus.REMITTED
            }
        }
    }

    private fun calculateGrossIncome(remittanceId: UUID): BigDecimal =
        RemittanceLineTable
            .selectAll()
            .where {
                (RemittanceLineTable.remittanceId eq remittanceId) and
                    (RemittanceLineTable.type eq RemittanceLineType.SESSION) and
                    RemittanceLineTable.deletedAt.isNull()
            }.map { it[RemittanceLineTable.amount] }
            .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }

    internal fun netOf(
        grossIncome: BigDecimal,
        totalCompensation: BigDecimal,
        totalExpenses: BigDecimal,
    ): BigDecimal = grossIncome.subtract(totalCompensation).subtract(totalExpenses)

    internal fun calculateCompensationSum(breakdownIds: List<UUID>): BigDecimal =
        transaction {
            if (breakdownIds.isEmpty()) return@transaction BigDecimal.ZERO
            CompensationTable
                .selectAll()
                .where { CompensationTable.payingBranchDayId inList breakdownIds }
                .map { it[CompensationTable.amount] }
                .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
        }

    internal fun calculateExpenseSum(breakdownIds: List<UUID>): BigDecimal =
        transaction {
            if (breakdownIds.isEmpty()) return@transaction BigDecimal.ZERO
            ExpenseTable
                .selectAll()
                .where {
                    (ExpenseTable.branchDayId inList breakdownIds) and
                        ExpenseTable.deletedAt.isNull()
                }.map { it[ExpenseTable.amount] }
                .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
        }

    private fun ResultRow.toBranchDay(): BranchDay =
        BranchDay(
            id = this[BranchDayTable.id],
            branchId = this[BranchDayTable.branchId],
            date = this[BranchDayTable.date],
            status = this[BranchDayTable.status],
        )

    private fun ResultRow.toRemittance(): Remittance =
        Remittance(
            id = this[RemittanceTable.id],
            type = this[RemittanceTable.type],
            status = this[RemittanceTable.status],
            branchId = this[RemittanceTable.branchId],
            method = this[RemittanceTable.method],
            submittedDate = this[RemittanceTable.submittedDate],
            submittedAt = this[RemittanceTable.submittedAt],
            submittedBy = this[RemittanceTable.submittedBy],
            dateRangeStart = this[RemittanceTable.dateRangeStart],
            dateRangeEnd = this[RemittanceTable.dateRangeEnd],
            createdAt = this[RemittanceTable.createdAt],
            version = this[RemittanceTable.version],
        )
}

data class UndoAuditContext(
    val remittanceBefore: Remittance,
    val remittanceAfter: Remittance,
    val branchDayPairs: List<Pair<BranchDay, BranchDay>>,
    val snapshotBefore: RemittanceFinancialSnapshot?,
)

data class SubmitAuditContext(
    val remittanceBefore: Remittance,
    val remittanceAfter: Remittance,
    val branchDayPairs: List<Pair<BranchDay, BranchDay>>,
)

data class RemittanceSubmissionResult(
    val remittance: Remittance,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
)
