package com.companyb.companyapp.commerce

import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.MovementType
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/** Ids, seed rows and helpers shared by the branch inventory suites. */
abstract class BranchInventoryServicePostgresBase : BasePostgresTest() {
    protected val callerId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()
    protected val categoryId = TestFixtures.uuid()
    protected val productId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "inv-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Inventory Branch ${branchId.toString().take(8)}")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
    }

    protected fun restock(
        targetProductId: UUID,
        branchDayId: UUID,
        quantity: Int,
        movementId: UUID = TestFixtures.uuid(),
    ): InventoryMovement =
        InventoryService.recordMovement(
            callerId = callerId,
            NewInventoryMovement(
                movementId = movementId,
                branchId = branchId,
                productId = targetProductId,
                movementType = MovementType.Restock,
                quantityChange = quantity,
                notes = null,
                branchDayId = branchDayId,
            ),
        )

    protected fun movementCount(targetProductId: UUID): Long =
        transaction {
            InventoryMovementTable
                .selectAll()
                .where {
                    (InventoryMovementTable.branchId eq branchId) and
                        (InventoryMovementTable.productId eq targetProductId)
                }.count()
        }

    /** A movement of [productId] at [branchId] with no notes. */
    protected fun movement(
        quantityChange: Int,
        branchDayId: UUID,
        movementType: MovementType = MovementType.Restock,
        movementId: UUID = TestFixtures.uuid(),
    ) = NewInventoryMovement(movementId, branchId, productId, movementType, quantityChange, null, branchDayId)
}
