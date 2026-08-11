package com.docuhyphen.app.api.service.subscription

/**
 * Lifecycle state of a subscription.
 *
 * Every status keeps existing data readable so that an owner can always recover or export what
 * they already created. Only the ability to create new resources or mutate existing ones is
 * clamped. `PAST_DUE` keeps mutations available while the configured grace period is still
 * running, which is resolved by the caller because it depends on the persisted grace period end.
 */
enum class SubscriptionStatus
{
    TRIALING,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELED;

    /** Reads and exports remain available in every state so owners can recover their data. */
    val permitsReads: Boolean
        get() = true

    /** True when mutations are allowed without consulting a lifecycle boundary. */
    val permitsMutations: Boolean
        get() = this == ACTIVE

    /** True when mutations depend on the persisted grace period still being open. */
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

