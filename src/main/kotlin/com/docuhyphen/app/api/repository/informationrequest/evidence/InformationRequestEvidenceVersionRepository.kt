package com.docuhyphen.app.api.repository.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceStoredUsage
import com.docuhyphen.app.api.repository.BaseRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

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

    fun findForIds(ids: Collection<UUID>): List<InformationRequestEvidenceVersion> {
        if (ids.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestEvidenceVersion version
            WHERE version.id IN :ids
            ORDER BY version.evidenceArtifactId, version.versionNumber
            """.trimIndent(),
            InformationRequestEvidenceVersion::class.java,
        )
            .setParameter("ids", ids)
            .resultList
    }

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

    fun storedUsageForUploaders(requestId: UUID, uploaders: Set<PrincipalRef>): InformationRequestEvidenceStoredUsage {
        if (uploaders.isEmpty()) return InformationRequestEvidenceStoredUsage(files = 0, bytes = 0)
        val rows = entityManager.createQuery(
            """
            SELECT version.createdByPrincipalKind, version.createdByPrincipalId,
                   COUNT(version.id), COALESCE(SUM(content.contentLength), 0)
            FROM InformationRequestEvidenceVersion version, DocumentVersion content
            WHERE content.id = version.documentVersionId
              AND version.informationRequestId = :requestId
              AND version.createdByPrincipalId IN :uploaderIds
            GROUP BY version.createdByPrincipalKind, version.createdByPrincipalId
            """.trimIndent(),
            Array<Any>::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("uploaderIds", uploaders.map(PrincipalRef::id))
            .resultList
            .filter { row -> PrincipalRef(row[0] as PrincipalKind, row[1] as UUID) in uploaders }
        return InformationRequestEvidenceStoredUsage(
            files = rows.sumOf { (it[2] as Number).toLong() },
            bytes = rows.sumOf { (it[3] as Number).toLong() },
        )
    }

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
