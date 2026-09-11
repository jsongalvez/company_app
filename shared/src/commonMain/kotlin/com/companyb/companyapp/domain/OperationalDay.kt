package com.companyb.companyapp.domain

/**
 * Single owner of the operational-day boundary rule (#875): branch days roll over at 04:00
 * Asia/Manila, not at calendar midnight.
 *
 * The rule stays portable on purpose — a zone-id [String], an hour [Int], and a pure function
 * over caller-supplied Manila calendar fields — so neither `java.time` nor `kotlinx.datetime`
 * leaks into `commonMain`. Each platform adapts at its own seam: backend `BranchDayService`
 * (java.time) and client `ReliefInviteLogic` (kotlinx.datetime) both delegate here, and the
 * display-only zone wrappers (`RemittanceUi.ManilaZone`, `TimestampFormat`, backend
 * `BranchDayService.manilaZone`) build from [MANILA_ZONE_ID].
 */
object OperationalDay {
    /** IANA zone id every business-day calculation anchors to. */
    const val MANILA_ZONE_ID = "Asia/Manila"

    /** Operational days roll over at this Manila wall-clock hour. */
    const val DAY_BOUNDARY_HOUR = 4

    /** True while a Manila wall-clock hour still belongs to the previous business day. */
    fun isBeforeCutoff(manilaHour: Int): Boolean = manilaHour < DAY_BOUNDARY_HOUR

    /**
     * Pure operational-date rule: the business day owning a Manila calendar instant.
     *
     * @param manilaHour hour-of-day (0-23) in Asia/Manila.
     * @param manilaCalendarEpochDay Manila calendar date as days since the epoch.
     * @return the owning business date in the same representation (the previous calendar day
     * while before the cutoff — e.g. 03:59 on Jun-27 owns Jun-26; 04:00 owns Jun-27).
     */
    fun operationalEpochDay(
        manilaHour: Int,
        manilaCalendarEpochDay: Long,
    ): Long =
        if (isBeforeCutoff(manilaHour)) manilaCalendarEpochDay - 1 else manilaCalendarEpochDay
}
