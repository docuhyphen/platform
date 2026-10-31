package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.*
import java.util.*

class OrganizationGroupDeletionTest {
    private val organization = Organization().apply {
        name = "Process organization"
        registrationNumber = "process-registration"
    }
    private val group = PrincipalGroup().apply {
        name = "Reviewers"
        ownerOrganizationId = organization.id
    }
    private val principalGroupService = mock<PrincipalGroupService>()
    private val shareRepository = mock<ShareRepository>()

    private val service = OrganizationGroupService(
        organizationRepository = mock<OrganizationRepository>().also {
            whenever(it.findById(organization.id)).thenReturn(organization)
        },
        authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = AppUser().apply { id = UUID.randomUUID() } }
        },
        appUserService = mock(),
        authAuditService = mock(),
        emailService = mock(),
        emailTemplateService = mock(),
        configurationService = mock(),
        principalGroupService = principalGroupService,
        principalGroupRepository = mock<PrincipalGroupRepository>().also {
            whenever(it.findById(group.id)).thenReturn(group)
        },
        principalGroupMemberRepository = mock(),
        shareRepository = shareRepository,
        organizationMembershipService = mock(),
        authorizationService = mock<AuthorizationService>().also {
            whenever(it.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        },
        authorizationContextFactory = mock<AuthorizationContextFactory>().also {
            whenever(it.currentPrincipal()).thenReturn(PrincipalRef.user(UUID.randomUUID()))
            whenever(it.currentContext()).thenReturn(AuthorizationContext())
        },
        subscriptionGuard = mock(),
    )

    @ParameterizedTest
    @EnumSource(value = ResourceType::class, names = ["EXCHANGE", "INFORMATION_REQUEST"])
    fun `a group that still holds an active Share on an Exchange or a request is deactivated rather than deleted`(
        resourceType: ResourceType,
    ) {
        whenever(shareRepository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, group.id)).thenReturn(
            listOf(
                Share().apply {
                    this.resourceType = resourceType
                    resourceId = UUID.randomUUID()
                    principalKind = PrincipalKind.PRINCIPAL_GROUP
                    principalId = group.id
                },
            ),
        )

        service.deleteOrganizationGroup(organization.id.toString(), group.id.toString(), AdminApprovalContext())

        verify(principalGroupService).deactivateGroup(group.id)
        verify(principalGroupService, never()).deleteGroup(any())
    }

    @Test
    fun `a group that holds no active Share is deleted`() {
        whenever(
            shareRepository.findActiveForPrincipal(
                PrincipalKind.PRINCIPAL_GROUP,
                group.id
            )
        ).thenReturn(emptyList())

        service.deleteOrganizationGroup(organization.id.toString(), group.id.toString(), AdminApprovalContext())

        verify(principalGroupService).deleteGroup(group.id)
        verify(principalGroupService, never()).deactivateGroup(any())
    }
}
