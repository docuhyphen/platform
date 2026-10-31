package com.docuhyphen.app.api.service.document

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory

@ApplicationScoped
class DocumentVersionStorageCleanupScheduler @Inject constructor(
    private val cleanup: DocumentVersionStorageCleanupService,
    @ConfigProperty(name = "app.document-version.storage-cleanup.batch-size", defaultValue = "100")
    private val batchSize: Int,
) {
    @Scheduled(
        every = "\${app.document-version.storage-cleanup.every:15m}",
        identity = "document-version-storage-cleanup",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun tick() {
        try {
            val run = cleanup.sweep(batchSize)
            if (run.removed > 0 || run.failed > 0) {
                logger.info(
                    "document version storage cleanup: removed={} kept={} failed={}",
                    run.removed,
                    run.kept,
                    run.failed,
                )
            }
        } catch (exception: Exception) {
            logger.error("document version storage cleanup pass failed", exception)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DocumentVersionStorageCleanupScheduler::class.java)
    }
}
