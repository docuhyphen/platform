import {AppUserPublicDto, ExchangeRecipientSelection} from '../../app/models/models';
import {ExchangeShareRoleName, PrincipalGroupRoleName} from './roles';

export interface ExchangeAccessEntryDto
{
    shareId: string;
    principalKind: string;   // 'USER' | 'GROUP'
    principalId: string;
    displayName?: string;
    roleName: ExchangeShareRoleName;
    source: string;           // 'DIRECT' | 'WORKFLOW' etc.
    status: string;           // 'ACTIVE' | 'REVOKED' etc.
    recipientPurpose?: 'PRIMARY' | 'PARTICIPANT';
    grantedByPrincipalKind?: string;
    grantedByPrincipalId?: string;
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

export interface InviteTrustedParticipantRequest
{
    selection: ExchangeRecipientSelection;
    roleName: ExchangeShareRoleName;
    constraintsJson?: string;
    expiresAtEpochMillis?: number;
}

export interface UpdateExchangeShareRoleRequest
{
    roleName: ExchangeShareRoleName;
    constraintsJson?: string;
}

export interface ShareConstraints
{
    can_download?: boolean;
    can_reshare?: boolean;
    watermark?: boolean;
    require_mfa?: boolean;
    allowed_download_formats?: string[];   // undefined = no restriction
}

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
    createdAt?: string;
    createdAtEpochMillis?: number;
}

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
    user?: AppUserPublicDto;
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

export interface AppUserSearchResult
{
    id: string;
    email: string;
    firstName?: string;
    lastName?: string;
    avatarUrl?: string | null;
}

