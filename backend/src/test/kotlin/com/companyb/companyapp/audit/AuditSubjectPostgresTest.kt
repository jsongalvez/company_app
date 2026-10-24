package com.companyb.companyapp.audit

import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Change-history entries say what they were about (user feedback bbeff139: "Can't really tell
 * what was changed... it only shows the value that was changed"). An UPDATE only stores the
 * changed field, so its subject comes from the record's own INSERT row plus live names.
 */
class AuditSubjectPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val workerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val dayId = TestFixtures.uuid()
    private val attendanceId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "subject-caller")
        IdentityFixtures.insertTestUser(workerId, "juan")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Silang")
        val day = dayId
        val branch = branchId
        transaction {
            BranchDayTable.insert {
                it[BranchDayTable.id] = day
                it[BranchDayTable.branchId] = branch
                it[BranchDayTable.date] = LocalDate.of(2026, 10, 19)
            }
        }
    }

    @Test
    fun `a clock-out change names the person and the day`() {
        val entries =
            transaction {
                AuditLogStore.recordInsert(
                    context = AuditContext(changedBy = workerId, branchId = branchId),
                    row = AuditedRow("attendance", attendanceId),
                    fields =
                        mapOf(
                            "userId" to workerId.toString(),
                            "branchDayId" to dayId.toString(),
                            "clockOut" to null,
                        ),
                )
                AuditLogStore.recordUpdate(
                    context = AuditContext(changedBy = callerId, branchId = branchId),
                    row = AuditedRow("attendance", attendanceId),
                    oldFields = mapOf("clockOut" to null),
                    newFields = mapOf("clockOut" to "2026-10-19T10:00:00Z"),
                )
                AuditLogStore.findByTableAndRecord("attendance", attendanceId, null, emptySet(), true)
            }
        val update = entries.single { it.action == AuditAction.UPDATE }
        val context = AuditSubjectRepository.describe(entries)

        assertEquals("Test juan · Silang, Oct 19", context.getValue(update.id).subject)
        assertEquals("Test juan", context.getValue(update.id).names[workerId.toString()])
    }
}
