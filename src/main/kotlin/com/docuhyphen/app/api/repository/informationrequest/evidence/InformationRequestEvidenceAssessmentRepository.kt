package com.docuhyphen.app.api.repository.informationrequest.evidence

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessment
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAssessmentKind
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceSourceKind
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceMalwareOutcome
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceScannerEngine
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestEvidenceAssessmentRepository :
    BaseRepository<InformationRequestEvidenceAssessment>(InformationRequestEvidenceAssessment::class.java)
{
    fun findForVersions(versionIds: Collection<UUID>): List<InformationRequestEvidenceAssessment> =
        if (versionIds.isEmpty()) emptyList()
        else entityManager.createQuery(
            """
            SELECT assessment
            FROM InformationRequestEvidenceAssessment assessment
            WHERE assessment.evidenceVersionId IN :versionIds
            ORDER BY assessment.assessedAt, assessment.createdAt, assessment.id
            """.trimIndent(),
            InformationRequestEvidenceAssessment::class.java,
        )
            .setParameter("versionIds", versionIds)
            .resultList

    fun findReusableMalwareScan(
        contentHashAlgorithm: DocumentVersionContentHashAlgorithm,
        contentHash: String,
        contentLength: Long,
        engine: InformationRequestEvidenceScannerEngine,
        signatureVersion: String,
        assessedSince: Instant,
    ): InformationRequestEvidenceAssessment? =
        entityManager.createQuery(
            """
            SELECT assessment
            FROM InformationRequestEvidenceAssessment assessment
            WHERE assessment.contentHash = :contentHash
              AND assessment.contentHashAlgorithm = :contentHashAlgorithm
              AND assessment.contentLength = :contentLength
              AND assessment.assessmentKind = :kind
              AND assessment.outcome IN :settled
              AND assessment.engineName = :engineName
              AND assessment.engineVersion = :engineVersion
              AND assessment.signatureVersion = :signatureVersion
              AND assessment.productionEligible = :productionEligible
              AND assessment.assessedAt >= :assessedSince
            ORDER BY assessment.assessedAt DESC, assessment.createdAt DESC, assessment.id DESC
            """.trimIndent(),
            InformationRequestEvidenceAssessment::class.java,
        )
            .setParameter("contentHash", contentHash)
            .setParameter("contentHashAlgorithm", contentHashAlgorithm)
            .setParameter("contentLength", contentLength)
            .setParameter("kind", InformationRequestEvidenceAssessmentKind.MALWARE_SCAN)
            .setParameter("settled", SETTLED_SCAN_OUTCOMES)
            .setParameter("engineName", engine.name)
            .setParameter("engineVersion", engine.version)
            .setParameter("signatureVersion", signatureVersion)
            .setParameter("productionEligible", engine.productionEligible)
            .setParameter("assessedSince", Timestamp.from(assessedSince))
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    fun findVersionsDueForMalwareScan(retryBefore: Instant, rescanBefore: Instant, limit: Int): List<UUID> =
        entityManager.createQuery(
            """
            SELECT version.id
            FROM InformationRequestEvidenceVersion version
            WHERE version.sourceKind = :fileBacked
              AND NOT EXISTS (
                  SELECT assessment.id
                  FROM InformationRequestEvidenceAssessment assessment
                  WHERE assessment.evidenceVersionId = version.id
                    AND assessment.assessmentKind = :kind
                    AND (assessment.outcome IN :finalOutcomes
                         OR (assessment.outcome = :clean AND assessment.assessedAt > :rescanBefore)
                         OR assessment.assessedAt > :retryBefore)
              )
            ORDER BY CASE
                         WHEN EXISTS (
                             SELECT earlier.id
                             FROM InformationRequestEvidenceAssessment earlier
                             WHERE earlier.evidenceVersionId = version.id
                               AND earlier.assessmentKind = :kind
                         ) THEN 1
                         ELSE 0
                     END,
                     version.createdAt,
                     version.id
            """.trimIndent(),
            UUID::class.java,
        )
            .setParameter("fileBacked", InformationRequestEvidenceSourceKind.DOCUMENT_VERSION)
            .setParameter("kind", InformationRequestEvidenceAssessmentKind.MALWARE_SCAN)
            .setParameter("finalOutcomes", FINAL_SCAN_OUTCOMES)
            .setParameter("clean", InformationRequestEvidenceMalwareOutcome.CLEAN.name)
            .setParameter("rescanBefore", Timestamp.from(rescanBefore))
            .setParameter("retryBefore", Timestamp.from(retryBefore))
            .setMaxResults(limit)
            .resultList

    override fun update(entity: InformationRequestEvidenceAssessment): InformationRequestEvidenceAssessment =
        throw UnsupportedOperationException(REWRITE_REFUSAL)

    override fun delete(entity: InformationRequestEvidenceAssessment) =
        throw UnsupportedOperationException(REWRITE_REFUSAL)

    override fun deleteById(id: UUID) = throw UnsupportedOperationException(REWRITE_REFUSAL)

    private companion object
    {
        const val REWRITE_REFUSAL = "A recorded evidence assessment is append-only: record a new assessment instead"

        val SETTLED_SCAN_OUTCOMES =
            InformationRequestEvidenceMalwareOutcome.entries.filter { it.settled }.map { it.name }

        val FINAL_SCAN_OUTCOMES = listOf(
            InformationRequestEvidenceMalwareOutcome.MALWARE_DETECTED.name,
            InformationRequestEvidenceMalwareOutcome.SKIPPED.name,
        )
    }
}
