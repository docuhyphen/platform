package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import java.util.UUID

object InformationRequestReadAuthorization
{
    fun requireView(authorization: AuthorizationService, requestId: UUID, access: RequestAccessContext)
    {
        if (authorization.authorize(access.principal, Action.INFORMATION_REQUEST_VIEW,
                ResourceRef.informationRequest(requestId), access.authorization) is Decision.Deny)
            throw ForbiddenException("Access denied to view this Information Request")
    }
}
