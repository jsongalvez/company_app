package com.companyb.companyapp.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailPolicyTest {
    @Test
    fun dotted_local_part_is_valid() {
        assertTrue(EmailPolicy.isValid("first.last@company.com"))
    }

    @Test
    fun missing_dot_domain_is_invalid() {
        assertFalse(EmailPolicy.isValid("a@b"))
    }

    @Test
    fun dot_immediately_after_at_is_invalid() {
        assertFalse(EmailPolicy.isValid("a@.com"))
    }
}
