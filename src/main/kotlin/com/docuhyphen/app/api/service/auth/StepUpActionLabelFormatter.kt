package com.docuhyphen.app.api.service.auth

import java.util.Locale

object StepUpActionLabelFormatter
{
    private val actionLabels: Map<String, String> = mapOf(
        "ORG_APP_USER_ADD" to "add a new user",
        "ORG_APP_USER_UPDATE" to "update a user",
        "ORG_APP_USER_DELETE" to "remove a user",
        "ORG_UPDATE" to "update your organization",
        "ORG_SETTINGS_UPDATE" to "update organization settings",
        "ORG_GROUP_ADD" to "create a group",
        "ORG_GROUP_UPDATE" to "update a group",
        "ORG_GROUP_DELETE" to "delete a group",
        "PERSONAL_GROUP_DELETE" to "delete a personal group",
        "ORG_SHARE_EXTERNAL_CUSTOMER" to "share with an external customer",
        "ORG_IDP_CONFIG_CREATE" to "add an identity provider",
        "ORG_IDP_CONFIG_UPDATE" to "update an identity provider",
        "ORG_IDP_CONFIG_DELETE" to "remove an identity provider",
        "ORG_AUTH_EXCHANGE_POLICY_UPDATE" to "update the exchange authentication policy",
        "ORG_VARIABLE_CREATE" to "create an organization variable",
        "ORG_VARIABLE_UPDATE" to "update an organization variable",
        "ORG_VARIABLE_DELETE" to "delete an organization variable",
        "PERSONAL_VARIABLE_CREATE" to "create a personal variable",
        "PERSONAL_VARIABLE_UPDATE" to "update a personal variable",
        "PERSONAL_VARIABLE_DELETE" to "delete a personal variable",
        "ORG_SEQUENCE_CREATE" to "create a sequence",
        "ORG_SEQUENCE_UPDATE" to "update a sequence",
        "ORG_SEQUENCE_DELETE" to "delete a sequence",
        "ORG_SEQUENCE_RESET" to "reset a sequence counter",
        "PERSONAL_BLUEPRINT_CREATE" to "create a personal blueprint",
        "PERSONAL_BLUEPRINT_UPDATE" to "update a personal blueprint",
        "PERSONAL_BLUEPRINT_STATUS_UPDATE" to "change a personal blueprint status",
        "PERSONAL_BLUEPRINT_DELETE" to "delete a personal blueprint",
        "PERSONAL_BLUEPRINT_CLONE" to "duplicate a blueprint into your personal collection",
        "ORG_BLUEPRINT_CREATE" to "create an organization blueprint",
        "ORG_BLUEPRINT_UPDATE" to "update an organization blueprint",
        "ORG_BLUEPRINT_STATUS_UPDATE" to "change an organization blueprint status",
        "ORG_BLUEPRINT_PUBLISH_UPDATE" to "change organization blueprint publishing",
        "ORG_BLUEPRINT_DELETE" to "delete an organization blueprint",
        "ORG_BLUEPRINT_CLONE" to "duplicate a blueprint into the organization collection",
        "APP_BLUEPRINT_CREATE" to "create a platform blueprint",
        "APP_BLUEPRINT_UPDATE" to "update a platform blueprint",
        "APP_BLUEPRINT_STATUS_UPDATE" to "change a platform blueprint status",
        "APP_BLUEPRINT_PUBLISH_UPDATE" to "change platform blueprint publishing",
        "APP_BLUEPRINT_DELETE" to "delete a platform blueprint",
        "ORG_IDP_SECRET_ROTATE" to "rotate an identity provider secret",
        "ORG_IDP_SECRET_ROLLBACK" to "roll back an identity provider secret",
        "ORG_IDP_SECRET_ACTIVATE" to "activate an identity provider secret",
        "ORG_IDP_SECRET_DISABLE" to "disable an identity provider secret",
        "ORG_IDP_SECRET_ENABLE" to "enable an identity provider secret",
        "ORG_IDP_SECRET_RETIRE" to "retire an identity provider secret",
        "ORG_IDP_SECRET_ROTATION_RUNBOOK" to "run an identity provider secret rotation",
        "ORG_IDP_SECRET_ROTATION_PREVIEW" to "preview an identity provider secret rotation",
        "PLATFORM_ORG_SUBSCRIPTION_POLICY_UPSERT" to "update an organization subscription policy",
        "PLATFORM_ORG_SUBSCRIPTION_POLICY_DELETE" to "delete an organization subscription policy",
        "PLATFORM_ORG_FEATURE_ENTITLEMENTS_UPDATE" to "update organization feature entitlements",
        "PLATFORM_ORGANIZATION_STATUS_UPDATE" to "update an organization account status",
        "WORKFLOW_DEFINITION_SAVE" to "save this workflow",
        "ORG_WORKFLOW_DEFINITION_DELETE" to "delete a workflow",
    )

    private val leadingScopes = setOf("APP", "ORG", "PERSONAL", "PLATFORM")

    private val trailingVerbs = mapOf(
        "ADD" to "add",
        "CREATE" to "create",
        "UPDATE" to "update",
        "DELETE" to "delete",
        "REMOVE" to "remove",
        "SAVE" to "save",
        "RESET" to "reset",
        "CLONE" to "duplicate",
        "PUBLISH" to "publish",
        "UPSERT" to "update",
        "ROTATE" to "rotate",
        "ROLLBACK" to "roll back",
        "ACTIVATE" to "activate",
        "DISABLE" to "disable",
        "ENABLE" to "enable",
        "RETIRE" to "retire",
        "VIEW" to "view",
        "LIST" to "view",
    )

    fun labelFor(action: String?): String?
    {
        val trimmed = action?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val key = trimmed.uppercase(Locale.ROOT)
        actionLabels[key]?.let { return it }
        if (!isEnumLike(trimmed))
        {
            return trimmed
        }

        return fallbackLabel(key)
    }

    private fun isEnumLike(value: String): Boolean
    {
        return "_" in value || value == value.uppercase(Locale.ROOT)
    }

    private fun fallbackLabel(key: String): String
    {
        val tokens = key
            .split("_")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .dropWhile { it in leadingScopes }

        if (tokens.isEmpty())
        {
            return "complete this action"
        }

        val verb = trailingVerbs[tokens.last()]
        val nounTokens = if (verb == null) tokens else tokens.dropLast(1)
        val noun = nounTokens.joinToString(" ") { it.lowercase(Locale.ROOT) }

        return when
        {
            verb == null -> noun.replaceFirstChar { it.titlecase(Locale.ROOT) }
            noun.isBlank() -> verb
            else -> "$verb $noun"
        }
    }
}
