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

/** Roles assignable on a sharing-session share (access entry). */
export enum SessionShareRole
{
    OWNER = 'OWNER',
    EDITOR = 'EDITOR',
    REVIEWER = 'REVIEWER',
    SIGNER = 'SIGNER',
    VIEWER = 'VIEWER',
    COMMENTER = 'COMMENTER',
    PARTICIPANT = 'PARTICIPANT',
}

export const SessionShareRoleDisplayNames: Record<SessionShareRole, string> = {
    [SessionShareRole.OWNER]: 'Owner',
    [SessionShareRole.EDITOR]: 'Editor',
    [SessionShareRole.REVIEWER]: 'Reviewer',
    [SessionShareRole.SIGNER]: 'Signer',
    [SessionShareRole.VIEWER]: 'Viewer',
    [SessionShareRole.COMMENTER]: 'Commenter',
    [SessionShareRole.PARTICIPANT]: 'Participant',
};

/** Roles that the manage-access UI allows assigning to new/existing entries. OWNER is structural and not assignable. */
export const ASSIGNABLE_ROLES: ReadonlySet<SessionShareRole> = new Set([
    SessionShareRole.EDITOR,
    SessionShareRole.REVIEWER,
    SessionShareRole.SIGNER,
    SessionShareRole.VIEWER,
    SessionShareRole.COMMENTER,
    SessionShareRole.PARTICIPANT,
]);

export const AssignableRoleDisplayNames: Record<string, string> = Object.fromEntries(
    [...ASSIGNABLE_ROLES].map((r) => [r, SessionShareRoleDisplayNames[r]]),
);

/** Roles that support participant-level constraints (download gate, watermark, etc.). */
export const CONSTRAINED_ROLES: ReadonlySet<SessionShareRole> = new Set([
    SessionShareRole.PARTICIPANT,
    SessionShareRole.VIEWER,
]);

export enum PrincipalKind
{
    USER = 'USER',
    GROUP = 'GROUP',
}
