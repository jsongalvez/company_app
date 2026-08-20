package com.companyb.companyapp.service
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.ProductCategory
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductCategoryServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val cat1Id = TestFixtures.uuid()
    private val cat2Id = TestFixtures.uuid()
    private val categoryIds = listOf(cat1Id, cat2Id)

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
    }

    private val cat1Name = "Test Category $cat1Id"
    private val cat2Name = "Test Category $cat2Id"

    @Test
    fun `create persists category and writes audit row`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val result = ProductCategoryService.create(callerId, cat1Id, cat1Name)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, cat1Id)

        assertEquals(cat1Name, result.name)

        val persisted = persistedCategory(cat1Id)
        assertNotNull(persisted)
        assertEquals(cat1Name, persisted.name)
        assertEquals(1L, auditEntryCount(cat1Id))
        assertEquals(cat1Name, auditNewName(cat1Id))
    }

    @Test
    fun `duplicate client generated id returns existing category without extra audit`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        ProductCategoryService.create(callerId, cat1Id, cat1Name)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, cat1Id)

        val duplicate = ProductCategoryService.create(callerId, cat1Id, "Changed Name $cat1Id")

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        assertEquals(cat1Name, duplicate.name)
        assertEquals(1L, auditEntryCount(cat1Id))
    }

    @Test
    fun `list returns all categories`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        ProductCategoryService.create(callerId, cat1Id, cat1Name)
        ProductCategoryService.create(callerId, cat2Id, cat2Name)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        categoryIds.forEach { trackOwned(ProductCategoryTable, ProductCategoryTable.id, it) }

        val all = ProductCategoryService.findAll()
        val allIds = all.map { it.id }.toSet()

        assertTrue(cat1Id in allIds)
        assertTrue(cat2Id in allIds)
    }

    @Test
    fun `find by id returns persisted category`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        ProductCategoryService.create(callerId, cat1Id, cat1Name)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, cat1Id)

        val found = ProductCategoryService.findById(cat1Id)

        assertEquals(cat1Name, found.name)
    }

    @Test
    fun `create without MANAGE_PRODUCTS is allowed at service layer`() {
        val newCatId = TestFixtures.uuid()
        val result = ProductCategoryService.create(callerId, newCatId, "New Category")
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, newCatId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        assertTrue(categoryExists(newCatId))
        assertEquals(1L, auditEntryCount(newCatId))
    }

    @Test
    fun `findAll without MANAGE_PRODUCTS is allowed at service layer`() {
        ProductCategoryService.findAll()
    }

    @Test
    fun `findById without MANAGE_PRODUCTS is allowed at service layer`() {
        val newCatId = TestFixtures.uuid()
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        ProductCategoryService.create(callerId, newCatId, "Find Category")
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, newCatId)

        val otherCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherCaller, "other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)

        val found = ProductCategoryService.findById(newCatId)
        assertEquals("Find Category", found.name)
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
            DatabaseTestHelper.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "name")
        }
}
