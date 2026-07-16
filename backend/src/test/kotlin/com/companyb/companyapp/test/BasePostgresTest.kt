package com.companyb.companyapp.test

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BasePostgresTest {
    private data class TrackedRow(
        val table: Table,
        val column: Column<UUID>,
        val id: UUID,
    )

    private val tracked = mutableListOf<TrackedRow>()

    protected fun trackOwned(
        table: Table,
        column: Column<UUID>,
        id: UUID,
    ) {
        tracked.add(TrackedRow(table, column, id))
    }

    protected abstract fun initTestData()

    @BeforeTest
    fun setUpBase() {
        DatabaseTestHelper.ensureDatabase()
        cleanTrackedRows()
        tracked.clear()
        initTestData()
    }

    @AfterTest
    fun tearDownBase() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            cleanTrackedRows()
        }
    }

    private fun cleanTrackedRows() {
        val grouped = tracked.groupBy({ it.table to it.column }) { it.id }
        transaction {
            for (table in DELETION_ORDER) {
                val entries = grouped.filterKeys { (t, _) -> t === table }
                for ((_, column) in entries.keys) {
                    val ids = entries[table to column] ?: continue
                    val uniqueIds = ids.distinct()
                    table.deleteWhere {
                        if (uniqueIds.size == 1) {
                            column eq uniqueIds.single()
                        } else {
                            column inList uniqueIds
                        }
                    }
                }
            }
        }
    }

    companion object {
        val DELETION_ORDER: List<Table> =
            listOf(
                com.companyb.companyapp.repository.model.SessionConcernTable,
                com.companyb.companyapp.repository.model.SessionPractitionerTable,
                com.companyb.companyapp.repository.model.SessionVoidTable,
                com.companyb.companyapp.repository.model.CommissionManualInclusionTable,
                com.companyb.companyapp.repository.model.CommissionSplitTable,
                com.companyb.companyapp.repository.model.InventoryMovementTable,
                com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable,
                com.companyb.companyapp.repository.model.RemittanceLineTable,
                com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable,
                com.companyb.companyapp.repository.model.ProductSaleTable,
                com.companyb.companyapp.repository.model.SessionTable,
                com.companyb.companyapp.repository.model.CompensationTable,
                com.companyb.companyapp.repository.model.ExpenseTable,
                com.companyb.companyapp.repository.model.AttendanceTable,
                com.companyb.companyapp.repository.model.SessionBaseRateTable,
                com.companyb.companyapp.repository.model.BranchDayAssignmentTable,
                com.companyb.companyapp.repository.model.RemittanceTable,
                com.companyb.companyapp.repository.model.ProductTable,
                com.companyb.companyapp.repository.model.ProductCategoryTable,
                com.companyb.companyapp.repository.model.BranchDayTable,
                com.companyb.companyapp.repository.model.BranchInventoryTable,
                com.companyb.companyapp.repository.model.BranchTable,
                com.companyb.companyapp.repository.model.ClientTable,
                com.companyb.companyapp.repository.model.UserBranchAssignmentTable,
                com.companyb.companyapp.repository.model.UserRoleTable,
                com.companyb.companyapp.repository.model.UserCapabilityTable,
                com.companyb.companyapp.repository.model.AuditLogTable,
                com.companyb.companyapp.repository.model.NotificationTable,
                com.companyb.companyapp.repository.model.ConcernTable,
                com.companyb.companyapp.repository.model.AllowanceTable,
                com.companyb.companyapp.repository.model.GrantReliefAccessTable,
                com.companyb.companyapp.repository.model.MedicalMissionDelegateTable,
                com.companyb.companyapp.repository.model.AppUserTable,
            )
    }
}
