package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.model.dto.InformationRequestAccessSessionDto
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestNoAuthAttempt
import com.docuhyphen.app.api.resource.informationrequest.noauth.operations.InformationRequestNoAuthAccessResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.VerifyInformationRequestContactProofRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthRateLimit
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The no-auth, respondent-facing adapter for proving contact ownership of a
 * [com.docuhyphen.app.api.model.entity.ShareLinkMode.VERIFICATION_BOOTSTRAP] access link and minting
 * the [RequestAccessSession] it authorizes. This resource never authenticates via the global access
 * token; it validates the bootstrap token header itself, the same way
 * [com.docuhyphen.app.api.resource.exchange.NoAuthExchangeResource] validates its own headers.
 */
class InformationRequestNoAuthAccessResourceContractTest
{
    private val contactProofService = mock<InformationRequestContactProofService>()
    private val rateLimit = mock<InformationRequestNoAuthRateLimit>()
    private val resource = InformationRequestNoAuthAccessResource(contactProofService, rateLimit)

    @Test
    fun `an address over its challenge budget is answered 429 with Retry-After before any code is sent`()
    {
        doThrow(rateLimited()).whenever(rateLimit).requireWithinLimit(InformationRequestNoAuthAttempt.CHALLENGE)

        val response = resource.issueContactProofChallenge("raw-token-value")

        assertEquals(429, response.status)
        assertEquals("60", response.headers.getFirst("Retry-After").toString())
        assertEquals(InformationRequestErrorCatalog.RATE_LIMITED, (response.entity as ResponseError).reasonCode)
        verify(contactProofService, never()).issueChallenge(any())
    }

    @Test
    fun `an address over its session budget is answered 429 before any code is checked`()
    {
        doThrow(rateLimited()).whenever(rateLimit).requireWithinLimit(InformationRequestNoAuthAttempt.SESSION)

        val response = resource.verifyContactProofChallenge("raw-token-value", VerifyInformationRequestContactProofRequest("123456"))

        assertEquals(429, response.status)
        verify(contactProofService, never()).verifyChallenge(any(), any(), anyOrNull())
    }

    private fun rateLimited() =
        InformationRequestRateLimitedException(InformationRequestErrorCatalog.RATE_LIMITED, 60, "Too many attempts")

    @Test
    fun `resource exposes challenge and session sub-resources under the no-auth prefix`()
    {
        val resourceClass = InformationRequestNoAuthAccessResourceOperations::class.java
        val methods = resourceClass.declaredMethods.associateBy { it.name }

        assertEquals(
            "no-auth/information-request-access-links",
            resourceClass.getAnnotation(Path::class.java).value,
        )
        assertEquals(
            "/challenges",
            methods.getValue("issueContactProofChallenge").getAnnotation(Path::class.java).value,
        )
        assertEquals(
            "/sessions",
            methods.getValue("verifyContactProofChallenge").getAnnotation(Path::class.java).value,
        )
        assertEquals(true, methods.getValue("issueContactProofChallenge").isAnnotationPresent(POST::class.java))
        assertEquals(true, methods.getValue("verifyContactProofChallenge").isAnnotationPresent(POST::class.java))
    }

    @Test
    fun `issuing a challenge without a token is rejected before the service is called`()
    {
        val response = resource.issueContactProofChallenge(null)

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(contactProofService, never()).issueChallenge(any())
    }

    @Test
    fun `issuing a challenge delegates to the contact proof service and returns no content`()
    {
        val response = resource.issueContactProofChallenge("raw-token-value")

        assertEquals(Response.Status.NO_CONTENT.statusCode, response.status)
        verify(contactProofService).issueChallenge("raw-token-value")
    }

    @Test
    fun `verifying a challenge without a token is rejected before the service is called`()
    {
        val response = resource.verifyContactProofChallenge(
            null,
            VerifyInformationRequestContactProofRequest(otp = "123456"),
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
        verify(contactProofService, never()).verifyChallenge(any(), any(), anyOrNull())
    }

    @Test
    fun `verifying a challenge returns the minted session projection`()
    {
        val session = RequestAccessSession().apply {
            id = UUID.randomUUID()
            shareLinkId = UUID.randomUUID()
            participantPrincipalKind = PrincipalKind.PARTICIPANT
            participantPrincipalId = UUID.randomUUID()
            verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP
            issuedAt = Timestamp.from(Instant.now())
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
        }
        whenever(contactProofService.verifyChallenge(eq("raw-token-value"), eq("123456"), anyOrNull())).thenReturn(com.docuhyphen.app.api.model.informationrequest.noauth.IssuedRequestAccessSession(session, "session-secret"))

        val response = resource.verifyContactProofChallenge(
            "raw-token-value",
            VerifyInformationRequestContactProofRequest(otp = "123456"),
        )

        assertEquals(Response.Status.CREATED.statusCode, response.status)
        val dto = response.entity as InformationRequestAccessSessionDto
        assertEquals("session-secret", dto.sessionToken)
        assertEquals("no-store", response.getHeaderString("Cache-Control"))
        assertEquals(session.id, dto.sessionId)
        assertEquals(RequestAccessSessionVerificationStrength.EMAIL_OTP, dto.verificationStrength)
    }

    @Test
    fun `domain failures map to stable response statuses`()
    {
        whenever(contactProofService.issueChallenge(any()))
            .thenThrow(InformationRequestLifecycleException(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, "Invalid link"))
        val invalid = resource.issueContactProofChallenge("bad-token")
        assertEquals(Response.Status.CONFLICT.statusCode, invalid.status)
        assertEquals("Invalid link", (invalid.entity as ResponseError).errorMessage)

        whenever(contactProofService.issueChallenge(any())).doThrow(ForbiddenException("Denied"))
        val denied = resource.issueContactProofChallenge("bad-token")
        assertEquals(Response.Status.FORBIDDEN.statusCode, denied.status)
    }
}
