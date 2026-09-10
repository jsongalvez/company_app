package com.companyb.companyapp.proto.noirdetective

internal fun NoirRepo.actor(): String =
    currentUser.name
        .lowercase()
        .replace(" ", ".")
        .take(11)

internal fun NoirRepo.setCaseStatus(
    id: String,
    status: CaseStatus,
) {
    val index = cases.indexOfFirst { it.id == id }
    if (index < 0) return
    cases[index] = cases[index].copy(status = status)
    appendLog(actor(), "SESSION.${status.name}", "$id -> ${status.name}")
}

internal fun NoirRepo.voidCase(
    id: String,
    reason: String,
) {
    val index = cases.indexOfFirst { it.id == id }
    if (index < 0) return
    cases[index] = cases[index].copy(voided = true, voidReason = reason)
    appendLog(actor(), "SESSION.VOID", "$id voided: $reason")
}

internal fun NoirRepo.unvoidCase(id: String) {
    val index = cases.indexOfFirst { it.id == id }
    if (index < 0) return
    cases[index] = cases[index].copy(voided = false, voidReason = "")
    appendLog(actor(), "SESSION.UNVOID", "$id restored to the board")
}

internal fun NoirRepo.openCase(
    person: Person,
    walkIn: Boolean,
    service: String,
    price: Int,
) {
    val id = "c-${110 + cases.size}"
    cases.add(
        CaseFile(
            id,
            "15:30",
            person.id,
            person.name,
            walkIn,
            service,
            CaseStatus.PENDING,
            price,
            currentUser.name,
        ),
    )
    appendLog(actor(), "SESSION.CREATE", "$id opened for ${person.name}")
}

internal fun NoirRepo.anonymizePerson(id: String) {
    val index = persons.indexOfFirst { it.id == id }
    if (index < 0) return
    persons[index] =
        persons[index].copy(
            name = "REDACTED ${persons[index].id.uppercase()}",
            phone = "—",
            anonymized = true,
        )
    appendLog(actor(), "CLIENT.ANONYMIZE", "$id anonymized on request")
}

internal fun NoirRepo.markRead(id: String) {
    val index = wire.indexOfFirst { it.id == id }
    if (index < 0) return
    if (!wire[index].read) {
        wire[index] = wire[index].copy(read = true)
        appendLog(actor(), "NOTIFY.READ", "$id marked read")
    }
}

internal fun NoirRepo.markAllRead() {
    wire.indices.forEach { wire[it] = wire[it].copy(read = true) }
    appendLog(actor(), "NOTIFY.READ_ALL", "mailbox drained")
}

internal fun NoirRepo.sealEnvelope(kind: String) {
    val total = if (kind == "SESSION") sessionNet() else productTotal()
    val id = "SR-${2402 + envelopes.size}"
    envelopes.add(SealedEnvelope(id, kind, "2026-09-09 17:40", total, true))
    appendLog(actor(), "REMITTANCE.SUBMIT", "$id $kind sealed at $total")
}

internal fun NoirRepo.breakSeal(
    id: String,
    reason: String,
) {
    val index = envelopes.indexOfFirst { it.id == id }
    if (index < 0) return
    envelopes.removeAt(index)
    appendLog(actor(), "REMITTANCE.UNDO", "$id reopened: $reason")
}

internal fun NoirRepo.grantRelief() {
    val index = squad.indexOfFirst { it.id == "u-relief" }
    if (index < 0) return
    squad[index] = squad[index].copy(clockedIn = true)
    appendLog(actor(), "RELIEF.GRANT", "P. Gomez granted edit at Cebu Stakeout")
}

internal fun NoirRepo.visibleCases(): List<CaseFile> =
    cases.filter {
        when (caseFilter) {
            "VOIDED" -> it.voided
            "ALL" -> true
            else -> it.status.name == caseFilter && !it.voided
        }
    }

internal fun NoirRepo.bumpCase(delta: Int) {
    val rows = visibleCases()
    if (rows.isEmpty()) return
    val current = rows.indexOfFirst { it.id == selectedCaseId }.takeIf { it >= 0 } ?: 0
    selectedCaseId = rows[(current + delta + rows.size) % rows.size].id
}
