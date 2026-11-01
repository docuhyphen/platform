package com.docuhyphen.app.api.service.audit.identity

interface AuditIdentityVaultMasterKeyProvider
{
    fun keyId(): String
    fun keyBytes(): ByteArray
}
