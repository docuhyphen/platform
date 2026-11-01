package com.docuhyphen.app.api.model.variable

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import java.time.Instant

data class VariableResolutionContext(
    val user: AppUser,
    val organization: Organization?,
    val timestamp: Instant,
    val overrides: Map<String, String> = emptyMap(),
)

data class InterpolationResult(
    val resolved: String,
    val unresolvedTokens: List<String> = emptyList(),
)
