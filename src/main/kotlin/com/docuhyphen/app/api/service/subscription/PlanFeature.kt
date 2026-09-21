package com.docuhyphen.app.api.service.subscription

/**
 * Commercial product features that a plan either includes or excludes.
 *
 * These answer "does the paying subject own this feature", which is a different question to
 * "is this caller authorized to perform this action". Role capabilities answer the second
 * question and remain entirely separate.
 *
 */
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

    /**
     * Despite the name, no individual plan grants this and none is intended to.
     *
     * A reminder exists only as an addon on a step of a workflow definition, so it cannot be
     * given to somebody without also giving them workflow authoring. It therefore sits with the
     * organization plan next to [WORKFLOW_AUTOMATION]. Do not add it to an individual plan
     * unless a standalone reminder that cannot execute workflow actions is actually built.
     */
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
