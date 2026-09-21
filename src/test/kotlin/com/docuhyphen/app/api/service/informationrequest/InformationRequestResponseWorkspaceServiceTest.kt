package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.*
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestResource
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestNoAuthRequestResource
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoAuthAccess
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

    @Test
    fun `workspace configuration omits requirements and occurrence details the caller cannot view`()
    {
        val fixture = Fixture()
        fixture.hidden.occurrencePath = "protected-items[0]"
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("protected-rule", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = setOf("protected-source"), fieldDefinitionIds = emptySet(),
                occurrencePath = "protected-items[0]")))

        val result = fixture.service.load(fixture.request.id, fixture.access)
        val requirements = result.templateVersion.sections.flatMap { it.requirements }

        assertEquals(listOf("item-${fixture.visible.id}"), requirements.map { it.requirementKey })
        assertFalse(result.templateVersion.groups.any { it.groupKey == "protected-items" })
        assertFalse(result.occurrences.any { it.occurrencePath == "protected-items[0]" })
        assertFalse(result.templateVersion.conditionRules.any { it.ruleKey == "protected-rule" })
        assertFalse(requirements.single().supportingEvidenceRequirementKeys.contains("item-${fixture.hidden.id}"))
        assertFalse(result.request.conditionEvaluations.any { it.ruleKey == "protected-rule" })
        assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
    }

    @Test
    fun `workspace configuration keeps authorized zero occurrence group controls without exposing unmaterialized requirements`()
    {
        val fixture = Fixture()

        val result = fixture.service.load(fixture.request.id, fixture.access)
        val requirements = result.templateVersion.sections.flatMap { it.requirements }

        assertTrue(result.templateVersion.groups.any { it.groupKey == "optional-items" })
        assertFalse(result.occurrences.any { it.sourceTemplateGroupId == fixture.zeroGroupId })
        assertFalse(requirements.any { it.requirementKey == fixture.zeroRequirementKey })
        verify(fixture.groupAuthorization).authorizeMaterializedBindings(
            fixture.access,
            fixture.request,
            listOf(fixture.zeroBinding),
        )
    }

    @Test
    fun `workspace omits a denied sibling occurrence even when its group has a visible occurrence`()
    {
        val fixture = Fixture()
        fixture.hidden.occurrencePath = "items[1]"
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
            assertEquals(listOf("items[0]"), result.occurrences.map { it.occurrencePath })
        }
    }

    @Test
    fun `workspace omits condition results from a denied sibling that uses the same rule`()
    {
        val fixture = Fixture()
        fixture.visible.sourceTemplateBindingId = fixture.hidden.sourceTemplateBindingId
        fixture.visible.occurrencePath = "items[1]"
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.FALSE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"),
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[1]")))

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
            assertEquals(listOf("items[1]"), result.request.conditionEvaluations.map { it.occurrencePath })
        }
    }

    @Test
    fun `workspace keeps the ancestors of an authorized nested occurrence without disclosing sibling branches`()
    {
        val fixture = Fixture()
        fixture.visible.occurrencePath = "items[0]/entries[0]"
        fixture.hidden.occurrencePath = "items[1]/entries[0]"
        fixture.updateTemplate { template -> template.copy(
            sections = template.sections.map { section -> section.copy(
                requirements = section.requirements.map { it.copy(occurrenceAnchorKey = "entries") }) },
            groups = listOf(
                InformationRequestTemplateGroupDto(fixture.visibleGroupId, "items", minOccurrences = 0),
                InformationRequestTemplateGroupDto(fixture.hiddenGroupId, "entries", parentGroupKey = "items", minOccurrences = 0),
            ),
        ) }
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        val parent = fixture.occurrence("items[0]", fixture.visibleGroupId)
        val sibling = fixture.occurrence("items[1]", fixture.visibleGroupId)
        val child = fixture.occurrence(fixture.visible.occurrencePath, fixture.hiddenGroupId, parent.id)
        val deniedChild = fixture.occurrence(fixture.hidden.occurrencePath, fixture.hiddenGroupId, sibling.id)
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(parent, sibling, child, deniedChild))

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(parent.id, child.id), result.occurrences.map { it.id })
            assertEquals(parent.id, result.occurrences.last().parentOccurrenceId)
            assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
        }
    }

    @Test
    fun `redacting an undisclosed condition does not activate its hidden response`()
    {
        val fixture = Fixture()
        fixture.updateTemplate { template -> template.copy(
            conditionRules = template.conditionRules.map { rule -> rule.copy(predicates = listOf(
                InformationRequestTemplateConditionPredicateDto(UUID.randomUUID(),
                    sourceRequirementKey = "undisclosed-source", operator = FieldOperator.EQUALS,
                    value = JsonPrimitive("restricted")),
            )) },
        ) }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.FALSE,
                sourceRequirementKeys = setOf("undisclosed-source"), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]")))

        fixture.loadWorkspaces().forEach { result ->
            assertTrue(result.templateVersion.conditionRules.isEmpty())
            assertEquals(listOf(fixture.visible.id), result.responses.map { it.informationRequestRequirementId })
            assertFalse(result.schemaAssignment!!.bindings.any { it.fieldDefinitionId == fixture.hiddenFieldId })
        }
    }

    @Test
    fun `request lookups omit condition results from a denied sibling on both access surfaces`()
    {
        val fixture = Fixture()
        fixture.visible.sourceTemplateBindingId = fixture.hidden.sourceTemplateBindingId
        fixture.visible.occurrencePath = "items[1]"
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.FALSE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[0]"),
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = emptySet(), occurrencePath = "items[1]")))

        fixture.loadRequests().forEach { result ->
            assertEquals(listOf("items[1]"), result.conditionEvaluations.map { it.occurrencePath })
        }
    }

    @Test
    fun `authorized optional child controls retain their empty parent occurrence`()
    {
        val fixture = Fixture()
        fixture.updateTemplate { template -> template.copy(groups = template.groups.map { group ->
            if (group.id == fixture.zeroGroupId) group.copy(parentGroupKey = "items") else group
        }) }
        val parent = fixture.occurrence("items[0]", fixture.visibleGroupId)
        whenever(fixture.requirements.findForRequest(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(listOf(parent))

        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf(parent.id), result.occurrences.map { it.id })
            assertTrue(result.templateVersion.groups.any { it.id == fixture.zeroGroupId })
            assertTrue(result.responses.isEmpty())
        }

        doThrow(io.quarkus.security.ForbiddenException("Denied")).whenever(fixture.groupAuthorization)
            .authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.zeroBinding))
        fixture.loadWorkspaces().forEach { result ->
            assertTrue(result.occurrences.isEmpty())
            assertTrue(result.templateVersion.groups.isEmpty())
        }
    }

    @Test
    fun `a readable source in another branch cannot disclose a condition driven by a denied local source`()
    {
        val fixture = Fixture()
        val readableSource = InformationRequestRequirement().apply {
            informationRequestId = fixture.request.id
            sourceTemplateBindingId = fixture.hidden.sourceTemplateBindingId
            sourceTemplateRequirementId = fixture.hidden.sourceTemplateRequirementId
            occurrencePath = "items[1]"
        }
        whenever(fixture.requirements.findForRequest(fixture.request.id))
            .thenReturn(listOf(fixture.hidden, fixture.visible, readableSource))
        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenAnswer {
            val resource = it.getArgument<ResourceRef>(2)
            if (resource.id == fixture.hidden.id) Decision.Deny(Decision.REASON_NO_GRANT, "Denied") else Decision.Allow()
        }
        fixture.updateTemplate { template -> template.copy(
            sections = template.sections.map { section -> section.copy(requirements = section.requirements.map {
                it.copy(conditionalRuleKey = if (it.id == fixture.visible.sourceTemplateBindingId) "conditional-data" else null)
            }) },
            conditionRules = listOf(InformationRequestTemplateConditionRuleDto(UUID.randomUUID(), "conditional-data", 1,
                predicates = listOf(InformationRequestTemplateConditionPredicateDto(UUID.randomUUID(),
                    fieldDefinitionId = fixture.hiddenFieldId, operator = FieldOperator.EQUALS, value = JsonPrimitive("restricted"))))),
        ) }
        whenever(fixture.conditions.evaluate(fixture.request.id)).thenReturn(listOf(
            InformationRequestConditionEvaluationProjection("conditional-data", 1, InformationRequestConditionEvaluationState.TRUE,
                sourceRequirementKeys = emptySet(), fieldDefinitionIds = setOf(fixture.hiddenFieldId), occurrencePath = "items[0]")))

        fixture.loadWorkspaces().forEach { assertTrue(it.request.conditionEvaluations.isEmpty()) }
        fixture.loadRequests().forEach { assertTrue(it.conditionEvaluations.isEmpty()) }

        whenever(fixture.authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        fixture.loadWorkspaces().forEach { result ->
            assertEquals(listOf("items[0]"), result.request.conditionEvaluations.map { it.occurrencePath })
        }
        fixture.loadRequests().forEach { result ->
            assertEquals(listOf("items[0]"), result.conditionEvaluations.map { it.occurrencePath })
        }
    }

    private class Fixture
    {
        val request = InformationRequest().apply { exchangeId = UUID.randomUUID(); templateVersionId = UUID.randomUUID() }
        val access = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext.ANONYMOUS)
        val hiddenFieldId = UUID.randomUUID()
        val visibleFieldId = UUID.randomUUID()
        val zeroFieldId = UUID.randomUUID()
        val visibleGroupId = UUID.randomUUID()
        val hiddenGroupId = UUID.randomUUID()
        val zeroGroupId = UUID.randomUUID()
        val hidden = requirement()
        val visible = requirement()
        val zeroRequirementId = UUID.randomUUID()
        val zeroRequirementKey = "item-$zeroRequirementId"
        val zeroBinding = InformationRequestTemplateRequirementBinding().apply {
            id = UUID.randomUUID()
            templateVersionId = request.templateVersionId
            templateDefinitionId = UUID.randomUUID()
            templateRequirementId = zeroRequirementId
            templateSectionId = UUID.randomUUID()
            prompt = "Provide optional data"
            occurrenceAnchorKey = "optional-items"
            collectedFieldDefinitionId = zeroFieldId
        }
        val conditions = mock<InformationRequestConditionEvaluationService>()
        val responses = mock<InformationRequestResponseRepository>()
        val query = mock<InformationRequestQueryService>()
        val versions = mock<InformationRequestTemplateVersionRepository>()
        val templates = mock<InformationRequestTemplateProjectionLoader>()
        val requirements = mock<InformationRequestRequirementRepository>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val fields = mock<SchemaAssignmentService>()
        val authorization = mock<AuthorizationService>()
        val groupAuthorization = mock<InformationRequestGroupAuthorizationService>()
        val service = InformationRequestResponseWorkspaceService(query, versions, templates, occurrenceRepository, requirements,
            responses, bindingRepository, fields, authorization, conditions, groupAuthorization)

        fun updateTemplate(transform: (InformationRequestTemplateVersionDto) -> InformationRequestTemplateVersionDto)
        {
            val version = versions.findById(request.templateVersionId)!!
            val updated = transform(templates.loadVersion(version))
            whenever(templates.loadVersion(version)).thenReturn(updated)
        }

        fun occurrence(path: String, groupId: UUID, parentId: UUID? = null) = InformationRequestGroupOccurrence().apply {
            informationRequestId = request.id
            sourceTemplateGroupId = groupId
            occurrencePath = path
            parentOccurrenceId = parentId
        }

        fun loadWorkspaces(): List<InformationRequestResponseWorkspaceDto>
        {
            val accessFactory = mock<InformationRequestAccessContextFactory>()
            whenever(accessFactory.currentAuthenticated()).thenReturn(access)
            val authenticated = InformationRequestResource(query, mock(), mock(), accessFactory, service)
            val resolver = mock<InformationRequestNoAuthReadAccessService>()
            whenever(resolver.resolve("bootstrap", "session")).thenReturn(InformationRequestNoAuthAccess(access, request.id))
            val noAuth = InformationRequestNoAuthRequestResource(resolver, mock(), mock(), mock(), service)
            return listOf(
                authenticated.responseWorkspace(request.id.toString()),
                noAuth.responseWorkspace(request.id.toString(), "bootstrap", "session"),
            ).map { response ->
                assertEquals(200, response.status)
                response.entity as InformationRequestResponseWorkspaceDto
            }
        }

        fun loadRequests(): List<InformationRequestDto>
        {
            val accessFactory = mock<InformationRequestAccessContextFactory>()
            whenever(accessFactory.currentAuthenticated()).thenReturn(access)
            val authenticated = InformationRequestResource(query, mock(), mock(), accessFactory, service)
            val resolver = mock<InformationRequestNoAuthReadAccessService>()
            whenever(resolver.resolve("bootstrap", "session")).thenReturn(InformationRequestNoAuthAccess(access, request.id))
            val noAuth = InformationRequestNoAuthRequestResource(resolver, mock(), mock(), mock(), service)
            return listOf(
                authenticated.get(request.id.toString()),
                noAuth.get(request.id.toString(), "bootstrap", "session"),
            ).map { response ->
                assertEquals(200, response.status)
                response.entity as InformationRequestDto
            }
        }

        init
        {
            val version = InformationRequestTemplateVersion().apply { id = request.templateVersionId }
            whenever(query.findById(request.id, access)).thenReturn(request)
            whenever(versions.findById(version.id)).thenReturn(version)
            whenever(templates.loadVersion(version)).thenReturn(InformationRequestTemplateVersionDto(version.id,
                UUID.randomUUID(), 1, InformationRequestTemplateStatus.PUBLISHED,
                sections = listOf(InformationRequestTemplateSectionDto(UUID.randomUUID(), "data", "Data",
                    requirements = listOf(
                        template(hidden, hiddenFieldId, "conditional-data"),
                        template(visible, visibleFieldId, null),
                        InformationRequestTemplateRequirementDto(
                            zeroBinding.id,
                            zeroRequirementId,
                            zeroRequirementKey,
                            InformationRequestRequirementType.FIELD,
                            "Provide optional data",
                            responseMode = InformationRequestResponseMode.PROVIDE,
                            requiredness = InformationRequestRequiredness.OPTIONAL,
                            contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                            reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
                            occurrenceAnchorKey = "optional-items",
                            collectedFieldDefinitionId = zeroFieldId,
                        ),
                    ))),
                groups = listOf(
                    InformationRequestTemplateGroupDto(visibleGroupId, "items", minOccurrences = 0),
                    InformationRequestTemplateGroupDto(hiddenGroupId, "protected-items", minOccurrences = 0),
                    InformationRequestTemplateGroupDto(zeroGroupId, "optional-items", minOccurrences = 0),
                ),
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleDto(
                        UUID.randomUUID(),
                        "conditional-data",
                        1,
                    ),
                    InformationRequestTemplateConditionRuleDto(
                        UUID.randomUUID(),
                        "protected-rule",
                        1,
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateDto(
                                UUID.randomUUID(),
                                sourceRequirementKey = "protected-source",
                                operator = FieldOperator.EQUALS,
                                value = JsonPrimitive("protected-literal"),
                            ),
                        ),
                    ),
                ),
                createdAt = Timestamp.from(Instant.now())))
            whenever(bindingRepository.findOrdered(version.id)).thenReturn(listOf(zeroBinding))
            whenever(requirements.findForRequest(request.id)).thenReturn(listOf(hidden, visible))
            whenever(occurrenceRepository.findForRequest(request.id)).thenAnswer {
                requirements.findForRequest(request.id).map { it.occurrencePath }
                    .filterNot { InformationRequestOccurrencePath.isRoot(it) }
                    .distinct()
                    .map { path ->
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            sourceTemplateGroupId = if (path.startsWith("protected-items[")) hiddenGroupId else visibleGroupId
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
                conditionalRuleKey = rule, occurrenceAnchorKey = occurrenceAnchorKey(requirement),
                collectedFieldDefinitionId = fieldId,
                supportingEvidenceRequirementKeys = if (requirement == visible) listOf("item-${hidden.id}") else emptyList())

        private fun occurrenceAnchorKey(requirement: InformationRequestRequirement): String? =
            if (InformationRequestOccurrencePath.isRoot(requirement.occurrencePath))
                null
            else
                requirement.occurrencePath.substringBefore("[")
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
