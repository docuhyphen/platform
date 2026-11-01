package com.docuhyphen.app.api.model.audit.archive

import kotlinx.serialization.Serializable

@Serializable
internal data class AuditArchiveSigningSecret(
    val keyId: String,
    val privateKeyPem: String,
    val publicKeyPem: String,
    val historicalPublicKeys: Map<String, String> = emptyMap(),
)
