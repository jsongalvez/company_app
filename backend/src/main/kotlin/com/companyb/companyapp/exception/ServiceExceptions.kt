package com.companyb.companyapp.exception

open class ValidationException(
    message: String,
) : RuntimeException(message)

open class NotFoundException(
    message: String,
) : RuntimeException(message)

open class ConflictException(
    message: String,
) : RuntimeException(message)

open class ForbiddenException(
    message: String,
) : RuntimeException(message)
