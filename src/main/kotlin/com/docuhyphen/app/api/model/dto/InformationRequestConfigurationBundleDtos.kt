package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class InformationRequestConfigurationBundleProblemDto(
    val path: String,
    val code: String,
    val message: String,
)

@Serializable
data class InformationRequestConfigurationBundleValidationDto(
    val valid: Boolean,
    val problems: List<InformationRequestConfigurationBundleProblemDto>,
)
