package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestAcceptedFactDtoMapper
import com.docuhyphen.app.api.model.informationrequest.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestBusinessDecisionCommand
import com.docuhyphen.app.api.model.informationrequest.RevokeInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.resource.model.PromoteInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestBusinessDecisionRequest
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.RequestAccessContext
import jakarta.ws.rs.core.Response
import java.util.UUID

class InformationRequestAcceptedFactEndpoint(
    private val facts: InformationRequestAcceptedFactService,
    private val factQueries: InformationRequestAcceptedFactQueryService,
    private val decisions: InformationRequestBusinessDecisionService,
)
{
    fun promotedFrom(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(factQueries.promotedFrom(requestId, access).map(InformationRequestAcceptedFactDtoMapper::toDto).toTypedArray()).build()

    fun offers(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(factQueries.offers(requestId, access).map(InformationRequestAcceptedFactDtoMapper::toDto).toTypedArray()).build()

    fun promote(
        requestId: UUID,
        request: PromoteInformationRequestAcceptedFactRequest?,
        access: RequestAccessContext,
        idempotencyKey: String?,
    ): Response
    {
        val body = request ?: throw InformationRequestCommandRequestException("A promotion names its package, item, and purpose")
        val view = facts.promote(
            PromoteInformationRequestAcceptedFactCommand(
                requestId = requestId,
                packageId = body.packageId,
                submissionItemId = body.submissionItemId,
                purposeKey = body.purposeKey,
                policyBasisKey = body.policyBasisKey,
                evidenceVersionIds = body.evidenceVersionIds,
                visibility = body.visibility,
                validFrom = body.validFrom?.toInstant(),
                validTo = body.validTo?.toInstant(),
                expiresAt = body.expiresAt?.toInstant(),
                supersedesFactId = body.supersedesFactId,
                access = access,
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return Response.status(Response.Status.CREATED).entity(InformationRequestAcceptedFactDtoMapper.toDto(view)).build()
    }

    fun revoke(
        requestId: UUID,
        factId: UUID,
        request: RevokeInformationRequestAcceptedFactRequest?,
        access: RequestAccessContext,
        idempotencyKey: String?,
    ): Response
    {
        val body = request ?: throw InformationRequestCommandRequestException("A revocation states its reason")
        val view = facts.revoke(
            RevokeInformationRequestAcceptedFactCommand(
                requestId = requestId,
                factId = factId,
                reasonCode = body.reasonCode,
                narrative = body.narrative,
                access = access,
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return Response.ok(InformationRequestAcceptedFactDtoMapper.toDto(view)).build()
    }

    fun decisions(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(
            decisions.decisions(requestId, access).map { InformationRequestAcceptedFactDtoMapper.toDto(it, access.principal) }.toTypedArray(),
        ).build()

    fun recordDecision(
        requestId: UUID,
        request: RecordInformationRequestBusinessDecisionRequest?,
        access: RequestAccessContext,
        idempotencyKey: String?,
    ): Response
    {
        val body = request ?: throw InformationRequestCommandRequestException("A business decision names its process, outcome, and time")
        val result = decisions.record(
            RecordInformationRequestBusinessDecisionCommand(
                requestId = requestId,
                owningProcessKey = body.owningProcessKey,
                outcomeCode = body.outcomeCode,
                reasonReference = body.reasonReference,
                externalReference = body.externalReference,
                kind = body.kind,
                priorDecisionId = body.priorDecisionId,
                decidedAt = body.decidedAt.toInstant(),
                access = access,
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return Response.status(Response.Status.CREATED)
            .entity(InformationRequestAcceptedFactDtoMapper.toDto(result.decision, access.principal))
            .header("ETag", result.requestETag)
            .build()
    }
}
