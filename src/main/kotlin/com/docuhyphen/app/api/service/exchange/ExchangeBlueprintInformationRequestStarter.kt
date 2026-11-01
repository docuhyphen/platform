package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.exchange.ExchangeBlueprintInformationRequestStart
import com.docuhyphen.app.api.model.informationrequest.creation.CreateInformationRequestFromBlueprintCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionReference
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestBlueprintInstantiationService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyAssignmentService
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateVersionUnavailableException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.util.*

@ApplicationScoped
class ExchangeBlueprintInformationRequestStarter @Inject constructor(
    private val blueprintDefinitionService: BlueprintDefinitionService,
    private val blueprintInstantiationService: InformationRequestBlueprintInstantiationService,
    private val partyAssignments: InformationRequestPartyAssignmentService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
    private val entityManager: EntityManager,
) {
    fun prepare(
        blueprintDefinitionId: UUID?,
        initiatorId: UUID,
        activeOrganizationId: UUID?,
    ): ExchangeBlueprintInformationRequestStart? {
        val blueprintId = blueprintDefinitionId ?: return null
        val version = blueprintDefinitionService.resolveInformationRequestTemplateVersionForInstantiation(blueprintId)
            ?: return null
        if (!heldByExchangeOwner(version, initiatorId, activeOrganizationId)) {
            throw InformationRequestTemplateVersionUnavailableException(
                InformationRequestTemplateVersionUnavailableException.OWNER_MISMATCH,
                "This Blueprint's Information Request Template belongs to a different workspace than this " +
                        "Exchange. Choose an Organization Blueprint or switch workspace.",
            )
        }
        return ExchangeBlueprintInformationRequestStart(blueprintId, version.templateVersionId)
    }

    fun start(
        start: ExchangeBlueprintInformationRequestStart,
        exchangeId: UUID,
        initiatorId: UUID,
        exchangePrincipals: Set<PrincipalRef>,
    ) {
        entityManager.flush()
        val access = accessContextFactory.currentAuthenticated()
        val created = blueprintInstantiationService.createFromBlueprint(
            CreateInformationRequestFromBlueprintCommand(
                blueprintDefinitionId = start.blueprintDefinitionId,
                exchangeId = exchangeId,
                access = access,
                idempotencyKey = "exchange-initiation|$exchangeId",
                participantPrincipals = exchangePrincipals - PrincipalRef.user(initiatorId),
            ),
        )
        entityManager.flush()
        partyAssignments.assign(
            AssignInformationRequestPartyCommand(
                requestId = created.request.id,
                roleKey = InformationRequestShareRoleKey.DECISION_MAKER,
                principal = PrincipalRef.user(initiatorId),
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(created.request)),
                idempotencyKey = "exchange-initiation|$exchangeId|decision-maker",
            ),
        )
    }

    private fun heldByExchangeOwner(
        version: InformationRequestTemplateVersionReference,
        initiatorId: UUID,
        activeOrganizationId: UUID?,
    ): Boolean =
        when (version.ownerScopeKind) {
            InformationRequestTemplateScopeKind.ORGANIZATION ->
                activeOrganizationId != null && version.ownerOrganizationId == activeOrganizationId

            InformationRequestTemplateScopeKind.PERSONAL ->
                activeOrganizationId == null && version.ownerUserId == initiatorId

            InformationRequestTemplateScopeKind.PLATFORM -> false
        }
}
