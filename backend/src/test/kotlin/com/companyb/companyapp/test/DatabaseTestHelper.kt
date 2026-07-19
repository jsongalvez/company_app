package com.companyb.companyapp.test

import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.GrantCapabilityParams
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserStatus
import com.companyb.companyapp.service.BranchDayService
import com.companyb.companyapp.service.CapabilityService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object DatabaseTestHelper {
    private const val TEST_CLIENT_AGE = 30
    private const val MAX_POOL_SIZE = 3
    private const val MIN_IDLE = 3
    private const val CONNECTION_TIMEOUT_MS = 30_000L
    private var databaseReady = false

    @Volatile
    var testDataSource: HikariDataSource? = null
        private set

    fun ensureDatabase() {
        if (!databaseReady) {
            val config = AppConfig.parse()
            val dbName = System.getenv("TEST_DB_NAME") ?: "${config.dbName}_test"
            val ds =
                HikariDataSource(
                    HikariConfig().apply {
                        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
                        addDataSourceProperty("user", config.dbUser)
                        addDataSourceProperty("password", config.dbPassword)
                        addDataSourceProperty("databaseName", dbName)
                        addDataSourceProperty("serverName", config.dbHost)
                        addDataSourceProperty("portNumber", config.dbPort)
                        maximumPoolSize = MAX_POOL_SIZE
                        minimumIdle = MIN_IDLE
                        connectionTimeout = CONNECTION_TIMEOUT_MS
                    },
                )
            Flyway
                .configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load()
                .apply {
                    repair()
                    migrate()
                }
            Database.connect(ds)
            testDataSource = ds
            databaseReady = true
        }
    }

    fun isDatabaseReady(): Boolean = databaseReady

    /**
     * Returns the test [HikariDataSource], throwing if [ensureDatabase] has not been called.
     * Prefer this over `testDataSource!!` to get a clear error message on misuse.
     */
    fun requireTestDataSource(): HikariDataSource =
        testDataSource
            ?: error("DatabaseTestHelper.ensureDatabase() has not been called — testDataSource is null")

    @Suppress("LongParameterList")
    fun insertUser(
        id: UUID,
        username: String,
        passwordHash: String,
        email: String,
        displayName: String,
        status: UserStatus = UserStatus.ACTIVE,
    ) {
        transaction {
            AppUserTable.insert {
                it[AppUserTable.id] = id
                it[AppUserTable.username] = username
                it[AppUserTable.passwordHash] = passwordHash
                it[AppUserTable.status] = status
                it[AppUserTable.email] = email
                it[AppUserTable.displayName] = displayName
            }
        }
    }

    fun grantManageUsers(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.MANAGE_USERS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantEditBranchData(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantManageProducts(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.MANAGE_PRODUCTS,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantVoidSession(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VOID_SESSION,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantAssignCompensation(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.ASSIGN_COMPENSATION,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantAssignDelegate(
        userId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.ASSIGN_DELEGATE,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    fun grantSubmitRemittance(
        userId: UUID,
        sourceId: UUID,
        branchId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.SUBMIT_REMITTANCE,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }

    @Suppress("LongParameterList")
    fun grantCapability(
        userId: UUID,
        capabilityCode: String,
        contextType: CapabilityContextType,
        contextId: UUID,
        sourceId: UUID,
        priority: Int = GrantPriorities.DIRECT_GRANT.toInt(),
    ) {
        CapabilityRepository.grantCapability(
            GrantCapabilityParams(
                userId = userId,
                capabilityCode = capabilityCode,
                contextType = contextType,
                contextId = contextId,
                sourceId = sourceId,
                sourceType = CapabilitySourceType.SYSTEM,
                priority = priority.toShort(),
            ),
        )
    }

    fun insertTestUser(
        id: UUID,
        prefix: String,
    ) {
        insertUser(
            id = id,
            username = "$prefix-${id.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "${id.toString().take(8)}@t.st",
            displayName = "Test $prefix",
        )
    }

    fun insertTestBranch(
        id: UUID,
        name: String = "Test Branch ${id.toString().take(8)}",
        branchType: BranchType = BranchType.CLINIC,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = branchType
            }
        }
    }

    fun createBranchDayForToday(branchId: UUID): UUID =
        transaction {
            val today = LocalDate.now(BranchDayService.manilaZone)
            BranchDayTable.insertIgnore {
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = today
            }
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date eq today)
                }.single()[BranchDayTable.id]
        }

    fun insertTestClient(id: UUID = UUID.randomUUID()): UUID {
        transaction {
            ClientTable.insertIgnore {
                it[ClientTable.id] = id
                it[ClientTable.firstName] = "Test"
                it[ClientTable.lastName] = "Client"
                it[ClientTable.gender] = Gender.M.name
                it[ClientTable.age] = TEST_CLIENT_AGE
            }
        }
        return id
    }

    fun insertTestCategory(
        id: UUID,
        name: String = "Test Category ${id.toString().take(8)}",
    ) {
        transaction {
            ProductCategoryTable.insertIgnore {
                it[ProductCategoryTable.id] = id
                it[ProductCategoryTable.name] = name
            }
        }
    }

    @Suppress("LongParameterList")
    fun insertTestProduct(
        id: UUID,
        name: String = "Test Product ${id.toString().take(8)}",
        categoryId: UUID,
        unitPrice: BigDecimal = BigDecimal("100.00"),
        commissionAmount: BigDecimal = BigDecimal("10.00"),
    ) {
        transaction {
            ProductTable.insertIgnore {
                it[ProductTable.id] = id
                it[ProductTable.name] = name
                it[ProductTable.productCategoryId] = categoryId
                it[ProductTable.unitPrice] = unitPrice
                it[ProductTable.commissionAmount] = commissionAmount
            }
        }
    }

    @Suppress("LongParameterList")
    fun insertTestProductSale(
        id: UUID,
        branchDayId: UUID,
        productId: UUID,
        handledBy: UUID,
        clientId: UUID? = null,
        quantity: Int = 1,
        unitPrice: BigDecimal = BigDecimal("100.00"),
        totalAmount: BigDecimal = BigDecimal("100.00"),
        commissionAmount: BigDecimal = BigDecimal("10.00"),
        productName: String = "Test Product",
        isWalkIn: Boolean = true,
    ) {
        transaction {
            ProductSaleTable.insertIgnore {
                it[ProductSaleTable.id] = id
                it[ProductSaleTable.branchDayId] = branchDayId
                it[ProductSaleTable.productId] = productId
                it[ProductSaleTable.quantity] = quantity
                it[ProductSaleTable.isWalkIn] = isWalkIn
                it[ProductSaleTable.handledBy] = handledBy
                it[ProductSaleTable.unitPriceAtTime] = unitPrice
                it[ProductSaleTable.totalAmountAtTime] = totalAmount
                it[ProductSaleTable.commissionAmountAtTime] = commissionAmount
                it[ProductSaleTable.productName] = productName
                if (clientId != null) it[ProductSaleTable.clientId] = clientId
            }
        }
    }

    @Suppress("LongParameterList")
    fun insertTestSession(
        id: UUID,
        clientId: UUID,
        branchDayId: UUID,
        sessionType: SessionType = SessionType.REGULAR,
        sessionStatus: SessionStatus = SessionStatus.PENDING,
        isWalkIn: Boolean = false,
        basePrice: BigDecimal = BigDecimal("2500.00"),
        finalPrice: BigDecimal = BigDecimal("2500.00"),
    ) {
        transaction {
            SessionTable.insertIgnore {
                it[SessionTable.id] = id
                it[SessionTable.clientId] = clientId
                it[SessionTable.branchDayId] = branchDayId
                it[SessionTable.sessionType] = sessionType
                it[SessionTable.sessionStatus] = sessionStatus
                it[SessionTable.isWalkIn] = isWalkIn
                it[SessionTable.basePrice] = basePrice
                it[SessionTable.finalPrice] = finalPrice
            }
        }
    }

    @Suppress("LongParameterList")
    fun insertTestCompensation(
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        assignedBy: UUID,
    ) {
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = UUID.randomUUID()
                it[CompensationTable.workBranchDayId] = branchDayId
                it[CompensationTable.payingBranchDayId] = branchDayId
                it[CompensationTable.userId] = userId
                it[CompensationTable.amount] = amount
                it[CompensationTable.assignedBy] = assignedBy
            }
        }
    }

    @Suppress("LongParameterList")
    fun insertTestExpense(
        branchDayId: UUID,
        userId: UUID,
        amount: BigDecimal,
        deleted: Boolean = false,
    ) {
        transaction {
            ExpenseTable.insert {
                it[ExpenseTable.id] = UUID.randomUUID()
                it[ExpenseTable.branchDayId] = branchDayId
                it[ExpenseTable.amount] = amount
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = userId
                it[ExpenseTable.notes] = "Test expense"
                if (deleted) {
                    it[ExpenseTable.deletedBy] = userId
                    it[ExpenseTable.deletedAt] = CurrentTimestampWithTimeZone
                }
            }
        }
    }

    fun revokeAllCapabilities(userId: UUID) {
        CapabilityRepository.revokeAllCapabilities(userId)
    }

    private val json = Json

    fun extractJsonField(
        jsonString: String,
        field: String,
    ): String {
        val jsonElement = json.parseToJsonElement(jsonString)
        return jsonElement.jsonObject[field]?.jsonPrimitive?.content ?: ""
    }

    private const val SNAPSHOT_TABLE = "remittance_financial_snapshot"
    private const val SNAPSHOT_TRIGGER = "trg_remittance_snapshot_immutable"

    /**
     * Executes [block] inside an Exposed transaction with the remittance_financial_snapshot
     * trigger disabled. The trigger is session-level, so DDL + DML share the same connection.
     *
     * The trigger is re-enabled in a finally block after [block] completes.
     * Raw DDL is unavoidable here — Exposed has no API for trigger management.
     */
    fun <T> withSnapshotTriggerDisabled(block: org.jetbrains.exposed.v1.jdbc.JdbcTransaction.() -> T): T =
        transaction {
            exec("ALTER TABLE $SNAPSHOT_TABLE DISABLE TRIGGER $SNAPSHOT_TRIGGER")
            try {
                block()
            } finally {
                exec("ALTER TABLE $SNAPSHOT_TABLE ENABLE TRIGGER $SNAPSHOT_TRIGGER")
            }
        }
}
