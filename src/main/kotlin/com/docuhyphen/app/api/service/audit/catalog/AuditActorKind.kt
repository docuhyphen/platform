package com.docuhyphen.app.api.service.audit.catalog

import com.docuhyphen.app.api.model.entity.PrincipalKind

enum class AuditActorKind
{
    /** An authenticated human end user (app user). */
    HUMAN,

    /** A registered APPLICATION credential acting on its own behalf (machine-to-machine). */
    APP,

    /** An external participant acting as themselves rather than through a registered account. */
    PARTICIPANT,

    /** An unauthenticated recipient acting through a no-auth/public share link. */
    PUBLIC_LINK,

    /** The workflow engine acting on its own (no human in the loop for this step). */
    WORKFLOW,

    /** Any other system-initiated action (scheduled jobs, background processors). */
    SYSTEM,
    ;

    companion object
    {
        /**
         * History-only classification of a canonical authorization principal. This never becomes an
         * authorization decision of its own; it exists so a recorded event says what kind of caller
         * acted without a reader having to resolve the principal.
         *
         * A group and an organization are grant targets rather than callers, so neither can be the
         * acting principal of a request; they classify as system-initiated if one ever reaches here.
         */
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
