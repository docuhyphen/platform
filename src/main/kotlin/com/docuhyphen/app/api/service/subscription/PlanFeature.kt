package com.docuhyphen.app.api.service.subscription

/**
 * Commercial product features that a plan either includes or excludes.
 *
 * These answer "does the paying subject own this feature", which is a different question to
 * "is this caller authorized to perform this action". Role capabilities answer the second
 * question and remain entirely separate.
 *
 * [requiresRolloutGrant] marks a capability that is still being built. Owning it commercially is
 * then not enough on its own: the deployment must also have turned it on for that owner, which
 * [FeatureRolloutConfigService] answers. Whether a capability is finished is a property of the
 * code rather than of an environment, which is why it is declared here and cannot be switched off
 * by configuration.
 */
enum class PlanFeature(val requiresRolloutGrant: Boolean = false)
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

    /**
     * Held back from every plan while the capability is built.
     *
     * A plan cannot sell it, so the only way an owner holds it commercially is an explicit
     * platform-administered grant recorded against that owner. That grant alone still reaches
     * nothing: the capability is under controlled release, so the deployment must have turned it on
     * for the same owner as well. Do not add it to a plan or clear its rollout requirement until
     * the capability is complete and released.
     */
    INFORMATION_REQUESTS(requiresRolloutGrant = true);

    companion object
    {
        fun fromCodeOrNull(value: String?): PlanFeature?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}


