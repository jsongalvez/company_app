package com.companyb.companyapp.client

import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditValues
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientPatchField
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.session.SessionReads
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Client feature commands (#323, ADR-0024). Each mutating command owns exactly one business
 * transaction: persistence runs on it via `ClientRepository.*InTransaction` store operations,
 * the before/after state is captured inside it (ADR-0019 invariant), and the audit row is
 * inserted directly into it — so domain write + audit commit atomically or not at all.
 * The anonymize guard (client row lock + PENDING-session check) runs inside the command
 * transaction, keeping the atomic check+write ordering it always had.
 */
object ClientService {
    private val logger = KotlinLogging.logger {}

    @Suppress("LongParameterList", "ReturnCount", "ThrowsCount")
    fun create(
        callerId: UUID,
        id: UUID,
        firstName: String,
        lastName: String,
        middleName: String?,
        suffix: String?,
        phoneNumber: String?,
        address: String?,
        gender: Gender,
        age: Int,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
    ): ClientCreateResult =
        transaction {
            val result =
                ClientRepository.createInTransaction(
                    ClientCreateParams(
                        id = id,
                        firstName = firstName,
                        lastName = lastName,
                        middleName = middleName?.trim()?.takeIf { it.isNotEmpty() },
                        suffix = suffix?.trim()?.takeIf { it.isNotEmpty() },
                        phoneNumber = phoneNumber?.trim()?.takeIf { it.isNotEmpty() },
                        address = address?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_CLIENT_ADDRESS,
                        gender = gender,
                        age = age,
                        systolicBp = systolicBp,
                        diastolicBp = diastolicBp,
                        medicalConditions = medicalConditions?.trim()?.takeIf { it.isNotEmpty() },
                        changedBy = callerId,
                    ),
                )
            if (result.created) {
                ClientAudit.inserted(callerId, result.client)
            }
            result
        }.also {
            logger.info { "[CREATE-CLIENT] Client ${it.client.id.toString().maskUUID()} created=${it.created}" }
        }

    fun search(query: String): List<Client> = ClientRepository.search(query)

    fun countSessions(clientIds: Collection<UUID>): Map<UUID, Int> = ClientRepository.countSessions(clientIds)

    fun findById(clientId: UUID): Client =
        ClientRepository.findById(clientId) ?: throw NotFoundException("Client not found")

    @Suppress("LongParameterList", "ReturnCount", "ThrowsCount", "CyclomaticComplexMethod")
    fun update(
        callerId: UUID,
        clientId: UUID,
        firstName: String?,
        lastName: String?,
        middleName: String?,
        suffix: String?,
        phoneNumber: String?,
        address: String?,
        gender: Gender?,
        age: Int?,
        systolicBp: Short?,
        diastolicBp: Short?,
        medicalConditions: String?,
        clearFields: Set<String> = emptySet(),
    ): Client =
        transaction {
            // Locked before-state (#522): SELECT FOR UPDATE serializes concurrent
            // writers so the audit diff attributes only this actor's changes.
            val before =
                ClientRepository.acquireLockInTransaction(clientId)
                    ?: throw NotFoundException("Client not found")

            val patch =
                resolveClientPatch(
                    firstName = firstName,
                    lastName = lastName,
                    middleName = middleName,
                    suffix = suffix,
                    phoneNumber = phoneNumber,
                    address = address,
                    systolicBp = systolicBp,
                    diastolicBp = diastolicBp,
                    medicalConditions = medicalConditions,
                    clearFields = clearFields,
                )

            val (updatedCount, after) =
                ClientRepository.updateInTransaction(
                    ClientUpdateParams(
                        clientId = clientId,
                        firstName = patch.firstName,
                        lastName = patch.lastName,
                        middleName = patch.middleName,
                        suffix = patch.suffix,
                        phoneNumber = patch.phoneNumber,
                        address = patch.address,
                        gender = gender,
                        age = age,
                        systolicBp = patch.systolicBp,
                        diastolicBp = patch.diastolicBp,
                        medicalConditions = patch.medicalConditions,
                        clearMiddleName = patch.clearMiddleName,
                        clearSuffix = patch.clearSuffix,
                        clearPhoneNumber = patch.clearPhoneNumber,
                        clearAddress = patch.clearAddress,
                        clearMedicalConditions = patch.clearMedicalConditions,
                        clearBp = patch.clearBp,
                    ),
                )

            if (updatedCount > 0 && after != null) {
                ClientAudit.updated(callerId, before, after, patch.clearedFields)
            }
            after ?: throw NotFoundException("Client not found")
        }

    @Suppress("ThrowsCount")
    fun anonymize(
        callerId: UUID,
        clientId: UUID,
    ) {
        transaction {
            val before =
                ClientRepository.acquireLockInTransaction(clientId)
                    ?: throw NotFoundException("Client not found")

            // Atomic check+write guards (CR-018 C2): lock the client row, then reject when an
            // active PENDING session exists — both inside this command transaction.
            if (SessionReads.hasActivePendingSessionInTransaction(clientId)) {
                throw ConflictException(
                    "Client has an active PENDING session; complete or cancel it before anonymizing",
                )
            }

            val updatedCount = ClientRepository.anonymizeInTransaction(clientId)

            if (updatedCount > 0) {
                val after =
                    ClientRepository.findByIdInTransaction(clientId)
                        ?: error("client not found after anonymize")
                // Redact-then-record (#524): prior payloads are scrubbed before
                // the anonymization event lands, so the removed names never sit
                // in a new payload and the whole command stays atomic.
                AuditLog.redactClientNamesInTransaction(clientId)
                ClientAudit.anonymized(callerId, before, after)
            } else {
                throw NotFoundException("Client not found")
            }
        }
    }
}

/**
 * Resolved three-state PATCH (#523): present values are trimmed sets, [clearFields]
 * entries are explicit clears, absent values are unchanged. Throws [ValidationException]
 * so HTTP callers get 400 through the centralized handler; the route mirrors the same
 * rules with [BadRequestResponse] to preserve its existing error shape.
 */
internal data class ResolvedClientPatch(
    val firstName: String?,
    val lastName: String?,
    val middleName: String?,
    val suffix: String?,
    val phoneNumber: String?,
    val address: String?,
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val medicalConditions: String?,
    val clearMiddleName: Boolean,
    val clearSuffix: Boolean,
    val clearPhoneNumber: Boolean,
    val clearAddress: Boolean,
    val clearMedicalConditions: Boolean,
    val clearBp: Boolean,
    /** Normalized cleared names for the audit reason — values never recorded (#524 safe). */
    val clearedFields: Set<String>,
)

private const val BP_PAIR_MESSAGE =
    "Both systolic and diastolic blood pressure must be provided together or not at all"

/** BP pair's three-state resolution (#523): set together, cleared together, or unchanged. */
internal data class BpPatch(
    val systolicBp: Short?,
    val diastolicBp: Short?,
    val clear: Boolean,
)

internal fun resolveBpPatch(
    systolicBp: Short?,
    diastolicBp: Short?,
    clearSystolic: Boolean,
    clearDiastolic: Boolean,
): BpPatch {
    val hasValue = systolicBp != null || diastolicBp != null
    val hasClear = clearSystolic || clearDiastolic
    val setAndClear = hasValue && hasClear
    val halfSet = (systolicBp != null) != (diastolicBp != null)
    val halfClear = clearSystolic != clearDiastolic
    val partial = setAndClear || halfSet || halfClear
    if (setAndClear) {
        throw ValidationException("Blood pressure cannot be both set and cleared")
    }
    if (partial) {
        throw ValidationException(BP_PAIR_MESSAGE)
    }
    return BpPatch(systolicBp, diastolicBp, clearSystolic && clearDiastolic)
}

/** One optional text field's set-or-clear (#523): blank sets are rejected, never stored. */
internal fun resolveClearableText(
    label: String,
    value: String?,
    clear: Boolean,
    field: String,
): String? {
    if (clear && value != null) {
        throw ValidationException("$field cannot be both set and cleared")
    }
    if (!clear && value != null && value.trim().isEmpty()) {
        throw ValidationException("$label cannot be blank (use clearFields to clear)")
    }
    return value?.trim()
}

/** Required names stay non-blankable (#523 preserves the existing rule). */
internal fun resolveRequiredName(
    value: String?,
    label: String,
): String? {
    val trimmed = value?.trim()
    if (trimmed != null && trimmed.isEmpty()) {
        throw ValidationException("$label cannot be blank")
    }
    return trimmed?.takeIf { it.isNotEmpty() }
}

/** Clear-set shape: unknown names and required-field clears are rejected. */
internal fun checkClearShape(clearFields: Set<String>) {
    val unknown = clearFields - ClientPatchField.clearable - ClientPatchField.required
    if (unknown.isNotEmpty()) {
        throw ValidationException("Unknown clear field(s): ${unknown.sorted().joinToString()}")
    }
    val requiredClear = clearFields.intersect(ClientPatchField.required).sorted()
    if (requiredClear.isNotEmpty()) {
        throw ValidationException("${requiredClear.joinToString()} cannot be cleared")
    }
}

@Suppress("LongParameterList")
internal fun resolveClientPatch(
    firstName: String?,
    lastName: String?,
    middleName: String?,
    suffix: String?,
    phoneNumber: String?,
    address: String?,
    systolicBp: Short?,
    diastolicBp: Short?,
    medicalConditions: String?,
    clearFields: Set<String>,
): ResolvedClientPatch {
    checkClearShape(clearFields)
    val bp =
        resolveBpPatch(
            systolicBp,
            diastolicBp,
            ClientPatchField.SYSTOLIC_BP in clearFields,
            ClientPatchField.DIASTOLIC_BP in clearFields,
        )
    return ResolvedClientPatch(
        firstName = resolveRequiredName(firstName, "First name"),
        lastName = resolveRequiredName(lastName, "Last name"),
        middleName =
            resolveClearableText(
                "Middle name",
                middleName,
                ClientPatchField.MIDDLE_NAME in clearFields,
                "middleName",
            ),
        suffix =
            resolveClearableText("Suffix", suffix, ClientPatchField.SUFFIX in clearFields, "suffix"),
        phoneNumber =
            resolveClearableText(
                "Phone number",
                phoneNumber,
                ClientPatchField.PHONE_NUMBER in clearFields,
                "phoneNumber",
            ),
        address =
            resolveClearableText(
                "Address",
                address,
                ClientPatchField.ADDRESS in clearFields,
                "address",
            ),
        systolicBp = bp.systolicBp,
        diastolicBp = bp.diastolicBp,
        medicalConditions =
            resolveClearableText(
                "Medical conditions",
                medicalConditions,
                ClientPatchField.MEDICAL_CONDITIONS in clearFields,
                "medicalConditions",
            ),
        clearMiddleName = ClientPatchField.MIDDLE_NAME in clearFields,
        clearSuffix = ClientPatchField.SUFFIX in clearFields,
        clearPhoneNumber = ClientPatchField.PHONE_NUMBER in clearFields,
        clearAddress = ClientPatchField.ADDRESS in clearFields,
        clearMedicalConditions = ClientPatchField.MEDICAL_CONDITIONS in clearFields,
        clearBp = bp.clear,
        clearedFields = (clearFields intersect ClientPatchField.clearable).toSortedSet(),
    )
}

/**
 * Client audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit row commits atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ClientAudit {
    fun inserted(
        changedBy: UUID,
        client: Client,
    ) = AuditLog.recordInsert(
        tableName = ClientTable.tableName,
        recordId = client.id,
        changedBy = changedBy,
        fields = ClientTable.auditFields(client),
    )

    fun updated(
        changedBy: UUID,
        before: Client,
        after: Client,
        clearedFields: Set<String> = emptySet(),
    ) = AuditLog.recordUpdate(
        tableName = ClientTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        // Cleared-field identity without values (#523, #525): cleared PII never
        // lands in a payload — old cleared values become REDACTED, new stays
        // null (or N/A for address) — while the reason names the cleared fields.
        auditFields = { client -> scrubCleared(ClientTable.auditFields(client), clearedFields) },
        reason = clearedFields.takeIf { it.isNotEmpty() }?.let { "cleared: ${it.sorted().joinToString()}" },
    )

    /**
     * Anonymization event (#524, extended #525): the before-image carries the
     * redaction marker instead of every removed PII value, so the event itself
     * can never resurrect what anonymization just removed. The `anonymized`
     * reason is the audit-side marker of the anonymization.
     */
    fun anonymized(
        changedBy: UUID,
        before: Client,
        after: Client,
    ) {
        val scrubbed =
            before.copy(
                firstName = AuditValues.REDACTED,
                lastName = AuditValues.REDACTED,
                middleName = before.middleName?.let { AuditValues.REDACTED },
                suffix = before.suffix?.let { AuditValues.REDACTED },
                phoneNumber = before.phoneNumber?.let { AuditValues.REDACTED },
                address = before.address?.let { if (it == DEFAULT_CLIENT_ADDRESS) it else AuditValues.REDACTED },
                medicalConditions = before.medicalConditions?.let { AuditValues.REDACTED },
            )
        AuditLog.recordUpdate(
            tableName = ClientTable.tableName,
            recordId = after.id,
            before = scrubbed,
            after = after,
            changedBy = changedBy,
            // BP is numeric on the entity, so its marker is applied at the
            // payload level: non-null BP becomes REDACTED, nulls stay null.
            auditFields = { client ->
                ClientTable.auditFields(client).mapValues { (key, value) ->
                    if ((key == "systolicBp" || key == "diastolicBp") && value != null) {
                        AuditValues.REDACTED
                    } else {
                        value
                    }
                }
            },
            reason = ANONYMIZED_REASON,
        )
    }

    /**
     * Cleared-field scrub (#523, #525): for keys in [cleared], a stored PII value
     * becomes REDACTED while nulls and the N/A address default keep their shape,
     * so clears record the change without retaining the removed value.
     */
    internal fun scrubCleared(
        fields: Map<String, String?>,
        cleared: Set<String>,
    ): Map<String, String?> =
        fields.mapValues { (key, value) ->
            if (key in cleared && value != null && value != DEFAULT_CLIENT_ADDRESS) {
                AuditValues.REDACTED
            } else {
                value
            }
        }
}

private const val ANONYMIZED_REASON = "anonymized"
