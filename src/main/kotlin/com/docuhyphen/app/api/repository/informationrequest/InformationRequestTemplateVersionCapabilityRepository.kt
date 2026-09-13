package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.repository.BaseRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapability
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateVersionCapability]. A Version requires each capability
 * at one contract version, so the Version lookup answers with the whole set it froze with.
 */
@ApplicationScoped
class InformationRequestTemplateVersionCapabilityRepository :
    BaseRepository<InformationRequestTemplateVersionCapability>(
        InformationRequestTemplateVersionCapability::class.java,
    )
{
    /**
     * Asks the database for the set implied by the stored configuration. The publication trigger
     * calls the same function, so the writer and the completeness guard cannot drift into two
     * interpretations of the Template.
     */
    fun findRequiredCapabilities(templateVersionId: UUID): List<InformationRequestCapability> =
        entityManager.createNativeQuery(
            """
            SELECT capability_key
            FROM request_template_required_capabilities(?1)
            ORDER BY capability_key
            """.trimIndent(),
        )
            .setParameter(1, templateVersionId)
            .resultList
            .map { result ->
                InformationRequestCapability.fromCodeOrNull(result as? String)
                    ?: error("Unknown information request capability returned by storage: $result")
            }

    fun flushChanges()
    {
        entityManager.flush()
    }

    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateVersionCapability> =
        entityManager.createQuery(
            """
            SELECT capability
            FROM InformationRequestTemplateVersionCapability capability
            WHERE capability.templateVersionId = :versionId
            ORDER BY capability.capabilityKey
            """.trimIndent(),
            InformationRequestTemplateVersionCapability::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList
}
