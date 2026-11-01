package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

object InformationRequestRespondentAudience {
    fun isOutside(request: InformationRequest?, principal: PrincipalRef, isMember: (UUID, UUID) -> Boolean): Boolean =
        when (principal.kind) {
            PrincipalKind.PARTICIPANT, PrincipalKind.PUBLIC_LINK -> true
            PrincipalKind.USER -> request == null || isOutsideOwner(request, principal.id, isMember)
            else -> false
        }

    private fun isOutsideOwner(request: InformationRequest, userId: UUID, isMember: (UUID, UUID) -> Boolean): Boolean =
        request.ownerOrganizationId?.let { !isMember(userId, it) } ?: (request.ownerUserId != userId)
}
