package com.companyb.companyapp.proto.duotonesea

internal fun SeaRepo.actor(): String =
    currentUser.name
        .lowercase()
        .replace(" ", ".")
        .take(11)

internal fun SeaRepo.setSwimStatus(
    id: String,
    status: SwimStatus,
) {
    val index = swims.indexOfFirst { it.id == id }
    if (index < 0) return
    swims[index] = swims[index].copy(status = status)
    appendLog(actor(), "SESSION.${status.name}", "$id -> ${status.name}")
}

internal fun SeaRepo.voidSwim(
    id: String,
    reason: String,
) {
    val index = swims.indexOfFirst { it.id == id }
    if (index < 0) return
    swims[index] = swims[index].copy(voided = true, voidReason = reason)
    appendLog(actor(), "SESSION.VOID", "$id voided: $reason")
}

internal fun SeaRepo.unvoidSwim(id: String) {
    val index = swims.indexOfFirst { it.id == id }
    if (index < 0) return
    swims[index] = swims[index].copy(voided = false, voidReason = "")
    appendLog(actor(), "SESSION.UNVOID", "$id floated back to the board")
}

internal fun SeaRepo.openSwim(
    drifter: Drifter,
    walkIn: Boolean,
    service: String,
    price: Int,
): Boolean {
    if (swims.any { it.clientId == drifter.id && it.status == SwimStatus.PENDING && !it.voided }) return false
    val id = "s-${110 + swims.size}"
    swims.add(
        Swim(
            id,
            "15:30",
            drifter.id,
            drifter.name,
            walkIn,
            service,
            SwimStatus.PENDING,
            price,
            currentUser.name,
        ),
    )
    appendLog(actor(), "SESSION.CREATE", "$id opened for ${drifter.name}")
    return true
}

internal fun SeaRepo.anonymizeDrifter(id: String) {
    val index = drifters.indexOfFirst { it.id == id }
    if (index < 0) return
    drifters[index] =
        drifters[index].copy(
            name = "REDACTED ${drifters[index].id.uppercase()}",
            phone = "—",
            anonymized = true,
        )
    appendLog(actor(), "CLIENT.ANONYMIZE", "$id anonymized on request")
}

internal fun SeaRepo.markRead(id: String) {
    val index = buoy.indexOfFirst { it.id == id }
    if (index < 0) return
    if (!buoy[index].read) {
        buoy[index] = buoy[index].copy(read = true)
        appendLog(actor(), "NOTIFY.READ", "$id marked read")
    }
}

internal fun SeaRepo.markAllRead() {
    buoy.indices.forEach { buoy[it] = buoy[it].copy(read = true) }
    appendLog(actor(), "NOTIFY.READ_ALL", "buoy box drained")
}

internal fun SeaRepo.sealCatch(kind: String) {
    val total = if (kind == "SESSION") sessionNet() else productTotal()
    val id = "SC-${2402 + catches.size}"
    catches.add(SealedCatch(id, kind, "2026-09-09 17:40", total, true))
    appendLog(actor(), "REMITTANCE.SUBMIT", "$id $kind sealed at $total")
}

internal fun SeaRepo.breakSeal(
    id: String,
    reason: String,
) {
    val index = catches.indexOfFirst { it.id == id }
    if (index < 0) return
    catches.removeAt(index)
    appendLog(actor(), "REMITTANCE.UNDO", "$id reopened: $reason")
}

internal fun SeaRepo.grantRelief() {
    val index = crew.indexOfFirst { it.id == "u-relief" }
    if (index < 0) return
    crew[index] = crew[index].copy(clockedIn = true)
    appendLog(actor(), "RELIEF.GRANT", "P. Gomez granted edit at Cebu Crossing")
}

internal fun SeaRepo.acceptInvite() {
    reliefInviteOpen = false
    appendLog(actor(), "RELIEF.ACCEPT", "invite to Cebu Crossing accepted")
}

internal fun SeaRepo.declineInvite() {
    reliefInviteOpen = false
    appendLog(actor(), "RELIEF.DECLINE", "invite to Cebu Crossing declined")
}

internal fun SeaRepo.requestRelief() {
    reliefRequested = true
    buoy.add(
        0,
        BuoyMessage(
            "w-${10 + buoy.size}",
            "relief",
            "Relief request: ${currentUser.name}",
            "Cover for 2026-09-10 awaits a grant.",
            "2026-09-10",
            false,
        ),
    )
    appendLog(actor(), "RELIEF.REQUEST", "cover requested for 2026-09-10")
}

internal fun SeaRepo.visibleSwims(): List<Swim> =
    swims.filter {
        when (swimFilter) {
            "VOIDED" -> it.voided
            "ALL" -> true
            else -> it.status.name == swimFilter && !it.voided
        }
    }

internal fun SeaRepo.bumpSwim(delta: Int) {
    val rows = visibleSwims()
    if (rows.isEmpty()) return
    val current = rows.indexOfFirst { it.id == selectedSwimId }.takeIf { it >= 0 } ?: 0
    selectedSwimId = rows[(current + delta + rows.size) % rows.size].id
}
