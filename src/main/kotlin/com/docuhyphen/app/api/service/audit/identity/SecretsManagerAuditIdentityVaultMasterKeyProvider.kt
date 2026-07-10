package com.docuhyphen.app.api.service.audit.identity

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.service.config.AuditIdentityVaultConfigService
import com.docuhyphen.app.api.service.config.AwsSecretsManagerService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock

/**
 * Secrets-Manager-backed master key provider (Phase 8): a single base64 AES-256 key stored as one
 * JSON-free plain-text secret in the existing AWS Secrets Manager - no KMS, no per-subject secret.
 * The secret value must be provisioned out-of-band, same as `AuditArchiveSigningSecret`.
 */
@ApplicationScoped
@Aws
class SecretsManagerAuditIdentityVaultMasterKeyProvider @Inject constructor(
    private val configService: AuditIdentityVaultConfigService,
    private val secretsManagerService: AwsSecretsManagerService,
)
    : AuditIdentityVaultMasterKeyProvider
{
    private val lock = ReentrantLock()

    @Volatile
    private var cachedKey: ByteArray? = null

    override fun keyId(): String = configService.getSecretId()

    override fun keyBytes(): ByteArray
    {
        cachedKey?.let { return it }

        lock.lock()
        try
        {
            cachedKey?.let { return it }
            val secretString = secretsManagerService.getSecretString(configService.getSecretId(), configService.getRegion())
            val key = Base64.getDecoder().decode(secretString.trim())
            cachedKey = key
            return key
        }
        finally
        {
            lock.unlock()
        }
    }
}
