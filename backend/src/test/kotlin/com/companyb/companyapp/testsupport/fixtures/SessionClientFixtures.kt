package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.session.SessionBaseRateService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Session and client fixtures for map #533 (#552).
 *
 * Owns session, client, notification and base-rate rows only.
 */
object SessionClientFixtures {
    private const val TEST_CLIENT_AGE = 30
    private const val DEFAULT_TEST_BASE_RATE = "2500.00"

    /**
     * Seeds one session base rate through the production command (#901).
     *
     * Delegates to [SessionBaseRateService.setRate] so fixtures inherit the branch
     * lock, previous-rate rotation, overlap-conflict translation, MEDICAL_MISSION
     * ₱0 normalization, audit writes, and the FAR_FUTURE window — direct
     * base-rate table inserts bypass all six.
     */
    fun insertTestBaseRate(
        id: UUID,
        branchId: UUID,
        setBy: UUID,
        sessionType: SessionType = SessionType.REGULAR,
        rate: BigDecimal = BigDecimal(DEFAULT_TEST_BASE_RATE),
    ) {
        SessionBaseRateService.setRate(setBy, id, branchId, sessionType, rate)
    }

    /**
     * Inserts a PENDING REGULAR session row directly against [branchDayId] (bypasses
     * [com.companyb.companyapp.session.SessionService.create]).
     */
    @Suppress("LongParameterList") // #552 fixture parity with the retired helper signature
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
            SessionTable.insert {
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

    fun insertTestClient(id: UUID = TestFixtures.uuid()): UUID {
        transaction {
            ClientTable.insert {
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
        id: UUID = TestFixtures.uuid(),
        sessionId: UUID,
        userId: UUID,
        branchId: UUID,
        dedupKey: String? = null,
    ): com.companyb.companyapp.notification.Notification {
        transaction {
            NotificationTable.insert {
                it[NotificationTable.id] = id
                it[NotificationTable.sessionId] = sessionId
                it[NotificationTable.userId] = userId
                it[NotificationTable.branchId] = branchId
                it[NotificationTable.message] = "Test notification"
                // #508 — unique per row by default so helper repeats never collide; pass an
                // explicit key to pin occurrence identity.
                it[NotificationTable.dedupKey] = dedupKey ?: "APPT:$sessionId:$id"
            }
        }
        return transaction {
            NotificationTable
                .selectAll()
                .where { NotificationTable.id eq id }
                .single()
                .let { row ->
                    com.companyb.companyapp.notification.Notification(
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
}
