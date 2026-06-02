package com.docuhyphen.app.api.model.entity

/**
 * Resource types that can be the target of a [Share] or a [RoleAssignment] with
 * scope_type = RESOURCE.
 */
enum class ResourceType
{
    SHARING_SESSION,
    DOCUMENT,
    PRINCIPAL_GROUP,
}

