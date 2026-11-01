package com.docuhyphen.app.api.model.auth

import com.docuhyphen.app.api.service.auth.RevocationReasonCode

data class OrganizationMembershipValidationResult(
    val valid: Boolean,
    val reasonCode: RevocationReasonCode? = null,
    val message: String? = null,
    val orgActive: Boolean = true,
)
