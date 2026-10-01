package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRefusalDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.creation.CreateAdHocInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromBlueprintCommand
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromTemplateVersionCommand
import com.docuhyphen.app.api.model.informationrequest.creation.InformationRequestCreationResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestLifecycleResult
import com.docuhyphen.app.api.model.informationrequest.lifecycle.IssueInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.SupersedeInformationRequestCommand
import com.docuhyphen.app.api.resource.informationrequest.operations.InformationRequestResourceOperations
import com.docuhyphen.app.api.resource.model.CancelInformationRequestRequest
import com.docuhyphen.app.api.resource.model.CreateInformationRequestDraftRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SupersedeInformationRequestRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestAdHocCreationService
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestBlueprintInstantiationService
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestTemplateInstantiationService
import com.docuhyphen.app.api.service.informationrequest.execution.RequestExecutionUsageExhaustedException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateValidationException
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

/**
 * The owner-facing runtime request resource exposes list, draft creation, issuance, cancellation, and
 * supersession. These tests pin the HTTP shape, header handling, and service delegation; respondent
 * actions have their own resources and must not appear here.
 */
class InformationRequestResourceContractTest
{
    private val queryService = mock<InformationRequestQueryService>()
    private val creationService = mock<InformationRequestAdHocCreationService>()
    private val lifecycleService = mock<InformationRequestLifecycleService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val responseWorkspaceService = mock<InformationRequestResponseWorkspaceService>()
    private val blueprintInstantiationService = mock<InformationRequestBlueprintInstantiationService>()
    private val templateInstantiationService = mock<InformationRequestTemplateInstantiationService>()
    private val resource = InformationRequestResource(
        queryService,
        creationService,
        lifecycleService,
        accessContextFactory,
        responseWorkspaceService,
        blueprintInstantiationService,
        templateInstantiationService,
    )

    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val exchangeId = UUID.randomUUID()
    private val requestId = UUID.randomUUID()
    private val request = InformationRequest().apply {
        id = requestId
        this.exchangeId = this@InformationRequestResourceContractTest.exchangeId
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
    }

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `runtime request administration uses resource based paths and verbs`()
    {
        val resourceClass = InformationRequestResourceOperations::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals("/information-requests", resourceClass.getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("list").isAnnotationPresent(GET::class.java))
        assertFalse(methods.getValue("list").isAnnotationPresent(Path::class.java))
        assertTrue(methods.getValue("create").isAnnotationPresent(POST::class.java))
        assertFalse(methods.getValue("create").isAnnotationPresent(Path::class.java))
        assertTrue(methods.getValue("cancel").isAnnotationPresent(POST::class.java))
        assertEquals("/{id}/cancellation", methods.getValue("cancel").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("supersede").isAnnotationPresent(POST::class.java))
        assertEquals("/{id}/supersession", methods.getValue("supersede").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("responseWorkspace").isAnnotationPresent(GET::class.java))
        assertEquals(
            "/{id}/response-workspace",
            methods.getValue("responseWorkspace").getAnnotation(Path::class.java).value,
        )
    }

    @Test
    fun `issuance is a subordinate resource that needs a precondition and a key and no respondent action is exposed`()
    {
        val methods = InformationRequestResourceOperations::class.java.declaredMethods.associateBy { it.name }
        assertTrue(methods.getValue("issue").isAnnotationPresent(POST::class.java))
        assertEquals("/{id}/issuance", methods.getValue("issue").getAnnotation(Path::class.java).value)
        assertTrue(methods.keys.none { it.contains("respond", ignoreCase = true) })
        whenever(lifecycleService.issue(any())).thenReturn(InformationRequestLifecycleResult(request, "etag-issued"))

        val missingKey = resource.issue(requestId.toString(), "\"v1\"", " ")
        val invalidId = resource.issue("not-a-uuid", "\"v1\"", "idem-issue")
        val issued = resource.issue(requestId.toString(), "\"v1\"", "idem-issue")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, missingKey.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalidId.status)
        assertEquals(Response.Status.OK.statusCode, issued.status)
        assertEquals("etag-issued", issued.getHeaderString("ETag"))
        verify(lifecycleService).issue(
            IssueInformationRequestCommand(
                requestId = requestId,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-issue",
            ),
        )
    }

    @Test
    fun `issuance of a Version this deployment cannot serve is refused with the capability reason`()
    {
        whenever(lifecycleService.issue(any())).thenThrow(
            InformationRequestCapabilityNotInstalledException(
                "This deployment does not serve RESPONSE_REVIEW v1",
                listOf(InformationRequestCapabilityRequirement(InformationRequestCapability.RESPONSE_REVIEW, 1)),
            ),
        )

        val refused = resource.issue(requestId.toString(), "\"v1\"", "idem-issue")

        assertEquals(Response.Status.CONFLICT.statusCode, refused.status)
        assertEquals(InformationRequestErrorCatalog.CAPABILITY_NOT_INSTALLED, (refused.entity as ResponseError).reasonCode)
    }

    @Test
    fun `get exposes the runtime request by id and rejects an invalid id`()
    {
        val resourceClass = InformationRequestResourceOperations::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }
        assertTrue(methods.getValue("get").isAnnotationPresent(GET::class.java))
        assertEquals("/{id}", methods.getValue("get").getAnnotation(Path::class.java).value)

        val projected = InformationRequestDtoMapper.toDto(request, listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-response-provided",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.TRUE,
                    sourceRequirementKeys = setOf("prior-response"),
                    fieldDefinitionIds = setOf(UUID.randomUUID()),
                ),
            ))
        whenever(responseWorkspaceService.loadRequest(requestId, access)).thenReturn(projected)

        val found = resource.get(requestId.toString())
        val invalid = resource.get("not-a-uuid")

        assertEquals(Response.Status.OK.statusCode, found.status)
        val body = found.entity as InformationRequestDto
        assertEquals(requestId, body.id)
        assertEquals(1, body.conditionEvaluations.size)
        assertEquals("when-response-provided", body.conditionEvaluations.single().ruleKey)
        assertEquals(InformationRequestConditionEvaluationState.TRUE, body.conditionEvaluations.single().state)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalid.status)
        verify(responseWorkspaceService).loadRequest(requestId, access)
    }

    @Test
    fun `response workspace delegates through the authenticated access context`()
    {
        val workspace = mock<InformationRequestResponseWorkspaceDto>()
        whenever(responseWorkspaceService.load(requestId, access)).thenReturn(workspace)

        val response = resource.responseWorkspace(requestId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(workspace, response.entity)
        verify(responseWorkspaceService).load(requestId, access)
    }

    @Test
    fun `list requires an exchange id and delegates to the query service`()
    {
        whenever(queryService.listForExchange(exchangeId, access)).thenReturn(listOf(request))

        val missing = resource.list(null)
        val invalid = resource.list("not-a-uuid")
        val listed = resource.list(exchangeId.toString())

        assertEquals(Response.Status.BAD_REQUEST.statusCode, missing.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalid.status)
        assertEquals(Response.Status.OK.statusCode, listed.status)
        @Suppress("UNCHECKED_CAST")
        val body = listed.entity as Array<InformationRequestDto>
        assertEquals(1, body.size)
        assertEquals(requestId, body.single().id)
    }

    @Test
    fun `create requires an idempotency key and delegates to the ad hoc creation service`()
    {
        val draft = CreateInformationRequestDraftRequest(
            exchangeId = exchangeId,
            displayName = "Collection request",
            configuration = configurationRequest(),
        )
        whenever(creationService.createAdHoc(any())).thenReturn(
            InformationRequestCreationResult(request, "etag-value", requirementCount = 1),
        )

        val missingKey = resource.create(draft, null)
        val created = resource.create(draft, "idempotency-key-1")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, missingKey.status)
        assertEquals(Response.Status.CREATED.statusCode, created.status)
        assertEquals("etag-value", created.getHeaderString("ETag"))
        verify(creationService).createAdHoc(
            CreateAdHocInformationRequestCommand(
                exchangeId = exchangeId,
                displayName = "Collection request",
                description = draft.description,
                configuration = configurationRequest(),
                gatesExchangeClosure = draft.gatesExchangeClosure,
                access = access,
                idempotencyKey = "idempotency-key-1",
            ),
        )
    }

    @Test
    fun `creation names exactly one source and each source is created by its own service`()
    {
        val blueprintId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val created = InformationRequestCreationResult(request, "etag-created", requirementCount = 2)
        whenever(blueprintInstantiationService.createFromBlueprint(any())).thenReturn(created)
        whenever(templateInstantiationService.createFromTemplateVersion(any())).thenReturn(created)

        val fromBlueprint = resource.create(
            CreateInformationRequestDraftRequest(exchangeId = exchangeId, blueprintDefinitionId = blueprintId),
            "idem-blueprint",
        )
        val fromVersion = resource.create(
            CreateInformationRequestDraftRequest(
                exchangeId = exchangeId,
                templateVersionId = versionId,
                gatesExchangeClosure = false,
            ),
            "idem-version",
        )
        val noSource = resource.create(CreateInformationRequestDraftRequest(exchangeId = exchangeId), "idem-none")
        val twoSources = resource.create(
            CreateInformationRequestDraftRequest(
                exchangeId = exchangeId,
                blueprintDefinitionId = blueprintId,
                templateVersionId = versionId,
            ),
            "idem-two",
        )

        assertEquals(Response.Status.CREATED.statusCode, fromBlueprint.status)
        assertEquals("etag-created", fromBlueprint.getHeaderString("ETag"))
        assertEquals(Response.Status.CREATED.statusCode, fromVersion.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, noSource.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, twoSources.status)
        verify(blueprintInstantiationService).createFromBlueprint(
            CreateInformationRequestFromBlueprintCommand(
                blueprintDefinitionId = blueprintId,
                exchangeId = exchangeId,
                gatesExchangeClosure = true,
                access = access,
                idempotencyKey = "idem-blueprint",
            ),
        )
        verify(templateInstantiationService).createFromTemplateVersion(
            CreateInformationRequestFromTemplateVersionCommand(
                templateVersionId = versionId,
                exchangeId = exchangeId,
                gatesExchangeClosure = false,
                access = access,
                idempotencyKey = "idem-version",
            ),
        )
        verify(creationService, never()).createAdHoc(any())
    }

    @Test
    fun `a refused ad hoc configuration names the requirement it refuses`()
    {
        whenever(creationService.createAdHoc(any())).thenThrow(
            InformationRequestTemplateValidationException(
                "Requirement response-confirmation states no prompt",
                sectionKey = "requested-data",
                requirementKey = "response-confirmation",
            ),
        )

        val refused = resource.create(
            CreateInformationRequestDraftRequest(
                exchangeId = exchangeId,
                displayName = "Collection request",
                configuration = configurationRequest(),
            ),
            "idem-ad-hoc",
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, refused.status)
        val body = refused.entity as InformationRequestTemplateRefusalDto
        assertEquals("INFORMATION_REQUEST_TEMPLATE_INVALID", body.reasonCode)
        assertEquals("requested-data", body.sectionKey)
        assertEquals("response-confirmation", body.requirementKey)
    }

    @Test
    fun `an unavailable Template Version and exhausted capacity are refused with stable codes`()
    {
        whenever(templateInstantiationService.createFromTemplateVersion(any())).thenThrow(
            InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.RETIRED,
                "Information request template version 2 has been retired",
            ),
        )
        whenever(lifecycleService.issue(any())).thenThrow(
            RequestExecutionUsageExhaustedException(
                grantId = UUID.randomUUID(),
                usageKind = RequestExecutionUsageKind.ACTING_PARTY,
                cap = 1,
                activeUsage = 1,
                requested = 1,
            ),
        )

        val retired = resource.create(
            CreateInformationRequestDraftRequest(exchangeId = exchangeId, templateVersionId = UUID.randomUUID()),
            "idem-retired",
        )
        val exhausted = resource.issue(requestId.toString(), "\"v1\"", "idem-issue")

        assertEquals(Response.Status.CONFLICT.statusCode, retired.status)
        assertEquals(
            InformationRequestTemplateVersionUnavailableException.RETIRED,
            (retired.entity as ResponseError).reasonCode,
        )
        assertEquals(Response.Status.CONFLICT.statusCode, exhausted.status)
        assertEquals(InformationRequestErrorCatalog.CAPACITY_EXHAUSTED, (exhausted.entity as ResponseError).reasonCode)
    }

    @Test
    fun `cancel requires an if-match precondition and an idempotency key`()
    {
        whenever(lifecycleService.cancel(any())).thenReturn(
            InformationRequestLifecycleResult(request, "etag-cancelled"),
        )

        val missingKey = resource.cancel(requestId.toString(), CancelInformationRequestRequest(), "\"v1\"", null)
        val invalidId = resource.cancel("not-a-uuid", null, "\"v1\"", "idem-1")
        val cancelled = resource.cancel(requestId.toString(), CancelInformationRequestRequest("withdrawn"), "\"v1\"", "idem-1")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, missingKey.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalidId.status)
        assertEquals(Response.Status.OK.statusCode, cancelled.status)
        assertEquals("etag-cancelled", cancelled.getHeaderString("ETag"))
        verify(lifecycleService).cancel(
            CancelInformationRequestCommand(
                requestId = requestId,
                reasonCode = "withdrawn",
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-1",
            ),
        )
    }

    @Test
    fun `supersede requires the replacement request id an if-match precondition and an idempotency key`()
    {
        val replacementId = UUID.randomUUID()
        whenever(lifecycleService.supersede(any())).thenReturn(
            InformationRequestLifecycleResult(request, "etag-superseded"),
        )

        val superseded = resource.supersede(
            requestId.toString(),
            SupersedeInformationRequestRequest(replacementId, "replaced"),
            "\"v1\"",
            "idem-1",
        )

        assertEquals(Response.Status.OK.statusCode, superseded.status)
        assertEquals("etag-superseded", superseded.getHeaderString("ETag"))
        verify(lifecycleService).supersede(
            SupersedeInformationRequestCommand(
                requestId = requestId,
                supersededByRequestId = replacementId,
                reasonCode = "replaced",
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"v1\"")),
                idempotencyKey = "idem-1",
            ),
        )
    }

    @Test
    fun `domain failures map to stable response statuses`()
    {
        whenever(queryService.listForExchange(eq(exchangeId), any()))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Not found"))
            .thenThrow(IllegalArgumentException("Exchange not found"))
            .thenThrow(ForbiddenException("Denied"))

        assertMapped(Response.Status.CONFLICT, "Not found")
        assertMapped(Response.Status.NOT_FOUND, "Exchange not found")
        assertMapped(Response.Status.FORBIDDEN, "Denied")
    }

    private fun assertMapped(status: Response.Status, message: String)
    {
        val response = resource.list(exchangeId.toString())
        assertEquals(status.statusCode, response.status)
        assertEquals(message, (response.entity as ResponseError).errorMessage)
    }

    private fun configurationRequest() = InformationRequestTemplateConfigurationRequest(
        sections = listOf(
            com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest(
                sectionKey = "requested-data",
                title = "Requested data",
                requirements = listOf(
                    com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest(
                        requirementKey = "response-confirmation",
                        requirementType = com.docuhyphen.app.api.model.entity.InformationRequestRequirementType.RESPONSE_ATTESTATION,
                        prompt = "Confirm the response package",
                    ),
                ),
            ),
        ),
    )
}
