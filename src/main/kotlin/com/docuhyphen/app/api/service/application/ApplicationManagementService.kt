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
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

data class RotateCredentialsResult(val apiKey: String, val rawSecret: String)

data class CreateApplicationRequest(
    val name: String,
    val description: String?,
    val applicationType: ApplicationType,
    val ownerOrganizationId: UUID?,
    val grantedCapabilities: List<String>,
)

@ApplicationScoped
class ApplicationManagementService @Inject constructor(
    private val applicationRepository: ApplicationRepository,
    private val authorizationService: AuthorizationService,
    private val authAuditService: AuthAuditService,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ApplicationManagementService::class.java)
        private val secureRandom = SecureRandom()

        private const val API_KEY_BYTES = 24
        private const val API_SECRET_BYTES = 32
    }

    fun listAll(principal: PrincipalRef, context: AuthorizationContext): List<Application>
    {
        requireCapability(principal, Action.APP_REG_LIST, platformRef(), context)
        return applicationRepository.findAllOrdered()
    }

    fun getById(id: UUID, principal: PrincipalRef, context: AuthorizationContext): Application
    {
        requireCapability(principal, Action.APP_REG_VIEW, ResourceRef(ResourceType.APPLICATION, id), context)
        return applicationRepository.findById(id) ?: throw ApplicationNotFoundException()
    }

    @Transactional
    fun create(request: CreateApplicationRequest, principal: PrincipalRef, context: AuthorizationContext): Pair<Application, String>
    {
        requireCapability(principal, Action.APP_REG_CREATE, platformRef(), context)
        require(request.name.isNotBlank()) { "Application name is required" }
        subscriptionGuard.requireMutation(request.ownerOrganizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)

        val (rawKey, rawSecret) = generateCredentials()
        val application = Application().apply {
            name = request.name.trim()
            description = request.description?.trim()
            applicationType = request.applicationType
            ownerOrganizationId = request.ownerOrganizationId
            apiKey = rawKey
            apiSecretHash = BCrypt.hashpw(rawSecret, BCrypt.gensalt())
            roleName = ApplicationRoleName.APPLICATION
            isActive = true
            grantedCapabilitiesJson = buildCapabilitiesJson(request.grantedCapabilities)
        }
        applicationRepository.save(application)
        emitAudit("APP_REG_CREATE", principal.id, application.id, "created")
        return Pair(application, rawSecret)
    }

    @Transactional
    fun rotateCredentials(id: UUID, principal: PrincipalRef, context: AuthorizationContext): RotateCredentialsResult
    {
        requireCapability(principal, Action.APP_REG_ROTATE_CREDENTIALS, ResourceRef(ResourceType.APPLICATION, id), context)
        val application = applicationRepository.findById(id) ?: throw ApplicationNotFoundException()
        subscriptionGuard.requireMutation(application.ownerOrganizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)

        val (rawKey, rawSecret) = generateCredentials()
        application.apiKey = rawKey
        application.apiSecretHash = BCrypt.hashpw(rawSecret, BCrypt.gensalt())
        applicationRepository.update(application)
        emitAudit("APP_REG_ROTATE_CREDENTIALS", principal.id, id, "credentials rotated")
        return RotateCredentialsResult(rawKey, rawSecret)
    }

    @Transactional
    fun updateGrantedCapabilities(id: UUID, capabilities: List<String>, principal: PrincipalRef, context: AuthorizationContext)
    {
        requireCapability(principal, Action.APP_REG_GRANT_CAPABILITIES, ResourceRef(ResourceType.APPLICATION, id), context)
        val application = applicationRepository.findById(id) ?: throw ApplicationNotFoundException()
        subscriptionGuard.requireMutation(application.ownerOrganizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        application.grantedCapabilitiesJson = buildCapabilitiesJson(capabilities)
        applicationRepository.update(application)
        emitAudit("APP_REG_UPDATE_CAPABILITIES", principal.id, id, "capabilities updated")
    }

    @Transactional
    fun deactivate(id: UUID, principal: PrincipalRef, context: AuthorizationContext)
    {
        requireCapability(principal, Action.APP_REG_DEACTIVATE, ResourceRef(ResourceType.APPLICATION, id), context)
        val application = applicationRepository.findById(id) ?: throw ApplicationNotFoundException()
        subscriptionGuard.requireMutation(application.ownerOrganizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        if (!application.isActive) return
        application.isActive = false
        applicationRepository.update(application)
        emitAudit("APP_REG_DEACTIVATE", principal.id, id, "deactivated")
    }

    // -------------------------------------------------------------------------

    private fun requireCapability(principal: PrincipalRef, action: Action, resource: ResourceRef, context: AuthorizationContext)
    {
        val decision = authorizationService.authorize(principal, action, resource, context)
        if (!decision.isAllowed)
        {
            throw SecurityException("Not authorized to perform $action on application registrations")
        }
    }

    private fun platformRef(): ResourceRef = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))

    private fun generateCredentials(): Pair<String, String>
    {
        val keyBytes = ByteArray(API_KEY_BYTES).also { secureRandom.nextBytes(it) }
        val secretBytes = ByteArray(API_SECRET_BYTES).also { secureRandom.nextBytes(it) }
        val rawKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes)
        val rawSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)
        return Pair(rawKey, rawSecret)
    }

    private fun buildCapabilitiesJson(names: List<String>): String
    {
        if (names.isEmpty()) return "[]"
        val quoted = names.map { "\"${it.trim()}\"" }.joinToString(",")
        return "[$quoted]"
    }

    private fun emitAudit(action: String, actorId: UUID, targetId: UUID, reason: String)
    {
        runCatching {
            authAuditService.emit(
                action = action,
                outcome = "SUCCESS",
                actorId = actorId,
                reason = reason,
                targetType = "APPLICATION",
                afterSnapshot = "applicationId=$targetId",
            )
        }.onFailure { logger.error("Failed to emit audit for $action", it) }
    }
}
