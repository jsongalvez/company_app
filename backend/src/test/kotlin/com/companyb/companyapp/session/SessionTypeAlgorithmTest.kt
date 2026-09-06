package com.companyb.companyapp.session

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.session.SessionService
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTypeAlgorithmTest {
    @Test
    fun `medical mission branch always returns MEDICAL_MISSION regardless of count`() {
        assertEquals(SessionType.MEDICAL_MISSION, SessionService.computeSessionType(BranchType.MEDICAL_MISSION, 0L))
        assertEquals(SessionType.MEDICAL_MISSION, SessionService.computeSessionType(BranchType.MEDICAL_MISSION, 1L))
        assertEquals(SessionType.MEDICAL_MISSION, SessionService.computeSessionType(BranchType.MEDICAL_MISSION, 5L))
    }

    @Test
    fun `provincial tour with zero prior sessions returns PROVINCIAL_FIRST`() {
        assertEquals(
            SessionType.PROVINCIAL_FIRST,
            SessionService.computeSessionType(BranchType.PROVINCIAL_TOUR, 0L),
        )
    }

    @Test
    fun `provincial tour with prior sessions uses regular count logic`() {
        assertEquals(
            SessionType.SECOND_SESSION,
            SessionService.computeSessionType(BranchType.PROVINCIAL_TOUR, 1L),
        )
        assertEquals(
            SessionType.SUBSEQUENT,
            SessionService.computeSessionType(BranchType.PROVINCIAL_TOUR, 2L),
        )
    }

    @Test
    fun `clinic with zero prior sessions returns REGULAR`() {
        assertEquals(SessionType.REGULAR, SessionService.computeSessionType(BranchType.CLINIC, 0L))
    }

    @Test
    fun `clinic with one prior session returns SECOND_SESSION`() {
        assertEquals(SessionType.SECOND_SESSION, SessionService.computeSessionType(BranchType.CLINIC, 1L))
    }

    @Test
    fun `clinic with two or more prior sessions returns SUBSEQUENT`() {
        assertEquals(SessionType.SUBSEQUENT, SessionService.computeSessionType(BranchType.CLINIC, 2L))
        assertEquals(SessionType.SUBSEQUENT, SessionService.computeSessionType(BranchType.CLINIC, 10L))
    }
}
