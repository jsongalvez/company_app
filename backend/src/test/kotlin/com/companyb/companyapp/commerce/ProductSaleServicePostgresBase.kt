package com.companyb.companyapp.commerce

import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test

/** Ids, seed rows and stock helpers shared by the product sale service suites. */
abstract class ProductSaleServicePostgresBase : BasePostgresTest() {
    protected val callerId = TestFixtures.uuid()
    protected val branchId = TestFixtures.uuid()
    protected val categoryId = TestFixtures.uuid()
    protected val productId = TestFixtures.uuid()
    protected val clientId = TestFixtures.uuid()
    protected val sessionId = TestFixtures.uuid()

    protected lateinit var branchDayId: UUID

    protected val productName = "Sale Product ${TestFixtures.uuid().toString().take(8)}"

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "user")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Sale Branch")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, productName, categoryId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestClient(clientId)
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        IdentityFixtures.member(callerId, branchId)
        ensureInventoryCard(branchId, productId, 20)
    }

    protected fun ensureInventoryCard(
        branchId: UUID,
        productId: UUID,
        stock: Int,
    ) {
        transaction {
            CommerceFinanceFixtures.insertTestCard(branchId, productId, stock)
        }
    }

    protected fun currentStock(): Int =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq productId)
                }.single()[BranchInventoryTable.currentStock]
        }

    /** A walk-in sale of [productId] on [branchDayId]. */
    protected fun walkInSale(
        id: UUID,
        quantity: Int,
    ) = NewProductSale(id, branchDayId, null, null, true, productId, quantity)
}
