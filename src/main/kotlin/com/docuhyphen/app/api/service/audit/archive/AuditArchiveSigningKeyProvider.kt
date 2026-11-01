package com.docuhyphen.app.api.service.audit.archive

interface AuditArchiveSigningKeyProvider
{
    fun keyId(): String

    fun sign(data: ByteArray): ByteArray

    fun verify(data: ByteArray, signature: ByteArray, keyId: String): Boolean

    fun activePublicKeyPem(): String
}
