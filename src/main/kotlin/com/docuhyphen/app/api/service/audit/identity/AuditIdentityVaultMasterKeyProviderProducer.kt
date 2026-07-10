package com.docuhyphen.app.api.service.audit.identity

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.config.AuditIdentityVaultConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject

/** Selects the active [AuditIdentityVaultMasterKeyProvider] by `app.audit.identity-vault.provider`. */
@ApplicationScoped
class AuditIdentityVaultMasterKeyProviderProducer @Inject constructor(
    private val configService: AuditIdentityVaultConfigService,
    @Local private val localProvider: LocalAuditIdentityVaultMasterKeyProvider,
    @Aws private val secretsManagerProvider: SecretsManagerAuditIdentityVaultMasterKeyProvider,
)
{
    @Produces
    fun produceAuditIdentityVaultMasterKeyProvider(): AuditIdentityVaultMasterKeyProvider
    {
        return when (configService.getProvider())
        {
            "aws" -> secretsManagerProvider
            "local" -> localProvider
            else -> throw IllegalArgumentException("Invalid app.audit.identity-vault.provider: ${configService.getProvider()}")
        }
    }
}
