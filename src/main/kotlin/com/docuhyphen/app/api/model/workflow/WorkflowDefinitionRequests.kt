package com.docuhyphen.app.api.model.workflow

import kotlinx.serialization.Serializable

@Serializable
data class CreateWorkflowDefinitionRequest(
    val name: String,
    val summary: String? = null,
    val triggerEvent: String,
    val stepsJson: String,
    val generalTags: List<String> = emptyList(),
    val isActive: Boolean = true,
    val isTemplate: Boolean = false,
    val scope: String? = null,
)

@Serializable
data class UpdateWorkflowDefinitionRequest(
    val name: String? = null,
    val summary: String? = null,
    val stepsJson: String? = null,
    val generalTags: List<String>? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class PatchWorkflowStatusRequest(
    val isActive: Boolean,
)

@Serializable
data class PatchWorkflowPublishedRequest(
    val isPublished: Boolean,
)

@Serializable
data class CloneWorkflowRequest(
    val newName: String? = null,
)
