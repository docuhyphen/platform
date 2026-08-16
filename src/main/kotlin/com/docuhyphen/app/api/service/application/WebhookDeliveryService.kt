package com.docuhyphen.app.api.service.application

import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.repository.workflow.WorkflowWebhookEndpointRepository
import com.docuhyphen.app.api.service.auth.AuthAuditService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Delivers outbound webhook payloads for ACTION workflow steps. The raw signing token
 * stored in [com.docuhyphen.app.api.model.entity.WorkflowWebhookEndpoint.signingSecretToken]
 * is used to produce an HMAC-SHA256 signature sent in X-DocuHyphen-Signature-256 so
 * receivers can verify the payload is authentic.
 */
@ApplicationScoped
class WebhookDeliveryService @Inject constructor(
    private val webhookEndpointRepository: WorkflowWebhookEndpointRepository,
    private val authAuditService: AuthAuditService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WebhookDeliveryService::class.java)
        private val httpClient: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }

    fun deliver(
        endpointId: UUID,
        eventType: String,
        instance: WorkflowInstance,
    ): WebhookDeliveryResult
    {
        val endpoint = webhookEndpointRepository.findById(endpointId)
            ?: return WebhookDeliveryResult(success = false, reason = "Webhook endpoint $endpointId not found")

        if (!endpoint.isEnabled)
        {
            return WebhookDeliveryResult(success = false, reason = "Webhook endpoint $endpointId is disabled")
        }

        val deliveryId = UUID.randomUUID()
        val payload = WebhookPayload(
            deliveryId = deliveryId.toString(),
            eventType = eventType,
            workflowInstanceId = instance.id.toString(),
            subjectResourceType = instance.subjectResourceType,
            subjectResourceId = instance.subjectResourceId?.toString(),
            organizationId = instance.organizationId?.toString(),
            timestamp = Instant.now().toString(),
        )
        val payloadJson = json.encodeToString(payload)
        val signature = hmacSha256(payloadJson, endpoint.signingSecretToken)

        return try
        {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.targetUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-DocuHyphen-Signature-256", signature)
                .header("X-DocuHyphen-Delivery", deliveryId.toString())
                .header("X-DocuHyphen-Event", eventType)
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val ok = response.statusCode() in 200..299

            emitAudit(
                action = if (ok) "WEBHOOK_DELIVERED" else "WEBHOOK_DELIVERY_FAILED",
                endpointId = endpoint.id,
                deliveryId = deliveryId,
                detail = "status=${response.statusCode()} eventType=$eventType instanceId=${instance.id}",
                ok = ok,
            )

            WebhookDeliveryResult(success = ok, reason = if (ok) null else "HTTP ${response.statusCode()}")
        }
        catch (e: Exception)
        {
            logger.error("Webhook delivery to ${endpoint.targetUrl} failed for instance ${instance.id}", e)
            emitAudit(
                action = "WEBHOOK_DELIVERY_FAILED",
                endpointId = endpoint.id,
                deliveryId = deliveryId,
                detail = "error=${e.message} eventType=$eventType instanceId=${instance.id}",
                ok = false,
            )
            WebhookDeliveryResult(success = false, reason = e.message)
        }
    }

    private fun hmacSha256(payload: String, secret: String): String
    {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return "sha256=" + bytes.joinToString("") { "%02x".format(it) }
    }

    private fun emitAudit(action: String, endpointId: UUID, deliveryId: UUID, detail: String, ok: Boolean)
    {
        runCatching {
            authAuditService.emit(
                action = action,
                outcome = if (ok) "SUCCESS" else "FAILED",
                reason = detail,
                targetType = "WORKFLOW_WEBHOOK_ENDPOINT",
                afterSnapshot = "endpointId=$endpointId deliveryId=$deliveryId",
            )
        }.onFailure { logger.error("Failed to emit audit for $action", it) }
    }
}

data class WebhookDeliveryResult(val success: Boolean, val reason: String?)

@Serializable
data class WebhookPayload(
    val deliveryId: String,
    val eventType: String,
    val workflowInstanceId: String,
    val subjectResourceType: String?,
    val subjectResourceId: String?,
    val organizationId: String?,
    val timestamp: String,
)
