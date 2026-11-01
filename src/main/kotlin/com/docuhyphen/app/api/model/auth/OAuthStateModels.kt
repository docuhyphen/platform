package com.docuhyphen.app.api.model.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import java.util.UUID

data class VerifiedOAuthState(
    val flow: String,
    val provider: IdentityProviderType,
    val nonce: String,
    val orgIdpConfigId: UUID? = null,
    val codeVerifier: String? = null,
    val stepUpSessionId: UUID? = null,
    val stepUpAppUserId: UUID? = null,
    val stepUpExpectedSubjectId: String? = null,
    val stepUpReturnTo: String? = null,
    val linkAppUserId: UUID? = null,
)

data class SignedOAuthState(
    val token: String,
    val nonce: String,
    val codeChallenge: String,
)
