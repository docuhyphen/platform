package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.WorkflowWebhookEndpoint
import com.docuhyphen.app.api.repository.WorkflowWebhookEndpointRepository
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
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class RegisterWebhookRequest(
    val workflowDefinitionId: UUID,
    val targetUrl: String,
    val permittedEventTypes: List<String>,
    val registeredApplicationId: UUID?,
)

data class WebhookSigningSecret(val rawSecret: String, val version: Int)

@ApplicationScoped
class WorkflowWebhookEndpointManagementService @Inject constructor(
    private val webhookEndpointRepository: WorkflowWebhookEndpointRepository,
    private val authorizationService: AuthorizationService,
    private val destinationPolicy: WebhookDestinationPolicy,
    private val authAuditService: AuthAuditService,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WorkflowWebhookEndpointManagementService::class.java)
        private val secureRandom = SecureRandom()
        private const val SECRET_BYTES = 32
    }

    fun listByOrganization(orgId: UUID, principal: PrincipalRef, context: AuthorizationContext): List<WorkflowWebhookEndpoint>
    {
        requireWebhookAdmin(orgId, principal, context)
        subscriptionGuard.requireMutation(orgId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        return webhookEndpointRepository.findByOrganization(orgId)
    }

    fun getById(id: UUID, principal: PrincipalRef, context: AuthorizationContext): WorkflowWebhookEndpoint
    {
        val endpoint = webhookEndpointRepository.findById(id)
            ?: throw NoSuchElementException("Webhook endpoint not found")
        requireWebhookAdmin(endpoint.ownerOrganizationId, principal, context)
        return endpoint
    }

    @Transactional
    fun register(orgId: UUID, request: RegisterWebhookRequest, principal: PrincipalRef, context: AuthorizationContext): Pair<WorkflowWebhookEndpoint, WebhookSigningSecret>
    {
        requireWebhookAdmin(orgId, principal, context)

        val validation = destinationPolicy.validate(request.targetUrl)
        require(validation.allowed) { validation.reason ?: "Target URL is not permitted" }

        val rawSecret = generateSecret()
        val endpoint = WorkflowWebhookEndpoint().apply {
            ownerOrganizationId = orgId
            workflowDefinitionId = request.workflowDefinitionId
            registeredApplicationId = request.registeredApplicationId
            targetUrl = request.targetUrl
            signingSecretToken = rawSecret
            signingSecretVersion = 1
            isEnabled = true
            permittedEventTypes = buildEventTypesJson(request.permittedEventTypes)
        }
        webhookEndpointRepository.save(endpoint)
        emitAudit("WEBHOOK_REGISTER", principal.id, endpoint.id, "registered")
        return Pair(endpoint, WebhookSigningSecret(rawSecret, 1))
    }

    @Transactional
    fun enable(id: UUID, principal: PrincipalRef, context: AuthorizationContext)
    {
        val endpoint = webhookEndpointRepository.findById(id)
            ?: throw NoSuchElementException("Webhook endpoint not found")
        requireWebhookAdmin(endpoint.ownerOrganizationId, principal, context)
        subscriptionGuard.requireMutation(endpoint.ownerOrganizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        endpoint.isEnabled = true
        endpoint.updatedDate = Timestamp.from(Instant.now())
        webhookEndpointRepository.update(endpoint)
        emitAudit("WEBHOOK_ENABLE", principal.id, id, "enabled")
    }

    @Transactional
    fun disable(id: UUID, principal: PrincipalRef, context: AuthorizationContext)
    {
        val endpoint = webhookEndpointRepository.findById(id)
            ?: throw NoSuchElementException("Webhook endpoint not found")
        requireWebhookAdmin(endpoint.ownerOrganizationId, principal, context)
        subscriptionGuard.requireMutation(endpoint.ownerOrganizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        endpoint.isEnabled = false
        endpoint.updatedDate = Timestamp.from(Instant.now())
        webhookEndpointRepository.update(endpoint)
        emitAudit("WEBHOOK_DISABLE", principal.id, id, "disabled")
    }

    @Transactional
    fun rotateSigningSecret(id: UUID, principal: PrincipalRef, context: AuthorizationContext): WebhookSigningSecret
    {
        val endpoint = webhookEndpointRepository.findById(id)
            ?: throw NoSuchElementException("Webhook endpoint not found")
        requireWebhookAdmin(endpoint.ownerOrganizationId, principal, context)
        subscriptionGuard.requireMutation(endpoint.ownerOrganizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)

        val rawSecret = generateSecret()
        endpoint.signingSecretToken = rawSecret
        endpoint.signingSecretVersion = endpoint.signingSecretVersion + 1
        endpoint.updatedDate = Timestamp.from(Instant.now())
        webhookEndpointRepository.update(endpoint)
        emitAudit("WEBHOOK_ROTATE_SECRET", principal.id, id, "signing secret rotated to v${endpoint.signingSecretVersion}")
        return WebhookSigningSecret(rawSecret, endpoint.signingSecretVersion)
    }

    // -------------------------------------------------------------------------

    private fun requireWebhookAdmin(orgId: UUID, principal: PrincipalRef, context: AuthorizationContext)
    {
        val resource = ResourceRef(ResourceType.WORKFLOW_WEBHOOK_ENDPOINT, orgId)
        val decision = authorizationService.authorize(principal, Action.WEBHOOK_CONFIGURE, resource, context)
        if (!decision.isAllowed)
        {
            throw SecurityException("WEBHOOK_ADMIN capability required for this organization")
        }
    }

    private fun generateSecret(): String
    {
        val bytes = ByteArray(SECRET_BYTES).also { secureRandom.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun buildEventTypesJson(types: List<String>): String
    {
        if (types.isEmpty()) return "[]"
        return "[${types.map { "\"${it.trim()}\"" }.joinToString(",")}]"
    }

    private fun emitAudit(action: String, actorId: UUID, targetId: UUID, reason: String)
    {
        runCatching {
            authAuditService.emit(
                action = action,
                outcome = "SUCCESS",
                actorId = actorId,
                reason = reason,
                targetType = "WORKFLOW_WEBHOOK_ENDPOINT",
                afterSnapshot = "endpointId=$targetId",
            )
        }.onFailure { logger.error("Failed to emit audit for $action", it) }
    }
}
