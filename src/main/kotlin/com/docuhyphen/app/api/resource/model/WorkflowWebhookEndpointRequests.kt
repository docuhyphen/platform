package com.docuhyphen.app.api.resource.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterWebhookRequestBody(
    val workflowDefinitionId: String?,
    val targetUrl: String?,
    val permittedEventTypes: List<String>?,
    val registeredApplicationId: String?,
)
