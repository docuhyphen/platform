@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.model.notification

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.workflow.AssigneeSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("kind")
sealed class NotifAssigneeSpec
{
    @Serializable
    @kotlinx.serialization.SerialName("EVENT_PAYLOAD")
    data class EventPayload(val field: String) : NotifAssigneeSpec()

    @Serializable
    @kotlinx.serialization.SerialName("PRINCIPAL")
    data class Principal(
        val principalKind: PrincipalKind,
        val principalId: String,
    ) : NotifAssigneeSpec()

    @Serializable
    @kotlinx.serialization.SerialName("GROUP_ROLE")
    data class GroupRoleAssignees(
        val groupIdRef: String,
        val groupRole: com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName,
    ) : NotifAssigneeSpec()

    @Serializable
    @kotlinx.serialization.SerialName("APP_ROLE")
    data class AppRoleAssignees(
        val roleName: com.docuhyphen.app.api.model.entity.AppRoleName,
    ) : NotifAssigneeSpec()

    @Serializable
    @kotlinx.serialization.SerialName("ORGANIZATION_ROLE")
    data class OrganizationRoleAssignees(
        val roleName: com.docuhyphen.app.api.model.entity.OrganizationRoleName,
        val organizationIdRef: String,
    ) : NotifAssigneeSpec()

    fun toWorkflowSpec(): AssigneeSpec = when (this)
    {
        is Principal -> AssigneeSpec.Principal(principalKind, principalId)
        is GroupRoleAssignees -> AssigneeSpec.GroupRoleAssignees(groupIdRef, groupRole)
        is AppRoleAssignees -> AssigneeSpec.AppRoleAssignees(roleName)
        is OrganizationRoleAssignees ->
            AssigneeSpec.OrganizationRoleAssignees(roleName, organizationIdRef)

        is EventPayload -> error("EVENT_PAYLOAD must be handled by NotificationRuleEngine, not delegated")
    }
}
