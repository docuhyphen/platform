package com.docuhyphen.app.api.model.entity

enum class AppUserRole
{
    PLATFORM_ADMIN,

    APPLICATION,

    APP_USER,

    ORG_ADMIN,

    ORG_GROUP_ADMIN,

    // Regular users in an organization
    ORG_MEMBER,
}