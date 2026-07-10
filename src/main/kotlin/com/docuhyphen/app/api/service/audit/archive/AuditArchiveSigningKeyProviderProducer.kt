package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject

/** Selects the active [AuditArchiveSigningKeyProvider] by `app.audit.archive.signing.provider`. */
@ApplicationScoped
class AuditArchiveSigningKeyProviderProducer @Inject constructor(
    private val configService: AuditArchiveConfigService,
    @Local private val localProvider: LocalAuditArchiveSigningKeyProvider,
    @Aws private val secretsManagerProvider: SecretsManagerAuditArchiveSigningKeyProvider,
)
{
    @Produces
    fun produceAuditArchiveSigningKeyProvider(): AuditArchiveSigningKeyProvider
    {
        return when (configService.getSigningProvider())
        {
            "aws" -> secretsManagerProvider
            "local" -> localProvider
            else -> throw IllegalArgumentException("Invalid app.audit.archive.signing.provider: ${configService.getSigningProvider()}")
        }
    }
}
