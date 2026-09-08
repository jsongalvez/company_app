package com.companyb.companyapp.architecture

import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Projection-grant pins (map #615 #608): cross-owner persistence reads need a
 * recorded file-level grant even inside `internal` types; FK declarations and
 * `tableName` metadata need none; view writes fail even for the owner; stale
 * grants fail. Lives here instead of `SemanticOwnershipArchitectureTest` so
 * that class stays under its `LargeClass` pin.
 */
class ProjectionArchitectureTest {
    private fun parse(source: String): KtFile = BackendArchitectureOwners.parseKt(source)

    private fun grant(
        file: String,
        table: String,
    ): BackendArchitectureOwners.ProjectionGrant = BackendArchitectureOwners.ProjectionGrant(file, table)

    @Test
    fun `fixture - internal foreign-query helper without a grant fails`() {
        val helper =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable

            internal object SessionProjection {
                fun read() = ClientTable.selectAll()
            }
            """.trimIndent()
        val owners = mapOf("ClientTable" to "client", "SessionTable" to "session")
        assertEquals(
            listOf("ClientTable"),
            BackendArchitectureOwners.foreignTableReads("session/Helper.kt", "session", parse(helper), owners),
        )
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignTableReads(
                "session/Helper.kt",
                "session",
                parse(helper),
                owners,
                setOf(grant("session/Helper.kt", "ClientTable")),
            ),
        )
    }

    @Test
    fun `fixture - aliased and qualified foreign view queries fail without a grant`() {
        val aliased =
            """
            package com.companyb.companyapp.notification
            import com.companyb.companyapp.session.ActiveSessionVoidsView as Voids

            internal object Sweep {
                fun read() = Voids.selectAll()
            }
            """.trimIndent()
        val qualified =
            """
            package com.companyb.companyapp.notification

            internal object Sweep {
                fun read() = com.companyb.companyapp.session.ActiveSessionVoidsView.selectAll()
            }
            """.trimIndent()
        val owners = mapOf("ActiveSessionVoidsView" to "session")
        assertEquals(
            listOf("ActiveSessionVoidsView"),
            BackendArchitectureOwners.foreignTableReads(
                "notification/Sweep.kt",
                "notification",
                parse(aliased),
                owners,
            ),
        )
        assertEquals(
            listOf("ActiveSessionVoidsView"),
            BackendArchitectureOwners.foreignTableReads(
                "notification/Sweep.kt",
                "notification",
                parse(qualified),
                owners,
            ),
        )
    }

    @Test
    fun `fixture - foreign write fails despite a read grant and view writes fail for the owner`() {
        val writer =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable

            internal object SessionWriter {
                fun write() = ClientTable.insert { }
            }
            """.trimIndent()
        val owners = mapOf("ClientTable" to "client")
        // A read grant never permits a write: the read side passes but the write side still reports.
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignTableReads(
                "session/Writer.kt",
                "session",
                parse(writer),
                owners,
                setOf(grant("session/Writer.kt", "ClientTable")),
            ),
        )
        assertEquals(
            listOf("ClientTable.insert"),
            BackendArchitectureOwners.tableWriteOps(parse(writer), setOf("ClientTable")),
        )
        assertTrue(owners["ClientTable"] != "session", "the write stays foreign-owned")
        val ownerViewWrite =
            """
            package com.companyb.companyapp.reporting
            import com.companyb.companyapp.reporting.DailySalesSummaryView

            internal object SummaryStore {
                fun rewrite() = DailySalesSummaryView.update({ true }) { }
                fun wipe() = DailySalesSummaryView.deleteAll()
            }
            """.trimIndent()
        assertEquals(
            listOf("DailySalesSummaryView.update", "DailySalesSummaryView.deleteAll"),
            BackendArchitectureOwners.viewWriteOps(parse(ownerViewWrite), setOf("DailySalesSummaryView")),
        )
    }

    @Test
    fun `fixture - granted join and same-owner reads pass`() {
        val owners =
            mapOf(
                "AttendanceTable" to "workforce",
                "BranchDayTable" to "branchday",
                "SessionTable" to "session",
            )
        val grantedJoin =
            """
            package com.companyb.companyapp.identity
            import com.companyb.companyapp.branchday.BranchDayTable
            import com.companyb.companyapp.workforce.AttendanceTable

            internal object MeProjection {
                fun read() =
                    AttendanceTable
                        .innerJoin(BranchDayTable, { AttendanceTable.branchDayId }, { BranchDayTable.id })
                        .selectAll()
            }
            """.trimIndent()
        assertEquals(
            listOf("AttendanceTable", "BranchDayTable"),
            BackendArchitectureOwners.foreignTableReads(
                "identity/MeProjection.kt",
                "identity",
                parse(grantedJoin),
                owners,
            ),
        )
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignTableReads(
                "identity/MeProjection.kt",
                "identity",
                parse(grantedJoin),
                owners,
                setOf(
                    grant("identity/MeProjection.kt", "AttendanceTable"),
                    grant("identity/MeProjection.kt", "BranchDayTable"),
                ),
            ),
        )
        val sameOwner =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.session.SessionTable

            internal object SessionStore {
                fun read() = SessionTable.selectAll()
            }
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignTableReads("session/Store.kt", "session", parse(sameOwner), owners),
        )
    }

    @Test
    fun `fixture - mapping bodies hide only fk edges`() {
        val owners = mapOf("ClientTable" to "client")
        val rogueInMapping =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable
            import org.jetbrains.exposed.v1.core.Table

            internal object SessionTable : Table("session") {
                val clientId = uuid("client_id").references(ClientTable.id)

                fun rogue() = ClientTable.selectAll()
            }
            """.trimIndent()
        assertEquals(
            listOf("ClientTable"),
            BackendArchitectureOwners.foreignTableReads("session/Session.kt", "session", parse(rogueInMapping), owners),
        )
        val fakeHelper =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable

            internal object HelperTable {
                fun read() = ClientTable.selectAll()
            }
            """.trimIndent()
        assertEquals(
            listOf("ClientTable"),
            BackendArchitectureOwners.foreignTableReads("session/Helper.kt", "session", parse(fakeHelper), owners),
        )
    }

    @Test
    fun `fixture - join-receiver writes resolve target tables`() {
        val joinDelete =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable
            import com.companyb.companyapp.session.SessionTable

            internal object Sweeper {
                fun wipe() =
                    SessionTable
                        .innerJoin(ClientTable, { SessionTable.clientId }, { ClientTable.id })
                        .delete { }
            }
            """.trimIndent()
        assertEquals(
            listOf("SessionTable.delete", "ClientTable.delete"),
            BackendArchitectureOwners.tableWriteOps(parse(joinDelete), setOf("SessionTable", "ClientTable")),
        )
    }

    @Test
    fun `fixture - wildcard top-level and qualified tableName behave`() {
        val owners = mapOf("ClientTable" to "client")
        val wildcard =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.*

            internal object Reader {
                fun read() = ClientTable.selectAll()
            }
            """.trimIndent()
        assertEquals(
            listOf("ClientTable"),
            BackendArchitectureOwners.foreignTableReads("session/Reader.kt", "session", parse(wildcard), owners),
        )
        val topLevel =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable

            internal fun readClients() = ClientTable.selectAll()
            """.trimIndent()
        assertEquals(
            listOf("ClientTable"),
            BackendArchitectureOwners.foreignTableReads("session/Reads.kt", "session", parse(topLevel), owners),
        )
        val qualifiedMeta =
            """
            package com.companyb.companyapp.audit

            internal object Scrub {
                val name = com.companyb.companyapp.client.ClientTable.tableName
            }
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignTableReads("audit/Scrub.kt", "audit", parse(qualifiedMeta), owners),
        )
    }

    @Test
    fun `fixture - fk declarations tableName and prose pass`() {
        val owners = mapOf("ClientTable" to "client")
        val fk =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable
            import org.jetbrains.exposed.v1.core.Table

            internal object SessionTable : Table("session") {
                val clientId = uuid("client_id").references(ClientTable.id)
            }
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignTableReads("session/Session.kt", "session", parse(fk), owners),
        )
        val prose =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable

            internal object Seam {
                val t = ClientTable.tableName
            }

            object Svc {
                fun go(): String = "ClientTable.selectAll()"
            }
            // ClientTable in prose must not count
            /* ClientTable in a block must not count */
            """.trimIndent()
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.foreignTableReads("session/Svc.kt", "session", parse(prose), owners),
        )
    }

    @Test
    fun `fixture - removed and renamed projections leave no stale grant unnoticed`() {
        val live =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable

            internal object Live {
                fun read() = ClientTable.selectAll()
            }
            """.trimIndent()
        val renamed =
            """
            package com.companyb.companyapp.session
            import com.companyb.companyapp.client.ClientTable

            internal object Renamed {
                fun read() = ClientTable.selectAll()
            }
            """.trimIndent()
        val tree =
            mapOf(
                "session/Live.kt" to parse(live),
                "session/Renamed.kt" to parse(renamed),
            )
        val owners = mapOf("ClientTable" to "client")
        val allowed = setOf(grant("session/Live.kt", "ClientTable"), grant("session/Old.kt", "ClientTable"))
        assertEquals(
            listOf("session/Old.kt: ClientTable"),
            BackendArchitectureOwners.unusedProjectionGrants(tree, owners, allowed),
        )
        assertEquals(
            listOf("ClientTable"),
            BackendArchitectureOwners.foreignTableReads("session/Renamed.kt", "session", parse(renamed), owners),
        )
        assertEquals(
            emptyList(),
            BackendArchitectureOwners.unusedProjectionGrants(
                tree,
                owners,
                setOf(grant("session/Live.kt", "ClientTable"), grant("session/Renamed.kt", "ClientTable")),
            ),
        )
    }
}
