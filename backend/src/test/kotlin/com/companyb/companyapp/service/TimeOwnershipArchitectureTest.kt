package com.companyb.companyapp.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #322 time-ownership seam. Cheap source-level assertions that business time keeps its explicit
 * owners: the 04:00 Asia/Manila operational-day boundary and the Manila zone live only in
 * [BranchDayService] (#318), and every remaining direct JVM-clock read in main source is one of
 * the intentional uses recorded on #322 (auth token lifecycle, injected scheduler clocks, the
 * Branch Day convenience overload). Persistence time stays on the database clock
 * (`CurrentTimestampWithTimeZone`, `now()` in views/functions) and is not scanned here.
 *
 * Mechanical enforcement for broader dependency rules arrives with #324; this pins the sweep.
 */
class TimeOwnershipArchitectureTest {
    private fun walkMainSources(): List<String> =
        File("backend/src/main/kotlin")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .map { it.readText() }
            .toList()

    @Test
    fun `the Manila zone is constructed only by Branch Day`() {
        val offenders =
            walkMainSources()
                .withIndex()
                .filter { it.value.contains("""ZoneId.of("Asia/Manila")""") }
                .map { it.index }
        assertEquals(1, offenders.size, "BranchDayService must be the sole ZoneId.of(\"Asia/Manila\") site")
    }

    @Test
    fun `no consumer derives a calendar today independently`() {
        val offenders = walkMainSources().filter { it.contains("LocalDate.now") || it.contains("OffsetDateTime.now") }
        assertTrue(offenders.isEmpty(), "LocalDate.now/OffsetDateTime.now must not appear in main source: $offenders")
    }

    @Test
    fun `direct Instant reads are limited to the recorded owners`() {
        // Auth token lifecycle owns its own validity clock (invite/reset expiry, #350/#353);
        // Branch Day's overload IS the authority.
        val allowedFiles =
            setOf(
                "backend/src/main/kotlin/com/companyb/companyapp/auth/DenyList.kt",
                "backend/src/main/kotlin/com/companyb/companyapp/auth/JwtService.kt",
                "backend/src/main/kotlin/com/companyb/companyapp/service/AuthService.kt",
                "backend/src/main/kotlin/com/companyb/companyapp/service/UserService.kt",
                "backend/src/main/kotlin/com/companyb/companyapp/service/branchday/BranchDayService.kt",
            )
        val offenders =
            File("backend/src/main/kotlin")
                .walkTopDown()
                .filter { it.extension == "kt" && it.readText().contains("Instant.now()") }
                .map { it.path }
                .filterNot { it in allowedFiles }
                .toList()
        assertTrue(
            offenders.isEmpty(),
            "unrecorded Instant.now() sites: $offenders — record an exception or delegate to the owner",
        )
    }

    @Test
    fun `scheduler reads the operational day through the Branch Day boundary`() {
        val scheduler =
            File(
                "backend/src/main/kotlin/com/companyb/companyapp/service/NextAppointmentScheduler.kt",
            ).readText()
        assertTrue(scheduler.contains("ZonedDateTime.now(clock)"), "scheduler reads only its injected clock")
        assertTrue(
            scheduler.contains("BranchDayService.currentOperationalDate("),
            "run date comes from the operational-day authority",
        )
        assertTrue(
            !scheduler.contains(".toLocalDate()"),
            "scheduler must not derive a calendar date from the wall clock",
        )
    }
}
