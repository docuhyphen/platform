package com.docuhyphen.app.api.resource.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowDecisionRequest(
    val decision: String? = null,
    val reason: String? = null,
)
