package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.*
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.fields.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestResponseWorkspaceServiceTest
{
    @Test
    fun `false unknown and missing condition results cannot expose retained Fields through a fresh workspace`()
    {
        for (state in listOf(InformationRequestConditionEvaluationState.FALSE, InformationRequestConditionEvaluationState.UNKNOWN, null))
        {
            val fixture = Fixture()
            whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(state?.let {
                listOf(InformationRequestConditionEvaluationProjection("conditional-data", 1, it,
                    sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"))
            }.orEmpty())
            val result = fixture.service.load(fixture.request.id, fixture.access)
            assertFalse(result.responses.any { it.fieldValues.any { field -> field.value == JsonPrimitive("retained-secret") } })
            assertFalse(result.schemaAssignment?.fields.orEmpty().any { it.value == JsonPrimitive("retained-secret") })
            assertTrue(result.responses.any { it.informationRequestRequirementId == fixture.visible.id })
        }
    }

    @Test
    fun `a hidden envelope cannot be replaced with its retained Field values even while its condition is true`()
    {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]")))
        whenever(fixture.responses.findAllForRequest(fixture.request.id)).thenReturn(listOf(InformationRequestResponse().apply {
            informationRequestId = fixture.request.id
            informationRequestRequirementId = fixture.hidden.id
            activeInResponse = false
        }))
        val result = fixture.service.load(fixture.request.id, fixture.access)
        assertFalse(result.responses.any { it.fieldValues.any { field -> field.value == JsonPrimitive("retained-secret") } })
        assertFalse(result.schemaAssignment?.fields.orEmpty().any { it.value == JsonPrimitive("retained-secret") })
    }

    @Test
    fun `the same collected Field stays visible in one occurrence while another is hidden`()
    {
        val fixture = Fixture()
        fixture.visible.sourceTemplateBindingId = fixture.hidden.sourceTemplateBindingId
        fixture.visible.occurrencePath = "items[1]"
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.FALSE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"),
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[1]")))
        whenever(fixture.fields.getAssignment(any<FieldValueReadCommand>())).thenAnswer {
            val command = it.getArgument<FieldValueReadCommand>(0)
            responseFieldProjection(fixture.request.id, listOf(fixture.hiddenFieldId to
                if (command.valueSet == FieldValueSetRef.Occurrence("items[1]")) "visible-answer" else "retained-secret"))
        }
        val result = fixture.service.load(fixture.request.id, fixture.access)
        assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
        assertEquals(JsonPrimitive("visible-answer"), result.responses.single().fieldValues.single().value)
        assertTrue(result.schemaAssignment!!.fields.isEmpty())
    }

    @Test
    fun `a removed occurrence's requirement is excluded from the workspace though its row still exists`()
    {
        val fixture = Fixture()
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())

        val result = fixture.service.load(fixture.request.id, fixture.access)

        assertTrue(result.occurrences.isEmpty())
        assertFalse(result.responses.any { it.informationRequestRequirementId == fixture.hidden.id })
        assertFalse(result.responses.any { it.informationRequestRequirementId == fixture.visible.id })
    }

    private class Fixture
    {
        val request = InformationRequest().apply { exchangeId = UUID.randomUUID(); templateVersionId = UUID.randomUUID() }
        val access = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
        val hiddenFieldId = UUID.randomUUID()
        val visibleFieldId = UUID.randomUUID()
        val hidden = requirement()
        val visible = requirement()
        val conditions = mock<InformationRequestConditionEvaluationService>()
        val responses = mock<InformationRequestResponseRepository>()
        val query = mock<InformationRequestQueryService>()
        val versions = mock<InformationRequestTemplateVersionRepository>()
        val templates = mock<InformationRequestTemplateProjectionLoader>()
        val requirements = mock<InformationRequestRequirementRepository>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val fields = mock<SchemaAssignmentService>()
        val authorization = mock<AuthorizationService>()
        val service = InformationRequestResponseWorkspaceService(query, versions, templates, occurrenceRepository, requirements,
            responses, fields, authorization, conditions)

        init
        {
            val version = InformationRequestTemplateVersion().apply { id = request.templateVersionId }
            whenever(query.findById(request.id, access)).thenReturn(request)
            whenever(versions.findById(version.id)).thenReturn(version)
            whenever(templates.loadVersion(version)).thenReturn(InformationRequestTemplateVersionDto(version.id,
                UUID.randomUUID(), 1, InformationRequestTemplateStatus.PUBLISHED,
                sections = listOf(InformationRequestTemplateSectionDto(UUID.randomUUID(), "data", "Data",
                    requirements = listOf(template(hidden, hiddenFieldId, "conditional-data"), template(visible, visibleFieldId, null)))),
                createdAt = Timestamp.from(Instant.now())))
            whenever(requirements.findForRequest(request.id)).thenReturn(listOf(hidden, visible))
            whenever(occurrenceRepository.findForRequest(request.id)).thenAnswer {
                requirements.findForRequest(request.id).map { it.occurrencePath }
                    .filterNot { InformationRequestOccurrencePath.isRoot(it) }
                    .distinct()
                    .map { path ->
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            sourceTemplateGroupId = UUID.randomUUID()
                            occurrencePath = path
                        }
                    }
            }
            whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
            whenever(fields.getAssignment(any<FieldValueReadCommand>())).thenReturn(
                responseFieldProjection(request.id, listOf(hiddenFieldId to "retained-secret", visibleFieldId to "visible-answer")))
        }

        private fun requirement() = InformationRequestRequirement().apply {
            informationRequestId = request.id
            sourceTemplateBindingId = UUID.randomUUID()
            sourceTemplateRequirementId = UUID.randomUUID()
            occurrencePath = "items[0]"
        }

        private fun template(requirement: InformationRequestRequirement, fieldId: UUID, rule: String?) =
            InformationRequestTemplateRequirementDto(requirement.sourceTemplateBindingId, requirement.sourceTemplateRequirementId,
                "item-${requirement.id}", InformationRequestRequirementType.FIELD, "Provide data",
                responseMode = InformationRequestResponseMode.PROVIDE, requiredness = InformationRequestRequiredness.OPTIONAL,
                contributorRole = InformationRequestContributorRole.CONTRIBUTOR, reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
                conditionalRuleKey = rule, collectedFieldDefinitionId = fieldId)
    }
}

internal fun responseFieldProjection(requestId: UUID, values: List<Pair<UUID, String>>): SchemaAssignmentDto
{
    val bindings = values.mapIndexed { index, (id, _) -> SchemaFieldBindingDto(id, id, id, "process", "field-$index",
        "Process data", FieldValueType.SHORT_TEXT, index, isRequired = false, isReadOnly = false,
        visibility = FieldDataClassification.PUBLIC, constraints = FieldConstraints()) }
    return SchemaAssignmentDto(UUID.randomUUID(), ResourceType.INFORMATION_REQUEST.name, requestId, UUID.randomUUID(),
        UUID.randomUUID(), "process", "Process data", 1, SchemaAssignmentSource.API, Timestamp.from(Instant.now()),
        bindings = bindings, fields = values.map { (id, value) -> FieldValueDto(id, id, "process", "data", "Data",
            FieldValueType.SHORT_TEXT, false, JsonPrimitive(value)) }, etag = "\"fields-1\"")
}
