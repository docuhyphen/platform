package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.model.dto.InformationRequestPartyDto
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.AssignExternalParticipantInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyAssignmentResult
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyListing
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestPartyResourceOperations
import com.docuhyphen.app.api.resource.model.AssignInformationRequestPartyRequest
import com.docuhyphen.app.api.resource.model.ReassignInformationRequestPartyRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.execution.RequestExecutionUsageExhaustedException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestPartyResourceContractTest
{
    private val partyQueryService = mock<InformationRequestPartyQueryService>()
    private val partyService = mock<InformationRequestPartyService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestPartyResource(partyQueryService, partyService, accessContextFactory)

    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val requestId = UUID.randomUUID()
    private val partyDto = InformationRequestPartyDto(
        id = UUID.randomUUID(),
        informationRequestId = requestId,
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
        active = true,
        assignedAt = Timestamp.from(Instant.now()),
        partyRevision = 1,
        partyETag = "\"party-etag\"",
    )
    private val assigned = InformationRequestPartyAssignmentResult(
        party = InformationRequestParty().apply {
            informationRequestId = requestId
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        },
        partiesETag = "\"parties-2\"",
        partyETag = "\"party-1\"",
    )

    init
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `parties are listed, assigned, reassigned, and revoked under the request's parties path`()
    {
        val methods = InformationRequestPartyResourceOperations::class.java.declaredMethods.associateBy { it.name }

        assertEquals(
            "/information-requests/{id}/parties",
            InformationRequestPartyResourceOperations::class.java.getAnnotation(Path::class.java).value
        )
        assertTrue(methods.getValue("list").isAnnotationPresent(GET::class.java))
        assertTrue(methods.getValue("assign").isAnnotationPresent(POST::class.java))
        assertEquals("/{partyId}/reassignment", methods.getValue("reassign").getAnnotation(Path::class.java).value)
        assertEquals("/{partyId}/revocation", methods.getValue("revoke").getAnnotation(Path::class.java).value)
    }

    @Test
    fun `list answers the parties with the ETag the next party change must match`()
    {
        whenever(partyQueryService.listForManagement(requestId, access))
            .thenReturn(InformationRequestPartyListing(listOf(partyDto), "\"parties-1\""))

        val invalid = resource.list("not-a-uuid")
        val listed = resource.list(requestId.toString())

        assertEquals(Response.Status.BAD_REQUEST.statusCode, invalid.status)
        assertEquals(Response.Status.OK.statusCode, listed.status)
        assertEquals("\"parties-1\"", listed.getHeaderString("ETag"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(partyDto), (listed.entity as Array<InformationRequestPartyDto>).toList())
    }

    @Test
    fun `an assignment names exactly one party and each kind reaches its own party command`()
    {
        val userId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()
        whenever(partyService.assign(any())).thenReturn(assigned)
        whenever(partyService.assignExternalParticipant(any())).thenReturn(assigned)

        val byUser = resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(InformationRequestShareRoleKey.DECISION_MAKER, userId = userId),
            "\"parties-1\"",
            "assign-user",
        )
        resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(InformationRequestShareRoleKey.REVIEWER, principalGroupId = groupId),
            "\"parties-1\"",
            "assign-group",
        )
        resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(
                InformationRequestShareRoleKey.CONTRIBUTOR,
                email = "contact@example.test",
                displayName = "Contact",
            ),
            "\"parties-1\"",
            "assign-contact",
        )
        resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(
                InformationRequestShareRoleKey.SUBJECT,
                subjectIdentityRefId = subjectId
            ),
            "\"parties-1\"",
            "assign-subject",
        )
        val none = resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(InformationRequestShareRoleKey.CONTRIBUTOR),
            "\"parties-1\"",
            "assign-none",
        )
        val two = resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(
                InformationRequestShareRoleKey.CONTRIBUTOR,
                userId = userId,
                email = "contact@example.test"
            ),
            "\"parties-1\"",
            "assign-two",
        )
        val unkeyed = resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(InformationRequestShareRoleKey.CONTRIBUTOR, userId = userId),
            "\"parties-1\"",
            null,
        )

        assertEquals(Response.Status.CREATED.statusCode, byUser.status)
        assertEquals("\"parties-2\"", byUser.getHeaderString("ETag"))
        assertEquals(Response.Status.BAD_REQUEST.statusCode, none.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, two.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, unkeyed.status)
        val precondition = CommandPrecondition.ExpectedRevision(setOf("\"parties-1\""))
        verify(partyService).assign(
            AssignInformationRequestPartyCommand(
                requestId = requestId,
                roleKey = InformationRequestShareRoleKey.DECISION_MAKER,
                principal = PrincipalRef.user(userId),
                access = access,
                precondition = precondition,
                idempotencyKey = "assign-user",
            ),
        )
        verify(partyService).assign(
            AssignInformationRequestPartyCommand(
                requestId = requestId,
                roleKey = InformationRequestShareRoleKey.REVIEWER,
                principal = PrincipalRef.group(groupId),
                access = access,
                precondition = precondition,
                idempotencyKey = "assign-group",
            ),
        )
        verify(partyService).assign(
            AssignInformationRequestPartyCommand(
                requestId = requestId,
                roleKey = InformationRequestShareRoleKey.SUBJECT,
                subjectIdentityRefId = subjectId,
                access = access,
                precondition = precondition,
                idempotencyKey = "assign-subject",
            ),
        )
        verify(partyService).assignExternalParticipant(
            AssignExternalParticipantInformationRequestPartyCommand(
                requestId = requestId,
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR,
                email = "contact@example.test",
                displayName = "Contact",
                access = access,
                precondition = precondition,
                idempotencyKey = "assign-contact",
            ),
        )
    }

    @Test
    fun `reassignment and revocation act on one party under the parties precondition`()
    {
        val partyId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        whenever(partyService.reassign(any())).thenReturn(assigned)
        whenever(partyService.revoke(any())).thenReturn(assigned)

        val reassigned = resource.reassign(
            requestId.toString(),
            partyId.toString(),
            ReassignInformationRequestPartyRequest(userId = userId),
            "\"parties-1\"",
            "reassign-party",
        )
        val nobody = resource.reassign(
            requestId.toString(),
            partyId.toString(),
            ReassignInformationRequestPartyRequest(),
            "\"parties-1\"",
            "reassign-nobody",
        )
        val revoked = resource.revoke(requestId.toString(), partyId.toString(), "\"parties-1\"", "revoke-party")

        assertEquals(Response.Status.OK.statusCode, reassigned.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, nobody.status)
        assertEquals(Response.Status.OK.statusCode, revoked.status)
        assertEquals("\"parties-2\"", revoked.getHeaderString("ETag"))
        verify(partyService).reassign(
            ReassignInformationRequestPartyCommand(
                requestId = requestId,
                partyId = partyId,
                principal = PrincipalRef.user(userId),
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"parties-1\"")),
                idempotencyKey = "reassign-party",
            ),
        )
        verify(partyService).revoke(
            RevokeInformationRequestPartyCommand(
                requestId = requestId,
                partyId = partyId,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(setOf("\"parties-1\"")),
                idempotencyKey = "revoke-party",
            ),
        )
    }

    @Test
    fun `an absent precondition reaches the service, and exhausted capacity and a denial answer stable statuses`()
    {
        whenever(partyService.assign(any())).thenThrow(
            RequestExecutionUsageExhaustedException(
                UUID.randomUUID(),
                RequestExecutionUsageKind.ACTING_PARTY,
                1,
                1,
                1
            ),
        )
        whenever(partyQueryService.listForManagement(requestId, access)).thenThrow(ForbiddenException("Access denied"))

        val unconditioned = resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(
                InformationRequestShareRoleKey.CONTRIBUTOR,
                userId = UUID.randomUUID()
            ),
            null,
            "assign-without-precondition",
        )
        val exhausted = resource.assign(
            requestId.toString(),
            AssignInformationRequestPartyRequest(
                InformationRequestShareRoleKey.CONTRIBUTOR,
                userId = UUID.randomUUID()
            ),
            "\"parties-1\"",
            "assign-exhausted",
        )
        val denied = resource.list(requestId.toString())

        assertEquals(Response.Status.CONFLICT.statusCode, unconditioned.status)
        val sent = argumentCaptor<AssignInformationRequestPartyCommand>()
        verify(partyService, times(2)).assign(sent.capture())
        assertEquals(CommandPrecondition.Absent, sent.firstValue.precondition)
        assertEquals(Response.Status.CONFLICT.statusCode, exhausted.status)
        assertEquals(InformationRequestErrorCatalog.CAPACITY_EXHAUSTED, (exhausted.entity as ResponseError).reasonCode)
        assertEquals(Response.Status.FORBIDDEN.statusCode, denied.status)
        verify(partyService, never()).assignExternalParticipant(any())
    }
}
