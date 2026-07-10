package com.docuhyphen.app.api.service.audit.identity

import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.config.AuditIdentityVaultConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock

/**
 * Local-filesystem master key provider: bootstraps a single random AES-256 key on first use and
 * persists it base64-encoded under [AuditIdentityVaultConfigService.getLocalDirectory]. Dev/CI
 * default; set `app.audit.identity-vault.provider=aws` in a real deployment.
 */
@ApplicationScoped
@Local
class LocalAuditIdentityVaultMasterKeyProvider @Inject constructor(
    private val configService: AuditIdentityVaultConfigService,
)
    : AuditIdentityVaultMasterKeyProvider
{
    companion object
    {
        private const val KEY_ID = "local-dev-vault-key-1"
        private const val KEY_SIZE_BYTES = 32
    }

    private val lock = ReentrantLock()

    @Volatile
    private var cachedKey: ByteArray? = null

    override fun keyId(): String = KEY_ID

    override fun keyBytes(): ByteArray
    {
        cachedKey?.let { return it }

        lock.lock()
        try
        {
            cachedKey?.let { return it }

            val dir = Path.of(configService.getLocalDirectory())
            Files.createDirectories(dir)
            val keyPath = dir.resolve("$KEY_ID.key")

            val key = if (Files.exists(keyPath))
            {
                Base64.getDecoder().decode(Files.readString(keyPath).trim())
            }
            else
            {
                val generated = ByteArray(KEY_SIZE_BYTES)
                SecureRandom().nextBytes(generated)
                Files.writeString(keyPath, Base64.getEncoder().encodeToString(generated))
                generated
            }

            cachedKey = key
            return key
        }
        finally
        {
            lock.unlock()
        }
    }
}
