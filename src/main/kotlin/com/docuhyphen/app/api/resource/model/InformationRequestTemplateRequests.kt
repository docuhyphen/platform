package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import kotlinx.serialization.Serializable

@Serializable
data class InformationRequestTemplateNewVersionRequest(
    val sourceVersionNumber: Int,
)

@Serializable
data class CloneInformationRequestTemplateRequest(
    val sourceVersionNumber: Int,
    val target: CreateInformationRequestTemplateRequest,
)

