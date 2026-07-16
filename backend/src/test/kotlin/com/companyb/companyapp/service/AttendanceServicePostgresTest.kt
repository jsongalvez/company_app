package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.GrantReliefAccessTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ConflictResponse
import io.javalin.http.NotFoundResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class AttendanceServicePostgresTest {
    private val userId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        insertUser(userId)
        insertBranch(branchId, "Test Branch")
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `clockIn creates attendance and branch day assignment and writes audit`() {
        val attendanceId = UUID.randomUUID()

        val (result, duration) =
            measureTimedValue {
                AttendanceService.clockIn(attendanceId, branchId, userId)
            }
        assertTrue(duration < 5.seconds, "clockIn regressed: took $duration")

        assertTrue(result.created)
        assertEquals(attendanceId, result.id)
        assertEquals(userId, result.userId)
        assertNotNull(result.clockIn)
        assertNull(result.clockOut)
        assertEquals(1L, auditEntryCount(attendanceId))
        assertNotNull(branchDayAssignmentExists(attendanceId))
    }

    @Test
    fun `clockIn with same id throws Conflict due to active clock-in guard`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        assertFailsWith<ConflictResponse> {
            AttendanceService.clockIn(attendanceId, branchId, userId)
        }
    }

    @Test
    fun `clockIn throws Conflict when user already has active clock-in`() {
        val firstId = UUID.randomUUID()
        AttendanceService.clockIn(firstId, branchId, userId)

        val secondId = UUID.randomUUID()
        assertFailsWith<ConflictResponse> {
            AttendanceService.clockIn(secondId, branchId, userId)
        }
    }

    @Test
    fun `clockIn sets isRelief true when no branch assignment exists`() {
        val attendanceId = UUID.randomUUID()

        val result = AttendanceService.clockIn(attendanceId, branchId, userId)

        assertTrue(result.isRelief)
    }

    @Test
    fun `clockIn sets isRelief false when branch assignment exists`() {
        grantManageUsers(userId)
        val assignmentId = UUID.randomUUID()
        UserBranchAssignmentService.create(userId, assignmentId, branchId, userId, 1)

        val attendanceId = UUID.randomUUID()
        val result = AttendanceService.clockIn(attendanceId, branchId, userId)

        assertTrue(result.isRelief.not())
    }

    @Test
    fun `clockOut sets clockOut and writes audit`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        val result = AttendanceService.clockOut(attendanceId, userId)

        assertTrue(result.created.not())
        assertNotNull(result.clockOut)
        assertEquals(2L, auditEntryCount(attendanceId))
        assertNotNull(auditNewClockOut(attendanceId))
    }

    @Test
    fun `clockOut on already clocked out record returns existing`() {
        val attendanceId = UUID.randomUUID()
        AttendanceService.clockIn(attendanceId, branchId, userId)
        val first = AttendanceService.clockOut(attendanceId, userId)

        val second = AttendanceService.clockOut(attendanceId, userId)

        assertTrue(first.created.not())
        assertTrue(second.created.not())
        assertEquals(first.clockOut, second.clockOut)
        assertEquals(2L, auditEntryCount(attendanceId))
    }

    @Test
    fun `clockOut on non-existent attendance throws NotFound`() {
        val unknownId = UUID.randomUUID()

        assertFailsWith<NotFoundResponse> {
            AttendanceService.clockOut(unknownId, userId)
        }
    }

    private fun insertUser(id: UUID) {
        DatabaseTestHelper.insertUser(
            id = id,
            username = "att-$id",
            passwordHash = "test-password-hash",
            email = "${id.toString().take(8)}@t.st",
            displayName = "Attendance User",
        )
    }

    private fun insertBranch(
        id: UUID,
        name: String,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = BranchType.CLINIC
            }
        }
    }

    private fun grantManageUsers(userId: UUID) {
        DatabaseTestHelper.grantManageUsers(userId, sourceId)
    }

    private fun branchDayAssignmentExists(attendanceId: UUID): UUID? =
        transaction {
            val branchDayId =
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq attendanceId }
                    .single()[AttendanceTable.branchDayId]

            BranchDayAssignmentTable
                .selectAll()
                .where {
                    (BranchDayAssignmentTable.branchDayId eq branchDayId) and
                        (BranchDayAssignmentTable.userId eq userId)
                }.singleOrNull()
                ?.let { it[BranchDayAssignmentTable.id] }
        }

    private fun auditEntryCount(attendanceId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "attendance") and
                        (AuditLogTable.recordId eq attendanceId)
                }.count()
        }

    private fun auditNewClockOut(attendanceId: UUID): String =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "attendance") and
                            (AuditLogTable.recordId eq attendanceId)
                    }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()
            extractJsonField(row[AuditLogTable.newValue] ?: "{}", "clockOut")
        }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere { AuditLogTable.changedBy eq userId }
            GrantReliefAccessTable.deleteWhere { GrantReliefAccessTable.requestedBy eq userId }
            AttendanceTable.deleteWhere { AttendanceTable.userId eq userId }
            BranchDayAssignmentTable.deleteWhere { BranchDayAssignmentTable.userId eq userId }
            UserBranchAssignmentTable.deleteWhere {
                (UserBranchAssignmentTable.userId eq userId) or
                    (UserBranchAssignmentTable.assignedBy eq userId)
            }
            BranchDayTable.deleteWhere { BranchDayTable.branchId eq branchId }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
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
