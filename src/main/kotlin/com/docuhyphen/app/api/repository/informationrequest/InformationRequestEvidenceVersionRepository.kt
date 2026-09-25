package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceStoredUsage
import com.docuhyphen.app.api.repository.BaseRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for recorded evidence versions. Rows are only inserted and read: rewriting or
 * removing one would restate history that a later submission already relies on, so this repository
 * refuses those calls before they reach the database, which refuses them as well.
 */
@ApplicationScoped
class InformationRequestEvidenceVersionRepository :
    BaseRepository<InformationRequestEvidenceVersion>(InformationRequestEvidenceVersion::class.java)
{
    fun findForArtifact(artifactId: UUID): List<InformationRequestEvidenceVersion> =
        entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestEvidenceVersion version
            WHERE version.evidenceArtifactId = :artifactId
            ORDER BY version.versionNumber
            """.trimIndent(),
            InformationRequestEvidenceVersion::class.java,
        )
            .setParameter("artifactId", artifactId)
            .resultList

    fun findLatest(artifactId: UUID): InformationRequestEvidenceVersion? =
        entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestEvidenceVersion version
            WHERE version.evidenceArtifactId = :artifactId
            ORDER BY version.versionNumber DESC
            """.trimIndent(),
            InformationRequestEvidenceVersion::class.java,
        )
            .setParameter("artifactId", artifactId)
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    fun storedUsageForRequest(requestId: UUID): InformationRequestEvidenceStoredUsage =
        storedUsage(
            entityManager.createQuery(
                """
                SELECT COUNT(version.id), COALESCE(SUM(content.contentLength), 0)
                FROM InformationRequestEvidenceVersion version, DocumentVersion content
                WHERE content.id = version.documentVersionId
                  AND version.informationRequestId = :requestId
                """.trimIndent(),
                Array<Any>::class.java,
            )
                .setParameter("requestId", requestId)
                .singleResult,
        )

    fun storedUsageForUploader(requestId: UUID, uploader: PrincipalRef): InformationRequestEvidenceStoredUsage =
        storedUsage(
            entityManager.createQuery(
                """
                SELECT COUNT(version.id), COALESCE(SUM(content.contentLength), 0)
                FROM InformationRequestEvidenceVersion version, DocumentVersion content
                WHERE content.id = version.documentVersionId
                  AND version.informationRequestId = :requestId
                  AND version.createdByPrincipalKind = :uploaderKind
                  AND version.createdByPrincipalId = :uploaderId
                """.trimIndent(),
                Array<Any>::class.java,
            )
                .setParameter("requestId", requestId)
                .setParameter("uploaderKind", uploader.kind)
                .setParameter("uploaderId", uploader.id)
                .singleResult,
        )

    private fun storedUsage(row: Array<Any>): InformationRequestEvidenceStoredUsage =
        InformationRequestEvidenceStoredUsage(
            files = (row[0] as Number).toLong(),
            bytes = (row[1] as Number).toLong(),
        )

    override fun update(entity: InformationRequestEvidenceVersion): InformationRequestEvidenceVersion =
        throw UnsupportedOperationException(REWRITE_REFUSAL)

    override fun delete(entity: InformationRequestEvidenceVersion) =
        throw UnsupportedOperationException(REWRITE_REFUSAL)

    override fun deleteById(id: UUID) = throw UnsupportedOperationException(REWRITE_REFUSAL)

    private companion object
    {
        const val REWRITE_REFUSAL =
            "A recorded evidence version is append-only: record a new version instead of changing one"
    }
}
