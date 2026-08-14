package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.model.entity.UserSession
import com.docuhyphen.app.api.service.AppUserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class TokenIssuanceServiceTest
{
    private val authenticationService = mock<AuthenticationService>()
    private val csrfProtectionService = mock<CsrfProtectionService>()
    private val userSessionService = mock<UserSessionService>()
    private val authSessionPolicyService = mock<AuthSessionPolicyService>()
    private val appUserService = mock<AppUserService>()
    private val service = TokenIssuanceService(
        authenticationService,
        csrfProtectionService,
        userSessionService,
        authSessionPolicyService,
        appUserService,
    )

    @Test
    fun `token issuance reloads detached OAuth user with person`()
    {
        val userId = UUID.randomUUID()
        val detachedUser = AppUser().apply {
            id = userId
            email = "user@example.com"
        }
        val managedUser = AppUser().apply {
            id = userId
            email = "user@example.com"
            person = Person().apply {
                firstName = "Ada"
                lastName = "Lovelace"
            }
        }
        val policy = AuthSessionPolicy(
            accessTokenExpiryMinutes = 15,
            refreshTokenExpiryMinutes = 60,
            maxSessionDurationHours = 8,
            idleTimeoutMinutes = 30,
        )
        val session = UserSession().apply { sessionId = UUID.randomUUID() }

        whenever(appUserService.getByIdWithPerson(userId)).thenReturn(managedUser)
        whenever(authSessionPolicyService.resolveForAppUser(managedUser)).thenReturn(policy)
        whenever(userSessionService.createSession(managedUser, 8, null, null)).thenReturn(session)
        whenever(authenticationService.generateAccessToken(managedUser, session.sessionId, 15)).thenReturn("access")
        whenever(authenticationService.generateIdToken(managedUser, session.sessionId, 15)).thenReturn("id")
        whenever(authenticationService.generateRefreshToken(eq(managedUser), any(), eq(session.sessionId), eq(60)))
            .thenReturn(Triple("refresh", "jti", "family"))

        val result = service.issueTokenTriple(detachedUser)

        assertEquals("id", result.idToken)
        verify(authenticationService).generateIdToken(managedUser, session.sessionId, 15)
        verify(authenticationService).saveRefreshToken(
            eq(managedUser),
            eq("refresh"),
            eq("jti"),
            eq("family"),
            eq(session.sessionId),
            eq(60),
        )
    }
}
