package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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
        insertUser(callerId)
        insertCategory(categoryId, "Test Category")
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows(callerId, productId, productId2, categoryId)
        }
    }

    @Test
    fun `create persists product and writes audit row`() {
        grantManageProducts(callerId)

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
        grantManageProducts(callerId)
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
        grantManageProducts(callerId)
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

        val all = ProductService.findAllActive()
        val allIds = all.map { it.id }.toSet()

        assertTrue(productId in allIds)
        assertTrue(productId2 in allIds)
    }

    @Test
    fun `find by id returns persisted product`() {
        grantManageProducts(callerId)
        ProductService.create(
            callerId = callerId,
            id = productId,
            name = "Test Product",
            productCategoryId = categoryId,
            unitPrice = "250.00",
            commissionAmount = "25.00",
        )

        val found = ProductService.findById(productId)
        assertEquals("Test Product", found.name)
    }

    @Test
    fun `update persists changes and writes audit row`() {
        grantManageProducts(callerId)
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
    fun `create with non-existent category returns bad request`() {
        grantManageProducts(callerId)

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

    private fun insertUser(userId: UUID) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "prod-caller-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Product Caller",
        )
    }

    private fun insertCategory(
        id: UUID,
        name: String,
    ) {
        transaction {
            ProductCategoryTable.insertIgnore {
                it[ProductCategoryTable.id] = id
                it[ProductCategoryTable.name] = name
            }
        }
    }

    private fun grantManageProducts(userId: UUID) {
        DatabaseTestHelper.grantManageProducts(userId, sourceId)
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
