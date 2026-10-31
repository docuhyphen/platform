package com.docuhyphen.app.api.service.informationrequest.occurrence

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.model.informationrequest.occurrence.InformationRequestGroupOccurrenceTemplateData
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementGroupRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestGroupOccurrenceStructure @Inject constructor(
    private val groupRepository: InformationRequestTemplateRequirementGroupRepository,
    private val groupOccurrenceRepository: InformationRequestGroupOccurrenceRepository,
) {
    internal fun findGroup(
        template: InformationRequestGroupOccurrenceTemplateData,
        groupKey: String,
    ): InformationRequestTemplateRequirementGroup? = template.groupsByKey[groupKey.trim()]

    internal fun bindingsUnder(
        template: InformationRequestGroupOccurrenceTemplateData,
        group: InformationRequestTemplateRequirementGroup,
    ): List<InformationRequestTemplateRequirementBinding> =
        template.bindings.filter { it.occurrenceAnchorKey == group.groupKey } +
                template.childrenByParent[group.id].orEmpty().flatMap { bindingsUnder(template, it) }

    internal fun materializedBindings(
        template: InformationRequestGroupOccurrenceTemplateData,
        group: InformationRequestTemplateRequirementGroup,
    ): List<InformationRequestTemplateRequirementBinding> =
        template.bindings.filter { it.occurrenceAnchorKey == group.groupKey } +
                template.childrenByParent[group.id].orEmpty()
                    .filter { it.minOccurrences > 0 }
                    .flatMap { materializedBindings(template, it) }

    internal fun changeBindings(
        template: InformationRequestGroupOccurrenceTemplateData,
        group: InformationRequestTemplateRequirementGroup,
    ): List<InformationRequestTemplateRequirementBinding> =
        materializedBindings(template, group).ifEmpty { bindingsUnder(template, group) }

    fun requireParent(
        requestId: UUID,
        group: InformationRequestTemplateRequirementGroup,
        parentOccurrenceId: UUID?,
        groupsById: Map<UUID, InformationRequestTemplateRequirementGroup>,
    ): InformationRequestGroupOccurrence? {
        if (group.parentGroupId == null) {
            if (parentOccurrenceId != null) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.GROUP_OCCURRENCE_PARENT_INVALID,
                    "A root group cannot name a parent occurrence",
                )
            }
            return null
        }
        return parentOccurrenceId?.let { groupOccurrenceRepository.findActiveByIdForUpdate(requestId, it) }
            ?.takeIf { it.sourceTemplateGroupId == group.parentGroupId && groupsById[it.sourceTemplateGroupId] != null }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_PARENT_INVALID,
                "A nested group occurrence requires an active occurrence of its parent group",
            )
    }

    fun requireAddWithinMaximum(
        group: InformationRequestTemplateRequirementGroup,
        activeSiblings: List<InformationRequestGroupOccurrence>,
    ) {
        val max = group.maxOccurrences ?: return
        if (activeSiblings.size >= max) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_CARDINALITY_INVALID,
                "This group already has the maximum number of occurrences",
            )
        }
    }

    fun activeDescendantsOf(
        requestId: UUID,
        ancestors: List<InformationRequestGroupOccurrence>,
    ): List<InformationRequestGroupOccurrence> =
        groupOccurrenceRepository.findActiveForRequestForUpdate(requestId).filter { candidate ->
            ancestors.any { candidate.occurrencePath.startsWith("${it.occurrencePath}/") }
        }

    fun activeSiblingsOf(
        request: InformationRequest,
        groupKey: String,
        parentOccurrenceId: UUID?,
    ): List<InformationRequestGroupOccurrence> {
        val group = groupRepository.findForVersion(request.templateVersionId).firstOrNull { it.groupKey == groupKey }
            ?: return emptyList()
        return groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(request.id, group.id, parentOccurrenceId)
    }

    fun remainingSiblingsOf(request: InformationRequest, occurrenceId: UUID): List<InformationRequestGroupOccurrence> {
        val removed = groupOccurrenceRepository.findById(occurrenceId)?.takeIf { it.informationRequestId == request.id }
            ?: return emptyList()
        return groupOccurrenceRepository.findActiveForGroupAndParentForUpdate(
            request.id,
            removed.sourceTemplateGroupId,
            removed.parentOccurrenceId,
        )
    }
}
