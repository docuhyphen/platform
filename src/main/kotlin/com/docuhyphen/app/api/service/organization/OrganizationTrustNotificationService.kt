package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.organization.OrganizationTrustNotificationDetails
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class OrganizationTrustNotificationService @Inject constructor(
    private val organizationService: OrganizationService,
    private val organizationMembershipService: OrganizationMembershipService,
    private val emailService: EmailService,
    private val inAppNotificationService: InAppNotificationService,
    private val configurationService: ConfigurationService,
)
{
    fun publish(eventType: AuditEventType, relationship: OrganizationTrustRelationship)
    {
        runCatching { publishNotifications(eventType, relationship) }
            .onFailure { exception ->
                logger.warn(
                    "Failed to publish Trusted Organization notifications for relationshipId={} eventType={}",
                    relationship.id,
                    eventType.key,
                    exception,
                )
            }
    }

    private fun publishNotifications(eventType: AuditEventType, relationship: OrganizationTrustRelationship)
    {
        val organizationA = organizationService.getOrganizationById(relationship.organizationAId)
        val organizationB = organizationService.getOrganizationById(relationship.organizationBId)
        val notification = notificationDetails(eventType)
        listOf(organizationA, organizationB).forEach { recipientOrganization ->
            val partner = if (recipientOrganization.id == organizationA.id) organizationB else organizationA
            organizationMembershipService.activeAdmins(recipientOrganization.id).forEach { appUser ->
                val message = "${notification.message} ${partner.name}."
                runCatching {
                    inAppNotificationService.publishAdministrative(
                        appUserId = appUser.id,
                        type = eventType.key,
                        title = notification.title,
                        message = message,
                        data = mapOf(
                            "source" to "organization-trust",
                            "relationshipId" to relationship.id.toString(),
                            "organizationId" to recipientOrganization.id.toString(),
                        ),
                    )
                }.onFailure { exception ->
                    logger.warn(
                        "Failed to publish Trusted Organization notification for userId={} eventType={}",
                        appUser.id,
                        eventType.key,
                        exception,
                    )
                }
                runCatching {
                    emailService.sendEmail(
                        appUser.email,
                        notification.title,
                        "$message Open ${configurationService.baseUrl}/settings to review Trusted Organizations.",
                    )
                }.onFailure { exception ->
                    logger.warn(
                        "Failed to send Trusted Organization email for userId={} eventType={}",
                        appUser.id,
                        eventType.key,
                        exception,
                    )
                }
            }
        }
    }

    private fun notificationDetails(eventType: AuditEventType): OrganizationTrustNotificationDetails = when (eventType)
    {
        AuditEventType.ORG_TRUST_REQUESTED -> OrganizationTrustNotificationDetails(
            "Trusted Organization request",
            "A trust request was created with",
        )

        AuditEventType.ORG_TRUST_REREQUESTED -> OrganizationTrustNotificationDetails(
            "Trusted Organization requested again",
            "A new trust request was created with",
        )

        AuditEventType.ORG_TRUST_ACCEPTED -> OrganizationTrustNotificationDetails(
            "Trusted Organization request accepted",
            "Trust is now active with",
        )

        AuditEventType.ORG_TRUST_REJECTED -> OrganizationTrustNotificationDetails(
            "Trusted Organization request rejected",
            "A trust request was rejected with",
        )

        AuditEventType.ORG_TRUST_WITHDRAWN -> OrganizationTrustNotificationDetails(
            "Trusted Organization request withdrawn",
            "A trust request was withdrawn with",
        )

        AuditEventType.ORG_TRUST_EXPIRED -> OrganizationTrustNotificationDetails(
            "Trusted Organization request expired",
            "A trust request expired with",
        )

        AuditEventType.ORG_TRUST_SUSPENDED -> OrganizationTrustNotificationDetails(
            "Trusted Organization suspended",
            "Trust was suspended with",
        )

        AuditEventType.ORG_TRUST_RESUMED -> OrganizationTrustNotificationDetails(
            "Trusted Organization resumed",
            "A suspension was cleared with",
        )

        AuditEventType.ORG_TRUST_ENDED -> OrganizationTrustNotificationDetails(
            "Trusted Organization ended",
            "Trust was ended with",
        )

        AuditEventType.ORG_TRUST_POLICY_UPDATED -> OrganizationTrustNotificationDetails(
            "Trusted Organization policy updated",
            "Trust policy was updated with",
        )

        else -> throw IllegalArgumentException("Unsupported Trusted Organization notification event")
    }

    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationTrustNotificationService::class.java)
    }
}
