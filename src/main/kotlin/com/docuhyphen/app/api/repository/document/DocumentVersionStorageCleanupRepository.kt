package com.docuhyphen.app.api.repository.document

import com.docuhyphen.app.api.model.entity.DocumentVersionStorageCleanup
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp

@ApplicationScoped
class DocumentVersionStorageCleanupRepository :
    BaseRepository<DocumentVersionStorageCleanup>(DocumentVersionStorageCleanup::class.java) {
    fun insertNow(cleanup: DocumentVersionStorageCleanup) {
        entityManager.persist(cleanup)
        entityManager.flush()
    }

    fun discharge(locator: String): Int =
        entityManager.createNativeQuery("DELETE FROM document_version_storage_cleanup WHERE locator = :locator")
            .setParameter("locator", locator)
            .executeUpdate()

    fun claimNextDue(now: Timestamp): DocumentVersionStorageCleanup? {
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(
            """
            SELECT * FROM document_version_storage_cleanup
            WHERE next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """.trimIndent(),
            DocumentVersionStorageCleanup::class.java,
        )
            .setParameter("now", now)
            .resultList as List<DocumentVersionStorageCleanup>
        return rows.firstOrNull()
    }

    fun isReferenced(locator: String): Boolean =
        entityManager.createNativeQuery("SELECT EXISTS (SELECT 1 FROM document_version WHERE storage_locator = :locator)")
            .setParameter("locator", locator)
            .singleResult as Boolean
}
