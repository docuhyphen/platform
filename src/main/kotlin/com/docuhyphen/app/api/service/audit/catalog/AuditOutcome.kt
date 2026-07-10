package com.docuhyphen.app.api.service.audit.catalog

/**
 * Bounded outcome vocabulary for canonical audit capture. Deliberately small and stable: the
 * catalog records what happened at the event-type level, so the outcome only needs to say
 * whether the attempted action succeeded, failed, was denied, or errored.
 */
enum class AuditOutcome
{
    SUCCESS,
    FAILURE,
    DENIED,
    ERROR,
}
