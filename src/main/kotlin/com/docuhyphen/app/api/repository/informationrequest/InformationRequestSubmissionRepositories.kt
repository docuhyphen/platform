package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackageAttestation
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionSupportingLink
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionWithdrawal
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestSubmissionPackageRepository :
    BaseRepository<InformationRequestSubmissionPackage>(InformationRequestSubmissionPackage::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestSubmissionPackage> =
        entityManager.createQuery(
            """
            SELECT submission
            FROM InformationRequestSubmissionPackage submission
            WHERE submission.informationRequestId = :requestId
            ORDER BY submission.packageNumber
            """.trimIndent(),
            InformationRequestSubmissionPackage::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun nextPackageNumber(requestId: UUID): Int =
        (
            entityManager.createQuery(
                """
                SELECT MAX(submission.packageNumber)
                FROM InformationRequestSubmissionPackage submission
                WHERE submission.informationRequestId = :requestId
                """.trimIndent(),
                Integer::class.java,
            )
                .setParameter("requestId", requestId)
                .singleResult
                ?.toInt() ?: 0
            ) + 1
}

@ApplicationScoped
class InformationRequestSubmissionItemRepository :
    BaseRepository<InformationRequestSubmissionItem>(InformationRequestSubmissionItem::class.java)
{
    fun findForPackages(packageIds: Collection<UUID>): List<InformationRequestSubmissionItem>
    {
        if (packageIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT item
            FROM InformationRequestSubmissionItem item
            WHERE item.packageId IN :packageIds
            ORDER BY item.occurrencePath, item.requirementKey
            """.trimIndent(),
            InformationRequestSubmissionItem::class.java,
        )
            .setParameter("packageIds", packageIds)
            .resultList
    }
}

@ApplicationScoped
class InformationRequestSubmissionEvidenceRepository :
    BaseRepository<InformationRequestSubmissionEvidence>(InformationRequestSubmissionEvidence::class.java)
{
    fun findForPackages(packageIds: Collection<UUID>): List<InformationRequestSubmissionEvidence>
    {
        if (packageIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT member
            FROM InformationRequestSubmissionEvidence member
            WHERE member.packageId IN :packageIds
            ORDER BY member.evidenceArtifactId, member.evidenceVersionNumber
            """.trimIndent(),
            InformationRequestSubmissionEvidence::class.java,
        )
            .setParameter("packageIds", packageIds)
            .resultList
    }
}

@ApplicationScoped
class InformationRequestSubmissionSupportingLinkRepository :
    BaseRepository<InformationRequestSubmissionSupportingLink>(InformationRequestSubmissionSupportingLink::class.java)
{
    fun findForPackages(packageIds: Collection<UUID>): List<InformationRequestSubmissionSupportingLink>
    {
        if (packageIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT link
            FROM InformationRequestSubmissionSupportingLink link
            WHERE link.packageId IN :packageIds
            """.trimIndent(),
            InformationRequestSubmissionSupportingLink::class.java,
        )
            .setParameter("packageIds", packageIds)
            .resultList
    }
}

@ApplicationScoped
class InformationRequestSubmissionAttestationRepository :
    BaseRepository<InformationRequestSubmissionAttestation>(InformationRequestSubmissionAttestation::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestSubmissionAttestation> =
        entityManager.createQuery(
            """
            SELECT attestation
            FROM InformationRequestSubmissionAttestation attestation
            WHERE attestation.informationRequestId = :requestId
            ORDER BY attestation.sequenceNumber
            """.trimIndent(),
            InformationRequestSubmissionAttestation::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun nextSequenceNumber(requestId: UUID): Int =
        (
            entityManager.createQuery(
                """
                SELECT MAX(attestation.sequenceNumber)
                FROM InformationRequestSubmissionAttestation attestation
                WHERE attestation.informationRequestId = :requestId
                """.trimIndent(),
                Integer::class.java,
            )
                .setParameter("requestId", requestId)
                .singleResult
                ?.toInt() ?: 0
            ) + 1
}

@ApplicationScoped
class InformationRequestSubmissionPackageAttestationRepository :
    BaseRepository<InformationRequestSubmissionPackageAttestation>(InformationRequestSubmissionPackageAttestation::class.java)
{
    fun findForPackages(packageIds: Collection<UUID>): List<InformationRequestSubmissionPackageAttestation>
    {
        if (packageIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT member
            FROM InformationRequestSubmissionPackageAttestation member
            WHERE member.packageId IN :packageIds
            """.trimIndent(),
            InformationRequestSubmissionPackageAttestation::class.java,
        )
            .setParameter("packageIds", packageIds)
            .resultList
    }
}

@ApplicationScoped
class InformationRequestSubmissionWithdrawalRepository :
    BaseRepository<InformationRequestSubmissionWithdrawal>(InformationRequestSubmissionWithdrawal::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestSubmissionWithdrawal> =
        entityManager.createQuery(
            """
            SELECT withdrawal
            FROM InformationRequestSubmissionWithdrawal withdrawal
            WHERE withdrawal.informationRequestId = :requestId
            """.trimIndent(),
            InformationRequestSubmissionWithdrawal::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
