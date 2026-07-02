package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.repository.AppRoleAssignmentRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.quality.Strictness
import java.util.UUID

/**
 * No REST endpoint may self-bootstrap an APP_ADMIN assignment.
 *
 * Prior to Phase 6, [AppRoleAssignmentService.requireAppAdmin] promoted an org admin to
 * APP_ADMIN when the admin table was empty. This auto-bootstrap path violates the rule that
 * human platform roles must be bootstrapped out-of-band (startup config) rather than via a
 * REST endpoint call.
 *
 * After Phase 6:
 *  - requireAppAdmin throws SecurityException if the caller is not an existing APP_ADMIN.
 *  - No grantAppRole call is made from requireAppAdmin regardless of org-admin status.
 *  - The startup config bootstrap (bootstrapFirstAppAdmin) remains the only in-process path.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppAdminGuardTest
{
    @Mock private lateinit var appRoleAssignmentRepository: AppRoleAssignmentRepository
    @Mock private lateinit var appUserService: AppUserService
    @Mock private lateinit var configurationService: ConfigurationService
    @Mock private lateinit var authAuditService: AuthAuditService
    @Mock private lateinit var userRoleService: UserRoleService

    private lateinit var service: AppRoleAssignmentService

    private val actorId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setup()
    {
        service = AppRoleAssignmentService(
            appRoleAssignmentRepository = appRoleAssignmentRepository,
            appUserService = appUserService,
            configurationService = configurationService,
            authAuditService = authAuditService,
            userRoleService = userRoleService,
        )
    }

    @Test
    fun `requireAppAdmin throws SecurityException when caller is not an app admin`()
    {
        `when`(userRoleService.isAppAdmin(actorId)).thenReturn(false)

        assertThrows(SecurityException::class.java) {
            service.requireAppAdmin(actorId)
        }
    }

    @Test
    fun `requireAppAdmin does not persist any role grant when no admins exist and caller is org admin`()
    {
        `when`(userRoleService.isAppAdmin(actorId)).thenReturn(false)
        `when`(userRoleService.isOrgAdmin(actorId)).thenReturn(true)
        `when`(appRoleAssignmentRepository.findActiveAppAdmins()).thenReturn(emptyList())

        assertThrows(SecurityException::class.java) {
            service.requireAppAdmin(actorId)
        }

        // No role assignment must be persisted from requireAppAdmin
        verify(appRoleAssignmentRepository, never()).save(any())
    }

    @Test
    fun `requireAppAdmin does not throw when caller is an existing app admin`()
    {
        `when`(userRoleService.isAppAdmin(actorId)).thenReturn(true)

        service.requireAppAdmin(actorId)
    }

    @Test
    fun `requireAppAdmin with a different non-admin caller also throws`()
    {
        val otherId = UUID.randomUUID()
        `when`(userRoleService.isAppAdmin(otherId)).thenReturn(false)

        assertThrows(SecurityException::class.java) {
            service.requireAppAdmin(otherId)
        }
    }
}
