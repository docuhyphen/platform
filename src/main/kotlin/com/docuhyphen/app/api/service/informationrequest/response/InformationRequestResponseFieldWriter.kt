package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestFieldResponsePatchResult
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.util.*

@ApplicationScoped
class InformationRequestResponseFieldWriter @Inject constructor(
    private val schemaAssignmentService: SchemaAssignmentService,
    private val fieldsReader: FieldsReadFacade,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val activeResponses: InformationRequestActiveResponseReader,
) {
    internal fun writeBatchedFieldValues(
        requestId: UUID,
        requirements: Map<UUID, InformationRequestRequirement>,
        access: RequestAccessContext,
        patches: List<InformationRequestResponsePatch>,
    ): Map<UUID, InformationRequestFieldResponsePatchResult> {
        val fieldPatches = patches.filter { it.fieldValues != null }
        requireEntriesBoundToRequirements(fieldPatches, requirements)
        val groups = fieldPatches.groupBy { valueSetRef(requirements.getValue(it.requirementId).occurrencePath) }
        val preconditions = groups.mapValues { (valueSet, groupPatches) ->
            sharedPrecondition(valueSet, groupPatches, requirements)
        }

        val resultsByRequirement = mutableMapOf<UUID, InformationRequestFieldResponsePatchResult>()
        groups.forEach { (valueSet, groupPatches) ->
            val precondition = preconditions.getValue(valueSet)
            val entries = groupPatches.flatMap { it.fieldValues!!.entries }
            val projection = writeFieldEntries(requestId, access, valueSet, entries, precondition)
            val assignment =
                fieldsReader.assignmentFor(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId))
                    ?: throw IllegalStateException("No schema is assigned")
            val valueSetId = fieldsReader.valueSetOf(assignment.id, valueSet)?.id
                ?: throw IllegalStateException("Field Value Set was not stored")
            val changed = when (precondition) {
                is FieldsPrecondition.ExpectedRevision -> projection.etag !in precondition.etags
                else -> true
            }
            val result = InformationRequestFieldResponsePatchResult(valueSetId, projection, changed)
            groupPatches.forEach { patch -> resultsByRequirement[patch.requirementId] = result }
        }
        return resultsByRequirement
    }

    internal fun clearValuesOfValuelessAnswers(
        requestId: UUID,
        requirements: Map<UUID, InformationRequestRequirement>,
        access: RequestAccessContext,
        patches: List<InformationRequestResponsePatch>,
    ) {
        val resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId)
        val fieldsAccess = FieldsAccessContext(access.principal, access.authorization)
        patches.filter { it.fieldValues == null && it.disposition?.let { disposition -> disposition !in KEEPS_VALUE } == true }
            .forEach { patch ->
                val requirement = requirements.getValue(patch.requirementId)
                val fieldDefinitionId = bindingRepository.findById(requirement.sourceTemplateBindingId)
                    ?.collectedFieldDefinitionId ?: return@forEach
                val valueSet = valueSetRef(requirement.occurrencePath)
                val projection =
                    schemaAssignmentService.getAssignment(FieldValueReadCommand(resource, fieldsAccess, valueSet))
                        ?: return@forEach
                val field = InformationRequestActiveResponseProjection.field(
                    projection,
                    fieldDefinitionId
                ).fields.singleOrNull()
                    ?.takeUnless { it.isEmpty } ?: return@forEach
                schemaAssignmentService.clearValues(
                    FieldValueClearCommand(
                        resource,
                        fieldsAccess,
                        setOf(field.fieldContractId),
                        valueSet,
                        FieldsPrecondition.ExpectedRevision(requireNotNull(projection.etag)),
                    ),
                )
            }
    }

    fun readFieldValues(
        requestId: UUID,
        requirement: InformationRequestRequirement,
        access: RequestAccessContext,
    ): SchemaAssignmentDto {
        val projection = requireNotNull(
            schemaAssignmentService.getAssignment(
                FieldValueReadCommand(
                    resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
                    access = FieldsAccessContext(access.principal, access.authorization),
                    valueSet = valueSetRef(requirement.occurrencePath),
                ),
            ),
        ) { "No schema is assigned" }
        return activeResponses.activeFieldProjection(requestId, requirement, projection)
    }

    private fun requireEntriesBoundToRequirements(
        patches: List<InformationRequestResponsePatch>,
        requirements: Map<UUID, InformationRequestRequirement>,
    ) {
        val problems = patches.flatMap { patch ->
            val entries = patch.fieldValues?.entries.orEmpty()
            if (entries.isEmpty()) return@flatMap emptyList()
            val requirement = requirements.getValue(patch.requirementId)
            val collectedFieldDefinitionId =
                bindingRepository.findById(requirement.sourceTemplateBindingId)?.collectedFieldDefinitionId
            entries
                .filter { entry ->
                    val fieldDefinitionId = fieldsReader.contract(entry.fieldContractId)?.fieldDefinitionId
                    fieldDefinitionId == null || fieldDefinitionId != collectedFieldDefinitionId
                }
                .map { entry ->
                    requirementProblem(
                        InformationRequestErrorCatalog.FIELD_ENTRY_NOT_BOUND,
                        "This Field is not the one this Requirement collects",
                        requirement,
                        entry.fieldContractId,
                    )
                }
        }
        if (problems.isNotEmpty()) {
            throw InformationRequestItemRefusalException(
                InformationRequestErrorCatalog.FIELD_ENTRY_NOT_BOUND,
                "A Field entry does not belong to the Requirement it was sent for",
                problems,
            )
        }
    }

    private fun JsonElement.empties(): Boolean = when (this) {
        is JsonNull -> true
        is JsonArray -> isEmpty()
        is JsonPrimitive -> isString && content.isBlank()
        else -> false
    }

    private fun writeFieldEntries(
        requestId: UUID,
        access: RequestAccessContext,
        valueSet: FieldValueSetRef,
        entries: List<FieldValueEntry>,
        precondition: FieldsPrecondition,
    ): SchemaAssignmentDto {
        val resource = FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId)
        val fieldsAccess = FieldsAccessContext(access.principal, access.authorization)
        val (clears, values) = entries.partition { it.value.empties() }
        if (clears.isEmpty()) {
            return schemaAssignmentService.setValues(
                FieldValueWriteCommand(
                    resource,
                    fieldsAccess,
                    values,
                    valueSet,
                    precondition
                )
            )
        }
        val cleared = schemaAssignmentService.clearValues(
            FieldValueClearCommand(
                resource,
                fieldsAccess,
                clears.map { it.fieldContractId }.toSet(),
                valueSet,
                precondition
            ),
        )
        if (values.isEmpty()) return cleared
        return schemaAssignmentService.setValues(
            FieldValueWriteCommand(
                resource,
                fieldsAccess,
                values,
                valueSet,
                FieldsPrecondition.ExpectedRevision(requireNotNull(cleared.etag)),
            ),
        )
    }

    private fun sharedPrecondition(
        valueSet: FieldValueSetRef,
        groupPatches: List<InformationRequestResponsePatch>,
        requirements: Map<UUID, InformationRequestRequirement>,
    ): FieldsPrecondition {
        val distinct = groupPatches.map { it.fieldValues!!.precondition }.distinct()
        if (distinct.size == 1) return distinct.single()
        val occurrencePath =
            (valueSet as? FieldValueSetRef.Occurrence)?.occurrencePath ?: InformationRequestOccurrencePath.ROOT
        throw InformationRequestItemRefusalException(
            InformationRequestErrorCatalog.FIELD_PRECONDITION_CONFLICT,
            "Field changes to one occurrence must state the one version they were read from",
            groupPatches.map { patch ->
                InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.FIELD_PRECONDITION_CONFLICT,
                    message = "This Field change states a different read version than another change to the same occurrence",
                    requirementId = patch.requirementId,
                    occurrencePath = requirements[patch.requirementId]?.occurrencePath ?: occurrencePath,
                )
            },
        )
    }

    private fun valueSetRef(occurrencePath: String): FieldValueSetRef =
        if (occurrencePath == InformationRequestOccurrencePath.ROOT)
            FieldValueSetRef.Root
        else
            FieldValueSetRef.Occurrence(occurrencePath)

    private companion object {
        val KEEPS_VALUE = setOf(
            InformationRequestResponseDisposition.NOT_ANSWERED,
            InformationRequestResponseDisposition.PROVIDED,
            InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
        )
    }
}

internal fun fieldPreconditionFingerprint(precondition: FieldsPrecondition): List<Any> = when (precondition) {
    FieldsPrecondition.Unconditioned -> listOf("unconditioned")
    FieldsPrecondition.Absent -> listOf("absent")
    FieldsPrecondition.ExpectedAbsent -> listOf("expected-absent")
    is FieldsPrecondition.ExpectedRevision -> listOf("expected", precondition.etags.sorted())
}
