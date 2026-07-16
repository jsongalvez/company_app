package com.companyb.companyapp.test

import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CapabilitySourceType
import com.companyb.companyapp.repository.model.CapabilityTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.GrantPriorities
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserStatus
import com.companyb.companyapp.service.BranchDayService
import com.companyb.companyapp.service.CapabilityService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

object DatabaseTestHelper {
    private const val TEST_CLIENT_AGE = 30
    private var databaseReady = false

    fun ensureDatabase() {
        if (!databaseReady) {
            DatabaseConfig.runMigrations()
            DatabaseConfig.runExposed()
            databaseReady = true
        }
    }

    fun isDatabaseReady(): Boolean = databaseReady

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
        transaction {
            val capId =
                CapabilityTable
                    .selectAll()
                    .where { CapabilityTable.code eq capabilityCode }
                    .single()[CapabilityTable.id]

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
                it[ClientTable.gender] = "M"
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
}
