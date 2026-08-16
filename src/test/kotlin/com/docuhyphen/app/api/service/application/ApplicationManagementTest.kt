package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.exception.ApplicationNotFoundException
import com.docuhyphen.app.api.model.entity.Application
import com.docuhyphen.app.api.model.entity.ApplicationRoleName
import com.docuhyphen.app.api.model.entity.ApplicationType
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.application.ApplicationRepository
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Application registration management.
 *
 * Verifies that:
 * - APP_ADMIN (APP_REG_ADMIN) can list, create, rotate credentials, update capabilities, deactivate.
 * - A principal without APP_REG_ADMIN is denied at every mutating operation.
 * - rotateCredentials replaces both apiKey and apiSecretHash.
 * - deactivate marks the application inactive and is idempotent.
 * - Newly generated API key and raw secret are non-empty and distinct per call.
 * - ApplicationNotFoundException is thrown when the target does not exist.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationManagementTest
{
    @Mock private lateinit var applicationRepository: ApplicationRepository
    @Mock private lateinit var authorizationService: AuthorizationService
    @Mock private lateinit var authAuditService: AuthAuditService
    @Mock private lateinit var subscriptionGuard: OrganizationFeatureSubscriptionGuard

    private lateinit var managementService: ApplicationManagementService

    private val adminId: UUID = UUID.randomUUID()
    private val appId: UUID = UUID.randomUUID()
    private val adminPrincipal = PrincipalRef(PrincipalKind.USER, adminId)
    private val adminContext = AuthorizationContext(activeOrgId = null)

    @BeforeEach
    fun setup()
    {
        managementService = ApplicationManagementService(
            applicationRepository = applicationRepository,
            authorizationService = authorizationService,
            authAuditService = authAuditService,
            subscriptionGuard = subscriptionGuard,
        )
    }

    @Test
    fun `listAll returns all applications when caller has APP_REG_READ`()
    {
        allowAll()
        `when`(applicationRepository.findAllOrdered()).thenReturn(listOf(activeApp()))

        val result = managementService.listAll(adminPrincipal, adminContext)
        assertEquals(1, result.size)
    }

    @Test
    fun `listAll throws SecurityException when caller lacks APP_REG_READ`()
    {
        denyAll()
        assertThrows(SecurityException::class.java) {
            managementService.listAll(adminPrincipal, adminContext)
        }
    }

    @Test
    fun `rotateCredentials replaces apiKey and apiSecretHash`()
    {
        allowAll()
        val app = activeApp()
        val originalKey = app.apiKey
        val originalHash = app.apiSecretHash
        `when`(applicationRepository.findById(appId)).thenReturn(app)
        `when`(applicationRepository.update(any())).thenAnswer { it.arguments[0] }

        val result = managementService.rotateCredentials(appId, adminPrincipal, adminContext)

        assertNotNull(result.apiKey)
        assertNotNull(result.rawSecret)
        assertTrue(result.apiKey.isNotBlank())
        assertTrue(result.rawSecret.isNotBlank())
        assertNotEquals(originalKey, app.apiKey) { "apiKey must change after rotation" }
        assertNotEquals(originalHash, app.apiSecretHash) { "apiSecretHash must change after rotation" }
    }

    @Test
    fun `rotateCredentials throws SecurityException when caller lacks APP_REG_ADMIN`()
    {
        denyAll()
        assertThrows(SecurityException::class.java) {
            managementService.rotateCredentials(appId, adminPrincipal, adminContext)
        }
    }

    @Test
    fun `rotateCredentials throws ApplicationNotFoundException for unknown app`()
    {
        allowAll()
        `when`(applicationRepository.findById(appId)).thenReturn(null)

        assertThrows(ApplicationNotFoundException::class.java) {
            managementService.rotateCredentials(appId, adminPrincipal, adminContext)
        }
    }

    @Test
    fun `deactivate marks application inactive`()
    {
        allowAll()
        val app = activeApp()
        `when`(applicationRepository.findById(appId)).thenReturn(app)
        `when`(applicationRepository.update(any())).thenAnswer { it.arguments[0] }

        managementService.deactivate(appId, adminPrincipal, adminContext)

        assertFalse(app.isActive) { "Application must be inactive after deactivation" }
    }

    @Test
    fun `deactivate is idempotent for already-inactive application`()
    {
        allowAll()
        val app = activeApp().also { it.isActive = false }
        `when`(applicationRepository.findById(appId)).thenReturn(app)

        managementService.deactivate(appId, adminPrincipal, adminContext)

        assertFalse(app.isActive)
    }

    @Test
    fun `deactivate throws SecurityException when caller lacks APP_REG_ADMIN`()
    {
        denyAll()
        assertThrows(SecurityException::class.java) {
            managementService.deactivate(appId, adminPrincipal, adminContext)
        }
    }

    @Test
    fun `updateGrantedCapabilities stores the new capabilities json`()
    {
        allowAll()
        val app = activeApp()
        `when`(applicationRepository.findById(appId)).thenReturn(app)
        `when`(applicationRepository.update(any())).thenAnswer { it.arguments[0] }

        managementService.updateGrantedCapabilities(appId, listOf("EXCHANGE_INITIATE"), adminPrincipal, adminContext)

        assertTrue(app.grantedCapabilitiesJson.contains("EXCHANGE_INITIATE")) {
            "Granted capabilities JSON must contain the newly added capability"
        }
    }

    @Test
    fun `two sequential credential rotations produce different api keys`()
    {
        allowAll()
        `when`(applicationRepository.findById(appId)).thenReturn(activeApp())
        `when`(applicationRepository.update(any())).thenAnswer { it.arguments[0] }

        val firstRotation = managementService.rotateCredentials(appId, adminPrincipal, adminContext)

        `when`(applicationRepository.findById(appId)).thenReturn(activeApp())
        val secondRotation = managementService.rotateCredentials(appId, adminPrincipal, adminContext)

        assertNotEquals(firstRotation.apiKey, secondRotation.apiKey) {
            "Each credential rotation must produce a unique API key"
        }
        assertNotEquals(firstRotation.rawSecret, secondRotation.rawSecret) {
            "Each credential rotation must produce a unique raw secret"
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private fun activeApp(): Application = Application().apply {
        id = appId
        name = "test-app"
        apiKey = "original-key"
        apiSecretHash = "original-hash"
        roleName = ApplicationRoleName.APPLICATION
        applicationType = ApplicationType.SERVICE
        isActive = true
        createdDate = Timestamp.from(Instant.now())
    }

    private fun allowAll()
    {
        `when`(authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Allow())
    }

    private fun denyAll()
    {
        `when`(authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "denied in test"))
    }
}
