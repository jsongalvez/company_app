package com.companyb.companyapp.audit

import com.companyb.companyapp.app.GLOBAL_CAPABILITY_CONTEXT_ID
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.CapabilitySourceType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.dto.AuditLogEntryResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// #390 — the "Open client record" affordance visibility rule, pinned pure: per-row predicate
// (client-table rows only) × caller gate (GLOBAL EDIT_BRANCH_DATA — the backend's exact
// client-read scope; a BRANCH_DAY day-grant holder would 403 and must not see it).
class AuditEntryOpenRecordTest {
    private fun entry(tableName: String) =
        AuditLogEntryResponse(
            id = "e1",
            tableName = tableName,
            recordId = "record-1",
            action = AuditAction.UPDATE,
            changedBy = "user-1",
            changedAt = "2026-08-23T00:00:00.000Z",
            isFlagged = false,
        )

    private fun cap(
        code: String,
        contextType: CapabilityContextType,
    ) = UserCapabilityResponse(
        capabilityCode = code,
        contextType = contextType,
        contextId = GLOBAL_CAPABILITY_CONTEXT_ID,
        sourceType = CapabilitySourceType.ROLE,
    )

    @Test
    fun clientTableRow_isOpenable() {
        assertTrue(canOpenClientRecord(entry("client")))
    }

    @Test
    fun nonClientTables_neverOpenable() {
        assertFalse(canOpenClientRecord(entry("app_user")))
        assertFalse(canOpenClientRecord(entry("relief_invite")))
        assertFalse(canOpenClientRecord(entry("remittance")))
    }

    @Test
    fun globalEditBranchDataHolder_seesAffordanceOnClientRows() {
        val caps =
            listOf(
                cap("EDIT_BRANCH_DATA", CapabilityContextType.GLOBAL),
            )
        val canManageClients =
            caps.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.GLOBAL, GLOBAL_CAPABILITY_CONTEXT_ID)
        assertTrue(canManageClients && canOpenClientRecord(entry("client")))
    }

    @Test
    fun branchDayDayGrantOnly_doesNotSeeAffordance() {
        // The relief day-grant shape: EDIT_BRANCH_DATA at BRANCH_DAY is not the backend's
        // GLOBAL client-read scope (fail-closed — the jump would 403).
        val caps =
            listOf(
                cap("EDIT_BRANCH_DATA", CapabilityContextType.BRANCH_DAY),
            )
        val canManageClients =
            caps.hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.GLOBAL, GLOBAL_CAPABILITY_CONTEXT_ID)
        assertFalse(canManageClients)
    }

    @Test
    fun noCapabilities_doesNotSeeAffordance() {
        assertFalse(
            emptyList<UserCapabilityResponse>()
                .hasCapability("EDIT_BRANCH_DATA", CapabilityContextType.GLOBAL, GLOBAL_CAPABILITY_CONTEXT_ID),
        )
    }

    @Test
    fun globalContextId_matchesBackendZeroUuid() {
        assertEquals("00000000-0000-0000-0000-000000000000", GLOBAL_CAPABILITY_CONTEXT_ID)
    }
}
