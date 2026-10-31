package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.identity.PrincipalDisplay
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditAttribution
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestAuditActorLabelResolverTest {
    private val displays = mock<PrincipalDisplayService>()
    private val resolver = InformationRequestAuditActorLabelResolver(displays)

    @Test
    fun `an App User is labelled with the name and email the identity domain resolves`() {
        val user = PrincipalRef.user(UUID.randomUUID())
        whenever(displays.userLabel(user.id)).thenReturn("Process Owner <owner@process.test>")

        assertEquals("Process Owner <owner@process.test>", resolver.labelOf(user))
    }

    @Test
    fun `an App User the identity domain cannot resolve is labelled by a stable identifier`() {
        val user = PrincipalRef.user(UUID.randomUUID())

        assertEquals("user:${user.id}", resolver.labelOf(user))
    }

    @Test
    fun `every other principal is labelled by a stable non-secret identifier and never by contact details`() {
        val id = UUID.randomUUID()
        whenever(displays.display(any())).thenReturn(
            PrincipalDisplay(
                "respondent@process.test",
                "respondent@process.test"
            )
        )

        val labels = listOf(
            PrincipalRef.participant(id),
            PrincipalRef.service(id),
            PrincipalRef.application(id),
            PrincipalRef.group(id),
            PrincipalRef.organization(id),
            PrincipalRef.publicLink(id),
        ).map(resolver::labelOf)

        assertEquals(
            listOf(
                "participant:$id",
                "service-account:$id",
                "application:$id",
                "principal-group:$id",
                "organization:$id",
                "public-link:$id",
            ),
            labels,
        )
        assertFalse(labels.any { it.contains("@") })
        verify(displays, never()).display(any())
        verify(displays, never()).userLabel(any())
    }

    @Test
    fun `a respondent acting through an access link is attributed to the public link, its participant identifier, and its session`() {
        val respondent = PrincipalRef.participant(UUID.randomUUID())
        val accessSession = UUID.randomUUID().toString()

        val attribution = resolver.attribute(
            InformationRequestAuditActor.of(
                RequestAccessContext(
                    respondent,
                    AuthorizationContext(sessionRef = accessSession)
                )
            ),
        )

        assertEquals(
            InformationRequestAuditAttribution(
                respondent.id,
                AuditActorKind.PUBLIC_LINK,
                "participant:${respondent.id}",
                accessSession
            ),
            attribution,
        )
    }

    @Test
    fun `a respondent proving a link before any session exists is still attributed to the public link`() {
        val respondent = PrincipalRef.participant(UUID.randomUUID())

        val attribution = resolver.attribute(InformationRequestAuditActor.beforeSession(respondent))

        assertEquals(
            InformationRequestAuditAttribution(
                respondent.id,
                AuditActorKind.PUBLIC_LINK,
                "participant:${respondent.id}",
                null
            ),
            attribution,
        )
    }

    @Test
    fun `an authenticated owner keeps the human kind, the resolved label, and the user session`() {
        val owner = PrincipalRef.user(UUID.randomUUID())
        val userSession = UUID.randomUUID().toString()
        whenever(displays.userLabel(owner.id)).thenReturn("owner@process.test")

        val attribution = resolver.attribute(
            InformationRequestAuditActor.of(
                RequestAccessContext(
                    owner,
                    AuthorizationContext(sessionRef = userSession)
                )
            ),
        )

        assertEquals(
            InformationRequestAuditAttribution(
                owner.id,
                AuditActorKind.HUMAN,
                "owner@process.test",
                userSession
            ), attribution
        )
    }

    @Test
    fun `an account holder proving a request access session is attributed to that session`() {
        val account = PrincipalRef.user(UUID.randomUUID())
        val session = RequestAccessSession().apply {
            credentialHash = "stored-credential-digest"
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
        }

        val attribution = resolver.attribute(InformationRequestAuditActor.of(account, session))

        assertEquals(
            InformationRequestAuditAttribution(
                account.id,
                AuditActorKind.HUMAN,
                "user:${account.id}",
                session.id.toString()
            ), attribution
        )
    }

    @Test
    fun `a system actor is attributed to its service principal with an explicit label and no session`() {
        val service = PrincipalRef.service(UUID(0, 0))

        val attribution = resolver.attribute(InformationRequestAuditActor.system(service))

        assertEquals(
            InformationRequestAuditAttribution(
                UUID(0, 0),
                AuditActorKind.SYSTEM,
                "service-account:00000000-0000-0000-0000-000000000000",
                null,
            ),
            attribution,
        )
    }

    @Test
    fun `an application credential is attributed as an application`() {
        val application = PrincipalRef.application(UUID.randomUUID())

        val attribution = resolver.attribute(
            InformationRequestAuditActor.of(
                RequestAccessContext(
                    application,
                    AuthorizationContext()
                )
            )
        )

        assertEquals(
            InformationRequestAuditAttribution(
                application.id,
                AuditActorKind.APP,
                "application:${application.id}",
                null
            ),
            attribution,
        )
    }
}
