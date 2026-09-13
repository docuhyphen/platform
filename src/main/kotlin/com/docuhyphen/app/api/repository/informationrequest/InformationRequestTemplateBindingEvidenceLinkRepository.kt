package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateBindingEvidenceLink]. Links are read from the supported
 * end when rendering one requirement, from the supporting end when a requested Document needs to
 * report what depends on it, and for a whole version when projecting the configuration.
 */
@ApplicationScoped
class InformationRequestTemplateBindingEvidenceLinkRepository :
    BaseRepository<InformationRequestTemplateBindingEvidenceLink>(
        InformationRequestTemplateBindingEvidenceLink::class.java,
    )
{
    fun findForBinding(templateBindingId: UUID): List<InformationRequestTemplateBindingEvidenceLink> =
        entityManager.createQuery(
            """
            SELECT link
            FROM InformationRequestTemplateBindingEvidenceLink link
            WHERE link.templateBindingId = :bindingId
            ORDER BY link.supportingTemplateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingEvidenceLink::class.java,
        )
            .setParameter("bindingId", templateBindingId)
            .resultList

    fun findSupportedBy(
        supportingTemplateBindingId: UUID,
    ): List<InformationRequestTemplateBindingEvidenceLink> =
        entityManager.createQuery(
            """
            SELECT link
            FROM InformationRequestTemplateBindingEvidenceLink link
            WHERE link.supportingTemplateBindingId = :supportingBindingId
            ORDER BY link.templateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingEvidenceLink::class.java,
        )
            .setParameter("supportingBindingId", supportingTemplateBindingId)
            .resultList

    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateBindingEvidenceLink> =
        entityManager.createQuery(
            """
            SELECT link
            FROM InformationRequestTemplateBindingEvidenceLink link
            WHERE link.templateVersionId = :versionId
            ORDER BY link.templateBindingId, link.supportingTemplateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingEvidenceLink::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    /**
     * Clears this part of one draft version's configuration so the whole document can be rewritten.
     * The stored freeze rule refuses the same statement against a version that is no longer a
     * draft, so a frozen version cannot lose configuration this way.
     */
    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateBindingEvidenceLink link
            WHERE link.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
