package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.service.audit.AuditOwnerScope

internal fun informationRequestAuditOwner(request: InformationRequest): AuditOwnerScope = when (request.ownerType)
{
    InformationRequestOwnerType.ORGANIZATION -> AuditOwnerScope.Organization(
        requireNotNull(request.ownerOrganizationId) { "Organization owned Information Request has no owner id" },
    )
    InformationRequestOwnerType.USER -> AuditOwnerScope.Personal(
        requireNotNull(request.ownerUserId) { "User owned Information Request has no owner id" },
    )
}
