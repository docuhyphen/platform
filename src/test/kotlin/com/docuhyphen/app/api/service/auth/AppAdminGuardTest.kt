package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.service.application.AppRoleAssignmentService
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.model.entity.AppRoleAssignment
import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.exception.LastAppAdminException
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.quality.Strictness
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

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

        val auditArguments = auditArguments("emit")
        assertEquals("APP_ADMIN_OPERATION_DENIED", auditArguments[0])
        assertEquals("DENIED", auditArguments[1])
        assertEquals(actorId, auditArguments[3])
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

    @Test
    fun `grantAppRole rejects an inactive target user`()
    {
        val targetId = UUID.randomUUID()
        val target = appUser(targetId).apply { isActive = false }
        `when`(appUserService.getById(targetId)).thenReturn(target)

        assertThrows(IllegalArgumentException::class.java) {
            service.grantAppRole(targetId, AppRoleName.APP_ADMIN, actorId)
        }

        verify(appRoleAssignmentRepository, never()).save(any())
    }

    @Test
    fun `grantAppRole rejects a deprovisioned target user`()
    {
        val targetId = UUID.randomUUID()
        val target = appUser(targetId).apply {
            deprovisionedAt = Timestamp.from(Instant.now())
        }
        `when`(appUserService.getById(targetId)).thenReturn(target)

        assertThrows(IllegalArgumentException::class.java) {
            service.grantAppRole(targetId, AppRoleName.APP_ADMIN, actorId)
        }

        verify(appRoleAssignmentRepository, never()).save(any())
    }

    @Test
    fun `grantAppRole explicitly regrants an expired assignment`()
    {
        val targetId = UUID.randomUUID()
        val expiredAt = Timestamp.from(Instant.now().minusSeconds(60))
        val assignment = AppRoleAssignment().apply {
            appUserId = targetId
            roleName = AppRoleName.APP_ADMIN
            expiresAt = expiredAt
            isActive = true
        }
        `when`(appUserService.getById(targetId)).thenReturn(appUser(targetId))
        `when`(
            appRoleAssignmentRepository.findAppRoleForUser(targetId, AppRoleName.APP_ADMIN),
        ).thenReturn(assignment)

        val result = service.grantAppRole(targetId, AppRoleName.APP_ADMIN, actorId)

        assertEquals(assignment, result)
        assertEquals(actorId, assignment.grantedByAppUserId)
        assertNull(assignment.expiresAt)
        verify(appRoleAssignmentRepository).update(assignment)
        val auditArguments = auditArguments("emitRequired")
        assertEquals("APP_ADMIN_GRANT", auditArguments[0])
        assertEquals("SUCCESS", auditArguments[1])
        assertEquals(actorId, auditArguments[3])
        assertEquals("APP_ADMIN", auditArguments[4])
        assertEquals("APP_USER", auditArguments[11])
        assertEquals(targetId.toString(), auditArguments[12])
    }

    @Test
    fun `list App Admins records the result count`()
    {
        val assignment = AppRoleAssignment().apply { appUserId = UUID.randomUUID() }
        `when`(appRoleAssignmentRepository.findActiveAppAdmins()).thenReturn(listOf(assignment))

        service.listAppAdmins(actorId)

        val auditArguments = auditArguments("emitRequired")
        assertEquals("APP_ADMIN_LIST", auditArguments[0])
        assertEquals("SUCCESS", auditArguments[1])
        assertEquals(actorId, auditArguments[3])
        assertEquals("APP_ADMIN", auditArguments[4])
        assertEquals("1", structuredDetails(auditArguments)["result_count"])
    }

    @Test
    fun `candidate search records counts without recording the query`()
    {
        val candidate = appUser(UUID.randomUUID())
        `when`(appUserService.searchActiveUsers("alice", 20)).thenReturn(listOf(candidate))

        service.searchAppAdminCandidates(" alice ", 20, actorId)

        val auditArguments = auditArguments("emitRequired")
        assertEquals("APP_ADMIN_CANDIDATE_SEARCH", auditArguments[0])
        assertEquals(actorId, auditArguments[3])
        assertEquals(
            mapOf("query_length" to "5", "result_count" to "1", "limit" to "20"),
            structuredDetails(auditArguments),
        )
    }

    @Test
    fun `revokeAppRole allows an ineffective admin assignment to be revoked`()
    {
        val assignment = AppRoleAssignment().apply {
            appUserId = UUID.randomUUID()
            roleName = AppRoleName.APP_ADMIN
            isActive = true
        }
        `when`(appRoleAssignmentRepository.findById(assignment.id)).thenReturn(assignment)
        `when`(appRoleAssignmentRepository.isEffective(assignment.id)).thenReturn(false)

        service.revokeAppRole(assignment.id, actorId)

        assertEquals(false, assignment.isActive)
        verify(appRoleAssignmentRepository, never()).countActiveAppAdmins()
        verify(appRoleAssignmentRepository).update(assignment)
    }

    @Test
    fun `revokeAppRole protects the last effective admin assignment`()
    {
        val assignment = AppRoleAssignment().apply {
            appUserId = UUID.randomUUID()
            roleName = AppRoleName.APP_ADMIN
            isActive = true
        }
        `when`(appRoleAssignmentRepository.findById(assignment.id)).thenReturn(assignment)
        `when`(appRoleAssignmentRepository.isEffective(assignment.id)).thenReturn(true)
        `when`(appRoleAssignmentRepository.countActiveAppAdmins()).thenReturn(1)

        assertThrows(LastAppAdminException::class.java) {
            service.revokeAppRole(assignment.id, actorId)
        }

        verify(appRoleAssignmentRepository, never()).update(assignment)
    }

    @Test
    fun `bootstrap skips an inactive configured user`()
    {
        val email = "bootstrap@example.com"
        val target = appUser(UUID.randomUUID()).apply { isActive = false }
        `when`(configurationService.getBootstrapAppAdminEmail()).thenReturn(email)
        `when`(appRoleAssignmentRepository.countActiveAppAdmins()).thenReturn(0)
        `when`(appUserService.findByEmail(email)).thenReturn(target)

        service.bootstrapFirstAppAdmin(mock(StartupEvent::class.java))

        verify(appRoleAssignmentRepository, never()).save(any())
        verify(appRoleAssignmentRepository, never()).update(any())
    }

    @Test
    fun `bootstrap is idempotent when an effective admin exists`()
    {
        `when`(configurationService.getBootstrapAppAdminEmail()).thenReturn("bootstrap@example.com")
        `when`(appRoleAssignmentRepository.countActiveAppAdmins()).thenReturn(1)

        service.bootstrapFirstAppAdmin(mock(StartupEvent::class.java))

        verify(appUserService, never()).findByEmail(any())
        verify(appRoleAssignmentRepository, never()).save(any())
    }

    @Test
    fun `bootstrap grants the configured eligible user when no effective admin exists`()
    {
        val email = "bootstrap@example.com"
        val target = appUser(UUID.randomUUID())
        `when`(configurationService.getBootstrapAppAdminEmail()).thenReturn(email)
        `when`(appRoleAssignmentRepository.countActiveAppAdmins()).thenReturn(0)
        `when`(appUserService.findByEmail(email)).thenReturn(target)
        `when`(appUserService.getById(target.id)).thenReturn(target)
        `when`(
            appRoleAssignmentRepository.findAppRoleForUser(target.id, AppRoleName.APP_ADMIN),
        ).thenReturn(null)

        service.bootstrapFirstAppAdmin(mock(StartupEvent::class.java))

        verify(appRoleAssignmentRepository).save(any())
    }

    private fun appUser(id: UUID): AppUser = AppUser().apply {
        this.id = id
        email = "$id@example.com"
        isActive = true
        deprovisionedAt = null
    }

    private fun auditArguments(methodName: String): Array<Any?> =
        mockingDetails(authAuditService).invocations
            .last { it.method.name == methodName }
            .arguments

    @Suppress("UNCHECKED_CAST")
    private fun structuredDetails(arguments: Array<Any?>): Map<String, String> =
        arguments[13] as Map<String, String>
}
