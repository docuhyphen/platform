package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.qualifier.Aws
import com.docuhyphen.app.api.qualifier.Local
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject

/** Selects the active [AuditArchiveStorage] by `app.audit.archive.storage.type`, mirrors [com.docuhyphen.app.api.config.FileStorageServiceProducer]. */
@ApplicationScoped
class AuditArchiveStorageProducer @Inject constructor(
    private val configService: AuditArchiveConfigService,
    @Local private val localAuditArchiveStorage: LocalAuditArchiveStorage,
    @Aws private val s3AuditArchiveStorage: S3AuditArchiveStorage,
)
{
    @Produces
    fun produceAuditArchiveStorage(): AuditArchiveStorage
    {
        return when (configService.getStorageType())
        {
            "aws" -> s3AuditArchiveStorage
            "local" -> localAuditArchiveStorage
            else -> throw IllegalArgumentException("Invalid app.audit.archive.storage.type: ${configService.getStorageType()}")
        }
    }
}
