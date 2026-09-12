package com.companyb.companyapp.utils

import com.companyb.companyapp.exception.ValidationException
import java.math.BigDecimal

const val MONEY_MAX_PLAIN = "99999999.99"

val MONEY_MAX: BigDecimal = BigDecimal(MONEY_MAX_PLAIN)

/**
 * Persisted-range pre-gate (#925, the #924 session-money precedent): every NUMERIC(10,2)
 * money write is checked before the insert/update so overflow returns 400 instead of a
 * Postgres numeric-overflow 500 + #475 auto-file (the #912/#921/#923 fail-closed class).
 * NUMERIC(15,4) columns (commission splits, sale commission snapshots) are wider and
 * stay outside this bound.
 */
fun validateMoneyAmount(
    amount: BigDecimal,
    field: String,
) {
    if (amount > MONEY_MAX) {
        throw ValidationException("$field must be at most $MONEY_MAX_PLAIN")
    }
}
