package com.companyb.companyapp.database

import org.jetbrains.exposed.v1.core.ComparisonOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.stringParam

private class ILikeOp(
    expr1: Expression<*>,
    expr2: Expression<*>,
) : ComparisonOp(expr1, expr2, "ILIKE")

/** Postgres case-insensitive `column ILIKE pattern`; Exposed has no form for it. */
internal fun ilike(
    column: Expression<out String?>,
    pattern: String,
): Op<Boolean> = ILikeOp(column, stringParam(pattern))
