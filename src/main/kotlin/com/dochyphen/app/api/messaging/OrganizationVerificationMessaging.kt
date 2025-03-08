package com.dochyphen.app.api.messaging

import com.dochyphen.app.api.model.entity.Company
import com.dochyphen.app.api.repository.OrganizationRepository
import io.smallrye.reactive.messaging.kafka.KafkaRecord
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.slf4j.LoggerFactory
import io.smallrye.reactive.messaging.annotations.Blocking
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class OrganizationVerificationProducer(
    @Channel("company-verification-out") private val emitter: Emitter<KafkaRecord<String, Company>>
)
{
    private val logger = LoggerFactory.getLogger(OrganizationVerificationProducer::class.java)

    fun sendToQueue(company: Company)
    {
        logger.info("Sending company to verification queue: ${company.registrationNumber}")
//        emitter.send(KafkaRecord.of(company.registrationNumber, company))
    }
}

@ApplicationScoped
class OrganizationVerificationConsumer(
    private val organizationRepository: OrganizationRepository
)
{
    private val logger = LoggerFactory.getLogger(OrganizationVerificationConsumer::class.java)

    @Incoming("company-verification-in")
    @Blocking // Ensures processing does not block the reactive pipeline
    fun verifyCompany(company: Company)
    {
        logger.info("Received company for verification: ${company.registrationNumber}")

        // Simulate verification process
        company.isActive = true
        company.verificationComplete = true
        organizationRepository.save(company)

        logger.info("Company verification complete: ${company.registrationNumber}")
    }
}