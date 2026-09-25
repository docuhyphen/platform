package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionAttestation
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionEvidence
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionItem
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionPackage
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionSupportingLink
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionWithdrawal
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import java.util.UUID

data class RecordInformationRequestSubmissionAttestationCommand(
    val requestId: UUID,
    val requirementId: UUID,
    val decision: InformationRequestAttestationDecision,
    val refusalReason: String? = null,
    val externalSignatureReference: String? = null,
    val partyId: UUID? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestSubmissionAttestationResult(
    val attestation: InformationRequestSubmissionAttestation,
    val evaluation: InformationRequestAttestationRequirementEvaluation?,
    val submissionETag: String,
)

data class SubmitInformationRequestPackageCommand(
    val requestId: UUID,
    val stageKey: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class WithdrawInformationRequestPackageCommand(
    val requestId: UUID,
    val packageId: UUID,
    val reasonCode: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestSubmissionPackageView(
    val submissionPackage: InformationRequestSubmissionPackage,
    val items: List<InformationRequestSubmissionItem>,
    val evidence: List<InformationRequestSubmissionEvidence>,
    val links: List<InformationRequestSubmissionSupportingLink>,
    val attestations: List<InformationRequestSubmissionAttestation>,
    val withdrawal: InformationRequestSubmissionWithdrawal?,
)

data class InformationRequestSubmissionResult(
    val request: InformationRequest,
    val submission: InformationRequestSubmissionPackageView,
    val requestETag: String,
    val responseETag: String,
)

data class InformationRequestSubmissionItemProblem(
    val requirementId: UUID,
    val requirementKey: String,
    val occurrencePath: String,
    val code: InformationRequestSubmissionProblemCode,
)

enum class InformationRequestSubmissionProblemCode
{
    REQUIREMENT_INCOMPLETE,
    EVIDENCE_NOT_CONFORMING,
    ATTESTATION_MISSING,
    ATTESTATION_REFUSED,
    RECONFIRMATION_REQUIRED,
}

data class InformationRequestSubmissionReadiness(
    val problems: List<InformationRequestSubmissionItemProblem>,
    val undisclosedProblemCount: Int,
)
{
    val ready: Boolean get() = problems.isEmpty() && undisclosedProblemCount == 0
}
