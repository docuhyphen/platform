package com.docuhyphen.app.api.service.subscription

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

