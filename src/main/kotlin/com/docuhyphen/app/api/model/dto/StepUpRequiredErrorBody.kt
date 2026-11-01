package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class StepUpRequiredErrorBody(
    val errorMessage: String,
    val reasonCode: String,
    val action: String? = null,
)
