/**
 * Shared TypeScript interfaces mirroring backend DTOs.
 */
import {AppUserDetailedDto} from '../../app/models/models';
import {ExchangeShareRoleName, PrincipalGroupRoleName} from './roles';

// ── Exchange Access ──

export interface ExchangeAccessEntryDto
{
    shareId: string;
    principalKind: string;   // 'USER' | 'GROUP'
    principalId: string;
    displayName?: string;
    roleName: ExchangeShareRoleName;
    source: string;           // 'DIRECT' | 'WORKFLOW' etc.
    status: string;           // 'ACTIVE' | 'REVOKED' etc.
    grantedByAppUserId?: string;
    grantedAt: string;        // ISO timestamp
    expiresAt?: string;
    constraintsJson?: string;
}

export interface GrantExchangeShareRequest
{
    principalKind: string;
    principalId: string;
    roleName: ExchangeShareRoleName;
    constraintsJson?: string;
    expiresAtEpochMillis?: number;
}

export interface UpdateExchangeShareRoleRequest
{
    roleName: ExchangeShareRoleName;
    constraintsJson?: string;
}

// ── Share Constraints ──

export interface ShareConstraints
{
    can_download?: boolean;
    can_reshare?: boolean;
    watermark?: boolean;
    require_mfa?: boolean;
    allowed_download_formats?: string[];   // undefined = no restriction
}

// ── Workflow decisions ──

export interface WorkflowDecisionRequest
{
    decision: 'APPROVE' | 'REJECT';
    reason?: string;
}

export interface WorkflowDecisionResponse
{
    instanceId: string;
    stepInstanceId: string;
    stepStatus: string;
    instanceStatus: string;
}

export interface PendingWorkflowStep
{
    stepInstanceId: string;
    workflowInstanceId: string;
    stepType: string;
    exchangeId?: string;
    name?: string;
    requestedByEmail?: string;
    requestedByName?: string;
    groupName?: string;
    /**
     * Server returns epoch millis. Older client paths (the realtime push
     * fallback) populate `createdAt` with an ISO string when the notification payload
     * carries `timestamp`, kept here for back-compat. Prefer `createdAtEpochMillis`.
     */
    createdAt?: string;
    createdAtEpochMillis?: number;
}

// ── Personal Groups ──

export interface PrincipalGroupDto
{
    id: string;
    createdDate: string;
    isActive: boolean;
    name: string;
    description?: string;
    scope: string;              // 'PERSONAL' | 'ORGANIZATION'
    externallyPublished: boolean;
    ownerAppUserId?: string;
    iconUrl?: string;
    members: PrincipalGroupMemberDto[];
}

export interface PrincipalGroupMemberDto
{
    user?: AppUserDetailedDto;
    groupRole: PrincipalGroupRoleName;
}

export interface CreatePersonalGroupRequest
{
    name: string;
    description?: string;
}

export interface UpdatePersonalGroupRequest
{
    name: string;
    description?: string;
}

export interface AddGroupMembersRequest
{
    members: GroupMemberEntry[];
}

export interface GroupMemberEntry
{
    principalId: string;
    principalKind?: string;     // default 'USER'
    groupRole?: PrincipalGroupRoleName;
}

// ── App Admin ──

export interface AppAdminDto
{
    assignmentId: string;
    appUserId?: string;
    email?: string;
    firstName?: string;
    lastName?: string;
    grantedByAppUserId?: string;
    grantedAt?: string;
}

export interface GrantAppAdminRequest
{
    appUserId: string;
}

/**
 * A single hit from /admin/roles/app-admin-candidates. Used by the App
 * Admins picker (app-admin is a global role, not org-scoped).
 */
export interface AppUserSearchResult
{
    id: string;
    email: string;
    firstName?: string;
    lastName?: string;
    avatarUrl?: string | null;
}

// ── Org Settings ──
// NB: OrganizationSettingsV2Dto was removed; it was never consumed.
// OrganizationDetailsTab uses the legacy `OrganizationSettingsDto` from models.tsx.
