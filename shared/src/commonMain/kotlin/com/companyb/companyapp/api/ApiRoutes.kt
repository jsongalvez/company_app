package com.companyb.companyapp.api

// Route catalog intentionally centralizes many tiny path builders.
@Suppress("TooManyFunctions")
object ApiRoutes {
    const val API_PREFIX = "/api/"
    private const val API_ROOT = "/api"
    const val AUTH_LOGIN = "/auth/login"
    const val AUTH_ACCEPT_INVITE = "/auth/accept-invite"
    const val AUTH_FORGOT_PASSWORD = "/auth/forgot-password"
    const val AUTH_RESET_PASSWORD = "/auth/reset-password"
    const val AUTH_LOGOUT = "$API_ROOT/auth/logout"
    const val ME = "$API_ROOT/me"
    const val ME_BRANCHES = "$ME/branches"
    const val ME_CAPABILITIES = "$ME/capabilities"
    const val USERS = "$API_ROOT/users"
    const val INVITES = "$API_ROOT/invites"
    const val ROLES = "$API_ROOT/roles"
    const val BRANCHES = "$API_ROOT/branches"
    const val CLIENTS = "$API_ROOT/clients"
    const val SESSIONS = "$API_ROOT/sessions"
    const val CONCERNS = "$API_ROOT/concerns"
    const val PRODUCTS = "$API_ROOT/products"
    const val PRODUCT_CATEGORIES = "$API_ROOT/product-categories"
    const val PRODUCT_SALES = "$API_ROOT/product-sales"
    const val NOTIFICATIONS = "$API_ROOT/notifications"
    const val RELIEF_INVITES = "$API_ROOT/relief-invites"
    const val RELIEF_ACCESS = "$API_ROOT/relief-access"
    const val DELEGATES = "$API_ROOT/delegates"
    const val ATTENDANCE = "$API_ROOT/attendance"
    const val EXPENSES = "$API_ROOT/expenses"
    const val ALLOWANCES = "$API_ROOT/allowances"
    const val COMPENSATION = "$API_ROOT/compensation"
    const val COMPENSATIONS = "$API_ROOT/compensations"
    const val REMITTANCES = "$API_ROOT/remittances"
    const val COMMISSION_INCLUSIONS = "$API_ROOT/commission-inclusions"
    const val COMMISSION_SPLITS = "$API_ROOT/commission-splits"
    const val COMMISSION_RECALCULATE = "$API_ROOT/commission/recalculate"
    const val BRANCH_DAYS = "$API_ROOT/branch-days"
    const val AUDIT_LOG = "$API_ROOT/audit-log"
    const val HEALTH = "/health"
    const val BRANCHES_EXPORT = "$BRANCHES/export"

    fun user(id: String) = "$USERS/$id"

    fun branch(id: String) = "$BRANCHES/$id"

    fun client(id: String) = "$CLIENTS/$id"

    fun session(id: String) = "$SESSIONS/$id"

    fun sessionStatus(id: String) = "${session(id)}/status"

    fun sessionFinalPrice(id: String) = "${session(id)}/final-price"

    fun sessionVoid(id: String) = "${session(id)}/void"

    fun sessionUnvoid(id: String) = "${session(id)}/unvoid"

    fun sessionPractitioners(id: String) = "${session(id)}/practitioners"

    fun sessionPractitioner(
        sessionId: String,
        practitionerId: String,
    ) = "${sessionPractitioners(sessionId)}/$practitionerId"

    fun sessionConcerns(id: String) = "${session(id)}/concerns"

    fun sessionConcern(
        sessionId: String,
        concernId: String,
    ) = "${sessionConcerns(sessionId)}/$concernId"

    fun sessionPromoteConcern(id: String) = "${session(id)}/promote-concern"

    fun product(id: String) = "$PRODUCTS/$id"

    fun branchAssignments(id: String) = "$BRANCHES/$id/assignments"

    fun branchAssignment(
        branchId: String,
        userId: String,
    ) = "${branchAssignments(branchId)}/$userId"

    fun branchAssignmentSlot(
        branchId: String,
        userId: String,
    ) = "${branchAssignment(branchId, userId)}/slot"

    fun branchSlotsSwap(id: String) = "$BRANCHES/$id/slots/swap"

    fun branchRates(id: String) = "$BRANCHES/$id/rates"

    // #348 — pre-create preview: predicted session type + base rate for a client at a branch.
    fun branchSessionPreview(id: String) = "${branch(id)}/session-preview"

    fun branchInventory(id: String) = "$BRANCHES/$id/inventory"

    fun branchInventoryLowStock(id: String) = "${branchInventory(id)}/low-stock"

    fun branchInventoryMovements(id: String) = "${branchInventory(id)}/movements"

    fun branchInventoryRestock(
        branchId: String,
        productId: String,
    ) = "${branchInventory(branchId)}/$productId/restock"

    fun branchInventoryMovement(
        branchId: String,
        productId: String,
    ) = "${branchInventory(branchId)}/$productId/movement"

    fun branchToday(id: String) = "$BRANCHES/$id/today"

    fun branchDashboardToday(id: String) = "$BRANCHES/$id/dashboard/today"

    fun branchDailySummary(id: String) = "$BRANCHES/$id/daily-summary"

    fun branchDailySummaries(id: String) = "$BRANCHES/$id/daily-summaries"

    fun branchMonthlySummary(id: String) = "$BRANCHES/$id/monthly-summary"

    fun branchRemittanceDays(id: String) = "$BRANCHES/$id/remittance-days"

    fun branchRemittanceSessions(id: String) = "$BRANCHES/$id/remittance-sessions"

    fun branchRemittanceProductSales(id: String) = "$BRANCHES/$id/remittance-product-sales"

    fun branchReliefInvites(id: String) = "$BRANCHES/$id/relief-invites"

    // #377 — branch-wide ACCEPTED duties (any active member may revoke).
    fun branchReliefInvitesAccepted(id: String) = "${branchReliefInvites(id)}/accepted"

    // #401 — notification deep-link day read: invites at the branch for one date.
    fun branchReliefInvitesByDate(
        id: String,
        date: String,
    ) = "${branchReliefInvites(id)}/by-date?date=$date"

    fun branchReliefCandidates(id: String) = "$BRANCHES/$id/relief-candidates"

    // #366 — active-member directory (id + display name) for the requested-practitioner picker.
    fun branchMembers(id: String) = "$BRANCHES/$id/members"

    fun branchExport(
        id: String,
        format: String,
    ) = "$BRANCHES/$id/export/$format"

    fun notification(id: String) = "$NOTIFICATIONS/$id"

    fun notificationRead(id: String) = "${notification(id)}/read"

    const val NOTIFICATIONS_READ_ALL = "$NOTIFICATIONS/read-all"
    const val NOTIFICATIONS_HISTORY = "$NOTIFICATIONS/history"

    fun reliefInvite(id: String) = "$RELIEF_INVITES/$id"

    fun reliefInviteAction(
        id: String,
        action: String,
    ) = "${reliefInvite(id)}/$action"

    fun delegate(id: String) = "$DELEGATES/$id"

    const val ATTENDANCE_CLOCK_IN = "$ATTENDANCE/clock-in"
    const val ATTENDANCE_CLOCK_OUT = "$ATTENDANCE/clock-out"

    fun expense(id: String) = "$EXPENSES/$id"

    fun expenseRestore(id: String) = "${expense(id)}/restore"

    fun compensation(id: String) = "$COMPENSATION/$id"

    fun remittance(id: String) = "$REMITTANCES/$id"

    fun remittanceLines(id: String) = "${remittance(id)}/lines"

    fun remittanceLine(
        remittanceId: String,
        lineId: String,
    ) = "${remittanceLines(remittanceId)}/$lineId"

    fun remittanceDrift(id: String) = "${remittance(id)}/drift"

    fun remittanceDayBreakdowns(id: String) = "${remittance(id)}/day-breakdowns"

    fun remittanceDayBreakdown(
        remittanceId: String,
        breakdownId: String,
    ) = "${remittanceDayBreakdowns(remittanceId)}/$breakdownId"

    fun remittanceSubmit(id: String) = "${remittance(id)}/submit"

    fun remittanceUndo(id: String) = "${remittance(id)}/undo"

    fun commissionSplits(id: String) = "$COMMISSION_SPLITS/$id"

    fun commissionRecalculate(id: String) = "$COMMISSION_RECALCULATE/$id"

    fun branchDayUsers(id: String) = "$BRANCH_DAYS/$id/users"

    fun branchSessionBaseRates(id: String) = "$BRANCHES/$id/rates"

    // #404 — member-marked attendance (roster read + present/absent mark).
    fun branchAttendanceToday(id: String) = "$BRANCHES/$id/attendance/today"

    fun branchAttendanceMarks(id: String) = "$BRANCHES/$id/attendance/marks"

    fun branchExportDaily(id: String) = "$BRANCHES/$id/export/daily"

    fun branchExportRange(id: String) = "$BRANCHES/$id/export/range"

    fun branchExportMonthly(id: String) = "$BRANCHES/$id/export/monthly"

    fun branchExportAllTime(id: String) = "$BRANCHES/$id/export/all-time"

    const val BRANCHES_EXPORT_PROVINCIAL = "$BRANCHES_EXPORT/provincial"
    const val BRANCHES_EXPORT_MEDICAL_MISSION = "$BRANCHES_EXPORT/medical-mission"
    const val SESSION_PATH = "$SESSIONS/{sessionId}"
    const val SESSION_STATUS_PATH = "$SESSION_PATH/status"
    const val SESSION_FINAL_PRICE_PATH = "$SESSION_PATH/final-price"
    const val SESSION_VOID_PATH = "$SESSION_PATH/void"
    const val SESSION_UNVOID_PATH = "$SESSION_PATH/unvoid"
    const val SESSION_PRACTITIONERS_PATH = "$SESSION_PATH/practitioners"
    const val SESSION_CONCERNS_PATH = "$SESSION_PATH/concerns"
    const val REMITTANCE_PATH = "$REMITTANCES/{remittanceId}"
    const val REMITTANCE_LINES_PATH = "$REMITTANCE_PATH/lines"
    const val REMITTANCE_DRIFT_PATH = "$REMITTANCE_PATH/drift"
    const val REMITTANCE_SUBMIT_PATH = "$REMITTANCE_PATH/submit"
    const val REMITTANCE_UNDO_PATH = "$REMITTANCE_PATH/undo"
    const val BRANCH_PATH = "$BRANCHES/{branchId}"
    const val BRANCH_RATES_PATH = "$BRANCH_PATH/rates"

    // #348 — pre-create preview (query param: clientId).
    const val BRANCH_SESSION_PREVIEW_PATH = "$BRANCH_PATH/session-preview"
    const val BRANCH_RELIEF_INVITES_PATH = "$BRANCH_PATH/relief-invites"
    const val BRANCH_RELIEF_INVITES_ACCEPTED_PATH = "$BRANCH_PATH/relief-invites/accepted"

    // #401 — deep-link day read (query param: date): every invite at the branch on that day.
    const val BRANCH_RELIEF_INVITES_BY_DATE_PATH = "$BRANCH_RELIEF_INVITES_PATH/by-date"
    const val BRANCH_RELIEF_CANDIDATES_PATH = "$BRANCH_PATH/relief-candidates"
    const val BRANCH_MEMBERS_PATH = "$BRANCH_PATH/members"
    const val BRANCH_ATTENDANCE_TODAY_PATH = "$BRANCH_PATH/attendance/today"
    const val BRANCH_ATTENDANCE_MARKS_PATH = "$BRANCH_PATH/attendance/marks"
    const val BRANCH_ASSIGNMENTS_PATH = "$BRANCH_PATH/assignments"
    const val BRANCH_SLOTS_SWAP_PATH = "$BRANCH_PATH/slots/swap"
    const val BRANCH_INVENTORY_PATH = "$BRANCH_PATH/inventory"
    const val BRANCH_TODAY_PATH = "$BRANCH_PATH/today"
    const val BRANCH_DASHBOARD_TODAY_PATH = "$BRANCH_PATH/dashboard/today"
    const val BRANCH_DAILY_SUMMARY_PATH = "$BRANCH_PATH/daily-summary"
    const val BRANCH_DAILY_SUMMARIES_PATH = "$BRANCH_PATH/daily-summaries"
    const val BRANCH_MONTHLY_SUMMARY_PATH = "$BRANCH_PATH/monthly-summary"
    const val BRANCH_REMITTANCE_DAYS_PATH = "$BRANCH_PATH/remittance-days"
    const val BRANCH_REMITTANCE_SESSIONS_PATH = "$BRANCH_PATH/remittance-sessions"
    const val BRANCH_REMITTANCE_PRODUCT_SALES_PATH = "$BRANCH_PATH/remittance-product-sales"
    const val BRANCH_EXPORT_DAILY_PATH = "$BRANCH_PATH/export/daily"
    const val BRANCH_EXPORT_RANGE_PATH = "$BRANCH_PATH/export/range"
    const val BRANCH_EXPORT_MONTHLY_PATH = "$BRANCH_PATH/export/monthly"
    const val BRANCH_EXPORT_ALL_TIME_PATH = "$BRANCH_PATH/export/all-time"
    const val REMITTANCE_DAY_BREAKDOWNS_PATH = "$REMITTANCE_PATH/day-breakdowns"
    const val REMITTANCE_LINE_PATH = "$REMITTANCE_LINES_PATH/{lineId}"
    const val REMITTANCE_DAY_BREAKDOWN_PATH = "$REMITTANCE_DAY_BREAKDOWNS_PATH/{breakdownId}"
    const val PRODUCT_PATH = "$PRODUCTS/{productId}"
    const val PRODUCT_CATEGORY_PATH = "$PRODUCT_CATEGORIES/{categoryId}"
    const val CLIENT_PATH = "$CLIENTS/{clientId}"
    const val USER_PATH = "$USERS/{userId}"
    const val BRANCH_DAY_USERS_PATH = "$BRANCH_DAYS/{branchDayId}/users"
    const val COMMISSION_SPLITS_PATH = "$COMMISSION_SPLITS/{branchDayId}"
    const val COMMISSION_RECALCULATE_PATH = "$COMMISSION_RECALCULATE/{branchDayId}"
    const val AUDIT_LOG_ENTRIES_PATH = "$AUDIT_LOG/entries"
    const val AUDIT_LOG_FLAGGED_PATH = "$AUDIT_LOG/flagged"
    const val AUDIT_LOG_TABLES_PATH = "$AUDIT_LOG/tables"
    const val NOTIFICATION_READ_PATH = "$NOTIFICATIONS/{notificationId}/read"
    const val EXPENSE_PATH = "$EXPENSES/{expenseId}"
    const val EXPENSE_RESTORE_PATH = "$EXPENSE_PATH/restore"
    const val COMPENSATION_PATH = "$COMPENSATION/{compensationId}"
    const val RELIEF_ACCESS_GRANT_PATH = "$RELIEF_ACCESS/{requestId}/grant"
    const val RELIEF_ACCESS_DENY_PATH = "$RELIEF_ACCESS/{requestId}/deny"
    const val DELEGATE_PATH = "$DELEGATES/{delegateId}"
    const val CLIENT_ANONYMIZE_SUFFIX = "/anonymize"
    const val RELIEF_INVITE_ACCEPT_PATH = "$RELIEF_INVITES/{inviteId}/accept"
    const val RELIEF_INVITE_DECLINE_PATH = "$RELIEF_INVITES/{inviteId}/decline"
    const val RELIEF_INVITE_RETRACT_PATH = "$RELIEF_INVITES/{inviteId}/retract"
    const val RELIEF_INVITE_REVOKE_PATH = "$RELIEF_INVITES/{inviteId}/revoke"
    const val BRANCH_ASSIGNMENT_USER_PATH = "$BRANCH_ASSIGNMENTS_PATH/{userId}"
    const val BRANCH_ASSIGNMENT_SLOT_PATH = "$BRANCH_ASSIGNMENT_USER_PATH/slot"
    const val USER_DEACTIVATE_PATH = "$USERS/{userId}/deactivate"
    const val USER_REACTIVATE_PATH = "$USERS/{userId}/reactivate"
    const val USER_ROLES_PATH = "$USERS/{userId}/roles"
    const val BRANCH_INVENTORY_RESTOCK_PATH = "$BRANCH_INVENTORY_PATH/{productId}/restock"
    const val BRANCH_INVENTORY_MOVEMENT_PATH = "$BRANCH_INVENTORY_PATH/{productId}/movement"
    const val BRANCH_INVENTORY_LOW_STOCK_PATH = "$BRANCH_INVENTORY_PATH/low-stock"
    const val BRANCH_INVENTORY_MOVEMENTS_PATH = "$BRANCH_INVENTORY_PATH/movements"
    const val BRANCHES_ACCESSIBLE = "$BRANCHES/accessible"
    const val SESSION_CONCERN_PATH = "$SESSION_CONCERNS_PATH/{concernId}"
    const val SESSION_PROMOTE_CONCERN_PATH = "$SESSION_PATH/promote-concern"
    const val BRANCHES_EXPORT_PROVINCIAL_PATH = "$BRANCHES/export/provincial"
    const val BRANCHES_EXPORT_MEDICAL_MISSION_PATH = "$BRANCHES/export/medical-mission"
    const val AUDIT_LOG_ACKNOWLEDGE_PATH = "$AUDIT_LOG/{entryId}/acknowledge"
    const val CLIENT_ANONYMIZE_PATH = "$CLIENTS/{clientId}/anonymize"
    const val BRANCH_PARAM_PATH = "$BRANCHES/{branchId}"

    fun branchDailySummaryWithDate(
        id: String,
        date: String,
    ) = "${branchDailySummary(id)}?date=$date"

    fun branchExportWithQuery(
        path: String,
        query: String,
    ) = "$path?$query"

    fun branchRemittance(id: String) = "$BRANCHES/$id/remittance"

    fun branchInventoryProduct(
        branchId: String,
        productId: String,
    ) = "${branchInventory(branchId)}/$productId"

    fun reliefAccessRequest(id: String) = "$RELIEF_ACCESS/$id"

    const val RELIEF_ACCESS_REQUEST = "$RELIEF_ACCESS/request"
    const val RELIEF_ACCESS_MINE = "$RELIEF_ACCESS/mine"
    const val RELIEF_ACCESS_BRANCH_OPTIONS = "$RELIEF_ACCESS/branch-options"
    const val RELIEF_ACCESS_CANCEL_PATH = "$RELIEF_ACCESS/{requestId}/cancel"

    fun reliefAccessList(branchDayId: String) = "$RELIEF_ACCESS?branchDayId=$branchDayId"

    // #358 — notification deep-link addressing: branch+date, no branch-day id needed.
    fun reliefAccessByBranchAndDate(
        branchId: String,
        date: String,
    ) = "$RELIEF_ACCESS?branchId=$branchId&date=$date"

    fun userRoles(id: String) = "${user(id)}/roles"

    fun userDeactivate(id: String) = "${user(id)}/deactivate"

    fun userReactivate(id: String) = "${user(id)}/reactivate"

    fun expenseCollectionWithBranchDay(branchDayId: String) = "$EXPENSES?branchDayId=$branchDayId"

    fun allowanceCollectionWithBranchDay(branchDayId: String) = "$ALLOWANCES?branchDayId=$branchDayId"

    fun commissionInclusion(id: String) = "$COMMISSION_INCLUSIONS/$id"

    fun auditLogWithQuery(query: String) = "$AUDIT_LOG?$query"

    fun auditLogEntriesWithQuery(query: String) = "$AUDIT_LOG_ENTRIES?$query"

    const val AUDIT_LOG_ENTRIES = "$AUDIT_LOG/entries"
    const val AUDIT_LOG_FLAGGED = "$AUDIT_LOG/flagged"
    const val AUDIT_LOG_TABLES = "$AUDIT_LOG/tables"

    fun auditLogAcknowledge(id: String) = "$AUDIT_LOG/$id/acknowledge"
}
