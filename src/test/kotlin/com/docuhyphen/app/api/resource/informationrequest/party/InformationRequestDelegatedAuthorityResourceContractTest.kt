package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.dto.InformationRequestDelegatedAuthorityDto
import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestDelegatedAuthorityList
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestDelegatedAuthorityResult
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.resource.model.GrantInformationRequestDelegatedAuthorityRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestDelegatedAuthorityRequest
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestDelegatedAuthorityService
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestDelegatedAuthorityResourceContractTest
{
    private val delegatedAuthorityService = mock<InformationRequestDelegatedAuthorityService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource = InformationRequestDelegatedAuthorityResource(
        delegatedAuthorityService,
        accessContextFactory,
    )
    private val requestId = UUID.randomUUID()
    private val authorityId = UUID.randomUUID()
    private val assignedPartyId = UUID.randomUUID()
    private val delegatePrincipal = PrincipalRef.participant(UUID.randomUUID())
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val etag = "\"$requestId:8\""

    @Test
    fun `delegated authority uses resource based paths and verbs`()
    {
        val resourceClass = Class.forName(
            "com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestDelegatedAuthorityResourceOperations",
        )
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals("/information-requests/{id}/delegated-authorities", resourceClass.getAnnotation(Path::class.java).value)
        assertTrue(methods.getValue("grant").isAnnotationPresent(POST::class.java))
        assertFalse(methods.getValue("grant").isAnnotationPresent(Path::class.java))
        assertTrue(methods.getValue("revoke").isAnnotationPresent(POST::class.java))
        assertEquals(
            "/{authorityId}/revocations",
            methods.getValue("revoke").getAnnotation(Path::class.java).value,
        )

        val paths = methods.values.mapNotNull { it.getAnnotation(Path::class.java)?.value }
        assertTrue(paths.none { it.endsWith("/status") }, "Delegated authority exposes a status setter")
    }

    @Test
    fun `grant delegates authenticated access idempotency key and required precondition`()
    {
        val requirementId = UUID.randomUUID()
        val authority = authority(requirementId = requirementId)
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(delegatedAuthorityService.grant(any()))
            .thenReturn(InformationRequestDelegatedAuthorityResult(authority, etag))

        val effectiveAt = Timestamp.from(Instant.now())
        val expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
        val response = resource.grant(
            requestId.toString(),
            GrantInformationRequestDelegatedAuthorityRequest(
                assignedPartyId = assignedPartyId,
                delegatePrincipalKind = delegatePrincipal.kind,
                delegatePrincipalId = delegatePrincipal.id,
                requirementId = requirementId,
                authorityInstrumentRef = "authority-record:doc-42",
                effectiveAt = effectiveAt,
                expiresAt = expiresAt,
            ),
            etag,
            " grant-key ",
        )

        val command = argumentCaptor<GrantInformationRequestDelegatedAuthorityCommand>()
        verify(delegatedAuthorityService).grant(command.capture())
        assertEquals(Response.Status.CREATED.statusCode, response.status)
        assertEquals(etag, response.getHeaderString("ETag"))
        assertEquals(authority.id, (response.entity as InformationRequestDelegatedAuthorityDto).id)
        assertEquals(requestId, command.firstValue.requestId)
        assertEquals(assignedPartyId, command.firstValue.assignedPartyId)
        assertEquals(delegatePrincipal, command.firstValue.delegatePrincipal)
        assertEquals(requirementId, command.firstValue.requirementId)
        assertEquals("authority-record:doc-42", command.firstValue.authorityInstrumentRef)
        assertEquals(effectiveAt, command.firstValue.effectiveAt)
        assertEquals(expiresAt, command.firstValue.expiresAt)
        assertSame(access, command.firstValue.access)
        assertEquals("grant-key", command.firstValue.idempotencyKey)
        assertTrue(command.firstValue.precondition is CommandPrecondition.ExpectedRevision)
        assertDoesNotThrow { command.firstValue.precondition.requireSatisfiedBy(etag) }
    }

    @Test
    fun `revoke delegates authenticated access idempotency key and required precondition`()
    {
        val authority = authority().also { it.active = false }
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(delegatedAuthorityService.revoke(any()))
            .thenReturn(InformationRequestDelegatedAuthorityResult(authority, etag))

        val response = resource.revoke(
            requestId.toString(),
            authorityId.toString(),
            RevokeInformationRequestDelegatedAuthorityRequest(reason = "Access revoked"),
            etag,
            "revoke-key",
        )

        val command = argumentCaptor<RevokeInformationRequestDelegatedAuthorityCommand>()
        verify(delegatedAuthorityService).revoke(command.capture())
        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(etag, response.getHeaderString("ETag"))
        assertEquals(authority.id, (response.entity as InformationRequestDelegatedAuthorityDto).id)
        assertEquals(requestId, command.firstValue.requestId)
        assertEquals(authorityId, command.firstValue.authorityId)
        assertEquals("Access revoked", command.firstValue.reason)
        assertSame(access, command.firstValue.access)
        assertEquals("revoke-key", command.firstValue.idempotencyKey)
        assertDoesNotThrow { command.firstValue.precondition.requireSatisfiedBy(etag) }
    }

    @Test
    fun `listing returns the authorities of the request with the parties ETag`() {
        val authority = authority()
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(delegatedAuthorityService.list(requestId, access))
            .thenReturn(InformationRequestDelegatedAuthorityList(listOf(authority), etag))

        val response = resource.list(requestId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(etag, response.getHeaderString("ETag"))
        @Suppress("UNCHECKED_CAST")
        val listed = (response.entity as Array<InformationRequestDelegatedAuthorityDto>).toList()
        assertEquals(listOf(authority.id), listed.map { it.id })
        assertTrue(
            Class.forName(
                "com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestDelegatedAuthorityResourceOperations",
            ).declaredMethods.single { it.name == "list" }.isAnnotationPresent(GET::class.java),
        )
        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.list("not-a-uuid").status)
    }

    @Test
    fun `lifecycle refusals keep their reason code and a missing request is not found`() {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(delegatedAuthorityService.revoke(any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED,
                    "Grant revoked"
                )
            )
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request not found"
                )
            )

        assertMapped(Response.Status.CONFLICT.statusCode, InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED)
        assertMapped(Response.Status.NOT_FOUND.statusCode, InformationRequestErrorCatalog.NOT_FOUND)
    }

    @Test
    fun `an invalid window is a bad request, and an ineligible party or a revoked authority is a conflict, each with its reason`() {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(delegatedAuthorityService.grant(any()))
            .thenThrow(
                InformationRequestCommandRequestException(
                    "Delegated authority expiry must be after its effective date",
                    InformationRequestErrorCatalog.DELEGATED_AUTHORITY_WINDOW_INVALID,
                ),
            )
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.DELEGATED_AUTHORITY_PARTY_INELIGIBLE,
                    "Delegated authority cannot be granted for a subject party",
                ),
            )
        whenever(delegatedAuthorityService.revoke(any())).thenThrow(
            InformationRequestLifecycleException(
                InformationRequestErrorCatalog.DELEGATED_AUTHORITY_ALREADY_REVOKED,
                "Delegated authority is already revoked",
            ),
        )
        val request = GrantInformationRequestDelegatedAuthorityRequest(
            assignedPartyId = assignedPartyId,
            delegatePrincipalKind = delegatePrincipal.kind,
            delegatePrincipalId = delegatePrincipal.id,
        )

        val responses = listOf(
            resource.grant(requestId.toString(), request, etag, "grant-window"),
            resource.grant(requestId.toString(), request, etag, "grant-ineligible"),
            resource.revoke(requestId.toString(), authorityId.toString(), null, etag, "revoke-again"),
        )

        assertEquals(
            listOf(
                Response.Status.BAD_REQUEST.statusCode,
                Response.Status.CONFLICT.statusCode,
                Response.Status.CONFLICT.statusCode,
            ),
            responses.map { it.status },
        )
        assertEquals(
            listOf(
                InformationRequestErrorCatalog.DELEGATED_AUTHORITY_WINDOW_INVALID,
                InformationRequestErrorCatalog.DELEGATED_AUTHORITY_PARTY_INELIGIBLE,
                InformationRequestErrorCatalog.DELEGATED_AUTHORITY_ALREADY_REVOKED,
            ),
            responses.map { (it.entity as ResponseError).reasonCode },
        )
    }

    @Test
    fun `invalid paths and missing command keys are bad requests`()
    {
        val request = GrantInformationRequestDelegatedAuthorityRequest(
            assignedPartyId = assignedPartyId,
            delegatePrincipalKind = PrincipalKind.USER,
            delegatePrincipalId = UUID.randomUUID(),
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.grant("not-a-uuid", request, etag, "key").status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.grant(requestId.toString(), request, etag, "").status)
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.revoke(requestId.toString(), "not-a-uuid", null, etag, "key").status,
        )
        assertEquals(
            Response.Status.BAD_REQUEST.statusCode,
            resource.revoke(requestId.toString(), authorityId.toString(), null, etag, null).status,
        )
    }

    @Test
    fun `command refusals map to stable response statuses`()
    {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
        whenever(delegatedAuthorityService.revoke(any()))
            .thenThrow(CommandPreconditionException.required(etag))
            .thenThrow(CommandPreconditionException.stale(etag))
            .thenThrow(CommandReceiptConflictException())
            .thenThrow(IllegalStateException("Party changed"))
            .thenThrow(IllegalArgumentException("Delegated authority not found"))
            .thenThrow(RuntimeException("boom"))

        assertMapped(428, "COMMAND_PRECONDITION_REQUIRED", etag)
        assertMapped(Response.Status.PRECONDITION_FAILED.statusCode, "COMMAND_PRECONDITION_STALE", etag)
        assertMapped(Response.Status.CONFLICT.statusCode, "COMMAND_RECEIPT_FINGERPRINT_CONFLICT")
        assertMapped(Response.Status.CONFLICT.statusCode, null)
        assertMapped(Response.Status.NOT_FOUND.statusCode, null)
        assertMapped(Response.Status.INTERNAL_SERVER_ERROR.statusCode, null)
    }

    private fun assertMapped(status: Int, reasonCode: String?, currentETag: String? = null)
    {
        val response = resource.revoke(requestId.toString(), authorityId.toString(), null, etag, "key")
        assertEquals(status, response.status)
        assertEquals(reasonCode, (response.entity as ResponseError).reasonCode)
        assertEquals(currentETag, response.getHeaderString("ETag"))
    }

    private fun authority(requirementId: UUID? = null) =
        InformationRequestDelegatedAuthority().apply {
            id = authorityId
            informationRequestId = requestId
            assignedPartyId = this@InformationRequestDelegatedAuthorityResourceContractTest.assignedPartyId
            delegatePrincipalKind = delegatePrincipal.kind
            delegatePrincipalId = delegatePrincipal.id
            this.requirementId = requirementId
            grantorPrincipalKind = access.principal.kind
            grantorPrincipalId = access.principal.id
            effectiveAt = Timestamp.from(Instant.now())
            recordedAt = Timestamp.from(Instant.now())
            updatedAt = Timestamp.from(Instant.now())
        }
}
