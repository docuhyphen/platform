package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.FieldValueDto
import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.dto.InformationRequestGroupOccurrenceDto
import com.docuhyphen.app.api.model.dto.InformationRequestPartyDto
import com.docuhyphen.app.api.model.dto.InformationRequestResponseDto
import com.docuhyphen.app.api.model.dto.InformationRequestResponseWorkspaceDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.service.informationrequest.InformationRequestGroupOccurrenceResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.model.informationrequest.InformationRequestNoAuthAccess
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseDraftResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseWorkspaceService
import com.docuhyphen.app.api.service.informationrequest.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import kotlinx.serialization.json.JsonPrimitive
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The no-auth, respondent-facing read adapter for a runtime Information Request bound to a verified
 * bootstrap access link. It never authenticates via the global access-token filter; it validates the
 * bootstrap token presented in [ACCESS_LINK_TOKEN_HEADER] itself, the same way
 * [InformationRequestNoAuthAccessResource] validates its own header, then delegates to the very same
 * [InformationRequestQueryService] and [InformationRequestPartyQueryService] the authenticated
 * surface uses, so both surfaces project identical data for an equivalent caller.
 */
class InformationRequestNoAuthRequestResourceContractTest
{
    private val readAccessService = mock<InformationRequestNoAuthReadAccessService>()
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
    private val requirementId = UUID.randomUUID()
    private val sourceTemplateRequirementId = UUID.randomUUID()
    private val sourceTemplateBindingId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext())
    private val noAuthAccess = InformationRequestNoAuthAccess(access, requestId)
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
            guarded.patchResponses(id, PatchInformationRequestResponsesRequest(listOf(
                InformationRequestResponsePatchRequest(requirementId, InformationRequestResponseDisposition.PROVIDED),
            )), "forwarded-bootstrap", "\"etag\"", "command"),
            guarded.addGroupOccurrence(id, CreateInformationRequestGroupOccurrenceRequest("items"),
                "forwarded-bootstrap", "\"etag\"", "command"),
            guarded.removeGroupOccurrence(id, UUID.randomUUID().toString(), "forwarded-bootstrap", "\"etag\"", "command"),
            guarded.reorderGroupOccurrences(id, ReorderInformationRequestGroupOccurrencesRequest("items", occurrenceIds = emptyList()),
                "forwarded-bootstrap", "\"etag\"", "command"),
        )
        responses.forEach { response ->
            assertEquals(Response.Status.CONFLICT.statusCode, response.status)
            assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
                (response.entity as com.docuhyphen.app.api.resource.model.ResponseError).reasonCode)
        }
        org.mockito.kotlin.verifyNoInteractions(partyQueryService, responseDraftService,
            occurrenceService, responseWorkspaceService)
    }

    @Test
    fun `content adapters pass the independent session credential to the resolver`()
    {
        whenever(readAccessService.resolve("bootstrap", "session-secret")).thenReturn(noAuthAccess)
        whenever(responseWorkspaceService.loadRequest(requestId, access)).thenReturn(InformationRequestDtoMapper.toDto(request))
        assertEquals(200, resource.get(requestId.toString(), "bootstrap", "session-secret").status)
        verify(readAccessService).resolve("bootstrap", "session-secret")
    }

    @Test
    fun `response workspace delegates through the verified no-auth access context`()
    {
        val workspace = mock<InformationRequestResponseWorkspaceDto>()
        whenever(readAccessService.resolve("bootstrap", "session-secret")).thenReturn(noAuthAccess)
        whenever(responseWorkspaceService.load(requestId, access)).thenReturn(workspace)

        val response = resource.responseWorkspace(requestId.toString(), "bootstrap", "session-secret")

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(workspace, response.entity)
        verify(responseWorkspaceService).load(requestId, access)
    }

    @Test
    fun `resource is mounted under the no-auth request prefix and exposes get, parties, and response patch`()
    {
        val resourceClass = InformationRequestNoAuthRequestResource::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }
        for (name in listOf("get", "parties", "responseWorkspace", "patchResponses", "addGroupOccurrence",
            "removeGroupOccurrence", "reorderGroupOccurrences"))
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
        verify(readAccessService, never()).resolve(any(), org.mockito.kotlin.anyOrNull())
    }

    @Test
    fun `get resolves the token then returns the same detail projection the authenticated surface uses`()
    {
        whenever(readAccessService.resolve("raw-token")).thenReturn(noAuthAccess)
        val projected = InformationRequestDtoMapper.toDto(request, listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-response-missing",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.UNKNOWN,
                    sourceRequirementKeys = setOf("prior-response"),
                    fieldDefinitionIds = emptySet(),
                ),
            ))
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
        whenever(readAccessService.resolve("raw-token")).thenReturn(noAuthAccess)

        val response = resource.get(UUID.randomUUID().toString(), "raw-token")

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(responseWorkspaceService, never()).loadRequest(any(), any())
    }

    @Test
    fun `parties without a token is rejected before the read access service is called`()
    {
        val response = resource.parties(requestId.toString(), null)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(readAccessService, never()).resolve(any(), org.mockito.kotlin.anyOrNull())
    }

    @Test
    fun `parties resolves the token then returns the same party projection the authenticated surface uses`()
    {
        whenever(readAccessService.resolve("raw-token")).thenReturn(noAuthAccess)
        whenever(partyQueryService.listForRequest(requestId, access)).thenReturn(listOf(partyDto))

        val response = resource.parties(requestId.toString(), "raw-token")

        assertEquals(Response.Status.OK.statusCode, response.status)
        @Suppress("UNCHECKED_CAST")
        val body = response.entity as List<InformationRequestPartyDto>
        assertEquals(1, body.size)
    }

    @Test
    fun `patchResponses without a token is rejected before the read access service is called`()
    {
        val response = resource.patchResponses(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(InformationRequestResponsePatchRequest(requirementId, InformationRequestResponseDisposition.PROVIDED)),
            ),
            null,
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(readAccessService, never()).resolve(any(), org.mockito.kotlin.anyOrNull())
    }

    @Test
    fun `patchResponses refuses a token bound to a different request than the one named in the path`()
    {
        whenever(readAccessService.resolve("raw-token")).thenReturn(noAuthAccess)

        val response = resource.patchResponses(
            UUID.randomUUID().toString(),
            PatchInformationRequestResponsesRequest(
                listOf(InformationRequestResponsePatchRequest(requirementId, InformationRequestResponseDisposition.PROVIDED)),
            ),
            "raw-token",
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
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
        whenever(readAccessService.resolve("raw-token")).thenReturn(noAuthAccess)
        whenever(responseDraftService.patch(any())).thenReturn(result)

        val response = resource.patchResponses(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(InformationRequestResponsePatchRequest(requirementId, InformationRequestResponseDisposition.PROVIDED)),
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
        whenever(readAccessService.resolve("raw-token")).thenReturn(noAuthAccess)
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
    fun `remove and reorder group occurrences reject a token bound to another request`()
    {
        whenever(readAccessService.resolve("raw-token")).thenReturn(noAuthAccess)

        val remove = resource.removeGroupOccurrence(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "raw-token",
            "\"etag\"",
            "remove-key",
        )
        val reorder = resource.reorderGroupOccurrences(
            UUID.randomUUID().toString(),
            ReorderInformationRequestGroupOccurrencesRequest("items", occurrenceIds = emptyList()),
            "raw-token",
            "\"etag\"",
            "reorder-key",
        )

        assertEquals(Response.Status.NOT_FOUND.statusCode, remove.status)
        assertEquals(Response.Status.NOT_FOUND.statusCode, reorder.status)
        verify(occurrenceService, never()).remove(any())
        verify(occurrenceService, never()).reorder(any())
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
    fun `domain failures map to stable response statuses`()
    {
        whenever(readAccessService.resolve("session-required-token"))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
                    "Contact verification is required",
                ),
            )
        val conflict = resource.get(requestId.toString(), "session-required-token")
        assertEquals(Response.Status.CONFLICT.statusCode, conflict.status)

        whenever(readAccessService.resolve("denied-token")).thenThrow(ForbiddenException("Denied"))
        val denied = resource.get(requestId.toString(), "denied-token")
        assertEquals(Response.Status.FORBIDDEN.statusCode, denied.status)
    }
}
