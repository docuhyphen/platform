package com.docuhyphen.app.api.service.audit.identity

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.service.config.AuditIdentityVaultConfigService
import com.docuhyphen.app.api.service.config.AwsSecretsManagerService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.locks.ReentrantLock

@ApplicationScoped
@Aws
class SecretsManagerAuditIdentityVaultMasterKeyProvider @Inject constructor(
    private val configService: AuditIdentityVaultConfigService,
    private val secretsManagerService: AwsSecretsManagerService,
) : AuditIdentityVaultMasterKeyProvider
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
            val secretString =
                secretsManagerService.getSecretString(configService.getSecretId(), configService.getRegion())
            val key = decodeOrDeriveKey(secretString)
            cachedKey = key
            return key
        }
        finally
        {
            lock.unlock()
        }
    }

    private fun decodeOrDeriveKey(secretString: String): ByteArray
    {
        val decoded = runCatching { Base64.getDecoder().decode(secretString.trim()) }.getOrNull()
        if (decoded?.size == 32)
        {
            return decoded
        }

        return MessageDigest.getInstance("SHA-256").digest(secretString.toByteArray(Charsets.UTF_8))
    }
}
