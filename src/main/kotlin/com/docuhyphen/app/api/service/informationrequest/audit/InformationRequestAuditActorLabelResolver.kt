package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditAttribution
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestAuditActorLabelResolver @Inject constructor(
    private val principalDisplayService: PrincipalDisplayService,
) {
    fun attribute(actor: InformationRequestAuditActor): InformationRequestAuditAttribution =
        InformationRequestAuditAttribution(
            actorId = actor.principal.id,
            actorKind = kindOf(actor.principal),
            actorLabel = labelOf(actor.principal),
            sessionId = actor.sessionRef,
        )

    fun labelOf(principal: PrincipalRef): String = when (principal.kind) {
        PrincipalKind.USER -> principalDisplayService.userLabel(principal.id) ?: "user:${principal.id}"
        PrincipalKind.PARTICIPANT -> "participant:${principal.id}"
        PrincipalKind.SERVICE_ACCOUNT -> "service-account:${principal.id}"
        PrincipalKind.APPLICATION -> "application:${principal.id}"
        PrincipalKind.PRINCIPAL_GROUP -> "principal-group:${principal.id}"
        PrincipalKind.ORGANIZATION -> "organization:${principal.id}"
        PrincipalKind.PUBLIC_LINK -> "public-link:${principal.id}"
    }

    private fun kindOf(principal: PrincipalRef): AuditActorKind =
        if (principal.kind == PrincipalKind.PARTICIPANT) AuditActorKind.PUBLIC_LINK
        else AuditActorKind.forPrincipal(principal.kind)
}
