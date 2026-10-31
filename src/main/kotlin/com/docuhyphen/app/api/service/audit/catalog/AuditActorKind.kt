package com.docuhyphen.app.api.service.audit.catalog

import com.docuhyphen.app.api.model.entity.PrincipalKind

enum class AuditActorKind
{
    HUMAN,

    APP,

    PARTICIPANT,

    PUBLIC_LINK,

    WORKFLOW,

    SYSTEM,
    ;

    companion object
    {
        fun forPrincipal(kind: PrincipalKind): AuditActorKind = when (kind)
        {
            PrincipalKind.USER -> HUMAN
            PrincipalKind.APPLICATION -> APP
            PrincipalKind.PARTICIPANT -> PARTICIPANT
            PrincipalKind.PUBLIC_LINK -> PUBLIC_LINK
            PrincipalKind.SERVICE_ACCOUNT -> SYSTEM
            PrincipalKind.PRINCIPAL_GROUP, PrincipalKind.ORGANIZATION -> SYSTEM
        }
    }
}
