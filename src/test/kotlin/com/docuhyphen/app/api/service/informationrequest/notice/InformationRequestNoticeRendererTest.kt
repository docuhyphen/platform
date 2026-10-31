package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.model.communication.CommunicationSource
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.service.communication.CommunicationResolver
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.variable.InterpolationResult
import com.docuhyphen.app.api.service.variable.TemplateVariableInterpolator
import com.docuhyphen.app.api.service.variable.VariableResolutionContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class InformationRequestNoticeRendererTest {
    private val appUsers = mock<AppUserService>()
    private val interpolator = mock<TemplateVariableInterpolator>()
    private val renderer = InformationRequestNoticeRenderer(
        communications = mock<CommunicationResolver>(),
        interpolator = interpolator,
        appUserService = appUsers,
        organizations = mock<OrganizationGroupService>(),
        clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC),
    )

    init {
        whenever(interpolator.interpolate(any(), any())).thenAnswer { InterpolationResult(it.getArgument(0)) }
    }

    @Test
    fun `a notice for a request a user created is sent in that user's name`() {
        val creator = AppUser().apply { id = UUID.randomUUID() }
        whenever(appUsers.getById(creator.id)).thenReturn(creator)

        renderer.render(organizationRequest(PrincipalKind.USER, creator.id), intent(), emptyMap())

        assertSame(creator, sender())
    }

    @Test
    fun `a notice for a personal request an application created is sent in the owner's name`() {
        val owner = AppUser().apply { id = UUID.randomUUID() }
        val applicationId = UUID.randomUUID()
        whenever(appUsers.getById(owner.id)).thenReturn(owner)
        val request = InformationRequest().apply {
            ownerType = InformationRequestOwnerType.USER
            ownerUserId = owner.id
            createdByPrincipalKind = PrincipalKind.APPLICATION
            createdByPrincipalId = applicationId
        }

        renderer.render(request, intent(), emptyMap())

        assertSame(owner, sender())
        verify(appUsers, never()).getById(applicationId)
    }

    @Test
    fun `a principal that is not a user is never looked up as an account`() {
        val serviceAccountId = UUID.randomUUID()

        renderer.render(organizationRequest(PrincipalKind.SERVICE_ACCOUNT, serviceAccountId), intent(), emptyMap())

        verify(appUsers, never()).getById(any())
        assertNotEquals(serviceAccountId, sender().id)
    }

    @Test
    fun `a request no account created renders the sender's email as empty instead of failing`() {
        val communicationId = UUID.randomUUID()
        val communications = mock<CommunicationResolver>()
        whenever(communications.sourceOf(communicationId)).thenReturn(
            CommunicationSource(
                id = communicationId,
                scope = CommunicationScope.ORG,
                organizationId = UUID.randomUUID(),
                createdByAppUserId = null,
                subject = "Reminder",
                body = "Questions? Write to {{USER_EMAIL}}.",
            ),
        )
        val withRealTokens = InformationRequestNoticeRenderer(
            communications = communications,
            interpolator = TemplateVariableInterpolator(mock(), mock()),
            appUserService = appUsers,
            organizations = mock<OrganizationGroupService>(),
            clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC),
        )

        val rendered = withRealTokens.render(
            organizationRequest(PrincipalKind.SERVICE_ACCOUNT, UUID.randomUUID()),
            intent().apply { sourceCommunicationId = communicationId },
            emptyMap(),
        )

        assertEquals("Questions? Write to .", rendered.body)
    }

    private fun sender(): AppUser {
        val context = argumentCaptor<VariableResolutionContext>()
        verify(interpolator, atLeastOnce()).interpolate(any(), context.capture())
        return context.firstValue.user
    }

    private fun organizationRequest(kind: PrincipalKind, id: UUID) = InformationRequest().apply {
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        createdByPrincipalKind = kind
        createdByPrincipalId = id
    }

    private fun intent() = InformationRequestNoticeIntent().apply {
        informationRequestId = UUID.randomUUID()
        partyId = UUID.randomUUID()
    }
}
