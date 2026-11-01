package com.docuhyphen.app.api.service.subscription

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

