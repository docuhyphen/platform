/**
 * Centralized role enums and capability helpers matching the backend domain model.
 *
 * Prefer hasCapabilityIn() over hasAppRole() / hasOrganizationRole() for all menu, button,
 * settings tab, and action-control visibility decisions. The capability-based helpers consume
 * the CurrentSessionDto returned by GET /app-user/session, which is computed server-side from
 * live role assignments and reflects the explicitly selected active organization.
 */

import {Capability} from '../../app/models/models.tsx';

export {Capability};

export enum AppRoleName
{
    APP_ADMIN = 'APP_ADMIN',
    APP_AUDITOR = 'APP_AUDITOR',
    APP_SUPPORT = 'APP_SUPPORT',
    APP_USER = 'APP_USER',
}

export const AppRoleDisplayNames: Record<AppRoleName, string> = {
    [AppRoleName.APP_ADMIN]: 'App Admin',
    [AppRoleName.APP_AUDITOR]: 'App Auditor',
    [AppRoleName.APP_SUPPORT]: 'App Support',
    [AppRoleName.APP_USER]: 'App User',
};

export enum ApplicationRoleName
{
    APPLICATION = 'APPLICATION',
}

export enum OrganizationRoleName
{
    ORG_OWNER = 'ORG_OWNER',
    ORG_ADMIN = 'ORG_ADMIN',
    ORG_BILLING_ADMIN = 'ORG_BILLING_ADMIN',
    ORG_USER_MANAGER = 'ORG_USER_MANAGER',
    ORG_AUDITOR = 'ORG_AUDITOR',
    ORG_MEMBER = 'ORG_MEMBER',
    ORG_GUEST = 'ORG_GUEST',
}

export const OrganizationRoleDisplayNames: Record<OrganizationRoleName, string> = {
    [OrganizationRoleName.ORG_OWNER]: 'Organization Owner',
    [OrganizationRoleName.ORG_ADMIN]: 'Organization Admin',
    [OrganizationRoleName.ORG_BILLING_ADMIN]: 'Billing Admin',
    [OrganizationRoleName.ORG_USER_MANAGER]: 'User Manager',
    [OrganizationRoleName.ORG_AUDITOR]: 'Organization Auditor',
    [OrganizationRoleName.ORG_MEMBER]: 'Organization Member',
    [OrganizationRoleName.ORG_GUEST]: 'Organization Guest',
};

export interface ScopedRoleHolder
{
    appRoles?: string[];
    organizationRoles?: string[];
}

export const hasAppRole = (user: ScopedRoleHolder | null | undefined, role: AppRoleName): boolean =>
    user?.appRoles?.includes(role) === true;

export const hasOrganizationRole = (
    user: ScopedRoleHolder | null | undefined,
    role: OrganizationRoleName,
): boolean => user?.organizationRoles?.includes(role) === true;

/**
 * Capability-based visibility helper. Use this in preference to hasAppRole /
 * hasOrganizationRole for all menu, button, and settings tab gates.
 *
 * @param capabilities - the capabilities array from CurrentSessionDto
 * @param cap          - the capability to check
 */
export const hasCapabilityIn = (
    capabilities: Capability[] | null | undefined,
    cap: Capability,
): boolean => capabilities?.includes(cap) === true;

export const isAppAdministrator = (caps: Capability[] | null | undefined): boolean =>
    hasCapabilityIn(caps, Capability.APP_ADMIN);

export const canAdministerOrganization = (caps: Capability[] | null | undefined): boolean =>
    hasCapabilityIn(caps, Capability.ORG_POLICY_MANAGE);

export enum PrincipalGroupRoleName
{
    OWNER = 'OWNER',
    MANAGER = 'MANAGER',
    MEMBER = 'MEMBER',
    OBSERVER = 'OBSERVER',
}

export const PrincipalGroupRoleDisplayNames: Record<PrincipalGroupRoleName, string> = {
    [PrincipalGroupRoleName.OWNER]: 'Owner',
    [PrincipalGroupRoleName.MANAGER]: 'Manager',
    [PrincipalGroupRoleName.MEMBER]: 'Member',
    [PrincipalGroupRoleName.OBSERVER]: 'Observer',
};

/** Roles assignable on a exchange share (access entry). */
export enum ExchangeShareRoleName
{
    OWNER = 'OWNER',
    EDITOR = 'EDITOR',
    REVIEWER = 'REVIEWER',
    SIGNER = 'SIGNER',
    VIEWER = 'VIEWER',
    COMMENTER = 'COMMENTER',
    PARTICIPANT = 'PARTICIPANT',
}

export const ExchangeShareRoleDisplayNames: Record<ExchangeShareRoleName, string> = {
    [ExchangeShareRoleName.OWNER]: 'Owner',
    [ExchangeShareRoleName.EDITOR]: 'Editor',
    [ExchangeShareRoleName.REVIEWER]: 'Reviewer',
    [ExchangeShareRoleName.SIGNER]: 'Signer',
    [ExchangeShareRoleName.VIEWER]: 'Viewer',
    [ExchangeShareRoleName.COMMENTER]: 'Commenter',
    [ExchangeShareRoleName.PARTICIPANT]: 'Participant',
};

/** Roles that the manage-access UI allows assigning to new/existing entries. OWNER is structural and not assignable. */
export const ASSIGNABLE_ROLES: ReadonlySet<ExchangeShareRoleName> = new Set([
    ExchangeShareRoleName.EDITOR,
    ExchangeShareRoleName.REVIEWER,
    ExchangeShareRoleName.SIGNER,
    ExchangeShareRoleName.VIEWER,
    ExchangeShareRoleName.COMMENTER,
    ExchangeShareRoleName.PARTICIPANT,
]);

export const AssignableRoleDisplayNames: Record<string, string> = Object.fromEntries(
    [...ASSIGNABLE_ROLES].map((r) => [r, ExchangeShareRoleDisplayNames[r]]),
);

/** Roles that support participant-level constraints (download gate, watermark, etc.). */
export const CONSTRAINED_ROLES: ReadonlySet<ExchangeShareRoleName> = new Set([
    ExchangeShareRoleName.PARTICIPANT,
    ExchangeShareRoleName.VIEWER,
]);

export enum PrincipalKind
{
    USER = 'USER',
    PARTICIPANT = 'PARTICIPANT',
    PRINCIPAL_GROUP = 'PRINCIPAL_GROUP',
    ORGANIZATION = 'ORGANIZATION',
    APPLICATION = 'APPLICATION',
    SERVICE_ACCOUNT = 'SERVICE_ACCOUNT',
    PUBLIC_LINK = 'PUBLIC_LINK',
}
