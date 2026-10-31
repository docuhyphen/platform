package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestPartyEligibilityService @Inject constructor(
    private val sharingPolicy: OrganizationExchangePolicyService,
    private val groups: OrganizationGroupService,
    private val appUsers: AppUserService,
    private val participants: ExternalParticipantService,
    private val recipientAddresses: InformationRequestRecipientAddressResolver,
) {
    fun requireEligible(request: InformationRequest, actor: PrincipalRef, principal: PrincipalRef) {
        when (principal.kind) {
            PrincipalKind.USER -> {
                val user = appUsers.getById(principal.id) ?: throw IllegalArgumentException("User not found")
                withinSharingPolicy {
                    sharingPolicy.assertCanShareWithUser(
                        owningOrganizationOf(request),
                        actor.id,
                        user.id.takeUnless { user.isTemporary })
                }
            }

            PrincipalKind.PARTICIPANT -> {
                val participant = participants.findOwned(principal.id, InformationRequestParticipantOwner.of(request))
                    ?: throw IllegalArgumentException("Participant not found")
                requireAddressEligible(request, actor, participant.email)
            }

            PrincipalKind.PRINCIPAL_GROUP -> {
                val group = groups.getById(principal.id.toString()) ?: throw IllegalArgumentException("Group not found")
                withinSharingPolicy {
                    sharingPolicy.assertCanShareWithGroup(
                        owningOrganizationOf(request),
                        actor.id,
                        group
                    )
                }
            }

            else -> Unit
        }
    }

    fun requireUntrustedAddress(request: InformationRequest, email: String) {
        if (owningOrganizationOf(request) == null) return
        if (recipientAddresses.isTrustedPersonAddress(request.exchangeId, email)) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.TRUSTED_RECIPIENT_SELECTION_REQUIRED,
                "This address belongs to a trusted recipient of the Exchange. Choose them from the trusted recipients.",
            )
        }
    }

    fun requireAddressEligible(request: InformationRequest, actor: PrincipalRef, email: String) {
        val account = appUsers.getAppUserByEmail(email.trim())?.takeUnless { it.isTemporary }
        withinSharingPolicy {
            sharingPolicy.assertCanShareWithUser(
                owningOrganizationOf(request),
                actor.id,
                account?.id
            )
        }
    }

    private fun owningOrganizationOf(request: InformationRequest): UUID? =
        request.ownerOrganizationId.takeIf { request.ownerType == InformationRequestOwnerType.ORGANIZATION }

    private fun withinSharingPolicy(check: () -> Unit) {
        try {
            check()
        } catch (refusal: IllegalArgumentException) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED,
                refusal.message ?: "The sharing policy does not permit this party",
            )
        }
    }
}
