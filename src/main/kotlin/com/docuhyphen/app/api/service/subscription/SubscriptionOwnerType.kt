package com.docuhyphen.app.api.service.subscription

/**
 * Identifies which kind of principal owns and pays for a subscription. No-account recipients
 * are deliberately absent: they authenticate against an Exchange access token and never own a
 * subscription or consume a paid seat.
 */
enum class SubscriptionOwnerType
{
    USER,
    ORGANIZATION;

    companion object
    {
        fun fromCodeOrNull(value: String?): SubscriptionOwnerType?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

