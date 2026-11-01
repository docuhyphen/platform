package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.occurrence.InformationRequestGroupOccurrenceResult
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyListing
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponseDraftResult
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationKind
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.resource.informationrequest.operations.InformationRequestNoAuthRequestResourceOperations
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthAccessDoubles
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Response
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestNoAuthRequestResourceContractTest
{
    private val readAccessService = InformationRequestNoAuthAccessDoubles.current()
    private val partyQueryService = mock<InformationRequestPartyQueryService>()
    private val responseDraftService = mock<InformationRequestResponseDraftService>()
    private val occurrenceService = mock<InformationRequestGroupOccurrenceService>()
    private val responseWorkspaceService = mock<InformationRequestResponseWorkspaceService>()
    private val resource = InformationRequestNoAuthRequestResource(
        readAccessService,
        partyQueryService,
        responseDraftService,
        occurrenceService,
        responseWorkspaceService,
    )

    private val requestId = UUID.randomUUID()
    private val otherRequestId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val sourceTemplateRequirementId = UUID.randomUUID()
    private val sourceTemplateBindingId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext())

    @Test
    fun `a wildcard If-Match on a no-auth group occurrence is sent to the service as a missing precondition`() {
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(occurrenceService.add(any()))
            .thenThrow(com.docuhyphen.app.api.service.command.CommandPreconditionException.required("\"responses-etag\""))

        val response = resource.addGroupOccurrence(
            requestId.toString(),
            CreateInformationRequestGroupOccurrenceRequest(groupKey = "entries"),
            "raw-token",
            "*",
            "key-wildcard",
        )

        assertEquals(428, response.status)
        verify(occurrenceService).add(
            org.mockito.kotlin.argThat { precondition == com.docuhyphen.app.api.model.command.CommandPrecondition.Absent },
        )
    }

    private val request = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
    }
    private val partyDto = InformationRequestPartyDto(
        id = UUID.randomUUID(),
        informationRequestId = requestId,
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
        active = true,
        principalId = null,
        principalKind = null,
        subjectIdentityRefId = null,
        exchangeRecipientId = null,
        assignedAt = Timestamp.from(Instant.now()),
        revokedAt = null,
        partyRevision = 1,
        partyETag = "\"party-etag\"",
    )
    private val partyListing = InformationRequestPartyListing(listOf(partyDto), "\"parties-etag\"")

    @Test
    fun `every content endpoint refuses a forwarded bootstrap token without a session secret`()
    {
        val resolver = InformationRequestNoAuthReadAccessService(mock(), mock(), mock())
        val guarded = InformationRequestNoAuthRequestResource(
            resolver, partyQueryService, responseDraftService, occurrenceService, responseWorkspaceService,
        )
        val id = requestId.toString()
        val responses = listOf(
            guarded.get(id, "forwarded-bootstrap"),
            guarded.parties(id, "forwarded-bootstrap"),
            guarded.responseWorkspace(id, "forwarded-bootstrap"),
            guarded.patchResponses(
                id, PatchInformationRequestResponsesRequest(
                    listOf(
                        InformationRequestResponsePatchRequest(
                            requirementId,
                            InformationRequestResponseDisposition.PROVIDED
                        ),
                    )
                ), "forwarded-bootstrap", "\"etag\"", "command"
            ),
            guarded.addGroupOccurrence(
                id, CreateInformationRequestGroupOccurrenceRequest("items"),
                "forwarded-bootstrap", "\"etag\"", "command"
            ),
            guarded.removeGroupOccurrence(
                id,
                UUID.randomUUID().toString(),
                "forwarded-bootstrap",
                "\"etag\"",
                "command"
            ),
            guarded.reorderGroupOccurrences(
                id, ReorderInformationRequestGroupOccurrencesRequest("items", occurrenceIds = emptyList()),
                "forwarded-bootstrap", "\"etag\"", "command"
            ),
        )
        responses.forEach { response ->
            assertEquals(Response.Status.CONFLICT.statusCode, response.status)
            assertEquals(
                InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
                (response.entity as com.docuhyphen.app.api.resource.model.ResponseError).reasonCode
            )
        }
        verifyNoInteractions(
            partyQueryService, responseDraftService,
            occurrenceService, responseWorkspaceService
        )
    }

    @Test
    fun `content adapters pass the independent session credential to the resolver`()
    {
        whenever(readAccessService.resolve(requestId, "bootstrap", "session-secret")).thenReturn(access)
        whenever(responseWorkspaceService.loadRequest(requestId, access)).thenReturn(
            InformationRequestDtoMapper.toDto(
                request,
                revealOwner = false,
                executionStanding = InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE)
            )
        )
        assertEquals(200, resource.get(requestId.toString(), "bootstrap", "session-secret").status)
        verify(readAccessService).resolve(requestId, "bootstrap", "session-secret")
    }

    @Test
    fun `response workspace delegates through the verified no-auth access context`()
    {
        val workspace = mock<InformationRequestResponseWorkspaceDto>()
        whenever(readAccessService.resolve(requestId, "bootstrap", "session-secret")).thenReturn(access)
        whenever(responseWorkspaceService.load(requestId, access)).thenReturn(workspace)

        val response = resource.responseWorkspace(requestId.toString(), "bootstrap", "session-secret")

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(workspace, response.entity)
        verify(responseWorkspaceService).load(requestId, access)
    }

    @Test
    fun `request, party, and workspace reads are made with the access the session recheck holds current`() {
        val current = access.copy(authorization = AuthorizationContext(sessionRef = "current-session"))
        whenever(readAccessService.resolve(requestId, "bootstrap", "session-secret")).thenReturn(access)
        doAnswer { it.getArgument<(RequestAccessContext) -> Any?>(2).invoke(current) }
            .whenever(readAccessService).readCurrent<Any?>(eq(requestId), eq(access), any())
        whenever(responseWorkspaceService.loadRequest(requestId, current)).thenReturn(
            InformationRequestDtoMapper.toDto(
                request,
                revealOwner = false,
                executionStanding = InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE)
            )
        )
        whenever(partyQueryService.listForManagement(requestId, current)).thenReturn(partyListing)
        whenever(responseWorkspaceService.load(requestId, current)).thenReturn(mock())

        val responses = listOf(
            resource.get(requestId.toString(), "bootstrap", "session-secret"),
            resource.parties(requestId.toString(), "bootstrap", "session-secret"),
            resource.responseWorkspace(requestId.toString(), "bootstrap", "session-secret"),
        )

        assertEquals(listOf(200, 200, 200), responses.map { it.status })
        verify(responseWorkspaceService, never()).loadRequest(requestId, access)
        verify(partyQueryService, never()).listForManagement(requestId, access)
        verify(responseWorkspaceService, never()).load(requestId, access)
    }

    @Test
    fun `resource is mounted under the no-auth request prefix and exposes get, parties, and response patch`()
    {
        val resourceClass = InformationRequestNoAuthRequestResourceOperations::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }
        for (name in listOf(
            "get", "parties", "responseWorkspace", "patchResponses", "addGroupOccurrence",
            "removeGroupOccurrence", "reorderGroupOccurrences"
        ))
            assertTrue(methods.getValue(name).parameters.any {
                it.getAnnotation(jakarta.ws.rs.HeaderParam::class.java)?.value == "X-Request-Session-Token"
            })

        assertEquals("no-auth/information-requests/{id}", resourceClass.getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("get").isAnnotationPresent(GET::class.java))
        assertTrue(methods.getValue("parties").isAnnotationPresent(GET::class.java))
        assertEquals("/parties", methods.getValue("parties").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("patchResponses").isAnnotationPresent(PATCH::class.java))
        assertEquals("/responses", methods.getValue("patchResponses").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("responseWorkspace").isAnnotationPresent(GET::class.java))
        assertEquals(
            "/response-workspace",
            methods.getValue("responseWorkspace").getAnnotation(Path::class.java).value,
        )
        assertTrue(methods.getValue("addGroupOccurrence").isAnnotationPresent(POST::class.java))
        assertEquals("/group-occurrences", methods.getValue("addGroupOccurrence").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("removeGroupOccurrence").isAnnotationPresent(DELETE::class.java))
        assertEquals(
            "/group-occurrences/{occurrenceId}",
            methods.getValue("removeGroupOccurrence").getAnnotation(Path::class.java).value,
        )
        assertTrue(methods.getValue("reorderGroupOccurrences").isAnnotationPresent(PATCH::class.java))
        assertEquals(
            "/group-occurrences/order",
            methods.getValue("reorderGroupOccurrences").getAnnotation(Path::class.java).value,
        )
    }

    @Test
    fun `get without a token is rejected before the read access service is called`()
    {
        val response = resource.get(requestId.toString(), null)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(readAccessService, never()).resolve(any(), any(), anyOrNull())
    }

    @Test
    fun `get resolves the token then returns the same detail projection the authenticated surface uses`()
    {
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        val projected = InformationRequestDtoMapper.toDto(
            request,
            revealOwner = false,
            InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE),
            listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-response-missing",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.UNKNOWN,
                    sourceRequirementKeys = setOf("prior-response"),
                    fieldDefinitionIds = emptySet(),
                ),
            )
        )
        whenever(responseWorkspaceService.loadRequest(requestId, access)).thenReturn(projected)

        val response = resource.get(requestId.toString(), "raw-token")

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as InformationRequestDto
        assertEquals(requestId, body.id)
        assertEquals(1, body.conditionEvaluations.size)
        assertEquals("when-response-missing", body.conditionEvaluations.single().ruleKey)
        assertEquals(InformationRequestConditionEvaluationState.UNKNOWN, body.conditionEvaluations.single().state)
        verify(responseWorkspaceService).loadRequest(requestId, access)
    }

    @Test
    fun `get refuses a token bound to a different request than the one named in the path`()
    {
        whenever(readAccessService.resolve(otherRequestId, "raw-token", null)).thenThrow(otherRequestRefusal())

        val response = resource.get(otherRequestId.toString(), "raw-token")

        assertNotFound(response)
        verify(responseWorkspaceService, never()).loadRequest(any(), any())
    }

    @Test
    fun `parties without a token is rejected before the read access service is called`()
    {
        val response = resource.parties(requestId.toString(), null)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(readAccessService, never()).resolve(any(), any(), anyOrNull())
    }

    @Test
    fun `parties resolves the token then returns the same party projection the authenticated surface uses`()
    {
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(partyQueryService.listForManagement(requestId, access)).thenReturn(partyListing)

        val response = resource.parties(requestId.toString(), "raw-token")

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(partyListing.partiesETag, response.getHeaderString("ETag"))
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as Array<InformationRequestPartyDto>
        assertEquals(1, body.size)
    }

    @Test
    fun `patchResponses without a token is rejected before the read access service is called`()
    {
        val response = resource.patchResponses(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            null,
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(readAccessService, never()).resolve(any(), any(), anyOrNull())
    }

    @Test
    fun `patchResponses refuses a token bound to a different request than the one named in the path`()
    {
        whenever(readAccessService.resolve(otherRequestId, "raw-token", null)).thenThrow(otherRequestRefusal())

        val response = resource.patchResponses(
            otherRequestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "raw-token",
            "\"etag\"",
            "key-1",
        )

        assertNotFound(response)
        verify(responseDraftService, never()).patch(any())
    }

    @Test
    fun `patchResponses resolves the token then delegates to the same draft service the authenticated surface uses`()
    {
        val ownResponse = InformationRequestResponse().apply {
            informationRequestId = requestId
            informationRequestRequirementId = requirementId
            requirementRevisionId = UUID.randomUUID()
            occurrencePath = "root"
            disposition = InformationRequestResponseDisposition.PROVIDED
            fieldValueSetId = UUID.randomUUID()
            responseRevision = 2
            recordedByPrincipalKind = PrincipalKind.PARTICIPANT
            recordedByPrincipalId = UUID.randomUUID()
        }
        val fieldValue = FieldValueDto(
            fieldContractId = UUID.randomUUID(),
            schemaFieldBindingId = UUID.randomUUID(),
            namespace = "process",
            fieldKey = "recorded-note",
            label = "Recorded note",
            valueType = FieldValueType.SHORT_TEXT,
            isEmpty = false,
            value = JsonPrimitive("Done"),
        )
        val result = InformationRequestResponseDraftResult(
            request = request,
            responseETag = "\"responses-etag\"",
            responses = listOf(ownResponse),
            requirementsById = mapOf(
                requirementId to requirement(requirementId, sourceTemplateRequirementId, sourceTemplateBindingId),
            ),
            fieldValueProjectionsByRequirementId = mapOf(
                requirementId to fieldProjection(listOf(fieldValue), "\"field-values-etag\""),
            ),
        )
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(responseDraftService.patch(any())).thenReturn(result)

        val response = resource.patchResponses(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
                confirmedHiddenResponseClearRequirementIds = setOf(requirementId),
            ),
            "raw-token",
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals("\"responses-etag\"", response.getHeaderString("ETag"))
        val body = response.entity as Array<InformationRequestResponseDto>
        assertEquals(1, body.size)
        assertEquals(requirementId, body.single().informationRequestRequirementId)
        assertEquals(sourceTemplateRequirementId, body.single().sourceTemplateRequirementId)
        assertEquals(sourceTemplateBindingId, body.single().sourceTemplateBindingId)
        assertEquals(ownResponse.fieldValueSetId, body.single().fieldValueSetId)
        assertEquals("\"field-values-etag\"", body.single().fieldValueSetETag)
        assertEquals(listOf(fieldValue), body.single().fieldValues)
        val captor = org.mockito.kotlin.argumentCaptor<PatchInformationRequestResponsesCommand>()
        verify(responseDraftService).patch(captor.capture())
        assertEquals(setOf(requirementId), captor.firstValue.confirmedHiddenResponseClears)
    }

    @Test
    fun `patchResponses maps every response the draft service discloses without filtering them again`() {
        val otherRequirementId = UUID.randomUUID()
        val responses = listOf(requirementId, otherRequirementId).map { id ->
            InformationRequestResponse().apply {
                informationRequestId = requestId
                informationRequestRequirementId = id
                requirementRevisionId = UUID.randomUUID()
                occurrencePath = "root"
                disposition = InformationRequestResponseDisposition.PROVIDED
                responseRevision = 2
                recordedByPrincipalKind = PrincipalKind.PARTICIPANT
                recordedByPrincipalId = UUID.randomUUID()
            }
        }
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(responseDraftService.patch(any())).thenReturn(
            InformationRequestResponseDraftResult(
                request = request,
                responseETag = "\"responses-etag\"",
                responses = responses,
                requirementsById = mapOf(
                    requirementId to requirement(requirementId, sourceTemplateRequirementId, sourceTemplateBindingId),
                    otherRequirementId to requirement(otherRequirementId, UUID.randomUUID(), UUID.randomUUID()),
                ),
            ),
        )

        val response = resource.patchResponses(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "raw-token",
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as Array<InformationRequestResponseDto>
        assertEquals(listOf(requirementId, otherRequirementId), body.map { it.informationRequestRequirementId })
    }

    @Test
    fun `an item refusal on the access link surface maps to 409 naming each refused Requirement`() {
        val refusal = InformationRequestItemRefusalException(
            InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED,
            "The response did not pass structured validation",
            listOf(
                InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED,
                    message = "This entry repeats an earlier entry",
                    requirementId = requirementId,
                    occurrencePath = "entries[2]",
                    validationKind = InformationRequestStructuredResponseValidationKind.DUPLICATE,
                ),
            ),
        )
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(responseDraftService.patch(any())).thenThrow(refusal)
        whenever(occurrenceService.add(any())).thenThrow(refusal)

        val patched = resource.patchResponses(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "raw-token",
            "\"etag\"",
            "key-1",
        )
        val added = resource.addGroupOccurrence(
            requestId.toString(),
            CreateInformationRequestGroupOccurrenceRequest(groupKey = "entries"),
            "raw-token",
            "\"etag\"",
            "key-2",
        )

        listOf(patched, added).forEach { response ->
            assertEquals(Response.Status.CONFLICT.statusCode, response.status)
            val body = org.junit.jupiter.api.Assertions.assertInstanceOf(
                InformationRequestItemRefusalDto::class.java,
                response.entity
            )
            assertEquals(InformationRequestErrorCatalog.STRUCTURED_RESPONSE_VALIDATION_FAILED, body.reasonCode)
            assertEquals(listOf(requirementId), body.problems.map { it.requirementId })
            assertEquals(
                InformationRequestStructuredResponseValidationKind.DUPLICATE,
                body.problems.single().validationKind
            )
        }
    }

    @Test
    fun `a stored rule refusing a save on the access link surface answers 409 with the constraint code`() {
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(responseDraftService.patch(any())).thenThrow(
            RuntimeException(
                "transaction rolled back",
                jakarta.persistence.PersistenceException(
                    "could not execute statement",
                    java.sql.SQLException("refused by a stored rule", "22001"),
                ),
            ),
        )

        val response = resource.patchResponses(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "raw-token",
            "\"etag\"",
            "key-stored-rule",
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(
            InformationRequestErrorCatalog.CONSTRAINT_CONFLICT,
            (response.entity as com.docuhyphen.app.api.resource.model.ResponseError).reasonCode,
        )
    }

    @Test
    fun `a refused hidden clear on the access link surface names the Requirements to confirm`() {
        val target = com.docuhyphen.app.api.model.informationrequest.response.HiddenResponseClearTarget(
            requirementId,
            sourceTemplateBindingId,
            "root"
        )
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(responseDraftService.patch(any())).thenThrow(
            com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponseClearRequiredException(
                listOf(target)
            ),
        )

        val response = resource.patchResponses(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "raw-token",
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        val body = response.entity as HiddenResponseClearRequiredErrorDto
        assertEquals(InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED, body.reasonCode)
        assertEquals(listOf(requirementId), body.hiddenResponseClears.map { it.requirementId })
    }

    private fun requirement(id: UUID, sourceRequirementId: UUID, sourceBindingId: UUID) =
        InformationRequestRequirement().apply {
            this.id = id
            informationRequestId = requestId
            sourceTemplateRequirementId = sourceRequirementId
            sourceTemplateBindingId = sourceBindingId
            occurrencePath = "root"
        }

    @Test
    fun `addGroupOccurrence resolves the token then delegates to the shared occurrence service`()
    {
        val occurrence = com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence().apply {
            informationRequestId = requestId
            sourceTemplateGroupId = UUID.randomUUID()
            occurrenceIndex = 0
            occurrencePath = "items[0]"
        }
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(occurrenceService.add(any())).thenReturn(
            InformationRequestGroupOccurrenceResult(
                request = request,
                responseETag = "\"occurrences-etag\"",
                occurrences = listOf(occurrence),
            ),
        )

        val response = resource.addGroupOccurrence(
            requestId.toString(),
            CreateInformationRequestGroupOccurrenceRequest(groupKey = "items"),
            "raw-token",
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals("\"occurrences-etag\"", response.getHeaderString("ETag"))
        val body = response.entity as Array<InformationRequestGroupOccurrenceDto>
        assertEquals("items[0]", body.single().occurrencePath)
        verify(occurrenceService).add(any())
    }

    @Test
    fun `a JAX-RS refusal from a no-auth occurrence command is rethrown as on the authenticated surface`() {
        val refusal = NotFoundException("Group occurrence not found")
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(occurrenceService.remove(any())).thenThrow(refusal)

        val thrown = assertThrows<NotFoundException> {
            resource.removeGroupOccurrence(
                requestId.toString(),
                UUID.randomUUID().toString(),
                "raw-token",
                "\"etag\"",
                "remove-key",
            )
        }

        assertEquals(refusal, thrown)
    }

    @Test
    fun `remove and reorder group occurrences reject a token bound to another request`()
    {
        whenever(readAccessService.resolve(otherRequestId, "raw-token", null)).thenThrow(otherRequestRefusal())

        val remove = resource.removeGroupOccurrence(
            otherRequestId.toString(),
            UUID.randomUUID().toString(),
            "raw-token",
            "\"etag\"",
            "remove-key",
        )
        val reorder = resource.reorderGroupOccurrences(
            otherRequestId.toString(),
            ReorderInformationRequestGroupOccurrencesRequest("items", occurrenceIds = emptyList()),
            "raw-token",
            "\"etag\"",
            "reorder-key",
        )

        assertNotFound(remove)
        assertNotFound(reorder)
        verify(occurrenceService, never()).remove(any())
        verify(occurrenceService, never()).reorder(any())
    }

    private fun otherRequestRefusal() =
        InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request not found")

    private fun assertNotFound(response: Response) {
        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        assertEquals(
            InformationRequestErrorCatalog.NOT_FOUND,
            (response.entity as com.docuhyphen.app.api.resource.model.ResponseError).reasonCode,
        )
    }

    private fun fieldProjection(fields: List<FieldValueDto>, etag: String) = SchemaAssignmentDto(
        id = UUID.randomUUID(),
        resourceType = "INFORMATION_REQUEST",
        resourceId = requestId,
        schemaVersionId = UUID.randomUUID(),
        schemaDefinitionId = UUID.randomUUID(),
        schemaKey = "process-data",
        displayName = "Process data",
        versionNumber = 1,
        assignmentSource = SchemaAssignmentSource.API,
        assignedAt = Timestamp.from(Instant.now()),
        fields = fields,
        etag = etag,
    )

    @Test
    fun `a plan refusal on the access link surface reaches the subscription mapper instead of becoming a server error`() {
        val denial = SubscriptionDenialException(
            SubscriptionDenial(
                reason = SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED,
                planCode = PlanCode.BUSINESS,
                ownerType = SubscriptionOwnerType.ORGANIZATION,
                message = "The owner's subscription is suspended",
            ),
        )
        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(responseDraftService.patch(any())).thenThrow(denial)

        val thrown = assertThrows<SubscriptionDenialException> {
            resource.patchResponses(
                requestId.toString(),
                PatchInformationRequestResponsesRequest(
                    listOf(
                        InformationRequestResponsePatchRequest(
                            requirementId,
                            InformationRequestResponseDisposition.PROVIDED
                        )
                    ),
                ),
                "raw-token",
                "\"etag\"",
                "key-1",
            )
        }
        assertEquals(denial, thrown)
    }

    @Test
    fun `domain failures map to stable response statuses`()
    {
        whenever(readAccessService.resolve(requestId, "session-required-token", null))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
                    "Contact verification is required",
                ),
            )
        val conflict = resource.get(requestId.toString(), "session-required-token")
        assertEquals(Response.Status.CONFLICT.statusCode, conflict.status)

        whenever(readAccessService.resolve(requestId, "denied-token", null)).thenThrow(ForbiddenException("Denied"))
        val denied = resource.get(requestId.toString(), "denied-token")
        assertEquals(Response.Status.FORBIDDEN.statusCode, denied.status)

        whenever(readAccessService.resolve(requestId, "raw-token", null)).thenReturn(access)
        whenever(responseWorkspaceService.load(requestId, access)).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            ),
        )
        assertNotFound(resource.responseWorkspace(requestId.toString(), "raw-token"))
    }
}
