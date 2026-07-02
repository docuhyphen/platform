package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrossOrgAuditScopeTest
{
    @Mock private lateinit var authAuditService: AuthAuditService
    @Mock private lateinit var userRoleService: UserRoleService
    @Mock private lateinit var membershipRepository: OrganizationMembershipRepository

    private lateinit var authTokenContext: AuthTokenContext
    private lateinit var resource: AuthAuditResource

    private val actorId: UUID = UUID.randomUUID()
    private val orgAId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setup()
    {
        val actor = AppUser().apply { id = actorId }
        authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = actor }
        }
        resource = AuthAuditResource(
            authTokenContext = authTokenContext,
            authAuditService = authAuditService,
            userRoleService = userRoleService,
            membershipRepository = membershipRepository,
        )
    }

    @Test
    fun `org admin receives 200 and events are scoped to own org`()
    {
        val membership = OrganizationMembership().apply { organizationId = orgAId }
        `when`(userRoleService.isAppAdmin(actorId)).thenReturn(false)
        `when`(userRoleService.isOrgAdmin(actorId)).thenReturn(true)
        `when`(membershipRepository.findPrimaryForUser(actorId)).thenReturn(membership)
        `when`(authAuditService.findRecent(anyInt(), isNull(), isNull(), anyBoolean(), any()))
            .thenReturn(emptyList())

        val response = resource.list(25, null, null, false)

        assertEquals(Response.Status.OK.statusCode, response.status)

        // Verify events were queried with Org A's scope, never with null (all-orgs / APP_ADMIN scope).
        val orgIdCaptor: ArgumentCaptor<UUID> = ArgumentCaptor.forClass(UUID::class.java)
        verify(authAuditService).findRecent(anyInt(), isNull(), isNull(), anyBoolean(), orgIdCaptor.capture())
        assertEquals(orgAId, orgIdCaptor.value)
    }

    @Test
    fun `org admin does not receive app admin scope`()
    {
        val membership = OrganizationMembership().apply { organizationId = orgAId }
        `when`(userRoleService.isAppAdmin(actorId)).thenReturn(false)
        `when`(userRoleService.isOrgAdmin(actorId)).thenReturn(true)
        `when`(membershipRepository.findPrimaryForUser(actorId)).thenReturn(membership)
        `when`(authAuditService.findRecent(anyInt(), isNull(), isNull(), anyBoolean(), any()))
            .thenReturn(emptyList())

        resource.list(25, null, null, false)

        // APP_ADMIN scope would pass organizationId=null; verify that never happened.
        verify(authAuditService, never())
            .findRecent(anyInt(), isNull(), isNull(), anyBoolean(), isNull())
    }

    @Test
    fun `caller with no admin role receives 403`()
    {
        `when`(userRoleService.isAppAdmin(actorId)).thenReturn(false)
        `when`(userRoleService.isOrgAdmin(actorId)).thenReturn(false)

        val response = resource.list(25, null, null, false)

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
    }
}
