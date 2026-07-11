package com.docuhyphen.app.api.service.audit.archive

/**
 * Abstraction over the asymmetric key pair used to sign archive segment manifests. Kept
 * deliberately narrow (sign + expose the public key + a stable key id) so the key material can
 * later move from Secrets Manager to KMS/HSM (a deferred, separately-approved hardening item,
 * without changing any
 * caller of this interface or the archived manifest format.
 */
interface AuditArchiveSigningKeyProvider
{
    /** Stable identifier for the active signing key, persisted on every segment as `signingKeyId`. */
    fun keyId(): String

    /** Signs [data] with the active private key. Returns the raw signature bytes. */
    fun sign(data: ByteArray): ByteArray

    /** Verifies [signature] over [data] against the key identified by [keyId] (may differ from the currently-active key for older segments). */
    fun verify(data: ByteArray, signature: ByteArray, keyId: String): Boolean

    /** PEM-encoded public key for the currently active key, for the offline verifier tool. */
    fun activePublicKeyPem(): String
}
