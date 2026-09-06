package com.companyb.companyapp.commerce
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
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
        IdentityFixtures.insertTestUser(callerId, "caller")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
    }

    @Test
    fun `create persists product and writes audit row`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)

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
    }

    @Test
    fun `duplicate client generated id returns existing product without extra audit`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
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
    }

    @Test
    fun `list returns active products`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
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
    }

    @Test
    fun `findAll includes deactivated row while findAllActive omits it`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Active Product",
            productCategoryId = categoryId,
            unitPrice = BigDecimal("100.00"),
            commissionAmount = BigDecimal("10.00"),
        )
        ProductService.create(
            callerId = callerId,
            id = productId2,
            name = "Soon Inactive",
            productCategoryId = categoryId,
            unitPrice = BigDecimal("200.00"),
            commissionAmount = BigDecimal("20.00"),
        )
        ProductService.update(
            callerId = callerId,
            productId = productId2,
            name = null,
            productCategoryId = null,
            unitPrice = null,
            commissionAmount = null,
            isActive = false,
        )

        val activeIds = ProductService.findAllActive().map { it.id }.toSet()
        assertTrue(productId in activeIds)
        assertFalse(productId2 in activeIds)

        val allIds = ProductService.findAll().map { it.id }.toSet()
        assertTrue(productId in allIds)
        assertTrue(productId2 in allIds)
    }

    @Test
    fun `find by id returns persisted product`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
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
    }

    @Test
    fun `update persists changes and writes audit row`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
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
    }

    @Test
    fun `findAllActive without MANAGE_PRODUCTS is allowed at service layer`() {
        ProductService.findAllActive()
    }

    @Test
    fun `findById without MANAGE_PRODUCTS is allowed at service layer`() {
        val newProductId = TestFixtures.uuid()
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        ProductService.create(
            callerId = callerId,
            id = newProductId,
            name = "Find Product",
            productCategoryId = categoryId,
            unitPrice = BigDecimal("250.00"),
            commissionAmount = BigDecimal("25.00"),
        )

        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "other")

        val found = ProductService.findById(newProductId)
        assertEquals("Find Product", found.name)
    }

    @Test
    fun `create with non-existent category returns bad request`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)

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
                            TestFixtures.extractJsonField(it, "isActive")
                        }
                    val newState =
                        row[AuditLogTable.newValue]?.let {
                            TestFixtures.extractJsonField(it, "isActive")
                        }
                    oldState to newState
                }
        }
}
