package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.time.LocalDate

@ApplicationScoped
class InformationRequestDocumentFileCompletion @Inject constructor(
    private val policyLoader: InformationRequestEvidencePolicyLoader,
    private val factsReader: InformationRequestEvidenceFactsReader,
    private val deploymentPolicy: InformationRequestEvidenceDeploymentPolicy,
    private val clock: Clock,
)
{
    fun completedByOwnFiles(requirement: InformationRequestRequirement): Boolean
    {
        val policy = policyLoader.forBinding(requirement.sourceTemplateBindingId) ?: return false
        return InformationRequestEvidencePolicyEvaluator.evaluateRequirement(
            policy,
            factsReader.facts(requirement),
            LocalDate.now(clock),
            malwareScanRequired = deploymentPolicy.malwareScanRequired(),
        ).state.completesWork
    }
}
