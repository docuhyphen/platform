package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.ApplicationRoleName
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName

object RoleCapabilities
{
    private val APP: Map<AppRoleName, Set<Capability>> = mapOf(
        AppRoleName.APP_ADMIN to setOf(
            Capability.APP_ADMIN,
            Capability.APP_AUDIT_READ,
            Capability.APP_AUDIT_EXPORT,
            Capability.APP_REG_READ,
            Capability.APP_REG_ADMIN,
            Capability.AUDIT_INTEGRITY_VERIFY,
            Capability.AUDIT_RETENTION_MANAGE,
            Capability.AUDIT_LEGAL_HOLD_MANAGE,
            Capability.AUDIT_ENGAGEMENT_MANAGE,
            Capability.AUDIT_EXPORT_APPROVE,
        ),
        AppRoleName.APP_AUDITOR to setOf(
            Capability.APP_AUDIT_READ,
            Capability.APP_AUDIT_EXPORT,
            Capability.ORG_AUDIT_READ,
            Capability.APP_REG_READ,
        ),
        AppRoleName.APP_SUPPORT to setOf(
            Capability.APP_SUPPORT,
        ),
        AppRoleName.APP_USER to setOf(
            Capability.EXCHANGE_INITIATE,
        ),
    )

    // The APPLICATION role maps to emptySet() by default.
    // EXCHANGE_INITIATE and other machine capabilities are granted per-application
    // via Application.grantedCapabilitiesJson, resolved in DefaultAuthorizationService.
    private val APPLICATION: Map<ApplicationRoleName, Set<Capability>> = mapOf(
        ApplicationRoleName.APPLICATION to emptySet(),
    )

    private val ORG_OWNER_AND_ADMIN_COMMON: Set<Capability> = setOf(
        Capability.EXCHANGE_INITIATE,
        Capability.ORG_TRUST_READ,
        Capability.ORG_TRUST_REQUEST,
        Capability.ORG_TRUST_DECIDE,
        Capability.ORG_TRUST_POLICY_MANAGE,
        Capability.ORG_TRUST_SUSPEND,
        Capability.EXTERNAL_IDENTITY_RESOLVE,
        Capability.EXTERNAL_GROUP_DISCOVER,
        Capability.GROUP_READ,
        Capability.GROUP_EDIT,
        Capability.GROUP_ADMIN,
        Capability.GROUP_DELETE,
        Capability.DOC_LIBRARY_DISCOVER,
        Capability.DOC_LIBRARY_READ,
        Capability.DOC_LIBRARY_USE,
        Capability.DOC_LIBRARY_WRITE,
        Capability.DOC_LIBRARY_DELETE,
        Capability.DOC_LIBRARY_ADMIN,
        Capability.BLUEPRINT_DISCOVER,
        Capability.BLUEPRINT_READ,
        Capability.BLUEPRINT_USE,
        Capability.BLUEPRINT_WRITE,
        Capability.BLUEPRINT_DELETE,
        Capability.BLUEPRINT_CLONE,
        Capability.BLUEPRINT_PUBLISH,
        Capability.BLUEPRINT_ADMIN,
        Capability.WORKFLOW_DISCOVER,
        Capability.WORKFLOW_READ,
        Capability.WORKFLOW_USE,
        Capability.WORKFLOW_WRITE,
        Capability.WORKFLOW_DELETE,
        Capability.WORKFLOW_CLONE,
        Capability.WORKFLOW_PUBLISH,
        Capability.WORKFLOW_ADMIN,
        Capability.WEBHOOK_ADMIN,
        Capability.WEBHOOK_AUDIT_READ,
        Capability.SEQUENCE_DISCOVER,
        Capability.SEQUENCE_READ,
        Capability.SEQUENCE_CONSUME,
        Capability.SEQUENCE_WRITE,
        Capability.SEQUENCE_DELETE,
        Capability.SEQUENCE_ADMIN,
        Capability.VARIABLE_DISCOVER,
        Capability.VARIABLE_READ,
        Capability.VARIABLE_USE,
        Capability.VARIABLE_WRITE,
        Capability.VARIABLE_DELETE,
        Capability.VARIABLE_ADMIN,
        Capability.COMMUNICATION_DISCOVER,
        Capability.COMMUNICATION_READ,
        Capability.COMMUNICATION_USE,
        Capability.COMMUNICATION_WRITE,
        Capability.COMMUNICATION_DELETE,
        Capability.COMMUNICATION_PUBLISH,
        Capability.COMMUNICATION_ADMIN,
        Capability.APP_REG_READ,
        Capability.ORG_MEMBER_MANAGE,
        Capability.ORG_POLICY_MANAGE,
        Capability.ORG_AUDIT_READ,
        Capability.ORG_AUDIT_EXPORT,
        Capability.ORG_AUDIT_VIEW_SENSITIVE,
        Capability.AUDIT_EXPORT_APPROVE,
        Capability.AUDIT_RETENTION_MANAGE,
        Capability.AUDIT_LEGAL_HOLD_MANAGE,
        Capability.AUDIT_INTEGRITY_VERIFY,
        Capability.AUDIT_ENGAGEMENT_MANAGE,
        Capability.FIELD_SCHEMA_READ,
        Capability.FIELD_SCHEMA_WRITE,
        Capability.FIELD_SCHEMA_PUBLISH,
    )

    private val ORGANIZATION: Map<OrganizationRoleName, Set<Capability>> = mapOf(
        OrganizationRoleName.ORG_OWNER to ORG_OWNER_AND_ADMIN_COMMON + setOf(
            Capability.ORG_BILLING_MANAGE,
        ),
        OrganizationRoleName.ORG_ADMIN to ORG_OWNER_AND_ADMIN_COMMON,
        OrganizationRoleName.ORG_BILLING_ADMIN to setOf(
            Capability.ORG_BILLING_MANAGE,
        ),
        OrganizationRoleName.ORG_USER_MANAGER to setOf(
            Capability.ORG_MEMBER_MANAGE,
            Capability.GROUP_READ,
        ),
        OrganizationRoleName.ORG_AUDITOR to setOf(
            Capability.ORG_AUDIT_READ,
            Capability.ORG_AUDIT_EXPORT,
        ),
        OrganizationRoleName.ORG_MEMBER to setOf(
            Capability.GROUP_READ,
            Capability.EXCHANGE_INITIATE,
            Capability.EXTERNAL_IDENTITY_RESOLVE,
            Capability.EXTERNAL_GROUP_DISCOVER,
            Capability.DOC_LIBRARY_DISCOVER,
            Capability.DOC_LIBRARY_READ,
            Capability.DOC_LIBRARY_USE,
            Capability.BLUEPRINT_DISCOVER,
            Capability.BLUEPRINT_READ,
            Capability.BLUEPRINT_USE,
            Capability.BLUEPRINT_CLONE,
            Capability.WORKFLOW_DISCOVER,
            Capability.WORKFLOW_READ,
            Capability.WORKFLOW_USE,
            Capability.WORKFLOW_CLONE,
            Capability.SEQUENCE_DISCOVER,
            Capability.SEQUENCE_READ,
            Capability.SEQUENCE_CONSUME,
            Capability.VARIABLE_DISCOVER,
            Capability.VARIABLE_USE,
            Capability.COMMUNICATION_DISCOVER,
            Capability.COMMUNICATION_READ,
            Capability.COMMUNICATION_USE,
            Capability.FIELD_SCHEMA_READ,
        ),
        OrganizationRoleName.ORG_GUEST to emptySet(),
    )

    private val PRINCIPAL_GROUP: Map<PrincipalGroupRoleName, Set<Capability>> = mapOf(
        PrincipalGroupRoleName.OWNER to setOf(
            Capability.GROUP_READ,
            Capability.GROUP_EDIT,
            Capability.GROUP_ADMIN,
            Capability.GROUP_DELETE,
        ),
        PrincipalGroupRoleName.MANAGER to setOf(
            Capability.GROUP_READ,
            Capability.GROUP_EDIT,
            Capability.GROUP_ADMIN,
        ),
        PrincipalGroupRoleName.MEMBER to setOf(Capability.GROUP_READ),
        PrincipalGroupRoleName.OBSERVER to setOf(Capability.GROUP_READ),
    )

    private val EXCHANGE_SHARE: Map<ExchangeShareRoleName, Set<Capability>> = mapOf(
        ExchangeShareRoleName.OWNER to setOf(
            Capability.EXCHANGE_OWNER,
            Capability.EXCHANGE_ADMIN,
            Capability.EXCHANGE_WRITE,
            Capability.EXCHANGE_READ,
            Capability.EXCHANGE_SHARE,
            Capability.EXCHANGE_DELETE,
            Capability.EXCHANGE_RESCIND,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_WRITE,
            Capability.DOCUMENT_DELETE,
            Capability.DOCUMENT_COMMENT,
        ),
        ExchangeShareRoleName.EDITOR to setOf(
            Capability.EXCHANGE_ACCEPT,
            Capability.EXCHANGE_READ,
            Capability.EXCHANGE_WRITE,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_WRITE,
            Capability.DOCUMENT_COMMENT,
        ),
        ExchangeShareRoleName.REVIEWER to setOf(
            Capability.EXCHANGE_ACCEPT,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_COMMENT,
        ),
        ExchangeShareRoleName.SIGNER to setOf(
            Capability.EXCHANGE_ACCEPT,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_DOWNLOAD,
            Capability.DOCUMENT_SIGN,
        ),
        ExchangeShareRoleName.VIEWER to setOf(
            Capability.EXCHANGE_ACCEPT,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
        ),
        ExchangeShareRoleName.COMMENTER to setOf(
            Capability.EXCHANGE_ACCEPT,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
            Capability.DOCUMENT_COMMENT,
        ),
        ExchangeShareRoleName.PARTICIPANT to setOf(
            Capability.EXCHANGE_ACCEPT,
            Capability.EXCHANGE_READ,
            Capability.DOCUMENT_READ,
        ),
    )

    fun forAppRole(role: AppRoleName): Set<Capability> = APP.getValue(role)

    fun forApplicationRole(role: ApplicationRoleName): Set<Capability> = APPLICATION.getValue(role)

    fun forOrganizationRole(role: OrganizationRoleName): Set<Capability> = ORGANIZATION.getValue(role)

    fun forPrincipalGroupRole(role: PrincipalGroupRoleName): Set<Capability> = PRINCIPAL_GROUP.getValue(role)

    fun forExchangeShareRole(role: ExchangeShareRoleName): Set<Capability> = EXCHANGE_SHARE.getValue(role)
}
