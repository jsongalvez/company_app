package com.companyb.companyapp.state

import com.companyb.companyapp.dto.MeResponse
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object SessionState {
    private val _currentUser = MutableStateFlow<MeResponse?>(null)
    val currentUser: StateFlow<MeResponse?> = _currentUser.asStateFlow()

    private val _capabilities = MutableStateFlow<Set<String>>(emptySet())
    val capabilities: StateFlow<Set<String>> = _capabilities.asStateFlow()

    private val _selectedBranchId = MutableStateFlow<String?>(null)
    val selectedBranchId: StateFlow<String?> = _selectedBranchId.asStateFlow()

    private val _selectedBranchName = MutableStateFlow<String?>(null)
    val selectedBranchName: StateFlow<String?> = _selectedBranchName.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> =
        currentUser
            .map { it != null }
            .stateIn(GlobalScope, SharingStarted.Eagerly, false)

    fun setUser(user: MeResponse) {
        _currentUser.value = user
    }

    fun setCapabilities(caps: Set<String>) {
        _capabilities.value = caps
    }

    fun setSelectedBranch(
        id: String,
        name: String,
    ) {
        _selectedBranchId.value = id
        _selectedBranchName.value = name
    }

    fun clear() {
        _currentUser.value = null
        _capabilities.value = emptySet()
        _selectedBranchId.value = null
        _selectedBranchName.value = null
    }
}
