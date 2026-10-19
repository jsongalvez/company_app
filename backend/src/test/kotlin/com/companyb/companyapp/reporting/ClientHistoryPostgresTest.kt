package com.companyb.companyapp.reporting

import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientHistoryPostgresTest : ExportServicePostgresBase() {
    @Test
    fun `shows the client's sessions and purchases, not anyone else's`() {
        val client = SessionClientFixtures.insertTestClient()
        val other = SessionClientFixtures.insertTestClient()
        val session = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(session, client, branchDayId, sessionStatus = SessionStatus.COMPLETED)
        SessionClientFixtures.insertTestSession(TestFixtures.uuid(), other, branchDayId)
        val me = callerId
        transaction {
            SessionPractitionerTable.insert {
                it[SessionPractitionerTable.sessionId] = session
                it[SessionPractitionerTable.practitionerId] = me
                it[SessionPractitionerTable.slotAtTime] = 1
            }
        }
        val category = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(category)
        val product = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(product, "Balm", category)
        CommerceFinanceFixtures.insertTestProductSale(
            TestFixtures.uuid(),
            branchDayId,
            product,
            callerId,
            clientId = client,
            quantity = 2,
        )
        CommerceFinanceFixtures.insertTestProductSale(
            TestFixtures.uuid(),
            branchDayId,
            product,
            callerId,
            clientId = other,
        )

        val history = ClientHistoryRepository.load(client)

        assertEquals(listOf(session.toString()), history.sessions.map { it.id })
        assertEquals(SessionStatus.COMPLETED, history.sessions.single().status)
        assertEquals("2500.00", history.sessions.single().price)
        assertEquals(
            1,
            history.sessions
                .single()
                .practitioners.size,
        )
        assertEquals(1, history.sales.size)
        assertEquals("Balm", history.sales.single().productName)
        assertEquals(2, history.sales.single().quantity)
        assertEquals("200.00", history.sales.single().total)
        assertTrue(
            history.sales
                .single()
                .branchName
                .startsWith("Export Test Branch"),
        )
    }

    @Test
    fun `a new client has no history`() {
        val history = ClientHistoryRepository.load(SessionClientFixtures.insertTestClient())
        assertEquals(0, history.sessions.size)
        assertEquals(0, history.sales.size)
    }
}
