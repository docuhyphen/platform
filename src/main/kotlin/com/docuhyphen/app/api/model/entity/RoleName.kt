package com.docuhyphen.app.api.model.entity

/**
 * Canonical role names used in [RoleAssignment.roleName], [Share.roleName],
 * [OrganizationMembership.roleName], and [PrincipalGroupMember.groupRole] (subset).
 *
 * Stored as plain strings so adding new roles never requires a Flyway migration.
 * Capability resolution lives in `service/auth/authz/RoleCapabilities.kt`.
 */
enum class RoleName
{
    // System-layer (scope_type = APP)
    APP_ADMIN,
    APP_AUDITOR,
    APP_SUPPORT,
    END_USER,

    // Org-layer (scope_type = ORG)
    ORG_OWNER,
    ORG_ADMIN,
    ORG_BILLING_ADMIN,
    ORG_USER_MANAGER,
    ORG_AUDITOR,
    ORG_MEMBER,
    ORG_GUEST,

    // Group-layer (scope_type = PRINCIPAL_GROUP) and resource-layer overlap
    OWNER,
    MANAGER,
    MEMBER,
    OBSERVER,

    // Resource-layer (scope_type = RESOURCE, on a Share)
    EDITOR,
    REVIEWER,
    SIGNER,
    VIEWER,
    COMMENTER,
    PARTICIPANT,
}

/** Where a [RoleAssignment] applies. */
enum class RoleScopeType
{
    APP,
    ORG,
    PRINCIPAL_GROUP,
    RESOURCE,
}

