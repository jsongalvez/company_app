package com.companyb.companyapp.client
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val clientAId = TestFixtures.uuid()
    private val clientBId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()
    private val pendingSessionId = TestFixtures.uuid()
    private val completedSessionId = TestFixtures.uuid()
    private val noShowSessionId = TestFixtures.uuid()
    private val cancelledSessionId = TestFixtures.uuid()
    private val medicalMissionSessionId = TestFixtures.uuid()
    private val voidedSessionId = TestFixtures.uuid()
    private val sessionVoidId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "client-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Client Test Branch $branchId")
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
    }

    @Test
    fun `create persists all client fields and writes audit`() {
        val result = createClient(callerId, clientAId)

        assertTrue(result.created)
        assertEquals("John", result.client.firstName)
        assertEquals("Doe", result.client.lastName)
        assertEquals("Jane", persistedClient(clientAId).middleName)
        assertEquals(Gender.M, persistedClient(clientAId).gender)
        assertEquals(30, persistedClient(clientAId).age)
        assertEquals(120.toShort(), persistedClient(clientAId).systolicBp)
        assertEquals(80.toShort(), persistedClient(clientAId).diastolicBp)
        assertEquals(1L, auditEntryCount(clientAId))
    }

    @Test
    fun `duplicate client generated id returns existing client without extra audit`() {
        val first = createClient(callerId, clientAId, firstName = "Original")
        val duplicate = createClient(callerId, clientAId, firstName = "Changed", lastName = "Different")

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals("Original", duplicate.client.firstName)
        assertEquals("Doe", duplicate.client.lastName)
        assertEquals(1L, auditEntryCount(clientAId))
    }

    @Test
    fun `bp both or none accepts both null`() {
        val result = createClient(callerId, clientAId, systolicBp = null, diastolicBp = null)

        assertTrue(result.created)
        val persisted = persistedClient(clientAId)
        assertEquals(null, persisted.systolicBp)
        assertEquals(null, persisted.diastolicBp)
    }

    @Test
    fun `bp both or none accepts both set`() {
        val result = createClient(callerId, clientAId, systolicBp = 130.toShort(), diastolicBp = 85.toShort())

        assertTrue(result.created)
        val persisted = persistedClient(clientAId)
        assertEquals(130.toShort(), persisted.systolicBp)
        assertEquals(85.toShort(), persisted.diastolicBp)
    }

    @Test
    fun `find by id returns persisted client`() {
        createClient(callerId, clientAId)
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        val found = ClientService.findById(clientAId)

        assertEquals("John", found.firstName)
        assertEquals("Doe", found.lastName)
    }

    @Test
    fun `find by id throws not found for missing client`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        assertFailsWith<NotFoundException> {
            ClientService.findById(TestFixtures.uuid())
        }
    }

    @Test
    fun `findById without EDIT_BRANCH_DATA is allowed at service layer`() {
        createClient(callerId, clientAId)

        val found = ClientService.findById(clientAId)

        assertEquals("John", found.firstName)
        assertEquals("Doe", found.lastName)
    }

    @Test
    fun `search matches by first name`() {
        createClient(callerId, clientAId, firstName = "John")
        createClient(callerId, clientBId, firstName = "Alice", lastName = "Smith")
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        val results = ClientService.search("John")

        assertTrue(results.any { it.id == clientAId })
        assertFalse(results.any { it.id == clientBId })
    }

    @Test
    fun `search matches by last name`() {
        createClient(callerId, clientAId, firstName = "John", lastName = "Doe")
        createClient(callerId, clientBId, firstName = "Alice", lastName = "Smith")
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        val results = ClientService.search("Doe")

        assertTrue(results.any { it.id == clientAId })
        assertFalse(results.any { it.id == clientBId })
    }

    @Test
    fun `search matches by phone prefix`() {
        createClient(callerId, clientAId, phoneNumber = "1234567890")
        createClient(callerId, clientBId, phoneNumber = "9876543210")
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        val results = ClientService.search("1234")

        assertTrue(results.any { it.id == clientAId })
        assertFalse(results.any { it.id == clientBId })
    }

    @Test
    fun `search without EDIT_BRANCH_DATA is allowed at service layer`() {
        createClient(callerId, clientAId, firstName = "John")

        val results = ClientService.search("John")

        assertTrue(results.any { it.id == clientAId })
    }

    @Test
    fun `search is case-insensitive`() {
        createClient(callerId, clientAId, firstName = "John")
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        val results = ClientService.search("john")

        assertTrue(results.any { it.id == clientAId })
    }

    @Test
    fun `search supports typo tolerance with trigram similarity`() {
        createClient(callerId, clientAId, firstName = "John", lastName = "Doe")
        createClient(callerId, clientBId, firstName = "Alice", lastName = "Smith")
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        val results = ClientService.search("Jhn")

        assertTrue(results.any { it.id == clientAId }, "Typo 'Jhn' should match 'John' via trigram similarity")
        assertFalse(results.any { it.id == clientBId })
    }

    @Test
    fun `search supports typo tolerance for multi-character typos`() {
        createClient(callerId, clientAId, firstName = "Maria", lastName = "Garcia")
        createClient(callerId, clientBId, firstName = "Alice", lastName = "Smith")
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        val results = ClientService.search("Mria")

        assertTrue(results.any { it.id == clientAId }, "Typo 'Mria' should match 'Maria' via trigram similarity")
        assertFalse(results.any { it.id == clientBId })
    }

    @Test
    fun `search ranks exact matches above fuzzy matches`() {
        createClient(callerId, clientAId, firstName = "Jon", lastName = "Smith")
        createClient(callerId, clientBId, firstName = "John", lastName = "Bravo")
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        val results = ClientService.search("John")

        assertTrue(results.any { it.id == clientBId }, "Exact ILIKE match 'John' should match 'John Bravo'")
        assertTrue(results.any { it.id == clientAId }, "Fuzzy match 'Jon Smith' should match via trigram for 'John'")
        assertEquals(clientBId, results.first().id, "Exact match should rank first")
    }

    @Test
    fun `session counts exclude medical missions and active voids`() {
        createClient(callerId, clientAId)
        createClient(callerId, clientBId)
        seedSession(pendingSessionId, clientAId, SessionStatus.PENDING)
        seedSession(completedSessionId, clientAId, SessionStatus.COMPLETED)
        seedSession(noShowSessionId, clientAId, SessionStatus.NO_SHOW)
        seedSession(cancelledSessionId, clientAId, SessionStatus.CANCELLED)
        seedSession(medicalMissionSessionId, clientAId, SessionStatus.COMPLETED, SessionType.MEDICAL_MISSION)
        seedSession(voidedSessionId, clientAId, SessionStatus.COMPLETED)
        seedSessionVoid(voidedSessionId)

        val counts = ClientService.countSessions(listOf(clientAId, clientBId))

        assertEquals(expected = 4, actual = counts[clientAId])
        assertEquals(expected = 0, actual = counts[clientBId])
    }

    @Test
    fun `update client succeeds with EDIT_BRANCH_DATA capability`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId)

        val updated =
            ClientService.update(
                callerId = callerId,
                clientId = clientAId,
                firstName = "Jane",
                lastName = "Smith",
                middleName = null,
                suffix = null,
                phoneNumber = "1112223333",
                address = "456 Oak St",
                gender = null,
                age = null,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
            )

        assertEquals("Jane", updated.firstName)
        assertEquals("Smith", updated.lastName)
        assertEquals("1112223333", updated.phoneNumber)
        assertEquals("456 Oak St", updated.address)
        val persisted = persistedClient(clientAId)
        assertEquals("Jane", persisted.firstName)
        assertEquals("Smith", persisted.lastName)
        assertTrue(auditEntryCount(clientAId) >= 2L)
    }

    @Test
    fun `update client without EDIT_BRANCH_DATA is allowed at service layer`() {
        createClient(callerId, clientAId)

        val updated =
            ClientService.update(
                callerId = callerId,
                clientId = clientAId,
                firstName = "Jane",
                lastName = null,
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = null,
                age = null,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
            )

        assertEquals("Jane", updated.firstName)
    }

    @Test
    fun `update client returns 404 for non-existent client`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        assertFailsWith<NotFoundException> {
            ClientService.update(
                callerId = callerId,
                clientId = TestFixtures.uuid(),
                firstName = "Jane",
                lastName = null,
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = null,
                age = null,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
            )
        }
    }

    @Test
    fun `update client after anonymize returns 404 and keeps PII null`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId)
        ClientService.anonymize(callerId, clientAId)

        assertFailsWith<NotFoundException> {
            ClientService.update(
                callerId = callerId,
                clientId = clientAId,
                firstName = "Jane",
                lastName = "Smith",
                middleName = null,
                suffix = null,
                phoneNumber = "1112223333",
                address = "456 Oak St",
                gender = null,
                age = null,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
            )
        }

        // The anonymized row is untouched — the stale PATCH could not re-populate PII.
        val persisted = persistedClient(clientAId)
        assertNull(persisted.firstName)
        assertNull(persisted.lastName)
        assertNull(persisted.phoneNumber)
        assertNotNull(persisted.deletedAt)
        // And no audit row for the blocked write (create 1 + anonymize 1).
        assertEquals(expected = 2L, actual = auditEntryCount(clientAId))
    }

    @Test
    fun `update client writes audit log`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId)

        ClientService.update(
            callerId = callerId,
            clientId = clientAId,
            firstName = "UpdatedFirst",
            lastName = "UpdatedLast",
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = null,
            age = null,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )

        val auditCount = auditEntryCount(clientAId)
        assertTrue(auditCount >= 2L)
    }

    @Test
    fun `anonymize client nullifies PII and retains age and gender`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        val age = 35
        val gender = Gender.F
        createClient(callerId, clientAId, firstName = "Alice", lastName = "Wang", age = age, gender = gender)

        ClientService.anonymize(callerId, clientAId)

        val persisted = persistedClient(clientAId)
        assertNull(persisted.firstName)
        assertNull(persisted.lastName)
        assertNull(persisted.middleName)
        assertNull(persisted.suffix)
        assertNull(persisted.phoneNumber)
        assertNull(persisted.address)
        assertNull(persisted.medicalConditions)
        assertNull(persisted.systolicBp)
        assertNull(persisted.diastolicBp)
        assertEquals(gender, persisted.gender)
        assertEquals(age, persisted.age)
        assertNotNull(persisted.deletedAt)
    }

    @Test
    fun `anonymize client without EDIT_BRANCH_DATA is allowed at service layer`() {
        createClient(callerId, clientAId)

        ClientService.anonymize(callerId, clientAId)

        val persisted = persistedClient(clientAId)
        assertNull(persisted.firstName)
    }

    @Test
    fun `anonymize client returns 404 for non-existent client`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())

        assertFailsWith<NotFoundException> {
            ClientService.anonymize(callerId, TestFixtures.uuid())
        }
    }

    @Test
    fun `anonymize client is excluded from search results`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId, firstName = "Searchable", lastName = "Client")

        ClientService.anonymize(callerId, clientAId)

        val results = ClientService.search("Searchable")
        assertTrue(results.none { it.id == clientAId })
    }

    @Test
    fun `anonymize client still returned by direct findById`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId)

        ClientService.anonymize(callerId, clientAId)

        val found = ClientService.findById(clientAId)
        assertEquals(clientAId, found.id)
        assertNull(found.firstName)
    }

    @Test
    fun `anonymize already anonymized client returns 404`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId)
        ClientService.anonymize(callerId, clientAId)

        assertFailsWith<NotFoundException> {
            ClientService.anonymize(callerId, clientAId)
        }
    }

    @Test
    fun `anonymize client with pending session throws conflict and keeps PII`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId, firstName = "Alice", lastName = "Wang")
        seedSession(pendingSessionId, clientAId, SessionStatus.PENDING)

        assertFailsWith<ConflictException> {
            ClientService.anonymize(callerId, clientAId)
        }

        val persisted = persistedClient(clientAId)
        assertEquals("Alice", persisted.firstName)
        assertEquals("Wang", persisted.lastName)
        assertNull(persisted.deletedAt)
        assertEquals(1L, auditEntryCount(clientAId))
    }

    @Test
    fun `anonymize client with only completed sessions succeeds`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId)
        seedSession(completedSessionId, clientAId, SessionStatus.COMPLETED)

        ClientService.anonymize(callerId, clientAId)

        val persisted = persistedClient(clientAId)
        assertNull(persisted.firstName)
        assertNotNull(persisted.deletedAt)
    }

    @Test
    fun `anonymize client with only cancelled and no-show sessions succeeds`() {
        IdentityFixtures.grantEditBranchData(callerId, TestFixtures.uuid())
        createClient(callerId, clientAId)
        seedSession(noShowSessionId, clientAId, SessionStatus.NO_SHOW)
        seedSession(cancelledSessionId, clientAId, SessionStatus.CANCELLED)

        ClientService.anonymize(callerId, clientAId)

        val persisted = persistedClient(clientAId)
        assertNull(persisted.firstName)
        assertNotNull(persisted.deletedAt)
    }

    // #599 helper mirrors ClientService.create signature (fixture parity, #593 precedent).
    @Suppress("LongParameterList") // #599
    private fun createClient(
        callerId: UUID,
        id: UUID,
        firstName: String = "John",
        lastName: String = "Doe",
        middleName: String? = "Jane",
        suffix: String? = null,
        phoneNumber: String? = "9998887777",
        address: String? = "123 Main St",
        gender: Gender = Gender.M,
        age: Int = 30,
        systolicBp: Short? = 120.toShort(),
        diastolicBp: Short? = 80.toShort(),
        medicalConditions: String? = null,
    ): ClientCreateResult =
        ClientService.create(
            callerId = callerId,
            id = id,
            firstName = firstName,
            lastName = lastName,
            middleName = middleName,
            suffix = suffix,
            phoneNumber = phoneNumber,
            address = address,
            gender = gender,
            age = age,
            systolicBp = systolicBp,
            diastolicBp = diastolicBp,
            medicalConditions = medicalConditions,
        )

    private fun persistedClient(clientId: UUID): com.companyb.companyapp.client.Client =
        transaction {
            ClientTable
                .selectAll()
                .where { ClientTable.id eq clientId }
                .single()
                .let { row ->
                    com.companyb.companyapp.client.Client(
                        id = row[ClientTable.id],
                        firstName = row[ClientTable.firstName],
                        lastName = row[ClientTable.lastName],
                        middleName = row[ClientTable.middleName],
                        suffix = row[ClientTable.suffix],
                        phoneNumber = row[ClientTable.phoneNumber],
                        address = row[ClientTable.address],
                        gender = Gender.valueOf(row[ClientTable.gender]),
                        age = row[ClientTable.age],
                        systolicBp = row[ClientTable.systolicBp],
                        diastolicBp = row[ClientTable.diastolicBp],
                        medicalConditions = row[ClientTable.medicalConditions],
                        deletedAt = row[ClientTable.deletedAt],
                    )
                }
        }

    private fun auditEntryCount(clientId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { (AuditLogTable.auditTableName eq "client") and (AuditLogTable.recordId eq clientId) }
                .count()
        }

    private fun seedSession(
        sessionId: UUID,
        clientId: UUID,
        status: SessionStatus,
        sessionType: SessionType = SessionType.REGULAR,
    ) {
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionType = sessionType,
            sessionStatus = status,
        )
    }

    private fun seedSessionVoid(sessionId: UUID) {
        transaction {
            SessionVoidTable.insert {
                it[SessionVoidTable.id] = sessionVoidId
                it[SessionVoidTable.sessionId] = sessionId
                it[SessionVoidTable.voidedBy] = callerId
                it[SessionVoidTable.voidReason] = "Test void"
            }
        }
    }
}
