package com.docuhyphen.app.api.model.entity

enum class AppRoleName
{
    APP_ADMIN,
    APP_AUDITOR,
    APP_SUPPORT,
    APP_USER,
}

enum class ApplicationRoleName
{
    APPLICATION,
}

enum class OrganizationRoleName
{
    ORG_OWNER,
    ORG_ADMIN,
    ORG_BILLING_ADMIN,
    ORG_USER_MANAGER,
    ORG_AUDITOR,
    ORG_MEMBER,
    ORG_GUEST,
}

enum class PrincipalGroupRoleName
{
    OWNER,
    MANAGER,
    MEMBER,
    OBSERVER,
}

enum class ExchangeShareRoleName
{
    OWNER,
    EDITOR,
    REVIEWER,
    SIGNER,
    VIEWER,
    COMMENTER,
    PARTICIPANT,
}

enum class InformationRequestShareRoleKey
{
    SUBJECT,
    CONTRIBUTOR,
    PREPARER,
    ATTESTOR,
    REVIEWER,
    DECISION_MAKER,
}
