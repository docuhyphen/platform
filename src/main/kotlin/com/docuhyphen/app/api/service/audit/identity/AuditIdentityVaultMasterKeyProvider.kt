package com.docuhyphen.app.api.service.audit.identity

/** Supplies the single application-wide AES-256 key that wraps every per-subject data key. */
interface AuditIdentityVaultMasterKeyProvider
{
    fun keyId(): String
    fun keyBytes(): ByteArray
}
