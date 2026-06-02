/**
 * Shared TypeScript interfaces mirroring backend DTOs for Plans 01–05.
 */
import {AppUserDetailedDto} from '../../app/models/models';

// ── Session Access (Plan 01/02 — share table) ──

export interface SessionAccessEntryDto
{
    shareId: string;
    principalKind: string;   // 'USER' | 'GROUP'
    principalId: string;
    displayName?: string;
    roleName: string;
    source: string;           // 'DIRECT' | 'WORKFLOW' etc.
    status: string;           // 'ACTIVE' | 'REVOKED' etc.
    grantedByAppUserId?: string;
    grantedAt: string;        // ISO timestamp
    expiresAt?: string;
    constraintsJson?: string;
}

export interface GrantSessionShareRequest
{
    principalKind: string;
    principalId: string;
    roleName: string;
    constraintsJson?: string;
    expiresAtEpochMillis?: number;
}

export interface UpdateSessionShareRoleRequest
{
    roleName: string;
    constraintsJson?: string;
}

// ── Share Constraints (Plan 01) ──

export interface ShareConstraints
{
    can_download?: boolean;
    can_reshare?: boolean;
    watermark?: boolean;
    max_views?: number;
    require_mfa?: boolean;
}

// ── Workflow decisions (Plan 01 workflow engine) ──

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
    sessionId?: string;
    sessionName?: string;
    requestedByEmail?: string;
    requestedByName?: string;
    groupName?: string;
    createdAt: string;
}

// ── Personal Groups (Plan 02) ──

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
    members: PrincipalGroupMemberDto[];
}

export interface PrincipalGroupMemberDto
{
    user?: AppUserDetailedDto;
    groupRole: string;
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
    groupRole?: string;         // default 'MEMBER'
}

// ── App Admin (Plan 04) ──

export interface AppAdminDto
{
    assignmentId: string;
    appUserId?: string;
    email?: string;
    grantedByAppUserId?: string;
    grantedAt?: string;
}

export interface GrantAppAdminRequest
{
    appUserId: string;
}

// ── Org Settings (Plan 05) ──

export interface OrganizationSettingsV2Dto
{
    id?: string;
    allowShareWithoutPairing: boolean;
    allowExternalCustomerSharing: boolean;
    allowProfileUpdate: boolean;
    allowEmailUpdate: boolean;
}
