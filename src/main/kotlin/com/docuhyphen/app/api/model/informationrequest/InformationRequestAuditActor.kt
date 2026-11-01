package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

@ConsistentCopyVisibility
data class InformationRequestAuditActor private constructor(
    val principal: PrincipalRef,
    val sessionRef: String?,
) {
    companion object {
        fun of(access: RequestAccessContext): InformationRequestAuditActor =
            InformationRequestAuditActor(access.principal, access.authorization.sessionRef?.takeUnless { it.isBlank() })

        fun of(principal: PrincipalRef, session: RequestAccessSession): InformationRequestAuditActor =
            InformationRequestAuditActor(principal, session.id.toString())

        fun beforeSession(principal: PrincipalRef): InformationRequestAuditActor =
            InformationRequestAuditActor(principal, null)

        fun system(principal: PrincipalRef): InformationRequestAuditActor =
            InformationRequestAuditActor(principal, null)
    }
}

data class InformationRequestAuditAttribution(
    val actorId: UUID,
    val actorKind: AuditActorKind,
    val actorLabel: String,
    val sessionId: String?,
)
