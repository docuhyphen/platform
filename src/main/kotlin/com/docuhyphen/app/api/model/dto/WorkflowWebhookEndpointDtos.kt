package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class WebhookEndpointDto(
    val id: String,
    val ownerOrganizationId: String,
    val workflowDefinitionId: String,
    val registeredApplicationId: String?,
    val targetUrl: String,
    val isEnabled: Boolean,
    val signingSecretVersion: Int,
    val permittedEventTypes: String,
    val createdDate: String,
    val updatedDate: String,
)

@Serializable
data class WebhookEndpointCreatedDto(
    val id: String,
    val targetUrl: String,
    val rawSigningSecret: String,
    val signingSecretVersion: Int,
)

@Serializable
data class WebhookSigningSecretRotatedDto(val rawSecret: String, val version: Int)
