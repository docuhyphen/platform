package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestGroupOccurrenceDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestGroupOccurrenceResult
import com.docuhyphen.app.api.service.informationrequest.InformationRequestGroupOccurrenceService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.DELETE
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
import java.util.UUID

class InformationRequestGroupOccurrenceResourceContractTest
{
    private val occurrenceService = mock<InformationRequestGroupOccurrenceService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestGroupOccurrenceResource(occurrenceService, accessContextFactory)
    private val requestId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext())

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `resource is mounted under the request group occurrence sub-path and exposes add remove and reorder`()
    {
        val resourceClass = InformationRequestGroupOccurrenceResource::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals("/information-requests/{id}/group-occurrences", resourceClass.getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("add").isAnnotationPresent(POST::class.java))
        assertTrue(methods.getValue("remove").isAnnotationPresent(DELETE::class.java))
        assertEquals("/{occurrenceId}", methods.getValue("remove").getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("reorder").isAnnotationPresent(PATCH::class.java))
        assertEquals("/order", methods.getValue("reorder").getAnnotation(Path::class.java).value)
    }

    @Test
    fun `add validates the request before touching the service`()
    {
        val response = resource.add(
            "not-a-uuid",
            CreateInformationRequestGroupOccurrenceRequest(groupKey = "items"),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(occurrenceService, never()).add(any())
    }

    @Test
    fun `add delegates with precondition and returns active occurrence projection with ETag`()
    {
        val occurrence = occurrence("items[0]")
        whenever(occurrenceService.add(any())).thenReturn(
            InformationRequestGroupOccurrenceResult(
                request = request(),
                responseETag = "\"occurrences-etag\"",
                occurrences = listOf(occurrence),
            ),
        )

        val response = resource.add(
            requestId.toString(),
            CreateInformationRequestGroupOccurrenceRequest(groupKey = "items"),
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
    fun `remove and reorder map service refusals to stable statuses`()
    {
        whenever(occurrenceService.remove(any()))
            .thenThrow(CommandPreconditionException.stale("\"current-etag\""))
        whenever(occurrenceService.reorder(any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.GROUP_OCCURRENCE_ORDER_INVALID,
                    "The occurrence order is invalid",
                ),
            )

        val stale = resource.remove(
            requestId.toString(),
            UUID.randomUUID().toString(),
            "\"stale\"",
            "remove-key",
        )
        val invalidOrder = resource.reorder(
            requestId.toString(),
            ReorderInformationRequestGroupOccurrencesRequest("items", occurrenceIds = emptyList()),
            "\"etag\"",
            "reorder-key",
        )

        assertEquals(412, stale.status)
        assertEquals("\"current-etag\"", stale.getHeaderString("ETag"))
        assertEquals(Response.Status.CONFLICT.statusCode, invalidOrder.status)
        assertEquals(
            InformationRequestErrorCatalog.GROUP_OCCURRENCE_ORDER_INVALID,
            (invalidOrder.entity as ResponseError).reasonCode,
        )
    }

    @Test
    fun `a denial from the occurrence service maps to 403`()
    {
        whenever(occurrenceService.add(any()))
            .thenThrow(ForbiddenException("Access denied to change Information Request group occurrences"))

        val response = resource.add(
            requestId.toString(),
            CreateInformationRequestGroupOccurrenceRequest(groupKey = "items"),
            "\"etag\"",
            "key-1",
        )

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
    }

    private fun request() = InformationRequest().apply {
        id = requestId
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
    }

    private fun occurrence(path: String) = InformationRequestGroupOccurrence().apply {
        informationRequestId = requestId
        sourceTemplateGroupId = UUID.randomUUID()
        occurrenceIndex = 0
        occurrencePath = path
    }
}
