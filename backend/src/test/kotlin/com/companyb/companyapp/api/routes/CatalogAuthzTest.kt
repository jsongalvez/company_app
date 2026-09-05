package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.auth.JwtService
import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.service.ProductService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.JavalinTestServerRule
import com.companyb.companyapp.test.TestFixtures
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import io.javalin.testtools.Request
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #436: shared catalog routes gate GLOBAL MANAGE_CATALOG on collections and
 * details. A BRANCH MANAGE_PRODUCTS grant must not satisfy the GLOBAL gates
 * (the #131 strictness); callers without the grant get 403, closing the
 * detail-UUID bypass.
 *
 * #437: HTTP authz matrix over those gates — unauthenticated 401 via the real
 * JWT filter, role-derived OWNER/COORDINATOR reachability, BRANCH-holder
 * strictness on every catalog path, and audit-read scoping (covered in
 * AuditLogAuthzTest).
 */
class CatalogAuthzTest : BasePostgresTest() {
    private val catalogUser = TestFixtures.uuid()
    private val branchProductsUser = TestFixtures.uuid()
    private val noneUser = TestFixtures.uuid()
    private val ownerUser = TestFixtures.uuid()
    private val coordinatorUser = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(catalogUser, "catalog")
        DatabaseTestHelper.insertTestUser(branchProductsUser, "branch-products")
        DatabaseTestHelper.insertTestUser(noneUser, "no-caps")
        DatabaseTestHelper.insertTestUser(ownerUser, "catalog-owner")
        DatabaseTestHelper.insertTestUser(coordinatorUser, "catalog-coordinator")
        assignRole(ownerUser, "OWNER")
        assignRole(coordinatorUser, "COORDINATOR")

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

        DatabaseTestHelper.insertTestCategory(categoryId)
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
                cfg.routes.before("${ApiRoutes.API_PREFIX}*") { ctx ->
                    if (ctx.header("X-Test-User") == null) {
                        val token = ctx.header("Authorization")?.removePrefix("Bearer ") ?: throw UnauthorizedResponse()
                        ctx.attribute("userId", JwtService.verifyToken(token) ?: throw UnauthorizedResponse())
                    }
                }
                cfg.routes.exception(ForbiddenException::class.java) { e, ctx ->
                    ctx.status(403).json(mapOf("error" to (e.message ?: "Forbidden")))
                }
                ProductRoutes.register(cfg)
                ProductCategoryRoutes.register(cfg)
            }
        }
    }

    private fun assignRole(
        userId: UUID,
        roleName: String,
    ) {
        transaction {
            UserRoleTable.insert {
                it[UserRoleTable.userId] = userId
                it[UserRoleTable.roleId] =
                    RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]
            }
        }
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `GET products allowed for MANAGE_CATALOG holder`() {
        assertEquals(200, testServer.client.get("/api/products", asUser(catalogUser)).code)
    }

    @Test
    fun `GET products defaults to active-only omitting deactivated row`() {
        ProductService.update(
            callerId = catalogUser,
            productId = productId,
            name = null,
            productCategoryId = null,
            unitPrice = null,
            commissionAmount = null,
            isActive = false,
        )
        val body =
            testServer.client
                .get("/api/products", asUser(catalogUser))
                .body
                .string()
                .orEmpty()
        assertFalse(body.contains(productId.toString()), body)
    }

    @Test
    fun `GET products includeInactive returns deactivated row for catalog holder`() {
        ProductService.update(
            callerId = catalogUser,
            productId = productId,
            name = null,
            productCategoryId = null,
            unitPrice = null,
            commissionAmount = null,
            isActive = false,
        )
        val response = testServer.client.get("/api/products?includeInactive=true", asUser(catalogUser))
        assertEquals(200, response.code)
        val body = response.body.string().orEmpty()
        assertTrue(body.contains(productId.toString()), body)
    }

    @Test
    fun `GET products includeInactive forbidden without catalog grant`() {
        assertEquals(403, testServer.client.get("/api/products?includeInactive=true", asUser(noneUser)).code)
    }

    @Test
    fun `GET products includeInactive forbidden for BRANCH MANAGE_PRODUCTS holder`() {
        assertEquals(
            403,
            testServer.client.get("/api/products?includeInactive=true", asUser(branchProductsUser)).code,
        )
    }

    @Test
    fun `GET products includeInactive unauthenticated gets 401`() {
        assertEquals(401, testServer.client.get("/api/products?includeInactive=true").code)
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
        val body = mapOf("id" to newId.toString(), "name" to "Catalog Test Category")
        assertEquals(201, testServer.client.post("/api/product-categories", body, asUser(catalogUser)).code)
    }

    @Test
    fun `POST product-categories forbidden without catalog grant`() {
        val body = mapOf("id" to TestFixtures.uuid().toString(), "name" to "Blocked Category")
        assertEquals(403, testServer.client.post("/api/product-categories", body, asUser(noneUser)).code)
    }

    // #437 matrix: unauthenticated callers get 401 from the real JWT filter on
    // every catalog path (the X-Test-User harness bypasses it otherwise).

    @Test
    fun `unauthenticated catalog requests get 401`() {
        testServer.client.let { client ->
            assertEquals(401, client.get("/api/products").code)
            assertEquals(401, client.get("/api/products/$productId").code)
            assertEquals(
                401,
                client
                    .post(
                        "/api/products",
                        mapOf(
                            "id" to TestFixtures.uuid().toString(),
                            "name" to "No Auth",
                            "productCategoryId" to categoryId.toString(),
                            "unitPrice" to "10.00",
                            "commissionAmount" to "1.00",
                        ),
                    ).code,
            )
            assertEquals(401, client.patch("/api/products/$productId", mapOf("name" to "No Auth")).code)
            assertEquals(401, client.get("/api/product-categories").code)
            assertEquals(401, client.get("/api/product-categories/$categoryId").code)
            assertEquals(
                401,
                client
                    .post(
                        "/api/product-categories",
                        mapOf("id" to TestFixtures.uuid().toString(), "name" to "No Auth"),
                    ).code,
            )
        }
    }

    // #437 matrix: role-derived GLOBAL holders reach the gated routes without
    // a direct grant (V26 leg b for OWNER/MANAGER/COORDINATOR).

    @Test
    fun `GET products allowed for role-derived OWNER`() {
        assertEquals(200, testServer.client.get("/api/products", asUser(ownerUser)).code)
    }

    @Test
    fun `GET products allowed for role-derived COORDINATOR`() {
        assertEquals(200, testServer.client.get("/api/products", asUser(coordinatorUser)).code)
    }

    @Test
    fun `GET product-categories allowed for role-derived OWNER`() {
        assertEquals(200, testServer.client.get("/api/product-categories", asUser(ownerUser)).code)
    }

    @Test
    fun `GET product detail allowed for role-derived COORDINATOR`() {
        assertEquals(200, testServer.client.get("/api/products/$productId", asUser(coordinatorUser)).code)
    }

    @Test
    fun `POST products allowed for role-derived OWNER`() {
        val newId = TestFixtures.uuid()
        val body =
            mapOf(
                "id" to newId.toString(),
                "name" to "Owner Catalog Product",
                "productCategoryId" to categoryId.toString(),
                "unitPrice" to "60.00",
                "commissionAmount" to "6.00",
            )
        assertEquals(201, testServer.client.post("/api/products", body, asUser(ownerUser)).code)
    }

    // #437 matrix: BRANCH MANAGE_PRODUCTS never satisfies the GLOBAL catalog
    // gates (#131 strictness) — every catalog path, not just the collection.

    @Test
    fun `GET product detail forbidden for BRANCH MANAGE_PRODUCTS holder`() {
        assertEquals(403, testServer.client.get("/api/products/$productId", asUser(branchProductsUser)).code)
    }

    @Test
    fun `POST products forbidden for BRANCH MANAGE_PRODUCTS holder`() {
        val body =
            mapOf(
                "id" to TestFixtures.uuid().toString(),
                "name" to "Branch Product",
                "productCategoryId" to categoryId.toString(),
                "unitPrice" to "50.00",
                "commissionAmount" to "5.00",
            )
        assertEquals(403, testServer.client.post("/api/products", body, asUser(branchProductsUser)).code)
    }

    @Test
    fun `PATCH product forbidden for BRANCH MANAGE_PRODUCTS holder`() {
        val body = mapOf("name" to "Branch Rename")
        val code = testServer.client.patch("/api/products/$productId", body, asUser(branchProductsUser)).code
        assertEquals(403, code)
    }

    @Test
    fun `GET product-categories forbidden for BRANCH MANAGE_PRODUCTS holder`() {
        assertEquals(403, testServer.client.get("/api/product-categories", asUser(branchProductsUser)).code)
    }

    @Test
    fun `GET product-category detail forbidden for BRANCH MANAGE_PRODUCTS holder`() {
        assertEquals(403, testServer.client.get("/api/product-categories/$categoryId", asUser(branchProductsUser)).code)
    }

    @Test
    fun `POST product-categories forbidden for BRANCH MANAGE_PRODUCTS holder`() {
        val body = mapOf("id" to TestFixtures.uuid().toString(), "name" to "Branch Category")
        assertEquals(403, testServer.client.post("/api/product-categories", body, asUser(branchProductsUser)).code)
    }
}
