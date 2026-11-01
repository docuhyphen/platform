package com.docuhyphen.app.api.model.application

import java.util.UUID

data class RegisterWebhookRequest(
    val workflowDefinitionId: UUID,
    val targetUrl: String,
    val permittedEventTypes: List<String>,
    val registeredApplicationId: UUID?,
)

data class WebhookSigningSecret(val rawSecret: String, val version: Int)
