package com.docuhyphen.app.api.model.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactRevocation
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecision
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestFactRecertification
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.time.Instant
import java.util.UUID

data class PromoteInformationRequestAcceptedFactCommand(
    val requestId: UUID,
    val packageId: UUID,
    val submissionItemId: UUID,
    val purposeKey: String,
    val policyBasisKey: String? = null,
    val evidenceVersionIds: List<UUID> = emptyList(),
    val visibility: InformationRequestAcceptedFactVisibility,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val expiresAt: Instant? = null,
    val supersedesFactId: UUID? = null,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class RevokeInformationRequestAcceptedFactCommand(
    val requestId: UUID,
    val factId: UUID,
    val reasonCode: String,
    val narrative: String? = null,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

enum class InformationRequestAcceptedFactFreshness
{
    CURRENT,
    EXPIRED,
    OUTSIDE_VALID_PERIOD,
}

data class InformationRequestAcceptedFactView(
    val fact: InformationRequestAcceptedFact,
    val revocation: InformationRequestAcceptedFactRevocation?,
    val supersededByFactId: UUID?,
    val freshness: InformationRequestAcceptedFactFreshness,
    val evidenceVersionIds: List<UUID> = emptyList(),
)

data class InformationRequestAcceptedFactOffer(
    val requirementId: UUID,
    val requirementKey: String,
    val fact: InformationRequestAcceptedFactView,
    val reconfirmationRequired: Boolean,
)

data class RecertifyInformationRequestAcceptedFactCommand(
    val requestId: UUID,
    val factId: UUID,
    val requirementId: UUID,
    val assented: Boolean,
    val precondition: CommandPrecondition,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class InformationRequestFactRecertificationView(
    val recertification: InformationRequestFactRecertification,
    val evidenceVersionIds: List<UUID>,
)

data class InformationRequestFactRecertificationResult(
    val view: InformationRequestFactRecertificationView,
    val responseETag: String,
)

data class RecordInformationRequestBusinessDecisionCommand(
    val requestId: UUID,
    val owningProcessKey: String,
    val outcomeCode: String,
    val reasonReference: String? = null,
    val externalReference: String? = null,
    val kind: InformationRequestBusinessDecisionKind,
    val priorDecisionId: UUID? = null,
    val decidedAt: Instant,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

data class InformationRequestBusinessDecisionResult(
    val decision: InformationRequestBusinessDecision,
    val requestETag: String,
)
