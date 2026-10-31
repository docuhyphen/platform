package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateCollectedField
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestTemplateFieldMembershipValidator @Inject constructor(
    private val fields: FieldsReadFacade,
) {
    fun requireCollectedFieldsBound(schemaVersionId: UUID?, sections: List<InformationRequestTemplateSectionRequest>) {
        val collecting = sections.flatMap { section ->
            section.requirements.mapNotNull { requirement ->
                requirement.collectedFieldDefinitionId?.let {
                    InformationRequestTemplateCollectedField(section.sectionKey, requirement.requirementKey, it)
                }
            }
        }
        if (collecting.isEmpty()) return
        val named = schemaVersionId ?: return requireCollectedFieldsExist(collecting)
        val bound = fields.schemaBindings(named).map { it.fieldDefinitionId }.toSet()
        val unbound = collecting.firstOrNull { it.fieldDefinitionId !in bound } ?: return
        throw InformationRequestTemplateValidationException(
            "Requirement ${unbound.requirementKey} collects a Field that the named Schema Version does not bind",
            sectionKey = unbound.sectionKey,
            requirementKey = unbound.requirementKey,
        )
    }

    private fun requireCollectedFieldsExist(collecting: List<InformationRequestTemplateCollectedField>) {
        val existing = fields.existingFieldDefinitionIds(collecting.map { it.fieldDefinitionId }.toSet())
        val missing = collecting.firstOrNull { it.fieldDefinitionId !in existing } ?: return
        throw InformationRequestTemplateValidationException(
            "Requirement ${missing.requirementKey} collects a Field that does not exist",
            sectionKey = missing.sectionKey,
            requirementKey = missing.requirementKey,
        )
    }

    fun requireStoredFieldsBound(schemaVersionId: UUID?, version: InformationRequestTemplateVersionDto?) {
        val stored = version ?: return
        val sections = InformationRequestTemplateConfigurationMapper.toRequest(stored).sections
        requireCollectedFieldsBound(schemaVersionId, sections)
        requireCollectedFieldsWritable(schemaVersionId, sections)
    }

    private fun requireCollectedFieldsWritable(
        schemaVersionId: UUID?,
        sections: List<InformationRequestTemplateSectionRequest>
    ) {
        val named = schemaVersionId ?: return
        val readOnly = fields.schemaBindings(named).filter { it.isReadOnly }.map { it.fieldDefinitionId }.toSet()
        if (readOnly.isEmpty()) return
        sections.forEach { section ->
            val answered = section.requirements.firstOrNull { requirement ->
                requirement.collectedFieldDefinitionId in readOnly && requirement.responseMode in ANSWERED_MODES
            } ?: return@forEach
            throw InformationRequestTemplateValidationException(
                "Requirement ${answered.requirementKey} collects a Field that the named Schema Version marks read-only, " +
                        "so no party could answer it",
                sectionKey = section.sectionKey,
                requirementKey = answered.requirementKey,
            )
        }
    }

    private companion object {
        val ANSWERED_MODES = setOf(InformationRequestResponseMode.PROVIDE, InformationRequestResponseMode.PROVIDE_ONCE)
    }
}
