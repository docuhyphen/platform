package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestTrustedRecipientCandidate
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.organization.OrganizationService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestTrustedRecipientCandidateQueryService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val authorizationService: AuthorizationService,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val shareService: ShareService,
    private val principalDisplayService: PrincipalDisplayService,
    private val organizationService: OrganizationService,
) {
    fun candidates(requestId: UUID, access: RequestAccessContext): List<InformationRequestTrustedRecipientCandidate> {
        InformationRequestReadAuthorization.requireVisibleTarget(authorizationService, requestId, access)
        val request = requestRepository.findById(requestId)
            ?: throw IllegalArgumentException("Information Request not found")
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_MANAGE_PARTIES,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny) {
            throw ForbiddenException("Access denied to manage Information Request parties")
        }
        if (request.ownerType != InformationRequestOwnerType.ORGANIZATION) return emptyList()
        return exchangeRecipientService.findByExchangeId(request.exchangeId)
            .filter { it.selectionType in TRUSTED_SELECTIONS }
            .filter { it.acceptanceStatus == ExchangeRecipientAcceptanceStatus.ACCEPTED }
            .mapNotNull { recipient ->
                shareService.getById(recipient.directShareId)
                    ?.takeIf { it.status == ShareStatus.ACTIVE }
                    ?.let { share -> candidateOf(recipient, PrincipalRef(share.principalKind, share.principalId)) }
            }
    }

    private fun candidateOf(
        recipient: ExchangeRecipient,
        principal: PrincipalRef
    ): InformationRequestTrustedRecipientCandidate {
        val display = principalDisplayService.display(principal)
        return InformationRequestTrustedRecipientCandidate(
            exchangeRecipientId = recipient.id,
            selectionType = recipient.selectionType,
            principal = principal,
            label = display.name ?: display.email ?: UNNAMED_RECIPIENT,
            targetOrganizationId = recipient.targetOrganizationId,
            targetOrganizationName = recipient.targetOrganizationId
                ?.let { runCatching { organizationService.getOrganizationById(it).name }.getOrNull() },
            trustSuspended = exchangeRecipientService.trustSuspended(recipient.id),
        )
    }

    private companion object {
        val TRUSTED_SELECTIONS = setOf(
            ExchangeRecipientSelectionType.TRUSTED_PERSON,
            ExchangeRecipientSelectionType.TRUSTED_GROUP,
        )
        const val UNNAMED_RECIPIENT = "Trusted recipient"
    }
}
