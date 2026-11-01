package com.docuhyphen.app.api.model.application

import kotlinx.serialization.Serializable

data class WebhookDeliveryResult(val success: Boolean, val reason: String?)

@Serializable
data class WebhookPayload(
    val deliveryId: String,
    val eventType: String,
    val workflowInstanceId: String,
    val subjectResourceType: String?,
    val subjectResourceId: String?,
    val organizationId: String?,
    val timestamp: String,
)
