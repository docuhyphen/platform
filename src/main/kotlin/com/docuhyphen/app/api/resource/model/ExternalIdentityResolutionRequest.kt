package com.docuhyphen.app.api.resource.model

import kotlinx.serialization.Serializable

@Serializable
data class ExternalIdentityResolutionRequest(val email: String?)
