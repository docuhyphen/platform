package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.creation.BlueprintInformationRequestPartyDefault
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyCopyResult
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestPartyMaterializationService @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val support: InformationRequestPartyCommandSupport,
    private val capacity: InformationRequestPartyCapacity,
    private val eligibility: InformationRequestPartyEligibilityService,
    private val trustedRecipients: InformationRequestTrustedRecipientEligibilityService,
) {
    fun materializeBlueprintDefaultParties(
        request: InformationRequest,
        defaults: List<BlueprintInformationRequestPartyDefault>,
        access: RequestAccessContext,
    ): List<InformationRequestParty> =
        defaults
            .distinctBy { it.roleKey to it.principal }
            .filterNot { support.isAlreadyAssigned(request, it.roleKey, it.principal) }
            .map { default ->
                createActingParty(
                    request = request,
                    roleKey = default.roleKey,
                    principal = default.principal,
                    access = access,
                    exchangeRecipientId = admittedRecipientOf(
                        request,
                        default.roleKey,
                        default.principal,
                        access.principal
                    ),
                )
            }

    fun copyEligibleParties(
        request: InformationRequest,
        sourceRequest: InformationRequest,
        sources: List<InformationRequestParty>,
        access: RequestAccessContext,
    ): InformationRequestPartyCopyResult {
        val author = userOf(sourceRequest.createdByPrincipalKind, sourceRequest.createdByPrincipalId)
            ?: sourceRequest.ownerUserId?.let(PrincipalRef::user)
        val copied = mutableListOf<InformationRequestParty>()
        val notCopied = mutableListOf<UUID>()
        sources.forEach { source ->
            val kind = source.principalKind ?: return@forEach
            val id = source.principalId ?: return@forEach
            val principal = PrincipalRef(kind, id)
            if (copied.any { it.roleKey == source.roleKey && it.principalKind == kind && it.principalId == id } ||
                support.isAlreadyAssigned(request, source.roleKey, principal)
            ) {
                return@forEach
            }
            val admitter =
                userOf(source.assignedByPrincipalKind, source.assignedByPrincipalId) ?: author ?: access.principal
            val exchangeRecipientId = try {
                admittedRecipientOf(request, source.roleKey, principal, admitter)
            } catch (_: InformationRequestLifecycleException) {
                notCopied += source.id
                return@forEach
            } catch (_: IllegalArgumentException) {
                notCopied += source.id
                return@forEach
            }
            copied += createActingParty(request, source.roleKey, principal, access, exchangeRecipientId)
        }
        return InformationRequestPartyCopyResult(copied, notCopied)
    }

    fun materializeSubjectParties(
        request: InformationRequest,
        subjectIdentityRefIds: List<UUID>,
        access: RequestAccessContext,
    ): List<InformationRequestParty> {
        if (subjectIdentityRefIds.isEmpty()) return emptyList()
        val now = Timestamp.from(Instant.now())
        val parties = subjectIdentityRefIds.distinct().map { subjectIdentityRefId ->
            support.requireSubjectOwnedByRequest(subjectIdentityRefId, request)
            partyRepository.save(
                InformationRequestParty().apply {
                    informationRequestId = request.id
                    roleKey = InformationRequestShareRoleKey.SUBJECT
                    this.subjectIdentityRefId = subjectIdentityRefId
                    assignedByPrincipalKind = access.principal.kind
                    assignedByPrincipalId = access.principal.id
                    assignedAt = now
                    createdAt = now
                    updatedAt = now
                },
            ).also { recordAssignment(request, it, access) }
        }
        support.advancePartyRevision(request, now)
        return parties
    }

    private fun userOf(kind: PrincipalKind?, id: UUID?): PrincipalRef? =
        id?.takeIf { kind == PrincipalKind.USER }?.let(PrincipalRef::user)

    private fun admittedRecipientOf(
        request: InformationRequest,
        roleKey: InformationRequestShareRoleKey,
        principal: PrincipalRef,
        actor: PrincipalRef,
    ): UUID? {
        support.requireSupportedActingPrincipal(principal)
        support.requireSignedInReviewer(roleKey, principal.kind)
        eligibility.requireEligible(request, actor, principal)
        return support.trustedRecipientOf(request, principal)?.also {
            trustedRecipients.requireAssignable(it, request.exchangeId, principal)
        }
    }

    private fun createActingParty(
        request: InformationRequest,
        roleKey: InformationRequestShareRoleKey,
        principal: PrincipalRef,
        access: RequestAccessContext,
        exchangeRecipientId: UUID?,
    ): InformationRequestParty {
        val assignedBy = access.principal
        val now = Timestamp.from(Instant.now())
        val party = InformationRequestParty().apply {
            informationRequestId = request.id
            this.roleKey = roleKey
            principalKind = principal.kind
            principalId = principal.id
            this.exchangeRecipientId = exchangeRecipientId
            assignedByPrincipalKind = assignedBy.kind
            assignedByPrincipalId = assignedBy.id
            assignedAt = now
            createdAt = now
            updatedAt = now
        }
        return capacity.withReservedSlot(request, party) { consumeSlot ->
            party.shareId = support.grantShare(request, principal, roleKey, assignedBy).id
            val saved = partyRepository.save(party)
            consumeSlot()
            support.advancePartyRevision(request, now)
            recordAssignment(request, saved, access)
            saved
        }
    }

    private fun recordAssignment(
        request: InformationRequest,
        party: InformationRequestParty,
        access: RequestAccessContext
    ) {
        support.recordHistory(
            request,
            party,
            InformationRequestMutation.ASSIGN_PARTY,
            InformationRequestAuditActor.of(access),
            CREATED_WITH_REQUEST,
        )
    }

    private companion object {
        const val CREATED_WITH_REQUEST = "created-with-request"
    }
}
