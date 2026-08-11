package com.docuhyphen.app.api.service.subscription

/**
 * How often a paid subscription renews. Null on records that have never been billed, such as
 * the Free plan or a grandfathered account created before billing was wired up.
 */
enum class BillingFrequency
{
    MONTHLY,
    ANNUAL;

    companion object
    {
        fun fromCodeOrNull(value: String?): BillingFrequency?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

