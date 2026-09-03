package com.companyb.companyapp.api.routes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #436: shared catalog routes gate GLOBAL MANAGE_CATALOG on collections and
 * details. A BRANCH MANAGE_PRODUCTS grant must not satisfy the GLOBAL gates
 * (the #131 strictness); callers without the grant get 403, closing the
 * detail-UUID bypass. The broader authz matrix (unauthenticated callers,
 * role-derived holders, audit reads) is #437's scope.
 */
class CatalogAuthzTest : BasePostgresTest() {
    private val catalogUser = TestFixtures.uuid()
    private val branchProductsUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        trackOwned(AppUserTable, AppUserTable.id, catalogUser)
        DatabaseTestHelper.insertTestUser(catalogUser, "catalog")
        trackOwned(AppUserTable, AppUserTable.id, branchProductsUser)
        DatabaseTestHelper.insertTestUser(branchProductsUser, "branch-products")
        trackOwned(AppUserTable, AppUserTable.id, noneUser)
        DatabaseTestHelper.insertTestUser(noneUser, "no-caps")

        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestBranch(branchId, "Catalog Branch $branchId")

        DatabaseTestHelper.grantCapability(
            userId = catalogUser,
            capabilityCode = CapabilityCodes.MANAGE_CATALOG,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = branchProductsUser,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, catalogUser)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, branchProductsUser)

        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
    }

    companion object {
        private val DEFAULT_USER = TestFixtures.uuid()

        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin {
            val config = AppConfig.parse()
            JwtService.init(config)
            Password.init(config.authDummyPassword)
            return Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    Database.connect(DatabaseTestHelper.requireTestDataSource())
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: DEFAULT_USER.toString())
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                ProductRoutes.register(cfg)
                ProductCategoryRoutes.register(cfg)
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `GET products allowed for MANAGE_CATALOG holder`() {
        assertEquals(200, testServer.client.get("/api/products", asUser(catalogUser)).code)
    }

    @Test
    fun `GET products forbidden without catalog grant`() {
        assertEquals(403, testServer.client.get("/api/products", asUser(noneUser)).code)
    }

    @Test
    fun `GET products forbidden for BRANCH MANAGE_PRODUCTS holder`() {
        assertEquals(403, testServer.client.get("/api/products", asUser(branchProductsUser)).code)
    }

    @Test
    fun `GET product detail allowed for MANAGE_CATALOG holder`() {
        assertEquals(200, testServer.client.get("/api/products/$productId", asUser(catalogUser)).code)
    }

    @Test
    fun `GET product detail forbidden without catalog grant`() {
        assertEquals(403, testServer.client.get("/api/products/$productId", asUser(noneUser)).code)
    }

    @Test
    fun `POST products allowed for MANAGE_CATALOG holder`() {
        val newId = TestFixtures.uuid()
        trackOwned(ProductTable, ProductTable.id, newId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, catalogUser)
        val body =
            mapOf(
                "id" to newId.toString(),
                "name" to "Catalog Test Product",
                "productCategoryId" to categoryId.toString(),
                "unitPrice" to "50.00",
                "commissionAmount" to "5.00",
            )
        assertEquals(201, testServer.client.post("/api/products", body, asUser(catalogUser)).code)
    }

    @Test
    fun `POST products forbidden without catalog grant`() {
        val body =
            mapOf(
                "id" to TestFixtures.uuid().toString(),
                "name" to "Blocked Product",
                "productCategoryId" to categoryId.toString(),
                "unitPrice" to "50.00",
                "commissionAmount" to "5.00",
            )
        assertEquals(403, testServer.client.post("/api/products", body, asUser(noneUser)).code)
    }

    @Test
    fun `PATCH product allowed for MANAGE_CATALOG holder`() {
        trackOwned(AuditLogTable, AuditLogTable.changedBy, catalogUser)
        val body = mapOf("name" to "Renamed Product")
        assertEquals(200, testServer.client.patch("/api/products/$productId", body, asUser(catalogUser)).code)
    }

    @Test
    fun `PATCH product forbidden without catalog grant`() {
        val body = mapOf("name" to "Hacked Product")
        assertEquals(403, testServer.client.patch("/api/products/$productId", body, asUser(noneUser)).code)
    }

    @Test
    fun `GET product-categories allowed for MANAGE_CATALOG holder`() {
        assertEquals(200, testServer.client.get("/api/product-categories", asUser(catalogUser)).code)
    }

    @Test
    fun `GET product-categories forbidden without catalog grant`() {
        assertEquals(403, testServer.client.get("/api/product-categories", asUser(noneUser)).code)
    }

    @Test
    fun `GET product-category detail allowed for MANAGE_CATALOG holder`() {
        assertEquals(200, testServer.client.get("/api/product-categories/$categoryId", asUser(catalogUser)).code)
    }

    @Test
    fun `GET product-category detail forbidden without catalog grant`() {
        assertEquals(403, testServer.client.get("/api/product-categories/$categoryId", asUser(noneUser)).code)
    }

    @Test
    fun `POST product-categories allowed for MANAGE_CATALOG holder`() {
        val newId = TestFixtures.uuid()
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, newId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, catalogUser)
        val body = mapOf("id" to newId.toString(), "name" to "Catalog Test Category")
        assertEquals(201, testServer.client.post("/api/product-categories", body, asUser(catalogUser)).code)
    }

    @Test
    fun `POST product-categories forbidden without catalog grant`() {
        val body = mapOf("id" to TestFixtures.uuid().toString(), "name" to "Blocked Category")
        assertEquals(403, testServer.client.post("/api/product-categories", body, asUser(noneUser)).code)
    }
}
