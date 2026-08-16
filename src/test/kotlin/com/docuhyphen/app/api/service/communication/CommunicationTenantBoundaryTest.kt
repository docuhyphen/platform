package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.model.dto.CloneCommunicationRequest
import com.docuhyphen.app.api.model.dto.CreateCommunicationRequest
import com.docuhyphen.app.api.model.dto.PatchCommunicationPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchCommunicationStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateCommunicationRequest
import com.docuhyphen.app.api.model.entity.Communication
import com.docuhyphen.app.api.model.entity.CommunicationScope
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.repository.communication.CommunicationRepository
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.variable.TemplateVariableInterpolator
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class CommunicationTenantBoundaryTest
{
    private val principalId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(principalId)

    private fun authorizationContext(activeOrgId: UUID? = organizationId) =
        AuthorizationContext(activeOrgId = activeOrgId)

    private fun contextFactory(context: AuthorizationContext = authorizationContext()): AuthorizationContextFactory =
        mock<AuthorizationContextFactory>().also {
            whenever(it.currentPrincipal()).thenReturn(principal)
            whenever(it.currentContext()).thenReturn(context)
        }

    private fun roleService(
        appAdmin: Boolean,
        orgAdmin: Boolean,
    ): UserRoleService =
        mock<UserRoleService>().also {
            whenever(it.isAppAdmin(any())).thenReturn(appAdmin)
            whenever(it.isOrgAdminIn(any(), any())).thenReturn(orgAdmin)
        }

    private fun authorizationService(vararg allowedActions: Action): AuthorizationService =
        mock<AuthorizationService>().also {
            whenever(it.authorize(any(), any(), any(), any())).thenAnswer { invocation ->
                val action = invocation.getArgument<Action>(1)
                if (action in allowedActions)
                    Decision.Allow()
                else
                    Decision.Deny("test-deny", "Denied in test")
            }
        }

    private data class ServiceFixture(
        val service: CommunicationService,
        val repository: CommunicationRepository,
        val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
    )

    private fun fixture(
        appAdmin: Boolean = true,
        orgAdmin: Boolean = false,
        allowedActions: Array<out Action> = emptyArray(),
        context: AuthorizationContext = authorizationContext(),
    ): ServiceFixture
    {
        val repository = mock<CommunicationRepository>()
        val subscriptionGuard = mock<OrganizationFeatureSubscriptionGuard>()
        return ServiceFixture(
            service = CommunicationService(
                repository = repository,
                appUserRepository = mock<AppUserRepository>(),
                organizationRepository = mock<OrganizationRepository>(),
                interpolator = mock<TemplateVariableInterpolator>(),
                authorizationService = authorizationService(*allowedActions),
                authorizationContextFactory = contextFactory(context),
                userRoleService = roleService(appAdmin, orgAdmin),
                subscriptionGuard = subscriptionGuard,
            ),
            repository = repository,
            subscriptionGuard = subscriptionGuard,
        )
    }

    private fun communication(
        scope: CommunicationScope,
        createdBy: UUID = principalId,
    ) = Communication().apply {
        this.scope = scope
        this.createdByAppUserId = createdBy
        this.organizationId = if (scope == CommunicationScope.ORG) organizationId else null
        this.name = "Boundary Communication"
        this.subject = "Subject"
        this.body = "Body"
    }

    @Test
    fun `APP_ADMIN does not receive unpublished organization communications in list query`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = false)
        whenever(
            fixture.repository.findAllAccessibleForCaller(principalId, organizationId, false)
        ).thenReturn(emptyList())

        assertEquals(emptyList<Any>(), fixture.service.listTemplates(null, null, null))
        verify(fixture.repository).findAllAccessibleForCaller(principalId, organizationId, false)
    }

    @Test
    fun `APP_ADMIN cannot read another user's personal communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.getTemplate(communication.id)
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot read organization communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.ORG)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.getTemplate(communication.id)
        }
    }

    @Test
    fun `dual-role APP_ADMIN reads organization communication through organization permission`()
    {
        val fixture = fixture(
            appAdmin = true,
            orgAdmin = true,
            allowedActions = arrayOf(Action.COMMUNICATION_VIEW),
        )
        val communication = communication(CommunicationScope.ORG)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        val result = fixture.service.getTemplate(communication.id)

        assertEquals(communication.id, result.id)
    }

    @Test
    fun `APP_ADMIN cannot update another user's personal communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.PERSONAL, UUID.randomUUID())
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.updateTemplate(
                communication.id,
                UpdateCommunicationRequest(name = "Changed"),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot update organization communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.ORG)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.updateTemplate(
                communication.id,
                UpdateCommunicationRequest(name = "Changed"),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot publish organization communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.ORG)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.patchPublished(
                communication.id,
                PatchCommunicationPublishedRequest(isPublished = true),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot change organization communication status`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.ORG)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.patchStatus(
                communication.id,
                PatchCommunicationStatusRequest(isActive = false),
            )
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot delete organization communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.ORG)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.deleteTemplate(communication.id)
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot preview organization communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.ORG)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.preview(communication.id, emptyMap())
        }
    }

    @Test
    fun `APP_ADMIN without organization permission cannot clone organization communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.ORG)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)

        assertThrows<ForbiddenException> {
            fixture.service.cloneTemplate(communication.id, CloneCommunicationRequest())
        }
    }

    @Test
    fun `APP_ADMIN can update platform communication`()
    {
        val fixture = fixture()
        val communication = communication(CommunicationScope.PLATFORM)
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)
        whenever(fixture.repository.update(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.updateTemplate(
            communication.id,
            UpdateCommunicationRequest(name = "Changed"),
        )

        assertEquals("Changed", result.name)
    }

    @Test
    fun `APP_ADMIN without organization role cannot create organization communication`()
    {
        val fixture = fixture()

        assertThrows<ForbiddenException> {
            fixture.service.createTemplate(
                CreateCommunicationRequest(
                    name = "Organization Communication",
                    subject = "Subject",
                    body = "Body",
                    scope = CommunicationScope.ORG.name,
                )
            )
        }
    }

    @Test
    fun `platform communication never inherits active organization identifier`()
    {
        val fixture = fixture()
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createTemplate(
            CreateCommunicationRequest(
                name = "Platform Communication",
                subject = "Subject",
                body = "Body",
                scope = CommunicationScope.PLATFORM.name,
                isTemplate = true,
            )
        )

        assertEquals(CommunicationScope.PLATFORM.name, result.scope)
        assertNull(result.organizationId)
        val saved = argumentCaptor<Communication>()
        verify(fixture.repository).save(saved.capture())
        assertNull(saved.firstValue.organizationId)
    }

    @Test
    fun `dual-role default creation uses active organization scope`()
    {
        val fixture = fixture(appAdmin = true, orgAdmin = true)
        whenever(fixture.repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = fixture.service.createTemplate(
            CreateCommunicationRequest(
                name = "Organization Communication",
                subject = "Subject",
                body = "Body",
            )
        )

        assertEquals(CommunicationScope.ORG.name, result.scope)
        assertEquals(organizationId, result.organizationId)
        verify(fixture.subscriptionGuard).requireMutation(organizationId, PlanFeature.WORKFLOW_AUTOMATION)
    }

    @Test
    fun `organization update checks the persisted owner subscription`()
    {
        val persistedOwnerId = UUID.randomUUID()
        val fixture = fixture(
            appAdmin = true,
            orgAdmin = true,
            allowedActions = arrayOf(Action.COMMUNICATION_EDIT),
        )
        val communication = communication(CommunicationScope.ORG).apply {
            organizationId = persistedOwnerId
        }
        whenever(fixture.repository.findById(communication.id)).thenReturn(communication)
        whenever(fixture.repository.update(any())).thenAnswer { it.getArgument(0) }

        fixture.service.updateTemplate(communication.id, UpdateCommunicationRequest(name = "Changed"))

        verify(fixture.subscriptionGuard).requireMutation(
            persistedOwnerId,
            PlanFeature.WORKFLOW_AUTOMATION,
        )
    }
}
