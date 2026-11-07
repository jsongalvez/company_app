package com.companyb.companyapp.authorization

import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.logging.RequestQueryTally
import com.companyb.companyapp.observability.traced
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.ResultSet
import java.util.UUID

/** Reads a person's [Facts] in one statement, once per request until a write to a table it reads. */
internal object FactsRepository {
    private data class FactsKey(
        val userId: UUID,
    ) : RequestQueryTally.ReadKey

    private val READS =
        setOf(
            "app_user",
            "branch",
            "medical_mission_delegate",
            "role",
            "user_branch_assignment",
            "user_capability",
            "user_role",
        )

    private val STAFF_ROLE_NAMES = STAFF_WITHOUT_ASSIGNMENT.keys.joinToString { "'${it.name}'" }

    private val SQL =
        """
        SELECT 'ROLE' AS kind, r.name AS label, NULL::uuid AS branch_id, NULL::uuid AS day_id
        FROM user_role ur JOIN role r ON r.id = ur.role_id WHERE ur.user_id = ?
        UNION ALL
        SELECT 'MEMBER', NULL, uba.branch_id, NULL FROM user_branch_assignment uba
        WHERE uba.user_id = ? AND uba.ended_at IS NULL
        UNION ALL
        SELECT 'HELPER', d.role, d.branch_id, NULL FROM medical_mission_delegate d
        WHERE d.target_user = ? AND d.ended_at IS NULL
        UNION ALL
        SELECT 'RELIEF', NULL, bd.branch_id, uc.context_id FROM user_capability uc
        JOIN branch_day bd ON bd.id = uc.context_id
        WHERE uc.user_id = ? AND uc.source_type = 'RELIEF_ACCESS' AND uc.context_type = 'BRANCH_DAY'
          AND now() BETWEEN uc.valid_from AND COALESCE(uc.valid_to, 'infinity'::timestamptz)
        UNION ALL
        SELECT 'BRANCH', b.branch_type::text, b.id, NULL FROM branch b
        WHERE EXISTS (
            SELECT 1 FROM user_role ur JOIN role r ON r.id = ur.role_id
            WHERE ur.user_id = ? AND r.name IN ($STAFF_ROLE_NAMES)
        )
        UNION ALL
        SELECT 'ACTIVE', NULL, NULL, NULL FROM app_user WHERE id = ? AND status = 'ACTIVE'
        """.trimIndent()

    private class Row(
        val kind: String,
        val label: String?,
        val branchId: UUID?,
        val dayId: UUID?,
    )

    fun factsOf(userId: UUID): Facts =
        traced("FactsRepository.factsOf") {
            RequestQueryTally.untilWrite(FactsKey(userId), Facts::class, READS) { transaction { load(userId) } }
        }

    private fun load(userId: UUID): Facts {
        val args: List<Pair<IColumnType<*>, Any?>> = List(SQL.count { it == '?' }) { UUIDColumnType() to userId }
        val rows = TransactionManager.current().exec(SQL, args) { rs -> rs.rows() }.orEmpty()
        if (rows.none { it.kind == "ACTIVE" }) return Facts.NONE
        val roles =
            rows
                .filter { it.kind == "ROLE" }
                .mapNotNull { row ->
                    Role.entries.find { it.name == row.label }
                }.toSet()
        val staffTypes = roles.flatMap { STAFF_WITHOUT_ASSIGNMENT[it].orEmpty() }.toSet()
        val footings =
            rows.mapNotNull { row ->
                val branch = row.branchId ?: return@mapNotNull null
                when (row.kind) {
                    "MEMBER" -> {
                        branch to Footing(Standing.MEMBER, roles)
                    }

                    "HELPER" -> {
                        branch to
                            Footing(Standing.HELPER, setOfNotNull(Role.entries.find { it.name == row.label }))
                    }

                    "BRANCH" -> {
                        (branch to Footing(Standing.MEMBER, roles)).takeIf {
                            BranchType.valueOf(row.label!!) in
                                staffTypes
                        }
                    }

                    else -> {
                        null
                    }
                }
            }
        return Facts(
            roles = roles,
            branches = footings.groupBy({ it.first }, { it.second }).mapValues { (_, list) -> list.distinct() },
            reliefDays = rows.filter { it.kind == "RELIEF" }.associate { it.dayId!! to it.branchId!! },
        )
    }

    private fun ResultSet.rows(): List<Row> =
        buildList {
            while (next()) {
                add(
                    Row(
                        getString("kind"),
                        getString("label"),
                        getObject("branch_id", UUID::class.java),
                        getObject("day_id", UUID::class.java),
                    ),
                )
            }
        }
}
