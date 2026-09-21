package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.resource.model.CancelInformationRequestRequest
import com.docuhyphen.app.api.resource.model.CreateInformationRequestDraftRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SupersedeInformationRequestRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.CancelInformationRequestCommand
import com.docuhyphen.app.api.service.informationrequest.CreateAdHocInformationRequestCommand
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAdHocCreationService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCreationResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.informationrequest.SupersedeInformationRequestCommand
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * The owner-facing runtime request resource exposes only list, draft creation, cancellation, and
 * supersession. These tests pin the HTTP shape, header handling, and service delegation; issuance and
 * every respondent action must not appear here.
 */
class InformationRequestResourceContractTest
{
    private val queryService = mock<InformationRequestQueryService>()
    private val creationService = mock<InformationRequestAdHocCreationService>()
    private val lifecycleService = mock<InformationRequestLifecycleService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val responseWorkspaceService = mock<InformationRequestResponseWorkspaceService>()
    private val resource = InformationRequestResource(
        queryService,
        creationService,
        lifecycleService,
        accessContextFactory,
        responseWorkspaceService,
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
        val resourceClass = InformationRequestResource::class.java
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

    /**
     * Issuance and respondent behavior are not ready: no method on this resource may expose them
     * before the dual-access authorization surface and runtime executors exist.
     */
    @Test
    fun `no issuance or respondent action is exposed`()
    {
        val paths = InformationRequestResource::class.java.declaredMethods
            .mapNotNull { it.getAnnotation(Path::class.java)?.value }
        val methodNames = InformationRequestResource::class.java.declaredMethods.map { it.name }

        assertTrue(paths.none { it.contains("issu", ignoreCase = true) })
        assertTrue(methodNames.none { it.contains("issue", ignoreCase = true) })
        assertTrue(methodNames.none { it.contains("respond", ignoreCase = true) })
    }

    @Test
    fun `get exposes the runtime request by id and rejects an invalid id`()
    {
        val resourceClass = InformationRequestResource::class.java
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
        val body = listed.entity as List<InformationRequestDto>
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
                displayName = draft.displayName,
                description = draft.description,
                configuration = draft.configuration,
                gatesExchangeClosure = draft.gatesExchangeClosure,
                access = access,
                idempotencyKey = "idempotency-key-1",
            ),
        )
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
