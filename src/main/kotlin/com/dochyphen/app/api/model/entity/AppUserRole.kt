package com.dochyphen.app.api.model.entity

enum class AppUserRole
{
    APPLICATION,

    APP_USER,

    ORG_ADMIN,

    ORG_GROUP_ADMIN,

    // Regular users in an organization
    ORG_MEMBER,
}