package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestReadAuthorizationTest {
    private val requestId = UUID.randomUUID()
    private val signedIn =
        RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext(sessionRef = "reader-session"))
    private val viaLink = RequestAccessContext(
        PrincipalRef.participant(UUID.randomUUID()),
        AuthorizationContext.ANONYMOUS,
        accessSessionId = UUID.randomUUID(),
    )

    @Test
    fun `a signed-in caller reads a request only when the central authorizer allows viewing it`() {
        val authorization = mock<AuthorizationService>()
        whenever(
            authorization.authorize(
                signedIn.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                signedIn.authorization
            )
        )
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "Denied"))

        assertThrows<ForbiddenException> {
            InformationRequestReadAuthorization.requireView(
                authorization,
                requestId,
                signedIn
            )
        }
        assertThrows<ForbiddenException> {
            InformationRequestReadAuthorization.requireVisibleTarget(
                authorization,
                requestId,
                signedIn
            )
        }

        whenever(
            authorization.authorize(
                signedIn.principal,
                Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId),
                signedIn.authorization
            )
        )
            .thenReturn(Decision.Allow())
        assertDoesNotThrow { InformationRequestReadAuthorization.requireView(authorization, requestId, signedIn) }
    }

    @Test
    fun `an access-link caller's target is visible through its session, so the central authorizer is not asked`() {
        val authorization = mock<AuthorizationService>()

        assertDoesNotThrow {
            InformationRequestReadAuthorization.requireVisibleTarget(
                authorization,
                requestId,
                viaLink
            )
        }

        verifyNoInteractions(authorization)
    }

    @Test
    fun `related requests are judged by whether the caller may view each one, and each is asked about once`() {
        val gate = mock<InformationRequestMutationGate>()
        val visible = UUID.randomUUID()
        val hidden = UUID.randomUUID()
        whenever(gate.permitsRequest(signedIn, Action.INFORMATION_REQUEST_VIEW, visible)).thenReturn(true)
        whenever(gate.permitsRequest(signedIn, Action.INFORMATION_REQUEST_VIEW, hidden)).thenReturn(false)

        val visibility = InformationRequestRelatedRequestVisibility.of(gate, signedIn)

        assertEquals(listOf(true, false, true, false), listOf(visible, hidden, visible, hidden).map(visibility))
        verify(gate, times(1)).permitsRequest(signedIn, Action.INFORMATION_REQUEST_VIEW, visible)
        verify(gate, times(1)).permitsRequest(signedIn, Action.INFORMATION_REQUEST_VIEW, hidden)
    }
}
