package com.docuhyphen.app.api.service.subscription

enum class PlanFeature
{
    EXCHANGE_CREATE,
    MULTIPLE_PARTICIPANTS,
    BLUEPRINT_USE,
    BLUEPRINT_MANAGE,
    DOCUMENT_LIBRARY_USE,
    DOCUMENT_LIBRARY_MANAGE,
    DOCUMENT_COMMENTS,
    DOCUMENT_VERSION_HISTORY,
    ADVANCED_ACCESS_CONTROLS,
    VARIABLES_AND_SEQUENCES,

    PERSONAL_REMINDERS,
    BUSINESS_FIELDS_AND_SCHEMAS,
    WORKFLOW_AUTOMATION,
    ORGANIZATION_ADMINISTRATION,
    AUDIT_GOVERNANCE,
    IDENTITY_AND_INTEGRATIONS,

    INFORMATION_REQUESTS;

    companion object
    {
        fun fromCodeOrNull(value: String?): PlanFeature?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}
