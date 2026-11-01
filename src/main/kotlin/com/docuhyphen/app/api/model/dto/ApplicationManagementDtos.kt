package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApplicationDto(
    val id: String,
    val name: String,
    val description: String?,
    val applicationType: String,
    val ownerOrganizationId: String?,
    val isActive: Boolean,
    val grantedCapabilities: String,
    val createdDate: String,
    val lastAccessDate: String?,
)

@Serializable
data class ApplicationCreatedDto(
    val id: String,
    val name: String,
    val description: String?,
    val applicationType: String,
    val ownerOrganizationId: String?,
    val apiKey: String,
    val rawSecret: String,
    val grantedCapabilities: String,
    val createdDate: String,
)

@Serializable
data class RotateCredentialsResponse(val apiKey: String, val rawSecret: String)
