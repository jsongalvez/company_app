package com.companyb.companyapp.authorization

object GrantPriorities {
    /**
     * Role-derived grants (active_user_capabilities view union). Above the raw
     * column default 0 so a derived row never loses to an unspecified default;
     * below every explicit grant so an explicit direct grant always wins. Keep
     * in sync with the priority literal in V1__full_schema.sql.
     */
    const val ROLE_DERIVED: Short = 5
    const val RELIEF_ACCESS: Short = 10
    const val MEDICAL_MISSION_DELEGATE: Short = 20
    const val DIRECT_GRANT: Short = 100
}
