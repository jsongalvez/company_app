package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.contracts.session.SessionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #405 — the mission ₱0 invariant's client predicate: only MEDICAL_MISSION locks the price. */
class MissionPricePolicyTest {
    @Test
    fun `only medical mission locks the price`() {
        assertTrue(missionPriceLocked(SessionType.MEDICAL_MISSION))
    }

    @Test
    fun `every non-mission type keeps the price editable`() {
        val nonMissionTypes = SessionType.entries - SessionType.MEDICAL_MISSION

        // #893 — #876 added the UNKNOWN forward-compat sentinel, the fifth non-mission
        // entry; it stays editable client-side (the server normalizes authoritatively
        // per #405), so only MEDICAL_MISSION locks the price.
        assertEquals(5, nonMissionTypes.size)
        nonMissionTypes.forEach { type -> assertFalse(missionPriceLocked(type)) }
    }
}
