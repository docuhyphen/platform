package com.docuhyphen.app.api.resource.informationrequest.party

import com.docuhyphen.app.api.model.dto.InformationRequestTrustedRecipientCandidateDto
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestTrustedRecipientCandidate
import com.docuhyphen.app.api.resource.informationrequest.party.operations.InformationRequestTrustedRecipientCandidateResourceOperations
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestTrustedRecipientCandidateQueryService
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestTrustedRecipientCandidateResourceContractTest {
    private val candidateQueryService = mock<InformationRequestTrustedRecipientCandidateQueryService>()
    private val accessContextFactory = mock<InformationRequestAccessContextFactory>()
    private val resource =
        InformationRequestTrustedRecipientCandidateResource(candidateQueryService, accessContextFactory)
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val requestId = UUID.randomUUID()

    init {
        whenever(accessContextFactory.currentAuthenticated()).thenReturn(access)
    }

    @Test
    fun `trusted recipient candidates are a read-only sub-resource of the request`() {
        val operations = InformationRequestTrustedRecipientCandidateResourceOperations::class.java
        assertEquals(
            "/information-requests/{id}/trusted-recipient-candidates",
            operations.getAnnotation(Path::class.java).value
        )
        val list = operations.declaredMethods.single { it.name == "list" }
        assertTrue(list.isAnnotationPresent(GET::class.java))
        assertEquals(1, operations.declaredMethods.size)
    }

    @Test
    fun `candidates are listed through the authenticated access context`() {
        val candidate = InformationRequestTrustedRecipientCandidate(
            exchangeRecipientId = UUID.randomUUID(),
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
            principal = PrincipalRef.user(UUID.randomUUID()),
            label = "Verified Member",
            targetOrganizationId = UUID.randomUUID(),
            targetOrganizationName = "Partner organization",
            trustSuspended = false,
        )
        whenever(candidateQueryService.candidates(requestId, access)).thenReturn(listOf(candidate))

        val response = resource.list(requestId.toString())

        assertEquals(Response.Status.OK.statusCode, response.status)
        val body = response.entity as Array<*>
        val dto = body.single() as InformationRequestTrustedRecipientCandidateDto
        assertEquals(candidate.exchangeRecipientId, dto.exchangeRecipientId)
        assertEquals(candidate.principal.id, dto.principalId)
        assertEquals("Partner organization", dto.targetOrganizationName)
    }

    @Test
    fun `a caller who may not manage parties and an invalid id are refused`() {
        whenever(candidateQueryService.candidates(requestId, access)).thenThrow(ForbiddenException("Denied"))

        assertEquals(Response.Status.FORBIDDEN.statusCode, resource.list(requestId.toString()).status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, resource.list("not-a-uuid").status)
    }
}
