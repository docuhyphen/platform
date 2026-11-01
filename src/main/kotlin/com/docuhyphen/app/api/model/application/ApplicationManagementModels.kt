package com.docuhyphen.app.api.model.application

import com.docuhyphen.app.api.model.entity.ApplicationType
import java.util.UUID

data class RotateCredentialsResult(val apiKey: String, val rawSecret: String)

data class CreateApplicationRequest(
    val name: String,
    val description: String?,
    val applicationType: ApplicationType,
    val ownerOrganizationId: UUID?,
    val grantedCapabilities: List<String>,
)
