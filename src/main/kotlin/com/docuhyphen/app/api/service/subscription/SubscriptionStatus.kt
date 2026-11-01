package com.docuhyphen.app.api.service.subscription

enum class SubscriptionStatus
{
    TRIALING,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELED;

    val permitsReads: Boolean
        get() = true

    val permitsMutations: Boolean
        get() = this == ACTIVE

    val permitsMutationsWhileInGracePeriod: Boolean
        get() = this == PAST_DUE

    companion object
    {
        fun fromCodeOrNull(value: String?): SubscriptionStatus?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }

        fun fromCode(value: String?): SubscriptionStatus
        {
            return fromCodeOrNull(value)
                ?: throw IllegalArgumentException("Unknown subscription status: $value")
        }
    }
}

