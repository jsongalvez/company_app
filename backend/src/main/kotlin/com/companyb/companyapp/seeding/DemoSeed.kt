package com.companyb.companyapp.seeding

import com.companyb.companyapp.auth.Password
import com.companyb.companyapp.config.AppConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.CapabilityRepository
import com.companyb.companyapp.repository.UserCreateParams
import com.companyb.companyapp.repository.UserRepository
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RoleTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.repository.model.UserRoleTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

private const val DEMO_PASSWORD = "demo-password-1"
private const val EMAIL_DOMAIN = "@example.com"
private const val RATE_HORIZON_YEARS = 10L
private const val CLINIC_SLOT: Short = 1
private const val SECOND_SLOT: Short = 2
internal const val CONSULTATION_LABEL = "General Consultation"
internal const val EXTRACTION_LABEL = "Tooth Extraction"

internal const val OWNER_USERNAME = "demo-owner"
internal const val COORDINATOR_USERNAME = "demo-coordinator"
internal const val PRACTITIONER_USERNAME = "demo-practitioner"
internal const val PRACTITIONER_B_USERNAME = "demo-practitioner-b"
internal const val ACCOUNTANT_USERNAME = "demo-accountant"
internal const val ONBOARDING_USERNAME = "demo-onboarding"

// Fixed namespaced UUIDs (00000000-0000-4000-8000-<n>) keep repeated boots idempotent.
internal val CLINIC_ID = UUID.fromString("00000000-0000-4000-8000-000000000301")
internal val TOUR_ID = UUID.fromString("00000000-0000-4000-8000-000000000302")
internal val MEDICINES_CATEGORY_ID = UUID.fromString("00000000-0000-4000-8000-000000000601")

private const val REGULAR_RATE = "2500.00"
private const val PROVINCIAL_FIRST_RATE = "3500.00"

private val BASE_RATES =
    listOf(
        SessionType.REGULAR to REGULAR_RATE,
        SessionType.PROVINCIAL_FIRST to PROVINCIAL_FIRST_RATE,
        SessionType.SECOND_SESSION to "2000.00",
        SessionType.SUBSEQUENT to "1500.00",
    )

/** Resolves a staff key used by [HISTORY_SESSIONS] to a concrete demo principal id. */
private typealias StaffKey = String

private const val STAFF_OWNER = "owner"
private const val STAFF_COORDINATOR = "coordinator"
private const val STAFF_PRACTITIONER = "practitioner"
private const val STAFF_PRACTITIONER_B = "practitioner-b"

private data class HistorySessionSpec(
    val fixedId: String,
    val branchId: UUID,
    val daysAgo: Long,
    val clientRef: String,
    val sessionType: SessionType,
    val price: String,
    val status: SessionStatus,
    val staffKey: StaffKey?,
    val slot: Short?,
    val concernLabel: String?,
)

/**
 * Fixed history: completed walk-ins with practitioners + concerns, plus non-walk-in
 * NO_SHOW/CANCELLED rows (the schema's walk_in_status constraint forbids walk-ins ending
 * in those statuses).
 */
private val HISTORY_SESSIONS =
    listOf(
        HistorySessionSpec(
            fixedId = "00000000-0000-4000-8000-000000000501",
            branchId = CLINIC_ID,
            daysAgo = 2,
            clientRef = "A",
            sessionType = SessionType.REGULAR,
            price = REGULAR_RATE,
            status = SessionStatus.COMPLETED,
            staffKey = STAFF_PRACTITIONER,
            slot = CLINIC_SLOT,
            concernLabel = CONSULTATION_LABEL,
        ),
        HistorySessionSpec(
            fixedId = "00000000-0000-4000-8000-000000000502",
            branchId = CLINIC_ID,
            daysAgo = 2,
            clientRef = "B",
            sessionType = SessionType.PROVINCIAL_FIRST,
            price = PROVINCIAL_FIRST_RATE,
            status = SessionStatus.COMPLETED,
            staffKey = STAFF_COORDINATOR,
            slot = SECOND_SLOT,
            concernLabel = EXTRACTION_LABEL,
        ),
        HistorySessionSpec(
            fixedId = "00000000-0000-4000-8000-000000000503",
            branchId = CLINIC_ID,
            daysAgo = 1,
            clientRef = "C",
            sessionType = SessionType.REGULAR,
            price = REGULAR_RATE,
            status = SessionStatus.COMPLETED,
            staffKey = STAFF_PRACTITIONER,
            slot = CLINIC_SLOT,
            concernLabel = CONSULTATION_LABEL,
        ),
        HistorySessionSpec(
            fixedId = "00000000-0000-4000-8000-000000000504",
            branchId = CLINIC_ID,
            daysAgo = 1,
            clientRef = "D",
            sessionType = SessionType.SUBSEQUENT,
            price = PROVINCIAL_FIRST_RATE,
            status = SessionStatus.NO_SHOW,
            staffKey = null,
            slot = null,
            concernLabel = null,
        ),
        HistorySessionSpec(
            fixedId = "00000000-0000-4000-8000-000000000505",
            branchId = CLINIC_ID,
            daysAgo = 1,
            clientRef = "E",
            sessionType = SessionType.SUBSEQUENT,
            price = REGULAR_RATE,
            status = SessionStatus.CANCELLED,
            staffKey = null,
            slot = null,
            concernLabel = null,
        ),
        HistorySessionSpec(
            fixedId = "00000000-0000-4000-8000-000000000507",
            branchId = TOUR_ID,
            daysAgo = 1,
            clientRef = "T",
            sessionType = SessionType.REGULAR,
            price = REGULAR_RATE,
            status = SessionStatus.COMPLETED,
            staffKey = STAFF_PRACTITIONER_B,
            slot = CLINIC_SLOT,
            concernLabel = CONSULTATION_LABEL,
        ),
    )

/** Resolves the owning day row for (branch, date), creating an OPEN row when missing. */
internal fun ensureDemoDay(
    branchId: UUID,
    date: LocalDate,
): UUID {
    BranchDayTable
        .selectAll()
        .where { (BranchDayTable.branchId eq branchId) and (BranchDayTable.date eq date) }
        .singleOrNull()
        ?.let { return it[BranchDayTable.id] }
    val id = UUID.randomUUID()
    BranchDayTable.insert {
        it[BranchDayTable.id] = id
        it[BranchDayTable.branchId] = branchId
        it[BranchDayTable.date] = date
    }
    return id
}

internal data class ClientSpec(
    val fixedId: String,
    val ref: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val age: Int,
    val systolic: Short?,
    val diastolic: Short?,
)

internal data class ProductSpec(
    val fixedId: String,
    val name: String,
    val unitPrice: String,
    val commission: String,
    val reorderPoint: Int?,
    val clinicStock: Int,
    val tourStock: Int,
)

private data class DemoIds(
    val owner: UUID,
    val coordinator: UUID,
    val practitioner: UUID,
    val practitionerB: UUID,
) {
    operator fun get(key: StaffKey): UUID =
        when (key) {
            STAFF_OWNER -> owner
            STAFF_COORDINATOR -> coordinator
            STAFF_PRACTITIONER -> practitioner
            else -> practitionerB
        }
}

internal val DEMO_CLIENTS =
    listOf(
        ClientSpec(
            fixedId = "00000000-0000-4000-8000-000000000401",
            ref = "A",
            firstName = "Maria",
            lastName = "Santos",
            gender = "F",
            age = 34,
            systolic = 120,
            diastolic = 80,
        ),
        ClientSpec(
            fixedId = "00000000-0000-4000-8000-000000000402",
            ref = "B",
            firstName = "Jose",
            lastName = "Reyes",
            gender = "M",
            age = 45,
            systolic = 130,
            diastolic = 85,
        ),
        ClientSpec(
            fixedId = "00000000-0000-4000-8000-000000000403",
            ref = "C",
            firstName = "Ana",
            lastName = "Dela Cruz",
            gender = "F",
            age = 28,
            systolic = null,
            diastolic = null,
        ),
        ClientSpec(
            fixedId = "00000000-0000-4000-8000-000000000404",
            ref = "D",
            firstName = "Pedro",
            lastName = "Bautista",
            gender = "M",
            age = 52,
            systolic = 140,
            diastolic = 90,
        ),
        ClientSpec(
            fixedId = "00000000-0000-4000-8000-000000000405",
            ref = "E",
            firstName = "Lisa",
            lastName = "Wang",
            gender = "F",
            age = 39,
            systolic = null,
            diastolic = null,
        ),
        ClientSpec(
            fixedId = "00000000-0000-4000-8000-000000000406",
            ref = "T",
            firstName = "Carlo",
            lastName = "Mendoza",
            gender = "M",
            age = 41,
            systolic = 125,
            diastolic = 82,
        ),
    )

internal val DEMO_PRODUCTS =
    listOf(
        ProductSpec(
            fixedId = "00000000-0000-4000-8000-000000000611",
            name = "Paracetamol 500mg",
            unitPrice = "25.00",
            commission = "5.00",
            reorderPoint = 20,
            clinicStock = 8,
            tourStock = 15,
        ),
        ProductSpec(
            fixedId = "00000000-0000-4000-8000-000000000612",
            name = "Amoxicillin 500mg",
            unitPrice = "45.00",
            commission = "9.00",
            reorderPoint = 15,
            clinicStock = 40,
            tourStock = 10,
        ),
        ProductSpec(
            fixedId = "00000000-0000-4000-8000-000000000613",
            name = "Vitamin C 1000mg",
            unitPrice = "80.00",
            commission = "16.00",
            reorderPoint = null,
            clinicStock = 25,
            tourStock = 12,
        ),
    )

private val OWNER_GLOBAL_CAPABILITIES =
    listOf(
        "VIEW_BRANCH_DATA",
        "EDIT_BRANCH_DATA",
        "EDIT_PAST_DAY",
        "VOID_SESSION",
        "SUBMIT_REMITTANCE",
        "ASSIGN_COMPENSATION",
        "MANAGE_PRODUCTS",
        "MANAGE_USERS",
        "ASSIGN_DELEGATE",
    )

/**
 * #415 — removable prototype demo dataset. Enabled only by `DEMO_SEED=true`; every step is
 * existence-checked so repeated boots are no-ops. Remove the feature by deleting this file,
 * its boot hook in Main.kt, and the `.env.example` block.
 */
object DemoSeed {
    fun seed(
        config: AppConfig,
        runInTransaction: (() -> Unit) -> Unit = { block -> transaction { block() } },
    ) {
        if (!config.demoSeed) return
        runInTransaction { seedAll() }
        logger.info {
            "[DEMO-SEED] Demo dataset ready. Logins (password '$DEMO_PASSWORD'): $OWNER_USERNAME " +
                "(global owner), $COORDINATOR_USERNAME (coordinator @ Demo Clinic), " +
                "$PRACTITIONER_USERNAME (practitioner @ Demo Clinic), $PRACTITIONER_B_USERNAME " +
                "(practitioner @ Demo Provincial Tour), $ACCOUNTANT_USERNAME (accountant, read-only), " +
                "$ONBOARDING_USERNAME (onboarding, no capabilities)"
        }
    }

    private fun seedAll() {
        fun ensureBranches() {
            for (
            branch in
            listOf(
                Triple(CLINIC_ID, "Demo Clinic", BranchType.CLINIC),
                Triple(TOUR_ID, "Demo Provincial Tour", BranchType.PROVINCIAL_TOUR),
            )
            ) {
                val (id, name, type) = branch
                val exists =
                    BranchTable
                        .selectAll()
                        .where { BranchTable.id eq id }
                        .empty()
                        .not()
                if (!exists) {
                    BranchTable.insert {
                        it[BranchTable.id] = id
                        it[BranchTable.name] = name
                        it[branchType] = type
                    }
                }
            }
        }

        ensureBranches()
        val ids = ensureUsers()

        fun baseRates(ownerId: UUID) {
            val effectiveFrom: OffsetDateTime =
                RoleTable.select(CurrentTimestampWithTimeZone).first()[CurrentTimestampWithTimeZone]
            val effectiveUntil = effectiveFrom.plusYears(RATE_HORIZON_YEARS)
            for (branchId in listOf(CLINIC_ID, TOUR_ID)) {
                for ((sessionType, rate) in BASE_RATES) {
                    val exists =
                        SessionBaseRateTable
                            .selectAll()
                            .where {
                                (SessionBaseRateTable.branchId eq branchId) and
                                    (SessionBaseRateTable.sessionType eq sessionType)
                            }.empty()
                            .not()
                    if (exists) continue
                    SessionBaseRateTable.insert {
                        it[SessionBaseRateTable.setBy] = ownerId
                        it[SessionBaseRateTable.branchId] = branchId
                        it[SessionBaseRateTable.sessionType] = sessionType
                        it[this.rate] = BigDecimal(rate)
                        it[this.effectiveFrom] = effectiveFrom
                        it[this.effectiveUntil] = effectiveUntil
                    }
                }
            }
        }

        baseRates(ids.owner)
        val clientIds = ensureClients()
        seedSessionsAndDays(ids, clientIds)
        seedInventory(ids.owner)
        seedTodayAttendance(ids)
    }

    private fun ensureUsers(): DemoIds {
        fun roleId(roleName: String): UUID =
            RoleTable.selectAll().where { RoleTable.name eq roleName }.single()[RoleTable.id]

        fun assignRole(
            userId: UUID,
            roleName: String,
        ) {
            val exists =
                UserRoleTable
                    .selectAll()
                    .where {
                        (UserRoleTable.userId eq userId) and (UserRoleTable.roleId eq roleId(roleName))
                    }.empty()
                    .not()
            if (!exists) {
                UserRoleTable.insert {
                    it[UserRoleTable.userId] = userId
                    it[UserRoleTable.roleId] = roleId(roleName)
                }
            }
        }

        val owner =
            ensureUser(OWNER_USERNAME, "Demo Owner") { userId ->
                assignRole(userId, "OWNER")
                grantGlobalCaps(userId)
            }
        val coordinator =
            ensureUser(COORDINATOR_USERNAME, "Demo Coordinator") { userId ->
                assignRole(userId, "COORDINATOR")
                ensureAssignment(userId, CLINIC_ID, CLINIC_SLOT)
            }
        val practitioner =
            ensureUser(PRACTITIONER_USERNAME, "Dana Practitioner") { userId ->
                assignRole(userId, "PRACTITIONER")
                ensureAssignment(userId, CLINIC_ID, SECOND_SLOT)
            }
        val practitionerB =
            ensureUser(PRACTITIONER_B_USERNAME, "Paolo Tour Practitioner") { userId ->
                assignRole(userId, "PRACTITIONER")
                ensureAssignment(userId, TOUR_ID, CLINIC_SLOT)
            }
        ensureUser(ACCOUNTANT_USERNAME, "Demo Accountant") { assignRole(it, "ACCOUNTANT") }
        ensureUser(ONBOARDING_USERNAME, "New Hire (Onboarding)") { assignRole(it, "ONBOARDING") }
        return DemoIds(owner, coordinator, practitioner, practitionerB)
    }

    private fun ensureUser(
        username: String,
        displayName: String,
        provision: (UUID) -> Unit,
    ): UUID {
        UserRepository.findByUsername(username)?.let { existing -> return UUID.fromString(existing.id) }
        val userId =
            UserRepository.createUserInTransaction(
                UserCreateParams(
                    username = username,
                    passwordHash = Password.create(DEMO_PASSWORD),
                    email = "$username$EMAIL_DOMAIN",
                    displayName = displayName,
                ),
            )
        provision(userId)
        return userId
    }

    private fun grantGlobalCaps(userId: UUID) {
        for (code in OWNER_GLOBAL_CAPABILITIES) {
            val capabilityId =
                CapabilityRepository.findIdByCode(code)
                    ?: error("Capability '$code' not found in database")
            UserCapabilityTable.insert {
                it[UserCapabilityTable.userId] = userId
                it[UserCapabilityTable.capabilityId] = capabilityId
                it[UserCapabilityTable.contextType] = CapabilityContextType.GLOBAL
                it[UserCapabilityTable.contextId] = CapabilityService.GLOBAL_CONTEXT_ID
                it[UserCapabilityTable.sourceType] = CapabilitySourceType.SYSTEM
                it[UserCapabilityTable.sourceId] = CapabilityService.GLOBAL_CONTEXT_ID
            }
        }
    }

    private fun ensureAssignment(
        userId: UUID,
        branchId: UUID,
        slot: Short,
    ) {
        val exists =
            UserBranchAssignmentTable
                .selectAll()
                .where {
                    (UserBranchAssignmentTable.userId eq userId) and
                        (UserBranchAssignmentTable.branchId eq branchId)
                }.empty()
                .not()
        if (exists) return
        UserBranchAssignmentTable.insert {
            it[id] = UUID.randomUUID()
            it[UserBranchAssignmentTable.userId] = userId
            it[UserBranchAssignmentTable.branchId] = branchId
            it[UserBranchAssignmentTable.slot] = slot
            it[assignedBy] = userId
        }
    }

    private fun ensureClients(): Map<String, UUID> {
        val byRef = mutableMapOf<String, UUID>()
        for (spec in DEMO_CLIENTS) {
            val id = UUID.fromString(spec.fixedId)
            val exists =
                ClientTable
                    .selectAll()
                    .where { ClientTable.id eq id }
                    .empty()
                    .not()
            if (!exists) {
                ClientTable.insert {
                    it[ClientTable.id] = id
                    it[firstName] = spec.firstName
                    it[lastName] = spec.lastName
                    it[ClientTable.gender] = spec.gender
                    it[age] = spec.age
                    it[systolicBp] = spec.systolic
                    it[diastolicBp] = spec.diastolic
                }
            }
            byRef[spec.ref] = id
        }
        return byRef
    }

    private fun seedSessionsAndDays(
        ids: DemoIds,
        clientIds: Map<String, UUID>,
    ) {
        val today = BranchDayService.currentOperationalDate()
        val bookedNow =
            RoleTable.select(CurrentTimestampWithTimeZone).first()[CurrentTimestampWithTimeZone]

        fun day(daysAgo: Long): LocalDate = today.minusDays(daysAgo)

        fun concern(label: String): UUID {
            ConcernTable
                .selectAll()
                .where { ConcernTable.label eq label }
                .singleOrNull()
                ?.let { return it[ConcernTable.id] }
            val id = UUID.randomUUID()
            ConcernTable.insert {
                it[ConcernTable.id] = id
                it[ConcernTable.label] = label
                it[createdBy] = ids.owner
            }
            return id
        }

        fun insertCompleted(spec: HistorySessionSpec) {
            if (SessionTable
                    .selectAll()
                    .where { SessionTable.id eq UUID.fromString(spec.fixedId) }
                    .empty()
                    .not()
            ) {
                return
            }
            val sessionId = UUID.fromString(spec.fixedId)
            val dayId = ensureDemoDay(spec.branchId, day(spec.daysAgo))
            SessionTable.insert {
                it[SessionTable.id] = sessionId
                it[clientId] = clientIds.getValue(spec.clientRef)
                it[branchDayId] = dayId
                it[requestedPractitionerId] = spec.staffKey?.let { ids[it] }
                it[sessionType] = spec.sessionType
                it[isWalkIn] = spec.status == SessionStatus.COMPLETED
                it[sessionStatus] = spec.status
                it[basePrice] = BigDecimal(spec.price)
                it[finalPrice] = BigDecimal(spec.price)
                it[bookedAt] = if (spec.status == SessionStatus.COMPLETED) null else bookedNow
            }
            if (spec.staffKey != null && spec.slot != null) {
                SessionPractitionerTable.insert {
                    it[id] = UUID.randomUUID()
                    it[this.sessionId] = sessionId
                    it[practitionerId] = ids[spec.staffKey]
                    it[slotAtTime] = spec.slot
                }
            }
            if (spec.concernLabel != null) {
                SessionConcernTable.insert {
                    it[this.sessionId] = sessionId
                    it[concernId] = concern(spec.concernLabel)
                }
            }
        }

        /** Creates at most one live PENDING walk-in per client (single-PENDING invariant). */
        fun insertPendingWalkIn(
            branchId: UUID,
            clientRef: String,
            requestedPractitioner: UUID,
        ) {
            val clientId = clientIds.getValue(clientRef)
            val hasPending =
                SessionTable
                    .selectAll()
                    .where {
                        (SessionTable.clientId eq clientId) and
                            (SessionTable.sessionStatus eq SessionStatus.PENDING)
                    }.empty()
                    .not()
            if (hasPending) return
            val sessionId = UUID.randomUUID()
            SessionTable.insert {
                it[SessionTable.id] = sessionId
                it[SessionTable.clientId] = clientId
                it[SessionTable.branchDayId] = ensureDemoDay(branchId, today)
                it[requestedPractitionerId] = requestedPractitioner
                it[sessionType] = SessionType.REGULAR
                it[isWalkIn] = true
                it[sessionStatus] = SessionStatus.PENDING
                it[basePrice] = BigDecimal(REGULAR_RATE)
                it[finalPrice] = BigDecimal(REGULAR_RATE)
            }
            SessionConcernTable.insert {
                it[this.sessionId] = sessionId
                it[concernId] = concern(CONSULTATION_LABEL)
            }
        }

        HISTORY_SESSIONS.forEach(::insertCompleted)
        insertPendingWalkIn(CLINIC_ID, "A", ids.practitioner)
        insertPendingWalkIn(TOUR_ID, "T", ids.practitionerB)
    }

    private fun seedInventory(ownerId: UUID) {
        val clinicToday = ensureDemoDay(CLINIC_ID, BranchDayService.currentOperationalDate())

        fun cardProduct(
            productId: UUID,
            branchId: UUID,
            stock: Int,
            movementDayId: UUID?,
        ) {
            val exists =
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.empty()
                    .not()
            if (exists) return
            BranchInventoryTable.insert {
                it[BranchInventoryTable.id] = UUID.randomUUID()
                it[BranchInventoryTable.branchId] = branchId
                it[BranchInventoryTable.productId] = productId
                it[currentStock] = stock
            }
            // One restock movement so the History dialog is not empty; only where a day row exists.
            if (movementDayId != null) {
                InventoryMovementTable.insert {
                    it[InventoryMovementTable.productId] = productId
                    it[productSaleId] = null
                    it[InventoryMovementTable.branchId] = branchId
                    it[InventoryMovementTable.branchDayId] = movementDayId
                    it[reason] = InventoryMovementReason.RESTOCK
                    it[quantityChange] = stock
                    it[movedBy] = ownerId
                    it[notes] = "Initial demo stock"
                }
            }
        }

        if (ProductCategoryTable.selectAll().where { ProductCategoryTable.id eq MEDICINES_CATEGORY_ID }.empty()) {
            ProductCategoryTable.insert {
                it[id] = MEDICINES_CATEGORY_ID
                it[name] = "Medicines"
            }
        }
        for (spec in DEMO_PRODUCTS) {
            val productId = UUID.fromString(spec.fixedId)
            if (ProductTable.selectAll().where { ProductTable.id eq productId }.empty()) {
                ProductTable.insert {
                    it[ProductTable.id] = productId
                    it[name] = spec.name
                    it[productCategoryId] = MEDICINES_CATEGORY_ID
                    it[unitPrice] = BigDecimal(spec.unitPrice)
                    it[commissionAmount] = BigDecimal(spec.commission)
                    it[reorderPoint] = spec.reorderPoint
                }
            }
            cardProduct(productId, CLINIC_ID, spec.clinicStock, clinicToday)
            cardProduct(productId, TOUR_ID, spec.tourStock, null)
        }
    }

    private fun seedTodayAttendance(ids: DemoIds) {
        val today = BranchDayService.currentOperationalDate()

        fun clockedIn(
            userId: UUID,
            branchId: UUID,
        ) {
            val branchDayId = ensureDemoDay(branchId, today)
            val assignmentExists =
                BranchDayAssignmentTable
                    .selectAll()
                    .where {
                        (BranchDayAssignmentTable.branchDayId eq branchDayId) and
                            (BranchDayAssignmentTable.userId eq userId)
                    }.empty()
                    .not()
            if (!assignmentExists) {
                BranchDayAssignmentTable.insert {
                    it[id] = UUID.randomUUID()
                    it[BranchDayAssignmentTable.branchDayId] = branchDayId
                    it[BranchDayAssignmentTable.userId] = userId
                    it[isRelief] = false
                }
            }
            val activeClockIn =
                AttendanceTable
                    .selectAll()
                    .where {
                        (AttendanceTable.userId eq userId) and
                            (AttendanceTable.branchDayId eq branchDayId) and
                            AttendanceTable.clockOut.isNull()
                    }.empty()
                    .not()
            if (activeClockIn) return
            AttendanceTable.insert {
                it[AttendanceTable.id] = UUID.randomUUID()
                it[AttendanceTable.branchDayId] = branchDayId
                it[AttendanceTable.userId] = userId
                it[markedBy] = userId
            }
        }

        clockedIn(ids.owner, CLINIC_ID)
        clockedIn(ids.coordinator, CLINIC_ID)
        clockedIn(ids.practitioner, CLINIC_ID)
        clockedIn(ids.practitionerB, TOUR_ID)
    }
}
