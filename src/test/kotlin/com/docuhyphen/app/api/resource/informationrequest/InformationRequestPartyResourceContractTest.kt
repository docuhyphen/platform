package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestPartyDto
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The owner- and party-facing sub-resource exposing [InformationRequestPartyQueryService]'s
 * recipient-safe party projection. This is the same service the no-auth surface uses, so both
 * surfaces reveal identical rows for an equivalent caller.
 */
class InformationRequestPartyResourceContractTest
{
    private val partyQueryService = mock<InformationRequestPartyQueryService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestPartyResource(partyQueryService, accessContextFactory)

    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val requestId = UUID.randomUUID()
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

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `resource is mounted under the request's parties sub-path`()
    {
        val resourceClass = InformationRequestPartyResource::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals("/information-requests/{id}/parties", resourceClass.getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("list").isAnnotationPresent(GET::class.java))
    }

    @Test
    fun `list rejects an invalid request id and otherwise delegates to the party query service`()
    {
        whenever(partyQueryService.listForRequest(requestId, access)).thenReturn(listOf(partyDto))

        val invalid = resource.list("not-a-uuid")
        val listed = resource.list(requestId.toString())

        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalid.status)
        assertEquals(Response.Status.OK.statusCode, listed.status)
        @Suppress("UNCHECKED_CAST")
        val body = listed.entity as List<InformationRequestPartyDto>
        assertEquals(1, body.size)
        assertEquals(partyDto.id, body.single().id)
    }

    @Test
    fun `a denial from the query service maps to 403`()
    {
        whenever(partyQueryService.listForRequest(requestId, access))
            .thenThrow(ForbiddenException("Access denied to view Information Request parties"))

        val response = resource.list(requestId.toString())

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
        assertEquals("Access denied to view Information Request parties", (response.entity as ResponseError).errorMessage)
    }
}
