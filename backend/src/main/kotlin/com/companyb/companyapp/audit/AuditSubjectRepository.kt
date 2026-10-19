package com.companyb.companyapp.audit

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.commerce.ProductCategoryTable
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.identity.AppUserTable
import com.companyb.companyapp.session.ConcernTable
import com.companyb.companyapp.session.SessionTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Gives each change-history entry its context: which session, person, product or day the
 * change was about ([Context.subject]), and readable names for the ids inside its values
 * ([Context.names]).
 *
 * An UPDATE row only stores the changed fields, so the record's own INSERT row (same table and
 * record id) supplies the rest of its fields (who, which product, which day). Names come from
 * the live tables, so a removed client reads as "Removed client", never an old name.
 */
internal object AuditSubjectRepository {
    data class Context(
        val subject: String?,
        val names: Map<String, String>,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private const val UUID_LENGTH = 36

    fun describe(entries: List<AuditLogEntry>): Map<UUID, Context> {
        if (entries.isEmpty()) return emptyMap()
        return transaction {
            val inserted = insertedValues(entries.filter { it.action != AuditAction.INSERT })
            val fields =
                entries.associate { e ->
                    e.id to inserted[e.tableName to e.recordId].orEmpty() + parse(e.oldValue) + parse(e.newValue)
                }
            val ids = entries.flatMap { e -> idsIn(fields.getValue(e.id)) + e.recordId }.toSet()
            val names = Names.load(ids)
            // A remittance's own INSERT row describes it, so its lines and days can name it too.
            val remittances =
                insertedRows("remittance", ids).mapNotNull { (id, f) -> remittanceLabel(f)?.let { id to it } }.toMap()
            entries.associate { e ->
                val f = fields.getValue(e.id)
                val subject =
                    listOfNotNull(
                        uuidOrNull(f["remittanceId"])?.let(remittances::get),
                        Subject(e.recordId, f, names).of(e.tableName),
                    ).joinToString(" · ").ifEmpty { null }
                val shown =
                    (idsIn(f) + e.recordId).mapNotNull { id ->
                        (names.any(id) ?: remittances[id])?.let { id.toString() to it }
                    }
                e.id to Context(subject, shown.toMap())
            }
        }
    }

    private fun idsIn(fields: Map<String, String?>): List<UUID> = fields.values.mapNotNull(::uuidOrNull)

    private fun insertedValues(entries: List<AuditLogEntry>): Map<Pair<String, UUID>, Map<String, String?>> {
        if (entries.isEmpty()) return emptyMap()
        val recordIds = entries.map { it.recordId }.distinct()
        return AuditLogTable
            .selectAll()
            .where { (AuditLogTable.action eq AuditAction.INSERT) and (AuditLogTable.recordId inList recordIds) }
            .associate {
                (it[AuditLogTable.auditTableName] to it[AuditLogTable.recordId]) to parse(it[AuditLogTable.newValue])
            }
    }

    private fun insertedRows(
        table: String,
        ids: Set<UUID>,
    ): Map<UUID, Map<String, String?>> =
        AuditLogTable
            .selectAll()
            .where {
                (AuditLogTable.action eq AuditAction.INSERT) and (AuditLogTable.auditTableName eq table) and
                    (AuditLogTable.recordId inList ids.toList())
            }.associate { it[AuditLogTable.recordId] to parse(it[AuditLogTable.newValue]) }

    private fun remittanceLabel(f: Map<String, String?>): String? =
        listOfNotNull(
            f["type"]?.let { "${words(it)} remittance" },
            range(f["dateRangeStart"], f["dateRangeEnd"]),
        ).joinToString(", ").ifEmpty { null }

    private fun parse(raw: String?): Map<String, String?> =
        runCatching {
            (json.parseToJsonElement(raw ?: return emptyMap()) as? JsonObject)
                ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
        }.getOrDefault(emptyMap())

    private fun uuidOrNull(v: String?): UUID? =
        if (v != null && v.length == UUID_LENGTH) runCatching { UUID.fromString(v) }.getOrNull() else null

    private fun words(v: String) = v.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun range(
        from: String?,
        to: String?,
    ): String? {
        fun day(v: String?) = v?.let { runCatching { LocalDate.parse(it).format(dayFormat) }.getOrNull() }
        return listOfNotNull(day(from), day(to)).distinct().joinToString(" – ").ifEmpty { null }
    }

    /** One entry's subject, built from its fields; one line per audited table. */
    private class Subject(
        val recordId: UUID,
        val f: Map<String, String?>,
        val n: Names,
    ) {
        fun id(key: String) = uuidOrNull(f[key])

        fun name(
            kind: Kind,
            key: String,
        ) = id(key)?.let { n[kind, it] }

        fun own(kind: Kind) = n[kind, recordId]

        fun person(key: String) = name(Kind.USER, key)

        fun day() = name(Kind.DAY, "branchDayId")

        fun branch() = name(Kind.BRANCH, "branchId")

        fun session() = name(Kind.SESSION, "sessionId")?.let { "Session: $it" }

        fun product() = f["productName"] ?: name(Kind.PRODUCT, "productId")

        fun join(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ").ifEmpty { null }

        fun of(table: String): String? = RULES[table]?.invoke(this)

        companion object {
            val RULES: Map<String, Subject.() -> String?> =
                mapOf(
                    "session" to { own(Kind.SESSION) },
                    "session_concern" to { join(session(), name(Kind.CONCERN, "concernId")) },
                    "session_practitioner" to { join(session(), person("practitionerId")) },
                    "session_void" to { session() },
                    "commission_manual_inclusion" to { session() ?: join(product(), day()) },
                    "client" to { own(Kind.CLIENT) },
                    "app_user" to { own(Kind.USER) },
                    "user_role" to { own(Kind.USER) ?: person("userId") },
                    "credential_token" to { person("userId") },
                    "attendance" to { join(person("userId"), day()) },
                    "allowance" to { join(person("userId"), day()) },
                    "compensation" to { join(person("userId"), day() ?: name(Kind.DAY, "workBranchDayId")) },
                    "user_branch_assignment" to { join(person("userId"), branch()) },
                    "branch" to { own(Kind.BRANCH) ?: f["name"] },
                    "branch_day" to { own(Kind.DAY) },
                    "product" to { own(Kind.PRODUCT) ?: f["name"] },
                    "product_category" to { own(Kind.CATEGORY) ?: f["name"] },
                    "concern" to { own(Kind.CONCERN) ?: f["label"] },
                    "branch_inventory" to { join(product(), branch()) },
                    "inventory_movement" to { join(product(), day()) },
                    "product_sale" to { join(product(), session() ?: day()) },
                    "expense" to { join(f["category"]?.let(::words), f["amount"], day()) },
                    "relief_invite" to { join(person("invitee"), day()) },
                    "grant_relief_access" to { join(person("requestedBy"), day()) },
                    "medical_mission_delegate" to { join(person("userId") ?: person("delegateId"), day()) },
                    "remittance" to { remittanceLabel(f) },
                    "remittance_day_breakdown" to { day() },
                    "remittance_line" to { session() ?: product() },
                    "visit_booking" to
                        {
                            join(
                                "Booked visit",
                                name(Kind.CLIENT, "clientId"),
                                f["visitDate"]?.let { range(it, null) },
                            )
                        },
                    "session_base_rate" to { join(branch(), f["sessionType"]?.let(::words)) },
                )
        }
    }

    private enum class Kind { USER, BRANCH, DAY, CLIENT, PRODUCT, CATEGORY, CONCERN, SESSION }

    /** Readable names for every id the page shows, loaded in one query per kind. */
    private class Names(
        private val byKind: Map<Kind, Map<UUID, String>>,
    ) {
        operator fun get(
            kind: Kind,
            id: UUID,
        ): String? = byKind[kind]?.get(id)

        fun any(id: UUID): String? = Kind.entries.firstNotNullOfOrNull { byKind[it]?.get(id) }

        companion object {
            fun clientName(
                first: String?,
                last: String?,
            ) = listOfNotNull(first, last).joinToString(" ").ifBlank { "Removed client" }

            fun load(ids: Set<UUID>): Names {
                val list = ids.toList()
                return Names(
                    mapOf(
                        Kind.USER to
                            AppUserTable.selectAll().where { AppUserTable.id inList list }.associate {
                                it[AppUserTable.id] to it[AppUserTable.displayName]
                            },
                        Kind.BRANCH to
                            BranchTable.selectAll().where { BranchTable.id inList list }.associate {
                                it[BranchTable.id] to it[BranchTable.name]
                            },
                        Kind.DAY to days(list),
                        Kind.CLIENT to
                            ClientTable.selectAll().where { ClientTable.id inList list }.associate {
                                it[ClientTable.id] to clientName(it[ClientTable.firstName], it[ClientTable.lastName])
                            },
                        Kind.PRODUCT to
                            ProductTable.selectAll().where { ProductTable.id inList list }.associate {
                                it[ProductTable.id] to it[ProductTable.name]
                            },
                        Kind.CATEGORY to
                            ProductCategoryTable.selectAll().where { ProductCategoryTable.id inList list }.associate {
                                it[ProductCategoryTable.id] to it[ProductCategoryTable.name]
                            },
                        Kind.CONCERN to
                            ConcernTable.selectAll().where { ConcernTable.id inList list }.associate {
                                it[ConcernTable.id] to it[ConcernTable.label]
                            },
                        Kind.SESSION to sessions(list),
                    ),
                )
            }

            private fun days(list: List<UUID>): Map<UUID, String> =
                BranchDayTable
                    .leftJoin(BranchTable, { branchId }, { BranchTable.id })
                    .selectAll()
                    .where { BranchDayTable.id inList list }
                    .associate {
                        it[BranchDayTable.id] to "${it[BranchTable.name]}, ${it[BranchDayTable.date].format(dayFormat)}"
                    }

            private fun sessions(list: List<UUID>): Map<UUID, String> =
                SessionTable
                    .leftJoin(ClientTable, { clientId }, { ClientTable.id })
                    .leftJoin(BranchDayTable, { SessionTable.branchDayId }, { BranchDayTable.id })
                    .selectAll()
                    .where { SessionTable.id inList list }
                    .associate {
                        val who =
                            if (it[SessionTable.isWalkIn]) {
                                "Walk-in"
                            } else {
                                clientName(it[ClientTable.firstName], it[ClientTable.lastName])
                            }
                        it[SessionTable.id] to "$who, ${it[BranchDayTable.date].format(dayFormat)}"
                    }
        }
    }
}
