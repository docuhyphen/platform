package com.docuhyphen.app.api.service.informationrequest

import jakarta.enterprise.context.ApplicationScoped

abstract class InformationRequestFirstContractExecutor(
    override val capability: InformationRequestCapability,
) : InformationRequestCapabilityExecutor
{
    override val contractVersion: Int = 1
    override val minimumSupportedContractVersion: Int = 1
}

@ApplicationScoped
class InformationRequestStructuredResponseExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.STRUCTURED_RESPONSE)

@ApplicationScoped
class InformationRequestDocumentEvidenceExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.DOCUMENT_EVIDENCE)

@ApplicationScoped
class InformationRequestResponseAttestationExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.RESPONSE_ATTESTATION)

@ApplicationScoped
class InformationRequestConditionalRequirementExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.CONDITIONAL_REQUIREMENT)

@ApplicationScoped
class InformationRequestRepeatableOccurrenceExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.REPEATABLE_OCCURRENCE)

@ApplicationScoped
class InformationRequestConfidentialityCompartmentExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.CONFIDENTIALITY_COMPARTMENT)

@ApplicationScoped
class InformationRequestEvidenceWaiverExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.EVIDENCE_WAIVER)

@ApplicationScoped
class InformationRequestSubstituteEvidenceExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.SUBSTITUTE_EVIDENCE)

@ApplicationScoped
class InformationRequestSupportingEvidenceExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.SUPPORTING_EVIDENCE)

@ApplicationScoped
class InformationRequestResponseSubmissionExecutor :
    InformationRequestFirstContractExecutor(InformationRequestCapability.RESPONSE_SUBMISSION)
