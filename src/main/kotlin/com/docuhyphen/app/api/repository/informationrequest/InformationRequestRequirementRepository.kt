package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestRequirementRepository :
    BaseRepository<InformationRequestRequirement>(InformationRequestRequirement::class.java)
{
    fun inactiveRequirementIds(requestId: UUID): Set<UUID> =
        entityManager.createNativeQuery(
            """
            SELECT requirement.id FROM information_request_requirement requirement
            LEFT JOIN information_request_requirement_current current_revision
              ON current_revision.information_request_requirement_id = requirement.id
            LEFT JOIN information_request_requirement_revision revision ON revision.id = current_revision.current_revision_id
            WHERE requirement.information_request_id = :requestId
              AND (revision.id IS NULL OR revision.effective_to IS NOT NULL OR
                   (requirement.occurrence_path <> 'root' AND NOT EXISTS (
                       SELECT 1 FROM information_request_group_occurrence occurrence
                       WHERE occurrence.information_request_id = requirement.information_request_id
                         AND occurrence.occurrence_path = requirement.occurrence_path AND occurrence.removed_at IS NULL
                   )))
            """.trimIndent(),
            UUID::class.java,
        ).setParameter("requestId", requestId).resultList.map { it as UUID }.toSet()

    fun findForRequest(requestId: UUID): List<InformationRequestRequirement> =
        entityManager.createQuery(
            """
            SELECT requirement
            FROM InformationRequestRequirement requirement
            WHERE requirement.informationRequestId = :requestId
              AND requirement.sourceTemplateVersionId = (
                  SELECT request.templateVersionId
                  FROM InformationRequest request
                  WHERE request.id = :requestId
              )
            ORDER BY requirement.occurrencePath, requirement.id
            """.trimIndent(),
            InformationRequestRequirement::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findAllForRequest(requestId: UUID): List<InformationRequestRequirement> =
        entityManager.createQuery(
            """
            SELECT requirement
            FROM InformationRequestRequirement requirement
            WHERE requirement.informationRequestId = :requestId
            ORDER BY requirement.occurrencePath, requirement.id
            """.trimIndent(),
            InformationRequestRequirement::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun flushChanges()
    {
        entityManager.flush()
    }

    fun findCompartmentedIds(requestIds: Collection<UUID>): Set<UUID> {
        if (requestIds.isEmpty()) return emptySet()
        return entityManager.createNativeQuery(
            """
            SELECT DISTINCT requirement.id
            FROM information_request_requirement requirement
            WHERE requirement.information_request_id IN (:requests)
              AND (EXISTS (SELECT 1
                           FROM information_request_template_requirement_binding binding
                           WHERE binding.id = requirement.source_template_binding_id
                             AND binding.confidentiality_compartment_key IS NOT NULL)
                OR EXISTS (SELECT 1
                           FROM information_request_requirement_revision revision
                                    JOIN information_request_template_requirement_binding binding
                                         ON binding.id = revision.source_template_binding_id
                           WHERE revision.information_request_requirement_id = requirement.id
                             AND binding.confidentiality_compartment_key IS NOT NULL))
            """.trimIndent(),
        )
            .setParameter("requests", requestIds)
            .resultList
            .map { it as UUID }
            .toSet()
    }

    fun findOccurrence(
        requestId: UUID,
        templateBindingId: UUID,
        occurrencePath: String,
    ): InformationRequestRequirement? =
        entityManager.createQuery(
            """
            SELECT requirement
            FROM InformationRequestRequirement requirement
            WHERE requirement.informationRequestId = :requestId
              AND requirement.sourceTemplateBindingId = :templateBindingId
              AND requirement.occurrencePath = :occurrencePath
            """.trimIndent(),
            InformationRequestRequirement::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("templateBindingId", templateBindingId)
            .setParameter("occurrencePath", occurrencePath)
            .resultList
            .firstOrNull()
}
