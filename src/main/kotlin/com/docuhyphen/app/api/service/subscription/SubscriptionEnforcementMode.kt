package com.docuhyphen.app.api.service.subscription

/**
 * How strictly commercial plan decisions are applied to a request.
 *
 * `OFF` skips evaluation entirely. `REPORT_ONLY` evaluates every decision and records what
 * would have been refused without changing the outcome of the request, which is how a new
 * environment is validated before anything is actually blocked. `ENFORCE` refuses the request.
 */
enum class SubscriptionEnforcementMode
{
    OFF,
    REPORT_ONLY,
    ENFORCE;

    val evaluatesDecisions: Boolean
        get() = this != OFF

    val refusesDeniedRequests: Boolean
        get() = this == ENFORCE

    companion object
    {
        fun fromCodeOrNull(value: String?): SubscriptionEnforcementMode?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

