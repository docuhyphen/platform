package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestDtoMapper
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestHeaderProjectionService @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val standingService: InformationRequestExecutionStandingService,
) {
    fun headerOf(
        request: InformationRequest,
        access: RequestAccessContext,
        conditionEvaluations: List<InformationRequestConditionEvaluationProjection> = emptyList(),
    ): InformationRequestDto {
        val manages = managesRequest(request, access)
        val standing = standingService.standingFor(request)
        return InformationRequestDtoMapper.toDto(
            request,
            manages,
            if (manages) standing else standing.forParticipant(),
            conditionEvaluations,
            revealReplacement = request.supersededByRequestId?.let { viewsRequest(it, access) } == true,
        )
    }

    private fun viewsRequest(requestId: UUID, access: RequestAccessContext): Boolean =
        authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        ) is Decision.Allow

    private fun managesRequest(request: InformationRequest, access: RequestAccessContext): Boolean =
        authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
            ResourceRef.informationRequest(request.id),
            access.authorization,
        ) !is Decision.Deny
}
