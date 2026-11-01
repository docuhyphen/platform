package com.docuhyphen.app.api.service.subscription

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

