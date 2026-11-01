package com.docuhyphen.app.api.resource.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateApplicationRequestBody(
    val name: String?,
    val description: String?,
    val applicationType: String?,
    val ownerOrganizationId: String?,
    val grantedCapabilities: List<String>?,
)

@Serializable
data class UpdateCapabilitiesRequestBody(val capabilities: List<String>?)
