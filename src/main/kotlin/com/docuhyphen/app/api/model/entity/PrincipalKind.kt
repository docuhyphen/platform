package com.docuhyphen.app.api.model.entity

/**
 * Canonical kinds of principal that can hold a permission or be the target of a share.
 *
 * - [USER]            : authenticated [AppUser]
 * - [PARTICIPANT]     : persistent [ExternalParticipant] (email-verified, login-less)
 * - [PRINCIPAL_GROUP] : a [PrincipalGroup] (org / personal / shared-project)
 * - [ORGANIZATION]    : an entire [Organization] (rarely used directly, mostly for inheritance)
 * - [SERVICE_ACCOUNT] : a machine identity (see [ServiceAccount])
 * - [APPLICATION]     : a registered external application
 * - [PUBLIC_LINK]     : an anonymous principal backed by a [ShareLink] token
 */
enum class PrincipalKind
{
    USER,
    PARTICIPANT,
    PRINCIPAL_GROUP,
    ORGANIZATION,
    APPLICATION,
    SERVICE_ACCOUNT,
    PUBLIC_LINK,
}

