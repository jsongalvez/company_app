package com.companyb.companyapp.test

import com.companyb.companyapp.repository.model.AllowanceTable
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import com.companyb.companyapp.repository.model.CommissionSplitTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
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
    open fun tearDownBase() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            cleanTrackedRows()
        }
    }

    protected fun cleanTrackedRows() {
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
        private val FK_GRAPH: Map<Table, Set<Table>> =
            mapOf(
                AllowanceTable to setOf(AppUserTable, BranchDayTable),
                AttendanceTable to setOf(BranchDayTable, AppUserTable),
                AuditLogTable to setOf(AppUserTable),
                BranchDayAssignmentTable to setOf(BranchDayTable, AppUserTable),
                BranchInventoryTable to setOf(BranchTable, ProductTable),
                CommissionManualInclusionTable to setOf(BranchDayTable),
                CommissionSplitTable to setOf(BranchDayTable, AppUserTable),
                CompensationTable to setOf(BranchDayTable, AppUserTable),
                ConcernTable to setOf(AppUserTable),
                ExpenseTable to setOf(BranchDayTable, AppUserTable),
                GrantReliefAccessTable to setOf(AppUserTable),
                InventoryMovementTable to setOf(BranchInventoryTable, ProductSaleTable),
                MedicalMissionDelegateTable to setOf(SessionTable, BranchDayTable, AppUserTable),
                NotificationTable to setOf(SessionTable, AppUserTable),
                RemittanceDayBreakdownTable to setOf(RemittanceTable, BranchDayTable),
                RemittanceFinancialSnapshotTable to setOf(RemittanceTable),
                RemittanceLineTable to setOf(RemittanceTable, SessionTable, ProductTable),
                RemittanceTable to setOf(BranchTable),
                SessionBaseRateTable to setOf(SessionTable, BranchDayTable),
                SessionConcernTable to setOf(SessionTable, ConcernTable),
                SessionPractitionerTable to setOf(SessionTable, AppUserTable),
                SessionVoidTable to setOf(SessionTable, AppUserTable),
                UserBranchAssignmentTable to setOf(AppUserTable, BranchTable),
                UserCapabilityTable to setOf(AppUserTable, CapabilityTable),
                UserRoleTable to setOf(AppUserTable, RoleTable),
                ProductSaleTable to setOf(BranchDayTable, SessionTable, ProductTable, AppUserTable, ClientTable),
                ProductTable to setOf(ProductCategoryTable),
                SessionTable to setOf(BranchDayTable, ClientTable),
                BranchDayTable to setOf(BranchTable),
            )

        private val ALL_TABLES: Set<Table> =
            FK_GRAPH.keys + FK_GRAPH.values.flatten()

        val DELETION_ORDER: List<Table> by lazy { topologicalSort() }

        private fun topologicalSort(): List<Table> {
            val reverseEdgeCount = mutableMapOf<Table, Int>()
            for (table in ALL_TABLES) {
                reverseEdgeCount[table] = 0
            }
            for ((_, parents) in FK_GRAPH) {
                for (parent in parents) {
                    reverseEdgeCount[parent] = reverseEdgeCount[parent]!! + 1
                }
            }
            val result = mutableListOf<Table>()
            while (reverseEdgeCount.isNotEmpty()) {
                val leaf =
                    reverseEdgeCount.entries.firstOrNull { (_, count) -> count == 0 }
                        ?: error("FK_GRAPH has a cycle: remaining tables $reverseEdgeCount")
                result.add(leaf.key)
                reverseEdgeCount.remove(leaf.key)
                val childParents = FK_GRAPH[leaf.key] ?: emptySet()
                for (parent in childParents) {
                    reverseEdgeCount.computeIfPresent(parent) { _, c -> c - 1 }
                }
            }
            return result
        }
    }
}
