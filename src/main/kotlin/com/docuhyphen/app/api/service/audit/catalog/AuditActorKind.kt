package com.docuhyphen.app.api.service.audit.catalog

enum class AuditActorKind
{
    /** An authenticated human end user (app user). */
    HUMAN,

    /** A registered APPLICATION credential acting on its own behalf (machine-to-machine). */
    APP,

    /** An unauthenticated recipient acting through a no-auth/public share link. */
    PUBLIC_LINK,

    /** The workflow engine acting on its own (no human in the loop for this step). */
    WORKFLOW,

    /** Any other system-initiated action (scheduled jobs, background processors). */
    SYSTEM,
}
