package com.companyb.companyapp.repository.model

object GrantPriorities {
    /**
     * Role-derived grants (V16 view union). Above the raw column default 0 so a
     * derived row never loses to an unspecified default; below every explicit
     * grant so an explicit direct grant always wins. Keep in sync with the
     * priority literal in V16__role_derived_global_capabilities.sql.
     */
    const val ROLE_DERIVED: Short = 5
    const val RELIEF_ACCESS: Short = 10
    const val MEDICAL_MISSION_DELEGATE: Short = 20
    const val DIRECT_GRANT: Short = 100
}
