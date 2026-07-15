package com.companyb.companyapp.service

import com.companyb.companyapp.repository.ClientCreateResult
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val clientAId = UUID.randomUUID()
    private val clientBId = UUID.randomUUID()
    private val testIds = listOf(clientAId, clientBId)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows(callerId, testIds)
        insertUser(callerId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows(callerId, testIds)
        }
    }

    @Test
    fun `create persists all client fields and writes audit`() {
        val result = createClient(callerId, clientAId)

        assertTrue(result.created)
        assertEquals("John", result.client.firstName)
        assertEquals("Doe", result.client.lastName)
        assertEquals("Jane", persistedClient(clientAId).middleName)
        assertEquals("M", persistedClient(clientAId).gender)
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
    fun `bp both or none rejects systolic only`() {
        assertFailsWith<BadRequestResponse> {
            createClient(callerId, clientAId, systolicBp = 120.toShort(), diastolicBp = null)
        }
    }

    @Test
    fun `bp both or none rejects diastolic only`() {
        assertFailsWith<BadRequestResponse> {
            createClient(callerId, clientAId, systolicBp = null, diastolicBp = 80.toShort())
        }
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
    fun `invalid gender is rejected`() {
        assertFailsWith<BadRequestResponse> {
            createClient(callerId, clientAId, gender = "X")
        }
    }

    @Test
    fun `blank first name is rejected`() {
        assertFailsWith<BadRequestResponse> {
            createClient(callerId, clientAId, firstName = "   ")
        }
    }

    @Test
    fun `blank last name is rejected`() {
        assertFailsWith<BadRequestResponse> {
            createClient(callerId, clientAId, lastName = "")
        }
    }

    @Test
    fun `find by id returns persisted client`() {
        createClient(callerId, clientAId)

        val found = ClientService.findById(clientAId)

        assertEquals("John", found.firstName)
        assertEquals("Doe", found.lastName)
    }

    @Test
    fun `find by id throws not found for missing client`() {
        assertFailsWith<NotFoundResponse> {
            ClientService.findById(UUID.randomUUID())
        }
    }

    @Test
    fun `search matches by first name`() {
        createClient(callerId, clientAId, firstName = "John")
        createClient(callerId, clientBId, firstName = "Alice", lastName = "Smith")

        val results = ClientService.search("John")

        assertTrue(results.any { it.id == clientAId })
        assertFalse(results.any { it.id == clientBId })
    }

    @Test
    fun `search matches by last name`() {
        createClient(callerId, clientAId, firstName = "John", lastName = "Doe")
        createClient(callerId, clientBId, firstName = "Alice", lastName = "Smith")

        val results = ClientService.search("Doe")

        assertTrue(results.any { it.id == clientAId })
        assertFalse(results.any { it.id == clientBId })
    }

    @Test
    fun `search matches by phone prefix`() {
        createClient(callerId, clientAId, phoneNumber = "1234567890")
        createClient(callerId, clientBId, phoneNumber = "9876543210")

        val results = ClientService.search("1234")

        assertTrue(results.any { it.id == clientAId })
        assertFalse(results.any { it.id == clientBId })
    }

    @Test
    fun `search rejects empty query`() {
        assertFailsWith<BadRequestResponse> {
            ClientService.search("   ")
        }
    }

    @Test
    fun `search is case-insensitive`() {
        createClient(callerId, clientAId, firstName = "John")

        val results = ClientService.search("john")

        assertTrue(results.any { it.id == clientAId })
    }

    @Test
    fun `update client succeeds with EDIT_BRANCH_DATA capability`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
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
    fun `update client fails without EDIT_BRANCH_DATA capability`() {
        createClient(callerId, clientAId)

        assertFailsWith<ForbiddenResponse> {
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
        }
    }

    @Test
    fun `update client returns 404 for non-existent client`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())

        assertFailsWith<NotFoundResponse> {
            ClientService.update(
                callerId = callerId,
                clientId = UUID.randomUUID(),
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
    fun `update client rejects invalid gender`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
        createClient(callerId, clientAId)

        assertFailsWith<BadRequestResponse> {
            ClientService.update(
                callerId = callerId,
                clientId = clientAId,
                firstName = null,
                lastName = null,
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = "X",
                age = null,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
            )
        }
    }

    @Test
    fun `update client rejects blank first name`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
        createClient(callerId, clientAId)

        assertFailsWith<BadRequestResponse> {
            ClientService.update(
                callerId = callerId,
                clientId = clientAId,
                firstName = "   ",
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
    fun `update client rejects partial blood pressure`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
        createClient(callerId, clientAId)

        assertFailsWith<BadRequestResponse> {
            ClientService.update(
                callerId = callerId,
                clientId = clientAId,
                firstName = null,
                lastName = null,
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = null,
                age = null,
                systolicBp = 120.toShort(),
                diastolicBp = null,
                medicalConditions = null,
            )
        }
    }

    @Test
    fun `update client writes audit log`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
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
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
        val age = 35
        val gender = "F"
        createClient(callerId, clientAId, firstName = "Alice", lastName = "Wang", age = age, gender = gender)

        ClientService.anonymize(callerId, clientAId)

        val persisted = persistedClient(clientAId)
        assertEquals("", persisted.firstName)
        assertEquals("", persisted.lastName)
        assertNull(persisted.middleName)
        assertNull(persisted.suffix)
        assertNull(persisted.phoneNumber)
        assertEquals("", persisted.address)
        assertNull(persisted.medicalConditions)
        assertNull(persisted.systolicBp)
        assertNull(persisted.diastolicBp)
        assertEquals(gender, persisted.gender)
        assertEquals(age, persisted.age)
        assertNotNull(persisted.deletedAt)
    }

    @Test
    fun `anonymize client fails without EDIT_BRANCH_DATA capability`() {
        createClient(callerId, clientAId)

        assertFailsWith<ForbiddenResponse> {
            ClientService.anonymize(callerId, clientAId)
        }
    }

    @Test
    fun `anonymize client returns 404 for non-existent client`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())

        assertFailsWith<NotFoundResponse> {
            ClientService.anonymize(callerId, UUID.randomUUID())
        }
    }

    @Test
    fun `anonymize client is excluded from search results`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
        createClient(callerId, clientAId, firstName = "Searchable", lastName = "Client")

        ClientService.anonymize(callerId, clientAId)

        val results = ClientService.search("Searchable")
        assertTrue(results.none { it.id == clientAId })
    }

    @Test
    fun `anonymize client still returned by direct findById`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
        createClient(callerId, clientAId)

        ClientService.anonymize(callerId, clientAId)

        val found = ClientService.findById(clientAId)
        assertEquals(clientAId, found.id)
        assertEquals("", found.firstName)
    }

    @Test
    fun `anonymize already anonymized client returns 404`() {
        DatabaseTestHelper.grantEditBranchData(callerId, UUID.randomUUID())
        createClient(callerId, clientAId)
        ClientService.anonymize(callerId, clientAId)

        assertFailsWith<NotFoundResponse> {
            ClientService.anonymize(callerId, clientAId)
        }
    }

    @Suppress("LongParameterList")
    private fun createClient(
        callerId: UUID,
        id: UUID,
        firstName: String = "John",
        lastName: String = "Doe",
        middleName: String? = "Jane",
        suffix: String? = null,
        phoneNumber: String? = "9998887777",
        address: String? = "123 Main St",
        gender: String = "M",
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

    private fun insertUser(userId: UUID) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "client-caller-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Client Caller",
        )
    }

    private fun persistedClient(clientId: UUID): com.companyb.companyapp.repository.model.Client =
        transaction {
            ClientTable
                .selectAll()
                .where { ClientTable.id eq clientId }
                .single()
                .let { row ->
                    com.companyb.companyapp.repository.model.Client(
                        id = row[ClientTable.id],
                        firstName = row[ClientTable.firstName],
                        lastName = row[ClientTable.lastName],
                        middleName = row[ClientTable.middleName],
                        suffix = row[ClientTable.suffix],
                        phoneNumber = row[ClientTable.phoneNumber],
                        address = row[ClientTable.address],
                        gender = row[ClientTable.gender],
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

    private fun deleteTestRows(
        userId: UUID,
        clientIds: List<UUID>,
    ) {
        transaction {
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq userId }
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq userId) or
                    (AuditLogTable.recordId eq clientIds[0]) or
                    (AuditLogTable.recordId eq clientIds[1])
            }
            ClientTable.deleteWhere {
                (ClientTable.id eq clientIds[0]) or (ClientTable.id eq clientIds[1])
            }
            AppUserTable.deleteWhere { AppUserTable.id eq userId }
        }
    }

    private companion object {
        private val json = Json

        @Suppress("unused")
        private fun extractJsonField(
            jsonString: String,
            field: String,
        ): String {
            val jsonElement = json.parseToJsonElement(jsonString)
            return jsonElement.jsonObject[field]?.jsonPrimitive?.content ?: ""
        }
    }
}
