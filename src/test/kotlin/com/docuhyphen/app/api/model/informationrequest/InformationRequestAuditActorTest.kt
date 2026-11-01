package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestAuditActorTest {
    @Test
    fun `an access context names its principal and the session the request arrived on`() {
        val owner = PrincipalRef.user(UUID.randomUUID())
        val userSession = UUID.randomUUID().toString()

        val actor =
            InformationRequestAuditActor.of(RequestAccessContext(owner, AuthorizationContext(sessionRef = userSession)))

        assertEquals(owner, actor.principal)
        assertEquals(userSession, actor.sessionRef)
    }

    @Test
    fun `a blank or missing session reference is recorded as no session`() {
        val respondent = PrincipalRef.participant(UUID.randomUUID())

        assertNull(
            InformationRequestAuditActor.of(
                RequestAccessContext(
                    respondent,
                    AuthorizationContext(sessionRef = "  ")
                )
            ).sessionRef
        )
        assertNull(InformationRequestAuditActor.of(RequestAccessContext(respondent, AuthorizationContext())).sessionRef)
    }

    @Test
    fun `a principal acting through a request access session names that session`() {
        val respondent = PrincipalRef.participant(UUID.randomUUID())
        val session = RequestAccessSession().apply {
            credentialHash = "stored-credential-digest"
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
        }

        val actor = InformationRequestAuditActor.of(respondent, session)

        assertEquals(respondent, actor.principal)
        assertEquals(session.id.toString(), actor.sessionRef)
    }

    @Test
    fun `system actors and link holders without a session carry no session`() {
        val service = PrincipalRef.service(UUID(0, 0))
        val respondent = PrincipalRef.participant(UUID.randomUUID())

        assertEquals(service, InformationRequestAuditActor.system(service).principal)
        assertNull(InformationRequestAuditActor.system(service).sessionRef)
        assertEquals(respondent, InformationRequestAuditActor.beforeSession(respondent).principal)
        assertNull(InformationRequestAuditActor.beforeSession(respondent).sessionRef)
    }
}
