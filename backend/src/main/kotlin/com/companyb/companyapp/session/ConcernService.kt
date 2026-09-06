package com.companyb.companyapp.session

/**
 * Catalog concern reads (map #533 #542): the shared concern catalog listing stays distinct
 * from session-concern mutation ([SessionConcernService]) while colocated in the session owner.
 */
object ConcernService {
    fun listAll(): List<Concern> = ConcernRepository.findAll()
}
