package com.docuhyphen.app.api.model.informationrequest.access

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import java.util.UUID

data class InformationRequestOwnerRef(
    val ownerType: InformationRequestOwnerType,
    val ownerId: UUID,
)
{
    val organizationId: UUID?
        get() = ownerId.takeIf { ownerType == InformationRequestOwnerType.ORGANIZATION }

    val userId: UUID?
        get() = ownerId.takeIf { ownerType == InformationRequestOwnerType.USER }

    fun auditOwner(): AuditOwnerScope = when (ownerType)
    {
        InformationRequestOwnerType.ORGANIZATION -> AuditOwnerScope.Organization(ownerId)
        InformationRequestOwnerType.USER -> AuditOwnerScope.Personal(ownerId)
    }

    companion object
    {
        fun of(request: InformationRequest): InformationRequestOwnerRef = when (request.ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION ->
                InformationRequestOwnerRef(request.ownerType, requireNotNull(request.ownerOrganizationId))
            InformationRequestOwnerType.USER ->
                InformationRequestOwnerRef(request.ownerType, requireNotNull(request.ownerUserId))
        }
    }
}
