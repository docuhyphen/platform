package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateVersionCapabilityRepository :
    BaseRepository<InformationRequestTemplateVersionCapability>(
        InformationRequestTemplateVersionCapability::class.java,
    )
{
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
