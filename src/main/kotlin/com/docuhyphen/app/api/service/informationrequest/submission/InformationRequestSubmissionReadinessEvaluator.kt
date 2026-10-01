package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionAssessment
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionContent
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionContentItem
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionItemProblem
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionProblemCode
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionReadiness
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationEvaluationService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCompletenessProgressService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

class InformationRequestSubmissionIncompleteException(
    val readiness: InformationRequestSubmissionReadiness,
) : InformationRequestLifecycleException(
    InformationRequestErrorCatalog.SUBMISSION_INCOMPLETE,
    "This Information Request cannot be submitted until every required item is complete",
)

@ApplicationScoped
class InformationRequestSubmissionReadinessEvaluator @Inject constructor(
    private val completenessProgressService: InformationRequestCompletenessProgressService,
    private val attestationEvaluationService: InformationRequestAttestationEvaluationService,
    private val gate: InformationRequestMutationGate,
)
{
    fun assess(content: InformationRequestSubmissionContent, access: RequestAccessContext): InformationRequestSubmissionAssessment
    {
        val contributions = completenessProgressService.evaluate(content.request.id).items
            .filter { it.requirementId != null }
            .groupBy { it.requirementId!! }
        val attestations = attestationEvaluationService.evaluate(content)
        val states = content.items.associate { item ->
            item.requirement.id to stateOf(contributions[item.requirement.id].orEmpty())
        }
        val problems = content.items.mapNotNull { item ->
            val state = states[item.requirement.id]
            val code = when
            {
                state == InformationRequestCompletenessItemState.INCOMPLETE -> problemOf(item, attestations[item.requirement.id])
                state != InformationRequestCompletenessItemState.HIDDEN &&
                    item.response?.reconfirmationRequiredByAmendmentId != null ->
                    InformationRequestSubmissionProblemCode.RECONFIRMATION_REQUIRED
                else -> return@mapNotNull null
            }
            InformationRequestSubmissionItemProblem(
                requirementId = item.requirement.id,
                requirementKey = item.requirementKey,
                occurrencePath = item.requirement.occurrencePath,
                code = code,
            )
        }
        val (disclosed, undisclosed) = problems.partition {
            gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it.requirementId)
        }
        return InformationRequestSubmissionAssessment(
            readiness = InformationRequestSubmissionReadiness(disclosed, undisclosed.size),
            stateByRequirement = states,
            attestations = attestations,
        )
    }

    private fun stateOf(contributions: List<InformationRequestCompletenessContribution>): InformationRequestCompletenessItemState =
        when
        {
            contributions.isEmpty() -> InformationRequestCompletenessItemState.INCOMPLETE
            contributions.any { it.state == InformationRequestCompletenessItemState.INCOMPLETE } ->
                InformationRequestCompletenessItemState.INCOMPLETE
            contributions.any { it.state == InformationRequestCompletenessItemState.REJECTED } ->
                InformationRequestCompletenessItemState.INCOMPLETE
            contributions.all { it.state == InformationRequestCompletenessItemState.HIDDEN } ->
                InformationRequestCompletenessItemState.HIDDEN
            contributions.any { it.state == InformationRequestCompletenessItemState.COMPLETE } ->
                InformationRequestCompletenessItemState.COMPLETE
            else -> InformationRequestCompletenessItemState.OPTIONAL_UNANSWERED
        }

    private fun problemOf(
        item: InformationRequestSubmissionContentItem,
        attestation: InformationRequestAttestationRequirementEvaluation?,
    ): InformationRequestSubmissionProblemCode = when
    {
        item.requirementType == InformationRequestRequirementType.RESPONSE_ATTESTATION &&
            attestation?.evaluation?.state == InformationRequestAttestationState.REFUSED ->
            InformationRequestSubmissionProblemCode.ATTESTATION_REFUSED
        item.requirementType == InformationRequestRequirementType.RESPONSE_ATTESTATION ->
            InformationRequestSubmissionProblemCode.ATTESTATION_MISSING
        item.requirementType == InformationRequestRequirementType.DOCUMENT && item.evidence?.members?.isNotEmpty() == true ->
            InformationRequestSubmissionProblemCode.EVIDENCE_NOT_CONFORMING
        else -> InformationRequestSubmissionProblemCode.REQUIREMENT_INCOMPLETE
    }
}
