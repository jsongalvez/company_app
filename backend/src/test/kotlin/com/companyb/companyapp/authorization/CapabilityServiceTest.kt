package com.companyb.companyapp.authorization

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class CapabilityServiceTest {
    @Test
    fun `GLOBAL_CONTEXT_ID is the nil UUID`() {
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000000"), CapabilityService.GLOBAL_CONTEXT_ID)
    }
}
