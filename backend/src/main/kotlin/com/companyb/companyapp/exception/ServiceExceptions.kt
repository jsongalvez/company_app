package com.companyb.companyapp.exception

import java.util.UUID

open class ValidationException(
    message: String,
) : RuntimeException(message)

open class NotFoundException(
    message: String,
) : RuntimeException(message)

open class ConflictException(
    message: String,
) : RuntimeException(message)

class VersionMismatchException(
    table: String,
    recordId: UUID,
) : ConflictException("Version mismatch on $table for record $recordId")

open class ForbiddenException(
    message: String,
) : RuntimeException(message)
