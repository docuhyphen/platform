/**
 * Centralized role enums matching the backend domain model.
 * Replaces any stale hardcoded role strings throughout the UI.
 */

/** Roles assignable to members within a PrincipalGroup (org or personal). */
export enum GroupRole
{
    OWNER = 'OWNER',
    MANAGER = 'MANAGER',
    MEMBER = 'MEMBER',
    OBSERVER = 'OBSERVER',
}

export const GroupRoleDisplayNames: Record<GroupRole, string> = {
    [GroupRole.OWNER]: 'Owner',
    [GroupRole.MANAGER]: 'Manager',
    [GroupRole.MEMBER]: 'Member',
    [GroupRole.OBSERVER]: 'Observer',
};

/** Roles assignable on a exchange share (access entry). */
export enum ExchangeShareRole
{
    OWNER = 'OWNER',
    EDITOR = 'EDITOR',
    REVIEWER = 'REVIEWER',
    SIGNER = 'SIGNER',
    VIEWER = 'VIEWER',
    COMMENTER = 'COMMENTER',
    PARTICIPANT = 'PARTICIPANT',
}

export const ExchangeShareRoleDisplayNames: Record<ExchangeShareRole, string> = {
    [ExchangeShareRole.OWNER]: 'Owner',
    [ExchangeShareRole.EDITOR]: 'Editor',
    [ExchangeShareRole.REVIEWER]: 'Reviewer',
    [ExchangeShareRole.SIGNER]: 'Signer',
    [ExchangeShareRole.VIEWER]: 'Viewer',
    [ExchangeShareRole.COMMENTER]: 'Commenter',
    [ExchangeShareRole.PARTICIPANT]: 'Participant',
};

/** Roles that the manage-access UI allows assigning to new/existing entries. OWNER is structural and not assignable. */
export const ASSIGNABLE_ROLES: ReadonlySet<ExchangeShareRole> = new Set([
    ExchangeShareRole.EDITOR,
    ExchangeShareRole.REVIEWER,
    ExchangeShareRole.SIGNER,
    ExchangeShareRole.VIEWER,
    ExchangeShareRole.COMMENTER,
    ExchangeShareRole.PARTICIPANT,
]);

export const AssignableRoleDisplayNames: Record<string, string> = Object.fromEntries(
    [...ASSIGNABLE_ROLES].map((r) => [r, ExchangeShareRoleDisplayNames[r]]),
);

/** Roles that support participant-level constraints (download gate, watermark, etc.). */
export const CONSTRAINED_ROLES: ReadonlySet<ExchangeShareRole> = new Set([
    ExchangeShareRole.PARTICIPANT,
    ExchangeShareRole.VIEWER,
]);

export enum PrincipalKind
{
    USER = 'USER',
    GROUP = 'GROUP',
}
