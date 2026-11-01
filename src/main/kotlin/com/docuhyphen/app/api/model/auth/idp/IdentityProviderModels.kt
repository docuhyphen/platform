package com.docuhyphen.app.api.model.auth.idp

data class OAuthTokenResponse(
    val idToken: String?,
    val accessToken: String?,
    val email: String,
    val subjectId: String,
    val name: String?,
)

data class OAuthUserInfo(
    val email: String,
    val subjectId: String,
    val firstName: String?,
    val lastName: String?,
    val legacySubjectId: String? = null,
    val emailVerified: Boolean = false,
)

data class RuntimeIdpCredentials(
    val clientId: String,
    val clientSecret: String,
    val tenantId: String? = null,
    val scopes: String? = null,
    val oidcIssuer: String? = null,
    val allowedAudiences: Set<String> = emptySet(),
    val allowedAlgs: Set<String> = emptySet(),
    val requiredClaims: Set<String> = emptySet(),
    val workspaceDomain: String? = null,
)
