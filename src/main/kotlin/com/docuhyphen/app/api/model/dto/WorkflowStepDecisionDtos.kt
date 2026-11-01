package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class WorkflowDecisionResponse(
    val instanceId: String,
    val stepInstanceId: String,
    val stepStatus: String,
    val instanceStatus: String,
)
