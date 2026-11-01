package com.docuhyphen.app.api.service.subscription

enum class PlanCode
{
    FREE,
    PERSONAL,
    BUSINESS;

    companion object
    {
        fun fromCodeOrNull(value: String?): PlanCode?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }

        fun fromCode(value: String?): PlanCode
        {
            return fromCodeOrNull(value)
                ?: throw IllegalArgumentException("Unknown subscription plan code: $value")
        }
    }
}

