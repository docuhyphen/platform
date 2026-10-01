package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestImportedValueFieldTarget
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestValueComparison
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.fields.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@ApplicationScoped
class InformationRequestImportedValueCanonicalizer @Inject constructor(
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val schemaAssignmentRepository: SchemaAssignmentRepository,
    private val schemaFieldBindingRepository: SchemaFieldBindingRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val fieldValueValidator: FieldValueValidator,
    private val typeRegistry: FieldTypeRegistry,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
)
{
    fun canonicalize(requirement: InformationRequestRequirement, valueType: FieldValueType, value: JsonElement): String
    {
        val target = fieldTarget(requirement)
        val canonical = if (target != null)
        {
            if (target.contract.valueType != valueType)
            {
                throw InformationRequestCommandRequestException("An imported value has the type of the Field its Requirement collects")
            }
            refusedAsCommand { fieldValueValidator.canonicalize(target.contract, value) }
        }
        else
        {
            if (valueType in SELECTION_TYPES)
            {
                throw InformationRequestCommandRequestException("A value for a Requirement that collects no Field is a single scalar")
            }
            refusedAsCommand {
                typeRegistry.contractFor(valueType).canonicalize(value, FieldConstraints.EMPTY, emptyList())
            }
        }
        if (canonical.isEmpty) throw InformationRequestCommandRequestException("An imported value states a value")
        return render(canonical, target?.scale)
    }

    fun compare(
        requirement: InformationRequestRequirement,
        imported: InformationRequestImportedValue,
        response: InformationRequestResponse?,
    ): InformationRequestValueComparison
    {
        val target = fieldTarget(requirement) ?: return InformationRequestValueComparison.NotComparable
        val valueSetId = response?.fieldValueSetId ?: return InformationRequestValueComparison.NoAnswer
        val revision = fieldValueRevisions.latestRevision(
            FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requirement.informationRequestId),
            valueSetId,
            target.fieldDefinitionId,
        ) ?: return InformationRequestValueComparison.NoAnswer
        val answered = fieldValueRevisions.canonicalValueOf(revision.id)
            ?.takeUnless { it.isEmpty }
            ?: return InformationRequestValueComparison.NoAnswer
        val proposed =
            fieldValueValidator.canonicalize(target.contract, Json.parseToJsonElement(imported.canonicalValue))
        return if (sameValue(proposed, answered)) InformationRequestValueComparison.Matches
        else InformationRequestValueComparison.Differs(render(answered, target.scale))
    }

    private fun fieldTarget(requirement: InformationRequestRequirement): InformationRequestImportedValueFieldTarget?
    {
        val fieldDefinitionId =
            bindingRepository.findById(requirement.sourceTemplateBindingId)?.collectedFieldDefinitionId ?: return null
        val assignment = schemaAssignmentRepository.findByResource(
            ResourceType.INFORMATION_REQUEST.name,
            requirement.informationRequestId
        )
            ?: unresolved()
        val binding = schemaFieldBindingRepository.findByVersion(assignment.schemaVersionId)
            .firstOrNull { it.fieldDefinitionId == fieldDefinitionId }
            ?: unresolved()
        val contract = fieldContractRepository.findById(binding.fieldContractId) ?: unresolved()
        return InformationRequestImportedValueFieldTarget(
            fieldDefinitionId,
            contract,
            FieldConstraints.parse(contract.constraintsJson).scale
        )
    }

    private fun sameValue(proposed: CanonicalFieldValue, answered: CanonicalFieldValue): Boolean =
        proposed.type == answered.type && when (proposed.type)
        {
            FieldValueType.SHORT_TEXT, FieldValueType.LONG_TEXT -> proposed.textValue == answered.textValue
            FieldValueType.BOOLEAN -> proposed.boolValue == answered.boolValue
            FieldValueType.INTEGER, FieldValueType.DECIMAL ->
                proposed.numberValue != null && answered.numberValue != null &&
                        proposed.numberValue.compareTo(answered.numberValue) == 0

            FieldValueType.DATE -> proposed.dateValue == answered.dateValue
            FieldValueType.DATE_TIME -> proposed.datetimeValue == answered.datetimeValue
            FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT ->
                proposed.selectionCodes.toSet() == answered.selectionCodes.toSet()
        }

    private fun render(value: CanonicalFieldValue, scale: Int?): String
    {
        val stored = FieldValue().also { CanonicalValueCodec.applyTo(it, value) }
        return CanonicalValueCodec.toJson(stored, value.selectionCodes, scale).toString()
    }

    private fun <T> refusedAsCommand(block: () -> T): T =
        try
        {
            block()
        }
        catch (exception: FieldValidationException)
        {
            throw InformationRequestCommandRequestException(
                exception.message ?: "The imported value is not valid for its type"
            )
        }

    private fun unresolved(): Nothing =
        throw IllegalStateException("The Field this Requirement collects cannot be resolved for the request")

    private companion object
    {
        val SELECTION_TYPES = setOf(FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT)
    }
}
