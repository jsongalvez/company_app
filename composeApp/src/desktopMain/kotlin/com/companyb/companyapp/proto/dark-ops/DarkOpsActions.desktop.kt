package com.companyb.companyapp.proto.darkops

internal fun DarkOpsRepo.actor(): String =
    currentUser.name
        .lowercase()
        .replace(" ", ".")
        .take(9)

internal fun DarkOpsRepo.setStatus(
    id: String,
    status: OpsSessionStatus,
) {
    val index = sessions.indexOfFirst { it.id == id }
    if (index < 0) return
    sessions[index] = sessions[index].copy(status = status)
    audit(actor(), "SESSION.${status.name}", "$id -> ${status.name}")
}

internal fun DarkOpsRepo.voidSession(
    id: String,
    reason: String,
) {
    val index = sessions.indexOfFirst { it.id == id }
    if (index < 0) return
    sessions[index] = sessions[index].copy(voided = true, voidReason = reason)
    audit(actor(), "SESSION.VOID", "$id voided: $reason")
}

internal fun DarkOpsRepo.unvoidSession(id: String) {
    val index = sessions.indexOfFirst { it.id == id }
    if (index < 0) return
    sessions[index] = sessions[index].copy(voided = false, voidReason = "")
    audit(actor(), "SESSION.UNVOID", "$id restored to roster")
}

internal fun DarkOpsRepo.createSession(
    client: OpsClient,
    walkIn: Boolean,
    service: String,
    price: Int,
) {
    val id = "s-${110 + sessions.size}"
    sessions.add(
        OpsSession(
            id,
            "15:30",
            client.id,
            client.name,
            walkIn,
            service,
            OpsSessionStatus.PENDING,
            price,
            currentUser.name,
        ),
    )
    audit(actor(), "SESSION.CREATE", "$id booked for ${client.name}")
}

internal fun DarkOpsRepo.anonymizeClient(id: String) {
    val index = clients.indexOfFirst { it.id == id }
    if (index < 0) return
    clients[index] =
        clients[index].copy(name = "ANON-${id.takeLast(2).uppercase()}", phone = "withheld", anonymized = true)
    audit(actor(), "CLIENT.ANONYMIZE", "$id PII nullified, gender/age kept")
}

internal fun DarkOpsRepo.markNoticeRead(id: String) {
    val notice = notices.firstOrNull { it.id == id } ?: return
    if (!notice.read) {
        notice.read = true
        audit(actor(), "NOTICE.READ", "$id acknowledged")
    }
}

internal fun DarkOpsRepo.markAllNoticesRead() {
    notices.forEach { it.read = true }
    audit(actor(), "NOTICE.READ_ALL", "mailbox drained")
}

internal fun DarkOpsRepo.submitRemittance(kind: String) {
    val total = if (kind == "SESSION") sessionNet() else productTotal()
    val id = "SR-${2402 + snapshots.size}"
    snapshots.add(0, OpsSnapshot(id, kind, "2026-09-09 17:40", total, true))
    audit(actor(), "REMITTANCE.SUBMIT", "$id $kind locked at $total")
}

internal fun DarkOpsRepo.undoRemittance(
    id: String,
    reason: String,
) {
    val index = snapshots.indexOfFirst { it.id == id }
    if (index < 0) return
    snapshots.removeAt(index)
    audit(actor(), "REMITTANCE.UNDO", "$id reopened: $reason")
}

internal fun DarkOpsRepo.addProductLine(
    name: String,
    qty: Int,
    unit: Int,
) {
    productLines.add(OpsProductLine("p-${productLines.size + 1}", name, qty, unit))
    audit(actor(), "REMITTANCE.DRAFT", "PRODUCT line added: $name x$qty")
}

internal fun DarkOpsRepo.toggleClock() {
    clockedIn = !clockedIn
    val verb = if (clockedIn) "CLOCK_IN" else "CLOCK_OUT"
    audit(actor(), verb, "${currentUser.name} $verb at ${currentBranch.name}")
}

internal fun DarkOpsRepo.grantRelief(id: String) {
    val index = staff.indexOfFirst { it.id == id }
    if (index < 0) return
    staff[index] = staff[index].copy(clockedIn = true)
    audit(actor(), "RELIEF.GRANT", "${staff[index].name} granted edit at ${currentBranch.name}")
}

internal fun DarkOpsRepo.logout() {
    audit(actor(), "AUTH.LOGOUT", "${currentUser.name} signed out")
    authed = false
    branchPicked = false
}
