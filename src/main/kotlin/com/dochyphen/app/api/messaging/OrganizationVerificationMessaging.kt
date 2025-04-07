package com.dochyphen.app.api.messaging

import com.dochyphen.app.api.model.entity.Organization
import com.dochyphen.app.api.repository.OrganizationRepository
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory

@ApplicationScoped
class OrganizationVerificationProducer(
//    @Channel("organization-verification-out") private val emitter: Emitter<KafkaRecord<String, Organization>>
)
{
    private val logger = LoggerFactory.getLogger(OrganizationVerificationProducer::class.java)

    fun sendToQueue(organization: Organization)
    {
        logger.info("Sending organization to verification queue: ${organization.registrationNumber}")
//        emitter.send(KafkaRecord.of(organization.registrationNumber, organization))
    }
}

@ApplicationScoped
class OrganizationVerificationConsumer(
    private val organizationRepository: OrganizationRepository
)
{
    private val logger = LoggerFactory.getLogger(OrganizationVerificationConsumer::class.java)

//    @Incoming("organization-verification-in")
//    @Blocking // Ensures processing does not block the reactive pipeline
    fun verifyOrganization(organization: Organization)
    {
        logger.info("Received organization for verification: ${organization.registrationNumber}")

        // Simulate verification process
        organization.isActive = true
        organization.verificationComplete = true
        organizationRepository.save(organization)

        logger.info("Organization verification complete: ${organization.registrationNumber}")
    }
}