package com.docuhyphen.app.api.service.subscription

/**
 * The commercial plans DocuHyphen sells. `FREE` and `PERSONAL` are owned by an individual
 * registered user. `BUSINESS` is owned by a registered organization and is priced per
 * purchased seat.
 */
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

