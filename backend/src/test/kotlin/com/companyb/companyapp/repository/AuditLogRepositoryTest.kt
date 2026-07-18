package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.Client
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.repository.model.ProductCategory
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdown
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.repository.model.Session
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditLogRepositoryTest {
    @Test
    fun `jsonField builds a single-field JSON object`() {
        assertEquals("""{"status":"INACTIVE"}""", AuditLogRepository.jsonField("status", "INACTIVE"))
    }

    @Test
    fun `jsonField escapes special characters in the value`() {
        assertEquals(
            """{"reason":"he said \"hi\""}""",
            AuditLogRepository.jsonField("reason", "he said \"hi\""),
        )
    }

    @Test
    fun `jsonFields with Map produces same output as vararg for equivalent input`() {
        val map = mapOf("name" to "Alice", "age" to "30")
        val varargResult = AuditLogRepository.jsonFields("name" to "Alice", "age" to "30")
        val mapResult = AuditLogRepository.jsonFields(map)
        assertEquals(varargResult, mapResult)
    }

    @Test
    fun `jsonFields with Map handles empty map`() {
        assertEquals("{}", AuditLogRepository.jsonFields(emptyMap()))
    }

    @Test
    fun `Client toAuditFields returns expected key set`() {
        val client =
            Client(
                id = UUID.randomUUID(),
                firstName = "Alice",
                lastName = "Smith",
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = Gender.F,
                age = 30,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
                deletedAt = null,
            )
        val fields = client.toAuditFields()
        assertEquals(setOf("id", "firstName", "lastName"), fields.keys)
    }

    @Test
    fun `Client toAuditFields encodes null firstName as string`() {
        val client =
            Client(
                id = UUID.randomUUID(),
                firstName = null,
                lastName = null,
                middleName = null,
                suffix = null,
                phoneNumber = null,
                address = null,
                gender = Gender.F,
                age = 30,
                systolicBp = null,
                diastolicBp = null,
                medicalConditions = null,
                deletedAt = null,
            )
        val fields = client.toAuditFields()
        assertEquals("null", fields["firstName"])
        assertEquals("null", fields["lastName"])
    }

    @Test
    fun `Product toAuditFields returns expected key set`() {
        val product =
            Product(
                id = UUID.randomUUID(),
                name = "Test Product",
                productCategoryId = UUID.randomUUID(),
                unitPrice = BigDecimal("100.00"),
                commissionAmount = BigDecimal("10.00"),
                isActive = true,
            )
        val fields = product.toAuditFields()
        assertEquals(
            setOf("id", "name", "productCategoryId", "unitPrice", "commissionAmount"),
            fields.keys,
        )
    }

    @Test
    fun `Session toAuditFields returns expected key set`() {
        val session =
            Session(
                id = UUID.randomUUID(),
                clientId = UUID.randomUUID(),
                branchDayId = UUID.randomUUID(),
                requestedPractitionerId = null,
                sessionType = "REGULAR",
                isWalkIn = false,
                sessionStatus = "PENDING",
                basePrice = BigDecimal("2500.00"),
                finalPrice = BigDecimal("2500.00"),
                remarks = null,
                otherConcerns = null,
                bookedAt = null,
                nextAppointmentDate = null,
                createdAt = OffsetDateTime.now(),
                version = 1,
            )
        val fields = session.toAuditFields()
        assertEquals(
            setOf("id", "clientId", "branchDayId", "sessionType", "finalPrice"),
            fields.keys,
        )
    }

    @Test
    fun `BranchInventory toAuditFields returns expected key set`() {
        val inventory =
            BranchInventory(
                id = UUID.randomUUID(),
                branchId = UUID.randomUUID(),
                productId = UUID.randomUUID(),
                currentStock = 10,
                version = 1,
            )
        val fields = inventory.toAuditFields()
        assertEquals(
            setOf("id", "branchId", "productId", "currentStock", "version"),
            fields.keys,
        )
    }

    @Test
    fun `Remittance toAuditFields returns expected key set`() {
        val remittance =
            Remittance(
                id = UUID.randomUUID(),
                type = RemittanceType.SESSION,
                status = RemittanceStatus.DRAFT,
                branchId = UUID.randomUUID(),
                method = RemittanceMethod.BANK_TRANSFER,
                submittedDate = LocalDate.now(),
                submittedBy = UUID.randomUUID(),
                dateRangeStart = LocalDate.now(),
                dateRangeEnd = LocalDate.now(),
                createdAt = OffsetDateTime.now(),
                version = 1,
            )
        val fields = remittance.toAuditFields()
        assertEquals(
            setOf("id", "type", "status", "branchId", "method", "dateRangeStart", "dateRangeEnd"),
            fields.keys,
        )
    }

    @Test
    fun `RemittanceLine toAuditFields returns expected key set`() {
        val line =
            RemittanceLine(
                id = UUID.randomUUID(),
                remittanceId = UUID.randomUUID(),
                type = RemittanceLineType.SESSION,
                amount = BigDecimal("100.00"),
                sessionId = null,
                productSaleId = null,
                createdBy = UUID.randomUUID(),
                createdAt = OffsetDateTime.now(),
                deletedBy = null,
                deletedAt = null,
            )
        val fields = line.toAuditFields()
        assertEquals(
            setOf("id", "remittanceId", "type", "amount"),
            fields.keys,
        )
    }

    @Test
    fun `RemittanceDayBreakdown toAuditFields returns expected key set`() {
        val breakdown =
            RemittanceDayBreakdown(
                id = UUID.randomUUID(),
                remittanceId = UUID.randomUUID(),
                branchDayId = UUID.randomUUID(),
            )
        val fields = breakdown.toAuditFields()
        assertEquals(
            setOf("id", "remittanceId", "branchDayId"),
            fields.keys,
        )
    }

    @Test
    fun `ProductCategory toAuditFields returns expected key set`() {
        val category =
            ProductCategory(
                id = UUID.randomUUID(),
                name = "Test Category",
            )
        val fields = category.toAuditFields()
        assertEquals(setOf("id", "name"), fields.keys)
    }

    @Test
    fun `Branch toAuditFields returns expected key set`() {
        val branch =
            Branch(
                id = UUID.randomUUID(),
                name = "Test Branch",
                branchType = BranchType.CLINIC,
            )
        val fields = branch.toAuditFields()
        assertEquals(setOf("id", "name", "branchType"), fields.keys)
    }
}
