package com.companyb.companyapp.service
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val productId2 = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
    }

    @Test
    fun `create persists product and writes audit row`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val result =
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "Test Product",
                productCategoryId = categoryId,
                unitPrice = BigDecimal("250.00"),
                commissionAmount = BigDecimal("25.00"),
            )

        assertTrue(result.created)
        assertEquals("Test Product", result.product.name)
        assertEquals(BigDecimal("250.00"), result.product.unitPrice)
        assertEquals(BigDecimal("25.00"), result.product.commissionAmount)
        assertTrue(result.product.isActive)
        assertEquals(1L, auditEntryCount(productId))
        val insertStates = auditActiveStateChanges(productId, AuditAction.INSERT)
        assertEquals(1, insertStates.size)
        assertEquals("true", insertStates.single().second)
        trackOwned(ProductTable, ProductTable.id, productId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `duplicate client generated id returns existing product without extra audit`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val first =
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "Test Product",
                productCategoryId = categoryId,
                unitPrice = BigDecimal("250.00"),
                commissionAmount = BigDecimal("25.00"),
            )

        val duplicate =
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "Changed Name",
                productCategoryId = categoryId,
                unitPrice = BigDecimal("100.00"),
                commissionAmount = BigDecimal("10.00"),
            )

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Test Product", duplicate.product.name)
        assertEquals(BigDecimal("250.00"), duplicate.product.unitPrice)
        assertEquals(1L, auditEntryCount(productId))
        trackOwned(ProductTable, ProductTable.id, productId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `list returns active products`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Product A",
            productCategoryId = categoryId,
            unitPrice = BigDecimal("100.00"),
            commissionAmount = BigDecimal("10.00"),
        )
        ProductService.create(
            callerId = callerId,
            id = productId2,
            name = "Product B",
            productCategoryId = categoryId,
            unitPrice = BigDecimal("200.00"),
            commissionAmount = BigDecimal("20.00"),
        )

        val all = ProductService.findAllActive()
        val allIds = all.map { it.id }.toSet()

        assertTrue(productId in allIds)
        assertTrue(productId2 in allIds)
        trackOwned(ProductTable, ProductTable.id, productId)
        trackOwned(ProductTable, ProductTable.id, productId2)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `find by id returns persisted product`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Test Product",
            productCategoryId = categoryId,
            unitPrice = BigDecimal("250.00"),
            commissionAmount = BigDecimal("25.00"),
        )

        val found = ProductService.findById(productId)
        assertEquals("Test Product", found.name)
        trackOwned(ProductTable, ProductTable.id, productId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `update persists changes and writes audit row`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Original Name",
            productCategoryId = categoryId,
            unitPrice = BigDecimal("100.00"),
            commissionAmount = BigDecimal("10.00"),
        )

        val updated =
            ProductService.update(
                callerId = callerId,
                productId = productId,
                name = "Updated Name",
                productCategoryId = null,
                unitPrice = BigDecimal("200.00"),
                commissionAmount = null,
                isActive = false,
            )

        assertEquals("Updated Name", updated.name)
        assertEquals(BigDecimal("200.00"), updated.unitPrice)
        assertFalse(updated.isActive)
        val updateStates = auditActiveStateChanges(productId, AuditAction.UPDATE)
        assertEquals(1, updateStates.size)
        assertTrue(updateStates.contains("true" to "false"))

        val reactivated =
            ProductService.update(
                callerId = callerId,
                productId = productId,
                name = null,
                productCategoryId = null,
                unitPrice = null,
                commissionAmount = null,
                isActive = true,
            )

        assertTrue(reactivated.isActive)
        assertEquals(3L, auditEntryCount(productId))
        val allUpdateStates = auditActiveStateChanges(productId, AuditAction.UPDATE)
        assertEquals(2, allUpdateStates.size)
        assertTrue(allUpdateStates.contains("true" to "false"))
        assertTrue(allUpdateStates.contains("false" to "true"))
        trackOwned(ProductTable, ProductTable.id, productId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `create without MANAGE_PRODUCTS is allowed at service layer`() {
        val newProductId = TestFixtures.uuid()
        val result =
            ProductService.create(
                callerId = callerId,
                id = newProductId,
                name = "New Product",
                productCategoryId = categoryId,
                unitPrice = BigDecimal("250.00"),
                commissionAmount = BigDecimal("25.00"),
            )

        assertTrue(result.created)
        assertEquals("New Product", result.product.name)
        trackOwned(ProductTable, ProductTable.id, newProductId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `findAllActive without MANAGE_PRODUCTS is allowed at service layer`() {
        ProductService.findAllActive()
    }

    @Test
    fun `findById without MANAGE_PRODUCTS is allowed at service layer`() {
        val newProductId = TestFixtures.uuid()
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        ProductService.create(
            callerId = callerId,
            id = newProductId,
            name = "Find Product",
            productCategoryId = categoryId,
            unitPrice = BigDecimal("250.00"),
            commissionAmount = BigDecimal("25.00"),
        )

        val otherCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherCaller, "other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)

        val found = ProductService.findById(newProductId)
        assertEquals("Find Product", found.name)
        trackOwned(ProductTable, ProductTable.id, newProductId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `create with non-existent category returns bad request`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<ValidationException> {
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "Test Product",
                productCategoryId = TestFixtures.uuid(),
                unitPrice = BigDecimal("250.00"),
                commissionAmount = BigDecimal("25.00"),
            )
        }
    }

    private fun auditEntryCount(productId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "product") and
                        (AuditLogTable.recordId eq productId)
                }.count()
        }

    private fun auditActiveStateChanges(
        productId: UUID,
        action: AuditAction,
    ): List<Pair<String?, String?>> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ProductTable.tableName) and
                        (AuditLogTable.recordId eq productId) and
                        (AuditLogTable.action eq action)
                }.map { row ->
                    val oldState =
                        row[AuditLogTable.oldValue]?.let {
                            DatabaseTestHelper.extractJsonField(it, "isActive")
                        }
                    val newState =
                        row[AuditLogTable.newValue]?.let {
                            DatabaseTestHelper.extractJsonField(it, "isActive")
                        }
                    oldState to newState
                }
        }
}
