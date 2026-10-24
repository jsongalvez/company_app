package com.companyb.companyapp.session

import com.companyb.companyapp.exception.NotFoundException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Catalog concern reads: the shared concern catalog listing stays distinct
 * from session-concern mutation ([SessionConcernService]) while colocated in the session owner.
 */
object ConcernService {
    fun listAll(): List<Concern> = ConcernRepository.findAll()

    /** Takes a bad entry off the shared list (audited). Past sessions keep the label. */
    fun removeFromList(
        callerId: UUID,
        concernId: UUID,
    ) {
        transaction {
            val concern =
                ConcernRepository.findByIdInTransaction(concernId)
                    ?: throw NotFoundException("Illness not found")
            if (ConcernRepository.removeFromListInTransaction(concernId)) {
                SessionConcernAudit.concernRemovedFromList(callerId, concern)
            }
        }
    }
}
