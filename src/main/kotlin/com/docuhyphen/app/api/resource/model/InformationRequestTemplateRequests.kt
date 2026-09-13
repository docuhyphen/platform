package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.dto.CreateInformationRequestTemplateRequest
import kotlinx.serialization.Serializable

/**
 * HTTP request bodies for Template lifecycle operations that name an exact source Version.
 *
 * The source Version is stated by the caller rather than resolved as whatever is latest, so a copy
 * always reproduces the configuration the caller was looking at even if another Version was
 * published in between.
 */

@Serializable
data class InformationRequestTemplateNewVersionRequest(
    val sourceVersionNumber: Int,
)

@Serializable
data class CloneInformationRequestTemplateRequest(
    val sourceVersionNumber: Int,
    val target: CreateInformationRequestTemplateRequest,
)

