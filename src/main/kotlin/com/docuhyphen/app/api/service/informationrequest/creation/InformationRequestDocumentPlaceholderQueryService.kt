package com.docuhyphen.app.api.service.informationrequest.creation

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.InformationRequestDocumentPlaceholder
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.creation.InformationRequestDocumentPlaceholderRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestDocumentPlaceholderQueryService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val placeholderRepository: InformationRequestDocumentPlaceholderRepository,
    private val authorizationService: AuthorizationService,
) {
    fun listForRequest(requestId: UUID, access: RequestAccessContext): List<InformationRequestDocumentPlaceholder> {
        InformationRequestReadAuthorization.requireVisibleTarget(authorizationService, requestId, access)
        requestRepository.findById(requestId) ?: throw IllegalArgumentException("Information Request not found")
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny) {
            throw ForbiddenException("Access denied to this Information Request's expected documents")
        }
        return placeholderRepository.findForRequest(requestId)
    }
}
