package com.companyb.companyapp.test

import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.MedicalMissionDelegateTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class BasePostgresTestTeardownTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @Test
    @Suppress("LongMethod")
    fun `tracked rows with corrected foreign key graph clean successfully`() {
        val userId = TestFixtures.uuid()
        val branchId = TestFixtures.uuid()
        val clientId = TestFixtures.uuid()
        val sessionId = TestFixtures.uuid()
        val categoryId = TestFixtures.uuid()
        val productId = TestFixtures.uuid()
        val saleId = TestFixtures.uuid()
        val movementId = TestFixtures.uuid()
        val delegateId = TestFixtures.uuid()
        val notificationId = TestFixtures.uuid()

        DatabaseTestHelper.insertTestUser(userId, "teardown")
        DatabaseTestHelper.insertTestBranch(branchId)
        val actualBranchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        DatabaseTestHelper.insertTestClient(clientId)
        DatabaseTestHelper.insertTestSession(sessionId, clientId, actualBranchDayId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
        DatabaseTestHelper.insertTestProductSale(
            id = saleId,
            branchDayId = actualBranchDayId,
            productId = productId,
            handledBy = userId,
        )
        transaction {
            InventoryMovementTable.insert {
                it[InventoryMovementTable.id] = movementId
                it[InventoryMovementTable.productId] = productId
                it[InventoryMovementTable.productSaleId] = saleId
                it[InventoryMovementTable.branchId] = branchId
                it[InventoryMovementTable.branchDayId] = actualBranchDayId
                it[InventoryMovementTable.reason] = InventoryMovementReason.SALE
                it[InventoryMovementTable.quantityChange] = -1
                it[InventoryMovementTable.movedBy] = userId
            }
            MedicalMissionDelegateTable.insert {
                it[MedicalMissionDelegateTable.id] = delegateId
                it[MedicalMissionDelegateTable.targetUser] = userId
                it[MedicalMissionDelegateTable.assignedBy] = userId
                it[MedicalMissionDelegateTable.branchId] = branchId
            }
            NotificationTable.insert {
                it[NotificationTable.id] = notificationId
                it[NotificationTable.sessionId] = sessionId
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = branchId
                it[NotificationTable.message] = "Test notification"
            }
        }

        trackOwned(InventoryMovementTable, InventoryMovementTable.id, movementId)
        trackOwned(MedicalMissionDelegateTable, MedicalMissionDelegateTable.id, delegateId)
        trackOwned(NotificationTable, NotificationTable.id, notificationId)
        trackOwned(ProductSaleTable, ProductSaleTable.id, saleId)
        trackOwned(ProductTable, ProductTable.id, productId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(BranchDayTable, BranchDayTable.id, actualBranchDayId)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(AppUserTable, AppUserTable.id, userId)

        cleanTrackedRows()

        transaction {
            assertEquals(
                0L,
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.id eq movementId }
                    .count(),
            )
            assertEquals(
                0L,
                MedicalMissionDelegateTable
                    .selectAll()
                    .where { MedicalMissionDelegateTable.id eq delegateId }
                    .count(),
            )
            assertEquals(
                0L,
                NotificationTable
                    .selectAll()
                    .where { NotificationTable.id eq notificationId }
                    .count(),
            )
        }
    }
}
