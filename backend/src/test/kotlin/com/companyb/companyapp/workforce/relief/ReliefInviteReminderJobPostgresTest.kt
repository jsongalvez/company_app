package com.companyb.companyapp.workforce.relief

import com.companyb.companyapp.notification.NotificationRepository
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceService
import com.companyb.companyapp.workforce.UserBranchAssignmentCreateParams
import com.companyb.companyapp.workforce.UserBranchAssignmentRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #359 — accepted-invite reminder cadence over a simulated Manila calendar: exactly three
 * reminders (T-3, T-1, day-of), invitee-only audience, marker-based idempotency, and the
 * current-state suppressions (non-ACCEPTED statuses never match; day-of clock-in silences).
 */
class ReliefInviteReminderJobPostgresTest : BasePostgresTest() {
    private val inviterId = TestFixtures.uuid()
    private val inviteeId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    override fun initTestData() {
        IdentityFixtures.insertTestUser(inviterId, "reminder-inviter")
        IdentityFixtures.insertTestUser(inviteeId, "reminder-invitee")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Reminder Branch ${branchId.toString().take(8)}")
        // Invite creation resolves-or-creates its duty-day rows at this branch.
        transaction {
            UserBranchAssignmentRepository.createInTransaction(
                UserBranchAssignmentCreateParams(
                    id = TestFixtures.uuid(),
                    userId = inviterId,
                    branchId = branchId,
                    slot = 1,
                    assignedBy = inviterId,
                ),
            )
        }
        // Accept writes the invitee's day-scoped grant as a user_capability row.
        // Branch-keyed notification tracking covers every recipient (the #358 lesson).
        // Clock-in upserts a branch_day_assignment row — untracked it blocks user teardown.
    }

    @Test
    fun `accepted invite produces exactly the three-slot cadence and replays stay silent`() {
        val duty = TestFixtures.today.plusDays(3)
        acceptInviteFor(duty)

        assertEquals(1, ReliefInviteReminderJob.run(clockAt(TestFixtures.today)), "T-3 sweep")
        assertEquals(0, ReliefInviteReminderJob.run(clockAt(TestFixtures.today)), "T-3 replay")
        assertEquals(1, ReliefInviteReminderJob.run(clockAt(duty.minusDays(1))), "T-1 sweep")
        assertEquals(1, ReliefInviteReminderJob.run(clockAt(duty)), "day-of sweep")

        val events = reminderRows().map { it.first }
        assertEquals(3, events.size, "exactly one reminder per slot")
        assertEquals(
            setOf(
                ReliefNotifications.REMINDER_3_DAYS,
                ReliefNotifications.REMINDER_1_DAY,
                ReliefNotifications.REMINDER_DAY_OF,
            ),
            events.toSet(),
            "one marker row per slot, invitee-only audience",
        )

        val messages = NotificationRepository.findHistoryByUserId(inviteeId).map { it.message }
        assertTrue(messages.any { it.contains("on $duty") }, "future reminders name the date: $messages")
        assertTrue(messages.any { it.contains("for today") }, "day-of reminder says today: $messages")
    }

    @Test
    fun `pending and declined invites never remind`() {
        val duty = TestFixtures.today.plusDays(3)
        val pending = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, duty)

        assertEquals(0, ReliefInviteReminderJob.run(clockAt(TestFixtures.today)), "PENDING stays quiet")

        ReliefInviteService.declineInvite(inviteeId, pending.id)
        assertEquals(0, ReliefInviteReminderJob.run(clockAt(duty.minusDays(1))), "DECLINED stays quiet")
        assertEquals(0, ReliefInviteReminderJob.run(clockAt(duty)), "day-of still quiet")
        assertTrue(reminderRows().isEmpty())
    }

    @Test
    fun `day-of clock-in silences the day-of reminder only`() {
        val duty = TestFixtures.today
        acceptInviteFor(duty)

        // T-3 / T-1 sweeps on the pre-days fire normally.
        assertEquals(1, ReliefInviteReminderJob.run(clockAt(duty.minusDays(3))), "T-3 fires")
        assertEquals(1, ReliefInviteReminderJob.run(clockAt(duty.minusDays(1))), "T-1 fires")

        AttendanceService.clockIn(TestFixtures.uuid(), branchId, inviteeId)
        assertEquals(0, ReliefInviteReminderJob.run(clockAt(duty)), "clocked-in invitee gets no day-of ping")

        val events = reminderRows().map { it.first }.sorted()
        assertEquals(
            listOf(ReliefNotifications.REMINDER_1_DAY, ReliefNotifications.REMINDER_3_DAYS),
            events,
        )
    }

    /** 07:00 Manila slot; before the hour lands today, at/after it rolls to tomorrow. */
    @Test
    fun `next run delay targets the 07o0 Manila sweep`() {
        val before = TestFixtures.today.atTime(6, 0).atZone(manilaZone)
        val after = TestFixtures.today.atTime(9, 30).atZone(manilaZone)
        val sweepToday = TestFixtures.today.atTime(7, 0).atZone(manilaZone)
        val sweepTomorrow =
            TestFixtures.today
                .plusDays(1)
                .atTime(7, 0)
                .atZone(manilaZone)

        assertEquals(
            Duration.between(before, sweepToday).toMillis(),
            ReliefInviteReminderJob.nextRunDelayMs(before),
        )
        assertEquals(
            Duration.between(after, sweepTomorrow).toMillis(),
            ReliefInviteReminderJob.nextRunDelayMs(after),
        )
    }

    private fun reminderRows(): List<Pair<String, LocalDate>> =
        NotificationRepository
            .findHistoryByUserId(inviteeId)
            .filter { it.eventType != null && it.eventType in REMINDER_EVENTS }
            .map { checkNotNull(it.eventType) to checkNotNull(it.targetDate) }

    private fun acceptInviteFor(date: LocalDate): UUID {
        val invite = ReliefInviteService.createInvite(inviterId, branchId, inviteeId, date)
        ReliefInviteService.acceptInvite(inviteeId, invite.id)
        return invite.id
    }

    private fun clockAt(date: LocalDate): Clock =
        Clock.fixed(date.atTime(LocalTime.of(7, 0)).atZone(manilaZone).toInstant(), manilaZone)

    private companion object {
        val REMINDER_EVENTS =
            setOf(
                ReliefNotifications.REMINDER_3_DAYS,
                ReliefNotifications.REMINDER_1_DAY,
                ReliefNotifications.REMINDER_DAY_OF,
            )
    }
}
