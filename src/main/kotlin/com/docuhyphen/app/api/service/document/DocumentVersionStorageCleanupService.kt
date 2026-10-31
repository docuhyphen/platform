package com.docuhyphen.app.api.service.document

import com.docuhyphen.app.api.model.document.DocumentVersionStorageCleanupOutcome
import com.docuhyphen.app.api.model.document.DocumentVersionStorageCleanupOutcome.*
import com.docuhyphen.app.api.model.document.DocumentVersionStorageCleanupRun
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import com.docuhyphen.app.api.model.entity.DocumentVersionStorageCleanup
import com.docuhyphen.app.api.repository.document.DocumentVersionStorageCleanupRepository
import com.docuhyphen.app.api.service.storage.DocumentVersionStorageService
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration

@ApplicationScoped
class DocumentVersionStorageCleanupService @Inject constructor(
    private val cleanups: DocumentVersionStorageCleanupRepository,
    private val storage: DocumentVersionStorageService,
    private val synchronizations: TransactionSynchronizationRegistry,
    private val clock: Clock,
    @ConfigProperty(name = "app.document-version.storage-cleanup.grace", defaultValue = "PT1H")
    private val grace: Duration,
    @ConfigProperty(name = "app.document-version.storage-cleanup.retry-after", defaultValue = "PT15M")
    private val retryAfter: Duration,
) {
    fun guard(locator: ObjectStoreDocumentVersionLocator) {
        check(QuarkusTransaction.isActive()) { "A document version is stored only inside the transaction that records it" }
        val now = clock.instant()
        QuarkusTransaction.requiringNew().run {
            cleanups.insertNow(
                DocumentVersionStorageCleanup().apply {
                    locatorKind = locator.kind
                    this.locator = locator.value
                    createdAt = Timestamp.from(now)
                    nextAttemptAt = Timestamp.from(now.plus(grace))
                },
            )
        }
        check(cleanups.discharge(locator.value) == 1) { "The cleanup of a stored document version was already taken" }
        synchronizations.registerInterposedSynchronization(RemoveWhenRolledBack(locator))
    }

    fun sweep(limit: Int): DocumentVersionStorageCleanupRun {
        val outcomes = generateSequence { QuarkusTransaction.requiringNew().call { settleNextDue() } }
            .take(limit)
            .toList()
        return DocumentVersionStorageCleanupRun(
            removed = outcomes.count { it == REMOVED },
            kept = outcomes.count { it == KEPT },
            failed = outcomes.count { it == FAILED },
        )
    }

    private fun settleNextDue(): DocumentVersionStorageCleanupOutcome? {
        val now = clock.instant()
        val cleanup = cleanups.claimNextDue(Timestamp.from(now)) ?: return null
        if (cleanups.isReferenced(cleanup.locator)) {
            cleanups.delete(cleanup)
            return KEPT
        }
        return try {
            storage.deleteVersion(ObjectStoreDocumentVersionLocator(cleanup.locator))
            cleanups.delete(cleanup)
            REMOVED
        } catch (failure: Exception) {
            cleanup.attemptCount += 1
            cleanup.nextAttemptAt = Timestamp.from(now.plus(backoff(cleanup.attemptCount)))
            cleanups.update(cleanup)
            logger.warn("Stored document version {} could not be removed; it will be retried", cleanup.locator, failure)
            FAILED
        }
    }

    private fun backoff(attempts: Int): Duration =
        retryAfter.multipliedBy(1L shl (attempts - 1).coerceIn(0, MAXIMUM_DOUBLINGS)).coerceAtMost(MAXIMUM_BACKOFF)

    private inner class RemoveWhenRolledBack(private val locator: ObjectStoreDocumentVersionLocator) : Synchronization {
        override fun beforeCompletion() = Unit

        override fun afterCompletion(status: Int) {
            if (status != Status.STATUS_ROLLEDBACK) return
            runCatching { storage.deleteVersion(locator) }.onFailure {
                logger.warn(
                    "Stored document version {} was not removed after its transaction rolled back",
                    locator.value,
                    it
                )
            }
        }
    }

    private companion object {
        const val MAXIMUM_DOUBLINGS = 7
        val MAXIMUM_BACKOFF: Duration = Duration.ofHours(24)
        val logger = LoggerFactory.getLogger(DocumentVersionStorageCleanupService::class.java)
    }
}
