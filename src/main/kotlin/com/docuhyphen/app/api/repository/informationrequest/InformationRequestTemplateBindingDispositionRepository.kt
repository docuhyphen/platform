package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateBindingDisposition]. Permitted answers are read for one
 * binding when resolving a single requirement, and for a whole version when projecting the
 * configuration a request was issued against.
 */
@ApplicationScoped
class InformationRequestTemplateBindingDispositionRepository :
    BaseRepository<InformationRequestTemplateBindingDisposition>(
        InformationRequestTemplateBindingDisposition::class.java,
    )
{
    fun findForBinding(templateBindingId: UUID): List<InformationRequestTemplateBindingDisposition> =
        entityManager.createQuery(
            """
            SELECT permitted
            FROM InformationRequestTemplateBindingDisposition permitted
            WHERE permitted.templateBindingId = :bindingId
            ORDER BY permitted.disposition
            """.trimIndent(),
            InformationRequestTemplateBindingDisposition::class.java,
        )
            .setParameter("bindingId", templateBindingId)
            .resultList

    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateBindingDisposition> =
        entityManager.createQuery(
            """
            SELECT permitted
            FROM InformationRequestTemplateBindingDisposition permitted
            WHERE permitted.templateVersionId = :versionId
            ORDER BY permitted.templateBindingId, permitted.disposition
            """.trimIndent(),
            InformationRequestTemplateBindingDisposition::class.java,
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
            DELETE FROM InformationRequestTemplateBindingDisposition permitted
            WHERE permitted.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
