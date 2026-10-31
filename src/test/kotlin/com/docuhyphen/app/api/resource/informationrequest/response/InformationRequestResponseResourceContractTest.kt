package com.docuhyphen.app.api.resource.informationrequest.response

import com.docuhyphen.app.api.exception.InformationRequestVerificationRequiredException
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.HiddenResponseClearTarget
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponseDraftResult
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.resource.informationrequest.response.operations.InformationRequestResponseResourceOperations
import com.docuhyphen.app.api.resource.model.InformationRequestResponseFieldValuesPatchRequest
import com.docuhyphen.app.api.resource.model.InformationRequestResponsePatchRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponseClearRequiredException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseDraftService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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
        val resourceClass = InformationRequestResponseResourceOperations::class.java
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
    fun `a wildcard If-Match is sent to the service as a missing precondition`() {
        whenever(responseDraftService.patch(any()))
            .thenThrow(CommandPreconditionException.required("\"responses-etag\""))

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId = requirementId,
                        disposition = InformationRequestResponseDisposition.PROVIDED,
                        narrative = "Done.",
                    ),
                ),
            ),
            "*",
            "key-1",
        )

        assertEquals(428, response.status)
        verify(responseDraftService).patch(
            org.mockito.kotlin.argThat { precondition == com.docuhyphen.app.api.model.command.CommandPrecondition.Absent },
        )
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
    fun `patch delegates to the draft service and maps every response the service discloses`()
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
        assertEquals(listOf(requirementId, otherRequirementId), body.map { it.informationRequestRequirementId })
        val own = body.first()
        assertEquals(sourceTemplateRequirementId, own.sourceTemplateRequirementId)
        assertEquals(sourceTemplateBindingId, own.sourceTemplateBindingId)
        assertEquals(ownResponse.fieldValueSetId, own.fieldValueSetId)
        assertEquals("\"field-values-etag\"", own.fieldValueSetETag)
        assertEquals(listOf(fieldValue), own.fieldValues)

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
    fun `a lapsed verification for a protected item maps to 403 with the confidentiality reason code`() {
        whenever(responseDraftService.patch(any()))
            .thenThrow(InformationRequestVerificationRequiredException("This protected item needs you to verify again before it can be saved"))

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        UUID.randomUUID(),
                        narrative = "Neutral note"
                    )
                )
            ),
            "\"etag\"",
            "key-verification",
        )

        assertEquals(403, response.status)
        val entity = response.entity as ResponseError
        assertEquals(InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED, entity.reasonCode)
        assertEquals("This protected item needs you to verify again before it can be saved", entity.errorMessage)
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
    fun `a stored rule refusing a save maps to 409 with the constraint code`() {
        whenever(responseDraftService.patch(any())).thenThrow(
            RuntimeException(
                "transaction rolled back",
                jakarta.persistence.PersistenceException(
                    "could not execute statement",
                    java.sql.SQLException("refused by a stored rule", "23514"),
                ),
            ),
        )

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "\"etag\"",
            "key-stored-rule",
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(InformationRequestErrorCatalog.CONSTRAINT_CONFLICT, (response.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a request the service cannot find maps to 404 with its reason code`() {
        whenever(responseDraftService.patch(any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request not found"
                )
            )

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, (response.entity as ResponseError).reasonCode)
    }

    @Test
    fun `a refused hidden clear maps to 409 naming the Requirements to confirm`() {
        val target = HiddenResponseClearTarget(otherRequirementId, sourceTemplateBindingId, "entries.2")
        whenever(responseDraftService.patch(any())).thenThrow(
            InformationRequestHiddenResponseClearRequiredException(
                listOf(target)
            )
        )

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        val body = response.entity as HiddenResponseClearRequiredErrorDto
        assertEquals(InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED, body.reasonCode)
        assertEquals(
            listOf(HiddenResponseClearTargetDto(otherRequirementId, sourceTemplateBindingId, "entries.2")),
            body.hiddenResponseClears
        )
    }

    @Test
    fun `an item refusal maps to 409 naming each refused Requirement`() {
        whenever(responseDraftService.patch(any())).thenThrow(
            InformationRequestItemRefusalException(
                InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED,
                "The response disposition is not permitted",
                listOf(
                    InformationRequestItemProblem(
                        code = InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED,
                        message = "This Requirement does not permit that disposition",
                        requirementId = requirementId,
                        occurrencePath = "entries[1]",
                    ),
                ),
                undisclosedProblemCount = 2,
            ),
        )

        val response = resource.patch(
            requestId.toString(),
            PatchInformationRequestResponsesRequest(
                listOf(
                    InformationRequestResponsePatchRequest(
                        requirementId,
                        InformationRequestResponseDisposition.PROVIDED
                    )
                ),
            ),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        val body = org.junit.jupiter.api.Assertions.assertInstanceOf(
            InformationRequestItemRefusalDto::class.java,
            response.entity
        )
        assertEquals(InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED, body.reasonCode)
        assertEquals(2, body.undisclosedProblemCount)
        assertEquals(
            listOf(
                InformationRequestItemProblemDto(
                    code = InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED,
                    message = "This Requirement does not permit that disposition",
                    requirementId = requirementId,
                    occurrencePath = "entries[1]",
                ),
            ),
            body.problems,
        )
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
