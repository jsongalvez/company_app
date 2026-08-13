package com.companyb.companyapp.test

import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserStatus
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.service.branchday.BranchDayService
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
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
        val capId =
            CapabilityRepository.findIdByCode(capabilityCode)
                ?: error("Capability code not found: $capabilityCode")
        transaction {
            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = userId
                it[UserCapabilityTable.capabilityId] = capId
                it[UserCapabilityTable.contextType] = contextType
                it[UserCapabilityTable.contextId] = contextId
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                it[UserCapabilityTable.sourceId] = sourceId
                it[UserCapabilityTable.priority] = priority.toShort()
            }
        }
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

    /**
     * Inserts a [user_branch_assignment] row directly (bypasses
     * [com.companyb.companyapp.repository.UserBranchAssignmentRepository]).
     * NOTE: inside `insert {}` the lambda receiver is the TABLE, so unqualified
     * names that collide with table columns resolve to COLUMNS, not to enclosing
     * scope — function parameters and locals win, but object properties lose.
     * Always pass local values or explicitly-qualified references (this is why
     * the repository uses `params.*`).
     */
    @Suppress("LongParameterList")
    fun insertTestAssignment(
        id: UUID = UUID.randomUUID(),
        userId: UUID,
        branchId: UUID,
        slot: Short,
        assignedBy: UUID,
        ended: Boolean = false,
    ): UUID {
        transaction {
            UserBranchAssignmentTable.insert {
                it[UserBranchAssignmentTable.id] = id
                it[UserBranchAssignmentTable.userId] = userId
                it[UserBranchAssignmentTable.branchId] = branchId
                it[UserBranchAssignmentTable.slot] = slot
                it[UserBranchAssignmentTable.assignedBy] = assignedBy
                if (ended) it[UserBranchAssignmentTable.endedAt] = CurrentTimestampWithTimeZone
            }
        }
        return id
    }

    fun createBranchDayForToday(branchId: UUID): UUID =
        createBranchDayForDate(branchId, LocalDate.now(BranchDayService.manilaZone))

    fun createBranchDayForDate(
        branchId: UUID,
        date: LocalDate,
    ): UUID =
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = date
            }
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date eq date)
                }.single()[BranchDayTable.id]
        }

    /** Creates a REMITTED branch day for [date] (e.g. a past covered day). */
    fun createRemittedBranchDay(
        branchId: UUID,
        date: LocalDate,
    ): UUID =
        createBranchDayForDate(branchId, date).also { id ->
            transaction {
                BranchDayTable.update({ BranchDayTable.id eq id }) {
                    it[BranchDayTable.status] = DayStatus.REMITTED
                }
            }
        }

    /** Grants EDIT_PAST_DAY at [branchId] — the capability that permits writes on PAST/REMITTED days. */
    fun grantEditPastDay(
        userId: UUID,
        branchId: UUID,
        sourceId: UUID,
    ) {
        grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.EDIT_PAST_DAY,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }

    /**
     * Inserts a PENDING REGULAR session row directly against [branchDayId] (bypasses
     * [SessionService.create]).
     */
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

    /**
     * Inserts a notification row directly (bypasses the scheduler — the only production
     * writer). Parameters named like the columns so callers can't fall into the Exposed v1
     * insert trap (the lambda receiver is the table, so an unqualified FIELD name resolves
     * to the column, not the test's field).
     */
    fun insertTestNotification(
        id: UUID = UUID.randomUUID(),
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
    ): com.companyb.companyapp.repository.model.Notification {
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = id
                it[NotificationTable.sessionId] = sessionId
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = branchId
                it[NotificationTable.message] = "Test notification"
            }
        }
        return transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.id eq id }
                .single()
                .let { row ->
                    com.companyb.companyapp.repository.model.Notification(
                        id = row[NotificationTable.id],
                        sessionId = row[NotificationTable.sessionId],
                        userId = row[NotificationTable.userId],
                        branchId = row[NotificationTable.branchId],
                        message = row[NotificationTable.message],
                        isRead = row[NotificationTable.isRead],
                        readAt = row[NotificationTable.readAt],
                        createdAt = row[NotificationTable.createdAt],
                    )
                }
        }
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
        reorderPoint: Int? = null,
    ) {
        transaction {
            ProductTable.insertIgnore {
                it[ProductTable.id] = id
                it[ProductTable.name] = name
                it[ProductTable.productCategoryId] = categoryId
                it[ProductTable.unitPrice] = unitPrice
                it[ProductTable.commissionAmount] = commissionAmount
                if (reorderPoint != null) it[ProductTable.reorderPoint] = reorderPoint
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

    fun insertTestAttendance(
        branchDayId: UUID,
        userId: UUID,
    ) {
        transaction {
            AttendanceTable.insertIgnore {
                it[AttendanceTable.id] = UUID.randomUUID()
                it[AttendanceTable.branchDayId] = branchDayId
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.markedBy] = userId
                it[AttendanceTable.clockIn] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }

    fun revokeAllCapabilities(userId: UUID) {
        transaction {
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
        }
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
