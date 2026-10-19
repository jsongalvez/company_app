package com.companyb.companyapp.client

import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/** The age is typed once; the shown age goes up each year since the day it was typed. */
class ClientAgePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "client-age-caller")
    }

    @Test
    fun `age counts full years since it was typed`() {
        val entered = LocalDate.of(2024, 6, 15)
        assertEquals(30, ageToday(30, entered, LocalDate.of(2025, 6, 14)))
        assertEquals(31, ageToday(30, entered, LocalDate.of(2025, 6, 15)))
        assertEquals(33, ageToday(30, entered, LocalDate.of(2027, 12, 1)))
        assertEquals(30, ageToday(30, entered, LocalDate.of(2020, 1, 1)))
        assertEquals(120, ageToday(119, entered, LocalDate.of(2030, 1, 1)))
    }

    @Test
    fun `stored client shows the age for today and typing a new age restarts the clock`() {
        val id = UUID.randomUUID()
        val result = create(id)
        assertEquals(30, result.client.age)
        assertEquals(clientToday(), result.client.ageEnteredOn)

        transaction {
            ClientTable.update({ ClientTable.id eq id }) { it[ageEnteredOn] = clientToday().minusYears(2) }
        }
        val aged = ClientRepository.findById(id)!!
        assertEquals(32, aged.age)
        assertEquals(30, aged.ageAsEntered)

        // Saving the same number again counts as typing it: the clock restarts from today.
        val updated = update(id, 32)
        assertEquals(32, updated.age)
        assertEquals(clientToday(), updated.ageEnteredOn)
        assertEquals(32, ClientRepository.findById(id)!!.ageAsEntered)
    }

    @Test
    fun `replay of the same create later still counts as the same payload`() {
        val id = UUID.randomUUID()
        create(id)
        transaction {
            ClientTable.update({ ClientTable.id eq id }) { it[ageEnteredOn] = clientToday().minusYears(1) }
        }
        val replay = create(id)
        assertEquals(false, replay.created)
        assertEquals(31, replay.client.age)
    }

    private fun create(id: UUID): ClientCreateResult =
        ClientService.create(
            callerId = callerId,
            id = id,
            firstName = "Age",
            lastName = "Tester",
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = Gender.F,
            age = 30,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )

    private fun update(
        id: UUID,
        age: Int,
    ): Client =
        ClientService.update(
            callerId = callerId,
            clientId = id,
            firstName = null,
            lastName = null,
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = null,
            gender = null,
            age = age,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
}
