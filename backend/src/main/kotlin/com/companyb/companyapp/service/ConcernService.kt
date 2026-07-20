package com.companyb.companyapp.service

import com.companyb.companyapp.repository.ConcernRepository
import com.companyb.companyapp.repository.model.Concern

object ConcernService {
    fun listAll(): List<Concern> = ConcernRepository.findAll()
}
