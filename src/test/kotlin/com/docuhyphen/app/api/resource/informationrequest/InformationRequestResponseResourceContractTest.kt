package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.FieldValueDto
import com.docuhyphen.app.api.model.dto.InformationRequestResponseDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.resource.model.InformationRequestResponseFieldValuesPatchRequest
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseDraftResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseDraftService
import com.docuhyphen.app.api.service.informationrequest.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.fields.FieldValueEntry
import com.docuhyphen.app.api.service.fields.FieldsPrecondition
import kotlinx.serialization.json.JsonPrimitive
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.PATCH
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
 * The authenticated, respondent-facing sub-resource exposing
 * [InformationRequestResponseDraftService]'s sparse patch. Only the requirements a caller's own
 * patch names are ever projected back in the response body.
 */
class InformationRequestResponseResourceContractTest
{
    private val responseDraftService = mock<InformationRequestResponseDraftService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestResponseResource(responseDraftService, accessContextFactory)

    private val access = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext())
    private val requestId = UUID.randomUUID()
    private val requirementId = UUID.randomUUID()
    private val otherRequirementId = UUID.randomUUID()
    private val sourceTemplateRequirementId = UUID.randomUUID()
    private val sourceTemplateBindingId = UUID.randomUUID()
    private val fieldContractId = UUID.randomUUID()

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `resource is mounted under the request's responses sub-path and exposes patch`()
    {
        val resourceClass = InformationRequestResponseResource::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals("/information-requests/{id}/responses", resourceClass.getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("patch").isAnnotationPresent(PATCH::class.java))
    }

    @Test
    fun `patch rejects an invalid request id before touching the service`()
    {
        val response = resource.patch(
            "not-a-uuid",
            PatchInformationRequestResponsesRequest(emptyList()),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(responseDraftService, never()).patch(any())
    }

    @Test
    fun `patch requires an idempotency key`()
    {
        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(InformationRequestResponsePatchRequest(requirementId, InformationRequestResponseDisposition.PROVIDED)),
            ),
            "\"etag\"",
            null,
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(responseDraftService, never()).patch(any())
    }

    @Test
    fun `patch rejects an empty patch list`()
    {
        val response = resource.patch(requestId.toString(), PatchInformationRequestResponsesRequest(emptyList()), "\"etag\"", "key-1")

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(responseDraftService, never()).patch(any())
    }

    @Test
    fun `patch rejects a narrative that is both set and cleared in the same entry`()
    {
        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId = requirementId,
                        narrative = "kept",
                        clearNarrative = true,
                    ),
                ),
            ),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(responseDraftService, never()).patch(any())
    }

    @Test
    fun `patch delegates to the draft service and projects only the requirements the caller patched`()
    {
        val ownResponse = InformationRequestResponse().apply {
            informationRequestId = requestId
            informationRequestRequirementId = requirementId
            requirementRevisionId = UUID.randomUUID()
            occurrencePath = "root"
            disposition = InformationRequestResponseDisposition.PROVIDED
            narrative = "Done."
            fieldValueSetId = UUID.randomUUID()
            responseRevision = 2
            recordedByPrincipalKind = PrincipalKind.PARTICIPANT
            recordedByPrincipalId = UUID.randomUUID()
        }
        val fieldValue = FieldValueDto(
            fieldContractId = fieldContractId,
            schemaFieldBindingId = UUID.randomUUID(),
            namespace = "process",
            fieldKey = "recorded-note",
            label = "Recorded note",
            valueType = FieldValueType.SHORT_TEXT,
            isEmpty = false,
            value = JsonPrimitive("Done"),
        )
        val otherPartyResponse = InformationRequestResponse().apply {
            informationRequestId = requestId
            informationRequestRequirementId = otherRequirementId
            requirementRevisionId = UUID.randomUUID()
            occurrencePath = "root"
            disposition = InformationRequestResponseDisposition.UNAVAILABLE
            responseRevision = 1
            recordedByPrincipalKind = PrincipalKind.PARTICIPANT
            recordedByPrincipalId = UUID.randomUUID()
        }
        val request = InformationRequest().apply {
            id = requestId
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
        }
        val result = InformationRequestResponseDraftResult(
            request = request,
            responseETag = "\"responses-etag\"",
            responses = listOf(ownResponse, otherPartyResponse),
            requirementsById = mapOf(
                requirementId to requirement(requirementId, sourceTemplateRequirementId, sourceTemplateBindingId),
                otherRequirementId to requirement(otherRequirementId, UUID.randomUUID(), UUID.randomUUID()),
            ),
            fieldValueProjectionsByRequirementId = mapOf(
                requirementId to fieldProjection(listOf(fieldValue), "\"field-values-etag\""),
                otherRequirementId to fieldProjection(emptyList(), "\"other-field-values-etag\""),
            ),
        )
        whenever(responseDraftService.patch(any())).thenReturn(result)

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId = requirementId,
                        disposition = InformationRequestResponseDisposition.PROVIDED,
                        narrative = "Done.",
                        fieldValues = InformationRequestResponseFieldValuesPatchRequest(
                            etag = "\"field-values-etag\"",
                            values = listOf(FieldValueEntry(fieldContractId, JsonPrimitive("Done"))),
                        ),
                    ),
                ),
                confirmedHiddenResponseClearRequirementIds = setOf(otherRequirementId),
            ),
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
        assertEquals(requestId, captor.firstValue.requestId)
        assertEquals("key-1", captor.firstValue.idempotencyKey)
        assertEquals(access, captor.firstValue.access)
        assertEquals(setOf(otherRequirementId), captor.firstValue.confirmedHiddenResponseClears)
        val fieldPatch = requireNotNull(captor.firstValue.patches.single().fieldValues)
        assertEquals(FieldsPrecondition.ExpectedRevision("\"field-values-etag\""), fieldPatch.precondition)
        assertEquals(listOf(FieldValueEntry(fieldContractId, JsonPrimitive("Done"))), fieldPatch.entries)
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

    private fun requirement(id: UUID, sourceRequirementId: UUID, sourceBindingId: UUID) =
        InformationRequestRequirement().apply {
            this.id = id
            informationRequestId = requestId
            sourceTemplateRequirementId = sourceRequirementId
            sourceTemplateBindingId = sourceBindingId
            occurrencePath = "root"
        }

    @Test
    fun `a stale precondition maps to 412 with the current etag`()
    {
        whenever(responseDraftService.patch(any()))
            .thenThrow(CommandPreconditionException.stale("\"current-etag\""))

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(InformationRequestResponsePatchRequest(requirementId, InformationRequestResponseDisposition.PROVIDED)),
            ),
            "\"stale-etag\"",
            "key-1",
        )

        assertEquals(412, response.status)
        assertEquals("\"current-etag\"", response.getHeaderString("ETag"))
    }

    @Test
    fun `a domain refusal maps to 409 with its reason code`()
    {
        whenever(responseDraftService.patch(any()))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.STATE_INVALID, "Cannot respond now"))

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(InformationRequestResponsePatchRequest(requirementId, InformationRequestResponseDisposition.PROVIDED)),
            ),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, (response.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a denial from the draft service maps to 403`()
    {
        whenever(responseDraftService.patch(any()))
            .thenThrow(ForbiddenException("Access denied to respond to Information Request Requirement"))

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(InformationRequestResponsePatchRequest(requirementId, InformationRequestResponseDisposition.PROVIDED)),
            ),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
    }
}
