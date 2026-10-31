package com.docuhyphen.app.api.resource.informationrequest.noauth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.InformationRequestAccessSessionDto
import com.docuhyphen.app.api.model.dto.InformationRequestAccessSessionStepUpDto
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAccessSessionStepUp
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestNoAuthAttempt
import com.docuhyphen.app.api.resource.informationrequest.noauth.operations.InformationRequestNoAuthAccessResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.VerifyInformationRequestContactProofRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestAccessSessionStepUpService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestContactProofService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthRateLimit
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import io.quarkus.security.ForbiddenException
import jakarta.ws.rs.HeaderParam
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

class InformationRequestNoAuthAccessResourceContractTest
{
    private val contactProofService = mock<InformationRequestContactProofService>()
    private val rateLimit = mock<InformationRequestNoAuthRateLimit>()
    private val stepUpService = mock<InformationRequestAccessSessionStepUpService>()
    private val requestContext = AuthTokenContext().apply { clientIp = "203.0.113.9" }
    private val resource =
        InformationRequestNoAuthAccessResource(contactProofService, rateLimit, stepUpService, requestContext)

    @Test
    fun `every challenge and session budget is charged to the caller's client address`() {
        resource.issueContactProofChallenge("raw-token-value")
        resource.verifyContactProofChallenge("raw-token-value", VerifyInformationRequestContactProofRequest("123456"))
        resource.issueStepUpChallenge("raw-token-value", "session-token")
        resource.verifyStepUp("raw-token-value", "session-token", VerifyInformationRequestContactProofRequest("123456"))

        verify(rateLimit, times(2)).requireWithinLimit(
            InformationRequestNoAuthAttempt.CHALLENGE,
            "203.0.113.9"
        )
        verify(rateLimit, times(2)).requireWithinLimit(
            InformationRequestNoAuthAttempt.SESSION,
            "203.0.113.9"
        )
    }

    @Test
    fun `an address over its challenge budget is answered 429 with Retry-After before any code is sent`()
    {
        doThrow(rateLimited()).whenever(rateLimit)
            .requireWithinLimit(eq(InformationRequestNoAuthAttempt.CHALLENGE), anyOrNull())

        val response = resource.issueContactProofChallenge("raw-token-value")

        assertEquals(429, response.status)
        assertEquals("60", response.headers.getFirst("Retry-After").toString())
        assertEquals(InformationRequestErrorCatalog.RATE_LIMITED, (response.entity as ResponseError).reasonCode)
        verify(contactProofService, never()).issueChallenge(any())
    }

    @Test
    fun `an address over its session budget is answered 429 before any code is checked`()
    {
        doThrow(rateLimited()).whenever(rateLimit)
            .requireWithinLimit(eq(InformationRequestNoAuthAttempt.SESSION), anyOrNull())

        val response = resource.verifyContactProofChallenge(
            "raw-token-value",
            VerifyInformationRequestContactProofRequest("123456")
        )

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
        whenever(
            contactProofService.verifyChallenge(
                eq("raw-token-value"),
                eq("123456"),
                anyOrNull()
            )
        ).thenReturn(
            com.docuhyphen.app.api.model.informationrequest.noauth.IssuedRequestAccessSession(
                session,
                "session-secret"
            )
        )

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
    fun `step-up sub-resources sit under the access link's current session and read both credentials`() {
        val methods =
            InformationRequestNoAuthAccessResourceOperations::class.java.declaredMethods.associateBy { it.name }

        assertEquals(
            "/sessions/current/step-up-challenges",
            methods.getValue("issueStepUpChallenge").getAnnotation(Path::class.java).value
        )
        assertEquals(
            "/sessions/current/step-ups",
            methods.getValue("verifyStepUp").getAnnotation(Path::class.java).value
        )
        listOf("issueStepUpChallenge", "verifyStepUp").forEach { name ->
            val method = methods.getValue(name)
            assertTrue(method.isAnnotationPresent(POST::class.java))
            assertEquals(
                listOf("X-Request-Access-Token", "X-Request-Session-Token"),
                method.parameterAnnotations.mapNotNull { annotations ->
                    annotations.filterIsInstance<HeaderParam>().firstOrNull()?.value
                },
            )
        }
    }

    @Test
    fun `sending a step-up code passes both credentials to the service and answers no content`() {
        val response = resource.issueStepUpChallenge("raw-token-value", "session-credential")

        assertEquals(Response.Status.NO_CONTENT.statusCode, response.status)
        verify(stepUpService).issueChallenge("raw-token-value", "session-credential")
    }

    @Test
    fun `a verified step-up code answers how long protected Requirements stay open without being cached`() {
        val sessionId = UUID.randomUUID()
        val verifiedUntil = Timestamp.from(Instant.now().plusSeconds(900))
        whenever(stepUpService.verify("raw-token-value", "session-credential", "123456"))
            .thenReturn(InformationRequestAccessSessionStepUp(sessionId, verifiedUntil))

        val response = resource.verifyStepUp(
            "raw-token-value",
            "session-credential",
            VerifyInformationRequestContactProofRequest("123456")
        )

        assertEquals(Response.Status.CREATED.statusCode, response.status)
        assertEquals("no-store", response.getHeaderString("Cache-Control"))
        val dto = response.entity as InformationRequestAccessSessionStepUpDto
        assertEquals(sessionId, dto.sessionId)
        assertEquals(verifiedUntil, dto.verifiedUntil)
    }

    @Test
    fun `step-up attempts spend the same address budgets as contact proof`() {
        doThrow(rateLimited()).whenever(rateLimit)
            .requireWithinLimit(eq(InformationRequestNoAuthAttempt.CHALLENGE), anyOrNull())
        doThrow(rateLimited()).whenever(rateLimit)
            .requireWithinLimit(eq(InformationRequestNoAuthAttempt.SESSION), anyOrNull())

        val challenge = resource.issueStepUpChallenge("raw-token-value", "session-credential")
        val verification = resource.verifyStepUp(
            "raw-token-value",
            "session-credential",
            VerifyInformationRequestContactProofRequest("123456")
        )

        assertEquals(429, challenge.status)
        assertEquals(429, verification.status)
        verify(stepUpService, never()).issueChallenge(any(), anyOrNull())
        verify(stepUpService, never()).verify(any(), anyOrNull(), any())
    }

    @Test
    fun `a step-up without an access link token is refused before the service is called`() {
        val challenge = resource.issueStepUpChallenge(null, "session-credential")
        val verification =
            resource.verifyStepUp(" ", "session-credential", VerifyInformationRequestContactProofRequest("123456"))

        assertEquals(Response.Status.BAD_REQUEST.statusCode, challenge.status)
        assertEquals(Response.Status.BAD_REQUEST.statusCode, verification.status)
        verify(stepUpService, never()).issueChallenge(any(), anyOrNull())
        verify(stepUpService, never()).verify(any(), anyOrNull(), any())
    }

    @Test
    fun `step-up refusals map to the contact proof statuses`() {
        whenever(stepUpService.verify(any(), anyOrNull(), any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.CONTACT_PROOF_INVALID,
                    "Invalid verification code"
                )
            )
        doThrow(ForbiddenException("Session is not bound to this ShareLink")).whenever(stepUpService)
            .issueChallenge(any(), anyOrNull())

        val invalid = resource.verifyStepUp(
            "raw-token-value",
            "session-credential",
            VerifyInformationRequestContactProofRequest("000000")
        )
        val unbound = resource.issueStepUpChallenge("raw-token-value", "session-credential")

        assertEquals(Response.Status.CONFLICT.statusCode, invalid.status)
        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_INVALID, (invalid.entity as ResponseError).reasonCode)
        assertEquals(Response.Status.FORBIDDEN.statusCode, unbound.status)
    }

    @Test
    fun `domain failures map to stable response statuses`()
    {
        whenever(contactProofService.issueChallenge(any()))
            .thenThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.ACCESS_LINK_INVALID,
                    "Invalid link"
                )
            )
        val invalid = resource.issueContactProofChallenge("bad-token")
        assertEquals(Response.Status.CONFLICT.statusCode, invalid.status)
        assertEquals("Invalid link", (invalid.entity as ResponseError).errorMessage)

        whenever(contactProofService.issueChallenge(any())).doThrow(ForbiddenException("Denied"))
        val denied = resource.issueContactProofChallenge("bad-token")
        assertEquals(Response.Status.FORBIDDEN.statusCode, denied.status)

        whenever(contactProofService.issueChallenge(any()))
            .doThrow(
                InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.NOT_FOUND,
                    "Information Request not found"
                )
            )
        val missing = resource.issueContactProofChallenge("bad-token")
        assertEquals(Response.Status.NOT_FOUND.statusCode, missing.status)
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, (missing.entity as ResponseError).reasonCode)
    }
}
