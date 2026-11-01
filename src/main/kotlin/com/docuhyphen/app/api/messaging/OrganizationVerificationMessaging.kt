package com.docuhyphen.app.api.messaging

import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory

@ApplicationScoped
class OrganizationVerificationProducer
{
    private val logger = LoggerFactory.getLogger(OrganizationVerificationProducer::class.java)

    fun sendToQueue(organization: Organization)
    {
        logger.info("Sending organization to verification queue: ${organization.registrationNumber}")
    }
}

@ApplicationScoped
class OrganizationVerificationConsumer(
    private val organizationRepository: OrganizationRepository,
    private val subscriptionPolicyService: SubscriptionPolicyService,
)
{
    private val logger = LoggerFactory.getLogger(OrganizationVerificationConsumer::class.java)

    fun verifyOrganization(organization: Organization)
    {
        logger.info("Received organization for verification: ${organization.registrationNumber}")

        organization.isActive = true
        organization.verificationComplete = true
        organizationRepository.save(organization)

        runCatching { subscriptionPolicyService.ensureOrganizationPolicy(organization) }
            .onFailure { logger.warn("Failed to create subscription record for organization {}", organization.id, it) }

        logger.info("Organization verification complete: ${organization.registrationNumber}")
    }
}
