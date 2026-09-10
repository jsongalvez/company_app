package com.companyb.companyapp.proto.lunchrush

internal fun LunchRushRepo.actor(): String =
    currentUser.name
        .lowercase()
        .replace(" ", ".")
        .take(9)

internal fun LunchRushRepo.setStatus(
    id: String,
    status: RushSessionStatus,
) {
    val index = sessions.indexOfFirst { it.id == id }
    if (index < 0) return
    sessions[index] = sessions[index].copy(status = status)
    audit(actor(), "SESSION.${status.name}", "$id -> ${status.name}")
}

internal fun LunchRushRepo.voidSession(
    id: String,
    reason: String,
) {
    val index = sessions.indexOfFirst { it.id == id }
    if (index < 0) return
    sessions[index] = sessions[index].copy(voided = true, voidReason = reason)
    audit(actor(), "SESSION.VOID", "$id voided: $reason")
}

internal fun LunchRushRepo.unvoidSession(id: String) {
    val index = sessions.indexOfFirst { it.id == id }
    if (index < 0) return
    sessions[index] = sessions[index].copy(voided = false, voidReason = "")
    audit(actor(), "SESSION.UNVOID", "$id back on the ticket rail")
}

internal fun LunchRushRepo.createSession(
    clientName: String,
    walkIn: Boolean,
    service: String,
) {
    val id = "s-${210 + sessions.size}"
    val clientId = clients.firstOrNull { it.name == clientName }?.id ?: "c-walk"
    sessions.add(
        RushSession(id, "13:30", clientId, clientName, walkIn, service, RushSessionStatus.PENDING, 900, currentUser.name),
    )
    audit(actor(), "SESSION.CREATE", "$id booked for $clientName")
}

internal fun LunchRushRepo.anonymizeClient(id: String) {
    val index = clients.indexOfFirst { it.id == id }
    if (index < 0) return
    clients[index] =
        clients[index].copy(name = "REGULAR-${id.takeLast(2).uppercase()}", phone = "withheld", anonymized = true)
    audit(actor(), "CLIENT.ANONYMIZE", "$id PII nullified, gender/age kept")
}

internal fun LunchRushRepo.markNoticeRead(id: String) {
    val notice = notices.firstOrNull { it.id == id } ?: return
    if (!notice.read) {
        notice.read = true
        audit(actor(), "NOTICE.READ", "$id acknowledged")
    }
}

internal fun LunchRushRepo.markAllNoticesRead() {
    notices.forEach { it.read = true }
    audit(actor(), "NOTICE.READ_ALL", "pass rail drained")
}

internal fun LunchRushRepo.submitRemittance(kind: String) {
    val total = if (kind == "SESSION") sessionNet() else productTotal()
    val id = "SR-${2402 + snapshots.size}"
    snapshots.add(0, RushSnapshot(id, kind, "2026-09-09 12:45", total, true))
    audit(actor(), "REMITTANCE.SUBMIT", "$id $kind locked at $total")
}

internal fun LunchRushRepo.undoRemittance(
    id: String,
    reason: String,
) {
    val index = snapshots.indexOfFirst { it.id == id }
    if (index < 0) return
    snapshots.removeAt(index)
    audit(actor(), "REMITTANCE.UNDO", "$id reopened: $reason")
}

internal fun LunchRushRepo.addProductLine(
    name: String,
    qty: Int,
    unit: Int,
) {
    productLines.add(RushProductLine("p-${productLines.size + 1}", name, qty, unit))
    audit(actor(), "REMITTANCE.DRAFT", "PRODUCT line added: $name x$qty")
}

internal fun LunchRushRepo.toggleClock() {
    clockedIn = !clockedIn
    val verb = if (clockedIn) "CLOCK_IN" else "CLOCK_OUT"
    audit(actor(), verb, "${currentUser.name} $verb at ${currentBranch.name}")
}

internal fun LunchRushRepo.grantRelief(id: String) {
    val index = staff.indexOfFirst { it.id == id }
    if (index < 0) return
    staff[index] = staff[index].copy(clockedIn = true)
    audit(actor(), "RELIEF.GRANT", "${staff[index].name} called onto the floor")
}

internal fun LunchRushRepo.callRelief() {
    reliefCallSent = true
    val target = notices.firstOrNull { it.kind == "RELIEF" }
    if (target != null) target.read = true
    audit(actor(), "RELIEF.CALL", "relief call rung for 12:30-15:00 cover")
}

internal fun LunchRushRepo.logout() {
    audit(actor(), "AUTH.LOGOUT", "${currentUser.name} hung up the apron")
    authed = false
    branchPicked = false
}
