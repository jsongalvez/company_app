package com.companyb.companyapp.api.mapping

import com.companyb.companyapp.domain.RegisterResult
import com.companyb.companyapp.dto.RegisterErrorResponse

fun RegisterResult.toErrorResponse(): RegisterErrorResponse {
    val error = requireNotNull(this.errorCode)
    return RegisterErrorResponse(
        code = error.name,
        label = error.label,
        detail = error.detail,
    )
}
