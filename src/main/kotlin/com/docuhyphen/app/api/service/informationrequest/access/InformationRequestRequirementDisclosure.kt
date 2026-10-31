package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestRequirementDisclosure @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val requirementRepository: InformationRequestRequirementRepository,
) {
    fun permitsContent(access: RequestAccessContext, requirementId: UUID): Boolean =
        CONTENT_ACTIONS.any { gate.permitsRequirement(access, it, requirementId) }

    fun requireContent(access: RequestAccessContext, requestId: UUID, requirementId: UUID) {
        if (permitsContent(access, requirementId)) return
        if (CONTENT_ACTIONS.any { awaitsVerification(access, it, requirementId) }) {
            requireRecentVerification(access, requestId, requirementId)
        }
        throw ForbiddenException("Access denied to this Information Request Requirement")
    }

    fun protectedRequirementIds(requestIds: Collection<UUID>): Set<UUID> =
        requirementRepository.findCompartmentedIds(requestIds.toSet())

    fun verifiedFor(access: RequestAccessContext, requestId: UUID, requirementId: UUID): Boolean =
        access.authorization.mfaSatisfied || requirementId !in protectedRequirementIds(listOf(requestId))

    fun awaitsVerification(access: RequestAccessContext, action: Action, requirementId: UUID): Boolean =
        !access.authorization.mfaSatisfied &&
                gate.permitsRequirement(
                    access.copy(authorization = access.authorization.copy(mfaSatisfied = true)),
                    action,
                    requirementId,
                )

    fun requireRecentVerification(access: RequestAccessContext, requestId: UUID, requirementId: UUID) {
        if (verifiedFor(access, requestId, requirementId)) return
        refuseUnverified(access)
    }

    fun refuseUnverified(access: RequestAccessContext): Nothing {
        if (access.principal.kind == PrincipalKind.USER) throw StepUpRequiredException(action = PROTECTED_ITEM_ACTION)
        throw ForbiddenException("This protected Information Request item needs recent verification")
    }

    private companion object {
        val CONTENT_ACTIONS = listOf(
            Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
            Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
            Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT,
        )
        const val PROTECTED_ITEM_ACTION = "work with a protected Information Request item"
    }
}
