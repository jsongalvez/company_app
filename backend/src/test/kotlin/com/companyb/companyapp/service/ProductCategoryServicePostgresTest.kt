package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.ProductCategory
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductCategoryServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val cat1Id = UUID.randomUUID()
    private val cat2Id = UUID.randomUUID()
    private val categoryIds = listOf(cat1Id, cat2Id)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows(callerId, categoryIds)
        insertUser(callerId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows(callerId, categoryIds)
        }
    }

    @Test
    fun `create persists category and writes audit row`() {
        grantManageProducts(callerId)

        val result = ProductCategoryService.create(callerId, cat1Id, "  Test Category  ")

        assertTrue(result.created)
        assertEquals("Test Category", result.category.name)

        val persisted = persistedCategory(cat1Id)
        assertNotNull(persisted)
        assertEquals("Test Category", persisted.name)
        assertEquals(1L, auditEntryCount(cat1Id))
        assertEquals("Test Category", auditNewName(cat1Id))
    }

    @Test
    fun `duplicate client generated id returns existing category without extra audit`() {
        grantManageProducts(callerId)
        val first = ProductCategoryService.create(callerId, cat1Id, "Test Category")

        val duplicate = ProductCategoryService.create(callerId, cat1Id, "Changed Name")

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Test Category", duplicate.category.name)
        assertEquals(1L, auditEntryCount(cat1Id))
    }

    @Test
    fun `list returns all categories`() {
        grantManageProducts(callerId)
        ProductCategoryService.create(callerId, cat1Id, "Category A")
        ProductCategoryService.create(callerId, cat2Id, "Category B")

        val all = ProductCategoryService.findAll(callerId)
        val allIds = all.map { it.id }.toSet()

        assertTrue(cat1Id in allIds)
        assertTrue(cat2Id in allIds)
    }

    @Test
    fun `find by id returns persisted category`() {
        grantManageProducts(callerId)
        ProductCategoryService.create(callerId, cat1Id, "Test Category")

        val found = ProductCategoryService.findById(callerId, cat1Id)

        assertEquals("Test Category", found.name)
    }

    @Test
    fun `create without MANAGE_PRODUCTS is forbidden and does not insert category`() {
        assertFailsWith<ForbiddenResponse> {
            ProductCategoryService.create(callerId, cat1Id, "Test Category")
        }

        assertFalse(categoryExists(cat1Id))
        assertEquals(0L, auditEntryCount(cat1Id))
    }

    @Test
    fun `findAll without MANAGE_PRODUCTS is forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            ProductCategoryService.findAll(callerId)
        }
    }

    @Test
    fun `findById without MANAGE_PRODUCTS is forbidden`() {
        grantManageProducts(callerId)
        ProductCategoryService.create(callerId, cat1Id, "Test Category")

        val otherCaller = UUID.randomUUID()
        insertUser(otherCaller)

        assertFailsWith<ForbiddenResponse> {
            ProductCategoryService.findById(otherCaller, cat1Id)
        }
    }

    private fun insertUser(userId: UUID) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "cat-caller-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Category Caller",
        )
    }

    private fun grantManageProducts(userId: UUID) {
        DatabaseTestHelper.grantManageProducts(userId, sourceId)
    }

    private fun persistedCategory(categoryId: UUID): ProductCategory? =
        transaction {
            ProductCategoryTable
                .selectAll()
                .where { ProductCategoryTable.id eq categoryId }
                .singleOrNull()
                ?.let {
                    ProductCategory(
                        id = it[ProductCategoryTable.id],
                        name = it[ProductCategoryTable.name],
                    )
                }
        }

    private fun categoryExists(categoryId: UUID): Boolean =
        transaction {
            ProductCategoryTable
                .selectAll()
                .where { ProductCategoryTable.id eq categoryId }
                .empty()
                .not()
        }

    private fun auditEntryCount(categoryId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "product_category") and
                        (AuditLogTable.recordId eq categoryId)
                }.count()
        }

    private fun auditNewName(categoryId: UUID): String =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "product_category") and
                            (AuditLogTable.recordId eq categoryId)
                    }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()
            extractJsonField(row[AuditLogTable.newValue] ?: "{}", "name")
        }

    private fun deleteTestRows(
        userId: UUID,
        categoryIds: List<UUID>,
    ) {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq userId) or
                    (AuditLogTable.recordId eq categoryIds[0]) or
                    (AuditLogTable.recordId eq categoryIds[1])
            }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
            ProductCategoryTable.deleteWhere {
                (ProductCategoryTable.id eq categoryIds[0]) or
                    (ProductCategoryTable.id eq categoryIds[1])
            }
            AppUserTable.deleteWhere { AppUserTable.id eq userId }
        }
    }

    private companion object {
        private val json = Json

        private fun extractJsonField(
            jsonString: String,
            field: String,
        ): String {
            val jsonElement = json.parseToJsonElement(jsonString)
            return jsonElement.jsonObject[field]?.jsonPrimitive?.content ?: ""
        }
    }
}
