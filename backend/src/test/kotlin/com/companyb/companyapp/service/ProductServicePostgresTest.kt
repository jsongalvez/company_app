package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val productId2 = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows(callerId, productId, productId2, categoryId)
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        DatabaseTestHelper.insertTestCategory(categoryId, "Test Category")
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows(callerId, productId, productId2, categoryId)
        }
    }

    @Test
    fun `create persists product and writes audit row`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        val result =
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "  Test Product  ",
                productCategoryId = categoryId,
                unitPrice = "250.00",
                commissionAmount = "25.00",
            )

        assertTrue(result.created)
        assertEquals("Test Product", result.product.name)
        assertEquals(BigDecimal("250.00"), result.product.unitPrice)
        assertEquals(BigDecimal("25.00"), result.product.commissionAmount)
        assertTrue(result.product.isActive)
        assertEquals(1L, auditEntryCount(productId))
    }

    @Test
    fun `duplicate client generated id returns existing product without extra audit`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        val first =
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "Test Product",
                productCategoryId = categoryId,
                unitPrice = "250.00",
                commissionAmount = "25.00",
            )

        val duplicate =
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "Changed Name",
                productCategoryId = categoryId,
                unitPrice = "100.00",
                commissionAmount = "10.00",
            )

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Test Product", duplicate.product.name)
        assertEquals(BigDecimal("250.00"), duplicate.product.unitPrice)
        assertEquals(1L, auditEntryCount(productId))
    }

    @Test
    fun `list returns active products`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Product A",
            productCategoryId = categoryId,
            unitPrice = "100.00",
            commissionAmount = "10.00",
        )
        ProductService.create(
            callerId = callerId,
            id = productId2,
            name = "Product B",
            productCategoryId = categoryId,
            unitPrice = "200.00",
            commissionAmount = "20.00",
        )

        val all = ProductService.findAllActive(callerId)
        val allIds = all.map { it.id }.toSet()

        assertTrue(productId in allIds)
        assertTrue(productId2 in allIds)
    }

    @Test
    fun `find by id returns persisted product`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Test Product",
            productCategoryId = categoryId,
            unitPrice = "250.00",
            commissionAmount = "25.00",
        )

        val found = ProductService.findById(callerId, productId)
        assertEquals("Test Product", found.name)
    }

    @Test
    fun `update persists changes and writes audit row`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Original Name",
            productCategoryId = categoryId,
            unitPrice = "100.00",
            commissionAmount = "10.00",
        )

        val updated =
            ProductService.update(
                callerId = callerId,
                productId = productId,
                name = "Updated Name",
                productCategoryId = null,
                unitPrice = "200.00",
                commissionAmount = null,
                isActive = false,
            )

        assertEquals("Updated Name", updated.name)
        assertEquals(BigDecimal("200.00"), updated.unitPrice)
        assertFalse(updated.isActive)
        assertEquals(2L, auditEntryCount(productId))
    }

    @Test
    fun `create without MANAGE_PRODUCTS is forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "Test Product",
                productCategoryId = categoryId,
                unitPrice = "250.00",
                commissionAmount = "25.00",
            )
        }
    }

    @Test
    fun `findAllActive without MANAGE_PRODUCTS is forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            ProductService.findAllActive(callerId)
        }
    }

    @Test
    fun `findById without MANAGE_PRODUCTS is forbidden`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Test Product",
            productCategoryId = categoryId,
            unitPrice = "250.00",
            commissionAmount = "25.00",
        )

        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "other")

        assertFailsWith<ForbiddenResponse> {
            ProductService.findById(otherCaller, productId)
        }
    }

    @Test
    fun `create with non-existent category returns bad request`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        assertFailsWith<BadRequestResponse> {
            ProductService.create(
                callerId = callerId,
                id = productId,
                name = "Test Product",
                productCategoryId = UUID.randomUUID(),
                unitPrice = "250.00",
                commissionAmount = "25.00",
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

    private fun deleteTestRows(
        userId: UUID,
        prodId1: UUID,
        prodId2: UUID,
        catId: UUID,
    ) {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq userId) or
                    (AuditLogTable.recordId eq prodId1) or
                    (AuditLogTable.recordId eq prodId2) or
                    (AuditLogTable.recordId eq catId)
            }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
            ProductTable.deleteWhere {
                (ProductTable.id eq prodId1) or (ProductTable.id eq prodId2)
            }
            ProductCategoryTable.deleteWhere { ProductCategoryTable.id eq catId }
            AppUserTable.deleteWhere { AppUserTable.id eq userId }
        }
    }
}
