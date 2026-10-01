package com.docuhyphen.app.api.model.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceTransition
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.util.*

data class IssueInformationRequestCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class CancelInformationRequestCommand(
    val requestId: UUID,
    val reasonCode: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class SupersedeInformationRequestCommand(
    val requestId: UUID,
    val supersededByRequestId: UUID,
    val reasonCode: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestLifecycleResult(
    val request: InformationRequest,
    val requestETag: String,
)

data class InformationRequestTransitionHistoryCommand(
    val request: InformationRequest,
    val fromState: InformationRequestState?,
    val toState: InformationRequestState,
    val mutation: InformationRequestMutation,
    val actor: PrincipalRef,
    val reasonCode: String? = null,
    val partyId: UUID? = null,
    val commandReceiptId: UUID? = null,
    val idempotencyKey: String? = null,
    val evidence: InformationRequestEvidenceTransition? = null,
    val details: Map<String, String> = emptyMap(),
)
