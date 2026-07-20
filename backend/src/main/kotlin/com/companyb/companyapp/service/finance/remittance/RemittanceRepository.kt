package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotCreateParams
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RemittanceType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDate
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

    @Suppress("ReturnCount", "ComplexMethod")
    fun submit(
        remittanceId: UUID,
        expectedVersion: Int,
        callerId: UUID,
        auditFn: (SubmitAuditContext) -> Unit = {},
    ): RemittanceSubmissionResult? =
        transaction(transactionIsolation = SERIALIZABLE_ISOLATION) {
            val remittance =
                RemittanceTable
                    .selectAll()
                    .where { RemittanceTable.id eq remittanceId }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull() ?: return@transaction null

            if (remittance[RemittanceTable.version] != expectedVersion) {
                throw VersionMismatchException(RemittanceTable.tableName, remittanceId)
            }
            if (remittance[RemittanceTable.status] != RemittanceStatus.DRAFT) {
                throw ValidationException("Can only submit DRAFT remittances")
            }

            val remittanceType = remittance[RemittanceTable.type]

            val breakdownIds =
                RemittanceDayBreakdownTable
                    .selectAll()
                    .where { RemittanceDayBreakdownTable.remittanceId eq remittanceId }
                    .map { it[RemittanceDayBreakdownTable.branchDayId] }

            val grossIncome = calculateGrossIncome(remittanceId)
            val totalCompensation = calculateTotalCompensation(breakdownIds)
            val totalExpenses = calculateTotalExpenses(breakdownIds)
            val netIncome = grossIncome.subtract(totalCompensation).subtract(totalExpenses)

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
            auditFn(SubmitAuditContext(remittanceId, breakdownIds))

            val submitted =
                findByIdInTransaction(remittanceId)
                    ?: error("remittance not found after submit for $remittanceId")

            RemittanceSubmissionResult(
                remittance = submitted,
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

    private fun calculateTotalCompensation(breakdownIds: List<UUID>): BigDecimal {
        if (breakdownIds.isEmpty()) return BigDecimal.ZERO
        return CompensationTable
            .selectAll()
            .where { CompensationTable.payingBranchDayId inList breakdownIds }
            .map { it[CompensationTable.amount] }
            .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
    }

    private fun calculateTotalExpenses(breakdownIds: List<UUID>): BigDecimal {
        if (breakdownIds.isEmpty()) return BigDecimal.ZERO
        return ExpenseTable
            .selectAll()
            .where {
                (ExpenseTable.branchDayId inList breakdownIds) and
                    ExpenseTable.deletedAt.isNull()
            }.map { it[ExpenseTable.amount] }
            .fold(BigDecimal.ZERO) { acc, amount -> acc.add(amount) }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRemittance(): Remittance =
        Remittance(
            id = this[RemittanceTable.id],
            type = this[RemittanceTable.type],
            status = this[RemittanceTable.status],
            branchId = this[RemittanceTable.branchId],
            method = this[RemittanceTable.method],
            submittedDate = this[RemittanceTable.submittedDate],
            submittedBy = this[RemittanceTable.submittedBy],
            dateRangeStart = this[RemittanceTable.dateRangeStart],
            dateRangeEnd = this[RemittanceTable.dateRangeEnd],
            createdAt = this[RemittanceTable.createdAt],
            version = this[RemittanceTable.version],
        )
}

data class SubmitAuditContext(
    val remittanceId: UUID,
    val breakdownIds: List<UUID>,
)

data class RemittanceSubmissionResult(
    val remittance: Remittance,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
)
