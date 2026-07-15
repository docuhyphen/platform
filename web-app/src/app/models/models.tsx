export enum ExchangeParticipantRole
{
    VIEWER = "VIEWER",
    FULL_ACCESS = "FULL_ACCESS",
    EDITOR = "EDITOR",
    COMMENTER = "COMMENTER",
    OWNER = "OWNER",
    UPLOADER = "UPLOADER",
    DOWNLOADER = "DOWNLOADER"
}

export enum ExchangeParticipantType
{
    GROUP = "GROUP",
    APP_USER = "APP_USER"
}

export interface ResponseError
{
    errorMessage?: string;
    reasonCode?: string;
    retryAfterSeconds?: number;
}

export interface SignInRequest
{
    email?: string;
    password?: string;
}

export interface SignInResponse
{
    message: string;
}

export interface SignInCompletionResponse
{
    token?: string;
    accessToken?: string;
    idToken?: string;
}

export interface SignOutRequest
{
    appUser: AppUser;
}

export interface SignUpCompletionRequest
{
    email?: string;
    otp?: string;
    password?: string;
    confirmationPassword?: string;
}

export interface PasswordResetCompletionRequest
{
    email?: string;
    otp?: string;
    password?: string;
    confirmationPassword?: string;
}

export interface SignUpInitiateRequest
{
    email?: string;
}

export interface SignUpInitiateResponse
{
    message?: string;
}

export interface SignUpRegenerationRequest
{
    email?: string;
}

export interface SignUpRegenerationResponse
{
    message?: string;
}

export interface PasswordResetInitiationRequest
{
    email?: string;
}

export interface PersonRegistrationRequest
{
    firstName?: string;
    lastName?: string;
    idNumber?: string;
    idType?: PersonIDType;
}

export enum PersonIDType
{
    ID_NUMBER = "ID_NUMBER",
    PASSPORT_NUMBER = "PASSPORT_NUMBER",
    SOCIAL_SECURITY = "SOCIAL_SECURITY"
}

export interface Person
{
    id: string;
    createdDate: string;
    firstName?: string;
    lastName?: string;
    identificationNumber?: string;
    personIDType?: PersonIDType;
    contactDetails?: ContactDetails;
}

export interface ContactDetails
{
    id: string;
    createdDate: string;
    phoneNumber?: string;
    email?: string;
    organization?: Organization;
    person?: Person;
}

export interface Organization
{
    id: string;
    isActive: boolean;
    verificationComplete: boolean;
    createdDate: string;
    name: string;
    registrationNumber: string;
    contactDetails?: ContactDetails;
    appUsers: AppUser[];
}

export interface AppUser
{
    id: string;
    isActive: boolean;
    createdDate: string;
    email: string;
    emailVerificationComplete: boolean;
    signInAttempts: number;
    mfaType: MultifactorAuthenticationType;
    person?: Person;
    appRoles: string[];
    organizationRoles: string[];
    isTemporary: boolean;
}

export enum MultifactorAuthenticationType
{
    SMS = "SMS",
    EMAIL = "EMAIL",
    PASSKEY = "PASSKEY",
    PASSWORD_RESET = "PASSWORD_RESET"
}

export enum MultifactorAuthenticationStatus
{
    PENDING = "PENDING",
    COMPLETED = "COMPLETED"
}

export interface PersonRegistrationResponse
{
    person: Person;
}

export interface OrganizationRegistrationRequest
{
    name?: string;
    registrationNumber?: string;
    phoneNumber?: string;
    email?: string;
}

export enum ExchangeRecipientType
{
    APP_USER = 'APP_USER',
    GROUP = 'GROUP',
    EMAIL = 'EMAIL'
}

export interface ExchangeInitiationRequest
{
    initialShareMessage?: string;
    description?: string;
    recipientType?: ExchangeRecipientType;
    recipientEmail?: string;
    recipientFirstName?: string;
    recipientLastName?: string;
    name?: string;
    exchangeDocuments?: ExchangeRequestDocumentRequest[];
    requestRecipientSignIn?: boolean;
    allowDocumentAddition?: boolean;
    allowDocumentDeletion?: boolean;
    allowDocumentDownload?: boolean;
    allowDocumentUpdate?: boolean;
    allowDocumentUpload?: boolean;
    participants?: ExchangeParticipantRequest[];
    status?: ExchangeStatus;
    rejectionReason?: string;
    recipientRoleName?: string;
    recipientConstraintsJson?: string;
    allowedDownloadFormats?: string[];
    variableOverrides?: Record<string, string>;
    schemaDefinitionId?: string;
    fieldValues?: { fieldContractId: string; value: unknown }[];
    schemaAssignmentSource?: SchemaAssignmentSource;
}

export enum ExchangeStatus
{
    INITIATED = "INITIATED",
    ACCEPTED_STARTED = "ACCEPTED_STARTED",
    ENDED = "ENDED",
    REJECTED = "REJECTED",
    RESCINDED = "RESCINDED"
}

export interface UpdateExchangeRequest
{
    initialShareMessage?: string;
    description?: string;
    name?: string;
    requireRecipientSignIn?: boolean;
    allowDocumentAddition?: boolean;
    allowDocumentDeletion?: boolean;
    allowDocumentDownload?: boolean;
    allowDocumentUpdate?: boolean;
    allowDocumentUpload?: boolean;
    noAuthAccessValidityDays?: number;
    status?: ExchangeStatus;
    rejectionReason?: string;
    allowedDownloadFormats?: string[];
}

export interface UpdateNoAuthExchangeRequest
{
    otp?: string;
    status?: ExchangeStatus;
    rejectReason?: string;
    rejectionReason?: string;
}

export interface ExchangeRequestDocumentRequest
{
    title: string;
    restrictedType?: DocumentType | ImageType;
    type?: DocumentType;
    restrictType?: boolean;
    required?: boolean;
    libraryDocumentId?: string;
}

export interface DownloadDocumentsZipRequest
{
    documentIds: string[];
}

export interface ExchangeParticipantRequest
{
    id: string;
    role: ExchangeParticipantRole;
}

export interface UpdateShareExchangeDocumentRequest
{
    title?: string;
    restrictedType?: DocumentType;
    restrictType?: boolean;
    type?: DocumentType;
}

export interface OrganizationBasicDto
{
    id?: string,
    createdDate?: string,
    name: string,
    registrationNumber: string,
    verificationComplete: boolean,
    isActive: boolean,
}

export interface OrganizationDetailedDto
{
    id?: string,
    createdDate?: string,
    name: string,
    registrationNumber: string,
    isActive: boolean,
    contactDetails: ContactDetailsDetailedDto,
    settings?: OrganizationSettingsDto,
}

export interface ExchangeBasicDto
{
    id: string;
    createdDate: string;
    lastActivity: string;
    name?: string;
    initialShareMessage?: string;
    description?: string;
    initiator?: string;
    recipientId?: string;
    status?: ExchangeStatus;
    recipientEmail?: string;
    recipientFirstName?: string;
    recipientLastName?: string;
    recipientOrganizationName?: string;
}

export interface NoAuthExchangeBasicDto
{
    id: string;
    createdDate: string;
    lastActivity: string;
    name?: string;
    initialShareMessage?: string;
    status?: ExchangeStatus;
    recipientEmail?: string;
    recipientFirstName?: string;
    recipientLastName?: string;
    recipientOrganizationName?: string;
    initiatorFirstName?: string;
    initiatorLastName?: string;
    noAuthAccessValidityDays?: number;
    documents?: DocumentBasicDto[];
    allowDocumentDownload?: boolean;
}

export interface ContactDetailsBasicDto
{
    id?: string;
    createdDate?: string;
    email?: string;
    phoneNumber?: string;
}

export interface PersonBasicDto
{
    id?: string;
    createdDate?: string;
    firstName?: string;
    lastName?: string;
    identificationNumber?: string;
    personIDType?: string;
    contactDetailsId?: string;
}

export interface AppUserBasicDto
{
    id?: string;
    createdDate?: string;
    isActive: boolean;
    email: string;
    personId?: string;
}

export interface DocumentBasicDto
{
    id?: string;
    createdDate?: string;
    uploadDate?: string;
    title?: string;
    type?: string;
    restrictedType?: string;
    hash?: string;
}

export interface DocumentCommentDetailedDto
{
    id?: string;
    createdDate?: string;
    text?: string;
    commentedByFirstName?: string,
    commentedByLastName?: string,
    commentedByEmail?: string;
    isInternal: boolean;
}

export interface DocumentDetailedDto
{
    id?: string;
    createdDate?: string;
    uploadDate?: string;
    title?: string;
    type?: string;
    restrictedType?: string;
    hash?: string;
    restrictType?: boolean;
    comments?: DocumentCommentDetailedDto[];
    fileSize?: number;
    lastUploadedByFirstName?: string;
    lastUploadedByLastName?: string;
}

export interface ExchangeDetailedDto
{
    id: string;
    createdDate: string;
    endDate: string;
    lastActivity: string;
    name?: string;
    initialShareMessage?: string;
    description?: string;
    initiator?: AppUserPublicDto;
    recipient?: AppUserPublicDto;
    recipientGroupName?: string;
    status?: ExchangeStatus;
    documents?: DocumentDetailedDto[];
    requestRecipientSignIn?: boolean;
    allowDocumentAddition?: boolean;
    allowDocumentDeletion?: boolean
    allowDocumentDownload?: boolean
    allowDocumentUpdate?: boolean
    allowDocumentUpload?: boolean
    noAuthAccessValidityDays?: number;
    participants?: ExchangeParticipantDetailedDto[];
    watermark?: boolean;
    requireMfa?: boolean;
    allowedDownloadFormats?: string[];   // mirrors backend DTO
}

export interface ExchangeParticipantDetailedDto
{
    id: string;
    participantType: 'GROUP' | 'APP_USER' | string;
    addedDate: string;
    appUserId?: string;
    appUserEmail?: string;
    appUserFirstName?: string;
    appUserLastName?: string;
    organizationGroupId?: string;
    organizationGroupName?: string;
}

export interface ContactDetailsDetailedDto
{
    id?: string;
    createdDate?: string;
    email?: string;
    phoneNumber?: string;
}

export interface PersonDetailedDto
{
    id?: string;
    createdDate?: string;
    firstName?: string;
    lastName?: string;
    identificationNumber?: string;
    personIDType?: string;
    contactDetails?: ContactDetailsDetailedDto;
}

export interface AppUserDetailedDto
{
    id?: string
    createdDate?: string
    isActive: boolean
    email: string
    appRoles: string[]
    organizationRoles: string[]
    person: PersonDetailedDto
    settings: AppUserSettingsDto
    mfaMethod: MfaMethod
    emailMfaFallbackEnabled: boolean
    identityProviders?: IdentityProviderLinkDto[]
    avatarUrl?: string | null
}

/**
 * Safe public view of another user returned by the API. Never includes private settings,
 * notification preferences, identification numbers, or contact details.
 */
export interface PersonPublicDto
{
    firstName?: string;
    lastName?: string;
}

export interface AppUserPublicDto
{
    id: string;
    email: string;
    person?: PersonPublicDto;
    avatarUrl?: string | null;
    isActive: boolean;
    appRoles: string[];
    organizationRoles: string[];
}

export enum Capability
{
    // Exchange lifecycle
    EXCHANGE_INITIATE = 'EXCHANGE_INITIATE',
    EXCHANGE_READ = 'EXCHANGE_READ',
    EXCHANGE_WRITE = 'EXCHANGE_WRITE',
    EXCHANGE_DELETE = 'EXCHANGE_DELETE',
    EXCHANGE_ADMIN = 'EXCHANGE_ADMIN',
    EXCHANGE_OWNER = 'EXCHANGE_OWNER',
    EXCHANGE_SHARE = 'EXCHANGE_SHARE',
    EXCHANGE_RESCIND = 'EXCHANGE_RESCIND',

    // Exchange document
    DOCUMENT_READ = 'DOCUMENT_READ',
    DOCUMENT_DOWNLOAD = 'DOCUMENT_DOWNLOAD',
    DOCUMENT_WRITE = 'DOCUMENT_WRITE',
    DOCUMENT_DELETE = 'DOCUMENT_DELETE',
    DOCUMENT_COMMENT = 'DOCUMENT_COMMENT',
    DOCUMENT_SIGN = 'DOCUMENT_SIGN',

    // Document Library
    DOC_LIBRARY_DISCOVER = 'DOC_LIBRARY_DISCOVER',
    DOC_LIBRARY_READ = 'DOC_LIBRARY_READ',
    DOC_LIBRARY_USE = 'DOC_LIBRARY_USE',
    DOC_LIBRARY_WRITE = 'DOC_LIBRARY_WRITE',
    DOC_LIBRARY_DELETE = 'DOC_LIBRARY_DELETE',
    DOC_LIBRARY_ADMIN = 'DOC_LIBRARY_ADMIN',

    // Blueprint
    BLUEPRINT_DISCOVER = 'BLUEPRINT_DISCOVER',
    BLUEPRINT_READ = 'BLUEPRINT_READ',
    BLUEPRINT_USE = 'BLUEPRINT_USE',
    BLUEPRINT_WRITE = 'BLUEPRINT_WRITE',
    BLUEPRINT_DELETE = 'BLUEPRINT_DELETE',
    BLUEPRINT_CLONE = 'BLUEPRINT_CLONE',
    BLUEPRINT_PUBLISH = 'BLUEPRINT_PUBLISH',
    BLUEPRINT_ADMIN = 'BLUEPRINT_ADMIN',

    // Workflow Definition
    WORKFLOW_DISCOVER = 'WORKFLOW_DISCOVER',
    WORKFLOW_READ = 'WORKFLOW_READ',
    WORKFLOW_USE = 'WORKFLOW_USE',
    WORKFLOW_WRITE = 'WORKFLOW_WRITE',
    WORKFLOW_DELETE = 'WORKFLOW_DELETE',
    WORKFLOW_CLONE = 'WORKFLOW_CLONE',
    WORKFLOW_PUBLISH = 'WORKFLOW_PUBLISH',
    WORKFLOW_ADMIN = 'WORKFLOW_ADMIN',

    // Workflow Webhook
    WEBHOOK_ADMIN = 'WEBHOOK_ADMIN',
    WEBHOOK_DELIVER = 'WEBHOOK_DELIVER',
    WEBHOOK_AUDIT_READ = 'WEBHOOK_AUDIT_READ',

    // Sequence
    SEQUENCE_DISCOVER = 'SEQUENCE_DISCOVER',
    SEQUENCE_READ = 'SEQUENCE_READ',
    SEQUENCE_CONSUME = 'SEQUENCE_CONSUME',
    SEQUENCE_WRITE = 'SEQUENCE_WRITE',
    SEQUENCE_DELETE = 'SEQUENCE_DELETE',
    SEQUENCE_ADMIN = 'SEQUENCE_ADMIN',

    // Variable
    VARIABLE_DISCOVER = 'VARIABLE_DISCOVER',
    VARIABLE_READ = 'VARIABLE_READ',
    VARIABLE_USE = 'VARIABLE_USE',
    VARIABLE_WRITE = 'VARIABLE_WRITE',
    VARIABLE_DELETE = 'VARIABLE_DELETE',
    VARIABLE_ADMIN = 'VARIABLE_ADMIN',

    // Communication
    COMMUNICATION_DISCOVER = 'COMMUNICATION_DISCOVER',
    COMMUNICATION_READ = 'COMMUNICATION_READ',
    COMMUNICATION_USE = 'COMMUNICATION_USE',
    COMMUNICATION_WRITE = 'COMMUNICATION_WRITE',
    COMMUNICATION_DELETE = 'COMMUNICATION_DELETE',
    COMMUNICATION_PUBLISH = 'COMMUNICATION_PUBLISH',
    COMMUNICATION_ADMIN = 'COMMUNICATION_ADMIN',

    // Principal Group
    GROUP_READ = 'GROUP_READ',
    GROUP_EDIT = 'GROUP_EDIT',
    GROUP_ADMIN = 'GROUP_ADMIN',
    GROUP_DELETE = 'GROUP_DELETE',

    // Organization (administrative)
    ORG_MEMBER_MANAGE = 'ORG_MEMBER_MANAGE',
    ORG_POLICY_MANAGE = 'ORG_POLICY_MANAGE',
    ORG_BILLING_MANAGE = 'ORG_BILLING_MANAGE',
    ORG_AUDIT_READ = 'ORG_AUDIT_READ',
    ORG_AUDIT_EXPORT = 'ORG_AUDIT_EXPORT',
    ORG_AUDIT_VIEW_SENSITIVE = 'ORG_AUDIT_VIEW_SENSITIVE',

    // Platform
    APP_ADMIN = 'APP_ADMIN',
    APP_AUDIT_READ = 'APP_AUDIT_READ',
    APP_AUDIT_EXPORT = 'APP_AUDIT_EXPORT',
    APP_SUPPORT = 'APP_SUPPORT',

    // Audit governance (export approval, retention, legal hold, integrity verification)
    AUDIT_EXPORT_APPROVE = 'AUDIT_EXPORT_APPROVE',
    AUDIT_RETENTION_MANAGE = 'AUDIT_RETENTION_MANAGE',
    AUDIT_LEGAL_HOLD_MANAGE = 'AUDIT_LEGAL_HOLD_MANAGE',
    AUDIT_INTEGRITY_VERIFY = 'AUDIT_INTEGRITY_VERIFY',

    // Application Registration
    APP_REG_READ = 'APP_REG_READ',
    APP_REG_ADMIN = 'APP_REG_ADMIN',
}

/**
 * One organization the caller can act within, surfaced on the session so the app can
 * auto-select (single membership) or present a picker (multiple memberships). isPrimary is
 * informational only and does not gate auto-selection.
 */
export interface SessionOrganizationOptionDto
{
    organizationId: string
    name: string
    isPrimary: boolean
    roles: string[]
}

/**
 * Current-session contract returned by GET /app-user/session.
 * The frontend derives menu visibility, action controls, and settings tab gates from
 * capabilities rather than from raw role strings.
 */
export interface CurrentSessionDto
{
    userId: string
    email: string
    appRoles: string[]
    activeOrganizationId: string | null
    organizationRoles: string[]
    capabilities: Capability[]
    availableOrganizations: SessionOrganizationOptionDto[]
    idleTimeoutMinutes: number
}

export type MfaMethod = 'EMAIL' | 'GOOGLE_AUTHENTICATOR' | 'MICROSOFT_AUTHENTICATOR';

export interface MfaConfiguration
{
    method: MfaMethod;
    authenticatorConfigured: boolean;
    emailFallbackEnabled: boolean;
}

export interface AuthenticatorEnrollment
{
    id: string;
    provider: Exclude<MfaMethod, 'EMAIL'>;
    secret: string;
    otpauthUri: string;
    expiresAt: string;
}

// ── OAuth / Multi-IDP Types ──

export enum IdentityProviderType
{
    INTERNAL = "INTERNAL",
    MICROSOFT = "MICROSOFT",
    GOOGLE = "GOOGLE",
}

export interface IdentityProviderLinkDto
{
    provider: string;
    externalEmail: string;
    createdDate: string;
}

export interface SignInLookupRequest
{
    email: string;
    orgId?: string;
}

export interface SignInLookupOrganizationOption
{
    id: string;
    name: string;
}

export interface SignInLookupResponse
{
    authMethod: string;
    redirectUrl?: string;
    outcome?: 'ORG_FOUND' | 'MULTIPLE_ORGS' | 'NO_ORG';
    fallbackAuthMethod?: string;
    organizations?: SignInLookupOrganizationOption[];
    availableProviders?: string[];
}

export interface UserSessionDto
{
    sessionId: string;
    deviceId?: string;
    deviceName?: string;
    ipAddress?: string;
    userAgent?: string;
    createdDate: string;
    lastSeenAt: string;
    expiresAt?: string;
    revokedAt?: string;
    revocationReasonCode?: string;
    isActive?: boolean;
    isCurrent?: boolean;
}

export interface UserSessionListResponse
{
    sessions: UserSessionDto[];
    total: number;
}

export interface OrgMemberCapacityResponse
{
    organizationId: string;
    tierCode: string;
    maxUsers?: number;
    activeUsers: number;
    atCap: boolean;
    nearCap: boolean;
}

export interface OAuthLinkConfirmRequest
{
    linkToken: string;
    password: string;
}

export interface OAuthLinkConfirmResponse
{
    accessToken: string;
    idToken: string;
}

export interface OAuthTokenExchangeResponse
{
    accessToken: string;
    idToken: string;
    isNewUser: boolean;
}

export interface TokenRefreshResponse
{
    accessToken: string;
    idToken: string;
}

export interface LinkProviderInitiateRequest
{
    provider: string;
}

export interface LinkProviderInitiateResponse
{
    redirectUrl: string;
}

export interface SetupPasswordRequest
{
    password: string;
    confirmationPassword: string;
}

export interface DocumentAuditDetailedDto
{
    id?: string;
    timestamp?: string;
    action?: string;
    performedBy?: AppUserPublicDto;
    performedByEmail?: string;
    documentId?: string;
    documentTitle?: string;
}

export enum DocumentType
{
    PDF = "PDF",
    DOCX = "DOCX",
    DOC = "DOC",
    XLSX = "XLSX",
    XLS = "XLS",
    PPTX = "PPTX",
    PPT = "PPT"
}

export enum ImageType
{
    PNG = "PNG",
    JPG = "JPG"
}

export interface SignUpInitiationRequest
{
    email: string
}

export interface SignUpOtpRegenerationRequest
{
    email: string
}

/** Request body for POST /auth/sign-up/email-confirm,  opaque-token flow. */
export interface SignUpEmailConfirmRequest
{
    token: string;
    password: string;
    confirmationPassword: string;
}

export interface SignUpEmailConfirmResponse
{
    message?: string;
}

/** Response from GET /auth/sign-up/email-confirm/{token},  token introspection. */
export interface SignUpEmailConfirmCheckResponse
{
    email: string;
}

export interface SignInOtpRegenerationRequest
{
    mfaSessionId: string
    email: string
}

export interface SignInInitiationRequest
{
    email: string,
    password: string,
}

export interface SignInCompletionRequest
{
    email: string
    otp: string
    mfaSessionId?: string
}

export interface DocumentVersion
{
    id: string;
    version: string;
    fileName: string;
    storagePath: string;
    createdAt: string;
    createdByEmail: string | null;
    createdBy: string | null;
}

export enum NotificationType
{
    NEW_COMMENT = 'NEW_COMMENT',
    NEW_SESSION = 'NEW_SESSION',
    DOCUMENT_ADDED = 'DOCUMENT_ADDED',
    DOCUMENT_UPDATED = 'DOCUMENT_UPDATED',
    EXCHANGE_ENDED = 'EXCHANGE_ENDED',
    EXCHANGE_INITIATED = 'EXCHANGE_INITIATED',
    // New types from Plans 01–05
    WORKFLOW_STEP_ASSIGNED = 'workflow.step_assigned',
    WORKFLOW_ESCALATED = 'workflow.escalated',
    EXCHANGE_ACTIVATED = 'session.activated',
    EXCHANGE_REJECTED = 'session.rejected',
}

export interface NotificationDto
{
    id: string;
    type: string;
    message: string;
    timestamp: string;
    exchangeId?: string | null;
    documentId?: string | null;
    commentId?: string | null;
    userId?: string | null;
    isRead: boolean;
    data: Record<string, string>;
}

export type NotificationPreferenceChannel = "EMAIL" | "IN_APP";

export interface AppUserSettingsDto
{
    id?: string;
    notifyLogin: boolean;
    autoPreviewDocuments: boolean;
    notifyShareStart: boolean;
    notifyShareAccept: boolean;
    notifyShareDecline: boolean;
    notifyShareEnd: boolean;
    notifyDocComment: boolean;
    notifyDocDelete: boolean;
    notifyDocAdd: boolean;
    notifyDocUpload: boolean;
    notifyShareStartChannels?: NotificationPreferenceChannel[];
    notifyShareAcceptChannels?: NotificationPreferenceChannel[];
    notifyShareDeclineChannels?: NotificationPreferenceChannel[];
    notifyShareEndChannels?: NotificationPreferenceChannel[];
    notifyDocCommentChannels?: NotificationPreferenceChannel[];
    notifyDocDeleteChannels?: NotificationPreferenceChannel[];
    notifyDocAddChannels?: NotificationPreferenceChannel[];
    notifyDocUploadChannels?: NotificationPreferenceChannel[];
    theme: "light" | "dark" | "system";
    tourCompleted: boolean;
    documentLibraryView?: 'cards' | 'table';
    blueprintsView?: 'cards' | 'table';
    workflowsView?: 'cards' | 'table';
    sequencesView?: 'cards' | 'table';
    variablesView?: 'cards' | 'table';
    communicationsView?: 'cards' | 'table';
}

export type ViewMode = 'cards' | 'table';

export interface OrganizationSettingsDto
{
    id?: string;
    allowShareWithoutPairing: boolean;
    allowExternalCustomerSharing: boolean;
    allowProfileUpdate: boolean;
    allowEmailUpdate: boolean;
    requireRecipientAcceptance: boolean;
}


export interface OrganizationGroupMemberPermissionDto
{
    allowExchangeAccept: boolean;
    allowExchangeReject: boolean;
    allowExchangeEdit: boolean;
    allowExchangeDelete: boolean;
    allowExchangeEnd: boolean;
    allowDocumentAddition: boolean;
    allowDocumentDeletion: boolean;
    allowDocumentDownload: boolean;
    allowDocumentUpdate: boolean;
    allowDocumentUpload: boolean;
}

export interface OrganizationGroupDetailedDto
{
    id: string;
    createdDate: string;
    isActive: boolean | null;
    name: string | null;
    members: OrganizationGroupMemberDetailedDto[];
}

export interface OrganizationGroupMemberDetailedDto
{
    user: AppUserDetailedDto;
    permissions: OrganizationGroupMemberPermissionDto;
}

export enum LinkStatus
{
    PENDING = "PENDING",
    ACCEPTED = "ACCEPTED",
    REJECTED = "REJECTED"
}

export interface OrganizationExchangeLinkBasicDto
{
    id: string;
    createdDate: string;
    requestingOrganizationName: string;
    requestingOrganizationId: string;
    requestedOrganizationId: string;
    requestedOrganizationName: string;
    requestingMessage?: string;
    status: LinkStatus;
    linkedDate?: string;
    rejectedDate?: string;
    rejectionReason?: string;
}

// ── Workflow DTOs ─────────────────────────────────────────────────────────────

export interface WorkflowDefinitionSummaryDto
{
    id: string;
    name: string;
    summary?: string;
    triggerEvent: string;
    version: number;
    isActive: boolean;
    isPublished: boolean;
    isTemplate: boolean;
    scope: 'APP' | 'ORG';
    generalTags: string[];
    organizationId?: string;
    sourceTemplateId?: string;
    createdAt: string;
}

/** Full definition, including the raw stepsJson DSL blob. */
export interface WorkflowDefinitionDto extends WorkflowDefinitionSummaryDto
{
    description?: string;
    createdByAppUserId?: string;
    stepsJson: string;
}

export interface WorkflowTriggerEventDto
{
    eventName: string;
    description?: string;
    subjectFields: WorkflowSubjectFieldDto[];
    isActive: boolean;
}

export interface WorkflowSubjectFieldDto
{
    name: string;
    type: string;
    description?: string;
    lookupType?: string;
}

export interface WorkflowEntityRefDto
{
    id: string;
    label: string;
    sublabel?: string;
}

export interface WorkflowInstanceSummaryDto
{
    id: string;
    definitionId: string;
    definitionName?: string;
    subjectResourceType?: string;
    subjectResourceId?: string;
    exchangeName?: string;
    status: 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED' | 'ESCALATED' | 'FAILED';
    currentStepIndex: number;
    createdAt: string;
    completedAt?: string;
}

/** Full instance with decoded step timeline. Returned by GET /workflows/instances/{id}. */
export interface WorkflowInstanceDetailDto extends WorkflowInstanceSummaryDto
{
    steps: WorkflowStepInstanceDto[];
    /** Definition version frozen at instance start; never changes on later definition edits. */
    definitionVersion: number;
    /**
     * Raw definition steps_json frozen at instance start. Reproduces the exact builder topology
     * and labels (including step names) so the Exchange diagram matches the definition preview.
     */
    definitionSnapshotJson: string;
    /** Explicitly traversed edges, oldest first. The only source of edge traversal for the diagram. */
    transitions: WorkflowStepTransitionDto[];
    /**
     * Safe machine failure code when status is FAILED (e.g. 'SNAPSHOT_MISSING', 'SNAPSHOT_CORRUPT');
     * absent otherwise. Never carries raw JSON or stack detail.
     */
    failureCode?: string;
}

/**
 * One traversed edge in an instance's execution graph. `fromStepIndex` is null for the START edge
 * (entry into the first step); `toStepIndex` is null for a terminal edge (the instance ends).
 */
export interface WorkflowStepTransitionDto
{
    fromStepIndex: number | null;
    toStepIndex: number | null;
    outcome: 'DEFAULT' | 'APPROVE' | 'REJECT' | 'TRUE' | 'FALSE' | 'FAILED';
}

export interface WorkflowStepInstanceDto
{
    id: string;
    stepIndex: number;
    stepType: string;
    status: string;
    assignees: WorkflowPrincipalRefDto[];
    decisions: WorkflowDecisionEntryDto[];
    dueAt?: string;
    escalatedAt?: string;
    completedAt?: string;
    createdAt: string;
}

export interface WorkflowPrincipalRefDto
{
    kind: string;
    id: string;
    displayName?: string;
    email?: string;
}

export interface WorkflowDecisionEntryDto
{
    principalKind: string;
    principalId: string;
    decision: 'APPROVE' | 'REJECT';
    reason?: string;
    atEpochMillis: number;
    displayName?: string;
    email?: string;
}

// ── Exchange clearance status ─────────────────────────────────────────────────

export type ClearanceStatus = 'NONE' | 'RUNNING' | 'CLEARED' | 'BLOCKED';

export interface PartyClearanceDto
{
    status: ClearanceStatus;
}

export interface ExchangeClearanceStatusDto
{
    myOrg: PartyClearanceDto;
    counterparties: PartyClearanceDto[];
}

// ── Workflow Designer DSL types (mirror of WorkflowSpec Kotlin DSL) ────────────

export type WorkflowStepType = 'APPROVAL' | 'NOTIFICATION' | 'CONDITION' | 'ACTION' | 'WAIT_FOR_COUNTERPARTY_CLEARANCE';
export type AssigneeKind = 'PRINCIPAL' | 'GROUP_ROLE' | 'APP_ROLE' | 'ORGANIZATION_ROLE';
export type QuorumKind = 'ANY' | 'ALL' | 'N_OF_M';
export type EscalationAction = 'ESCALATE' | 'AUTO_REJECT' | 'AUTO_APPROVE';
export type AddonKind = 'REMINDER_BEFORE_DUE' | 'REMINDER_IF_NO_DECISION';
export type WorkflowScopeType = 'APP' | 'ORG' | 'PERSONAL';

export interface AssigneeSpecDraft
{
    kind: AssigneeKind;
    principalKind?: string;   // PRINCIPAL: USER | PARTICIPANT | PRINCIPAL_GROUP
    principalId?: string;     // PRINCIPAL
    groupIdRef?: string;      // GROUP_ROLE: UUID or $subject.<field>
    groupRole?: string;       // GROUP_ROLE: OWNER | MANAGER | MEMBER | OBSERVER
    roleName?: string;        // APP_ROLE | ORGANIZATION_ROLE
    organizationIdRef?: string; // ORGANIZATION_ROLE: literal id or $subject.<field>
}

export interface QuorumSpecDraft
{
    kind: QuorumKind;
    n?: number; // N_OF_M only
}

export interface StepOutcomeSpecDraft
{
    nextStep: string; // "END" or numeric step index as string
    emit?: string;
}

export interface EscalationSpecDraft
{
    afterSlaBreach: EscalationAction;
    escalateTo: AssigneeSpecDraft[];
}

export interface AddonSpecDraft
{
    kind: AddonKind;
    minutesBeforeDue?: number;
    afterMinutes?: number;
    recipientRef: AssigneeSpecDraft;
    communicationId?: string;
    repeatEveryMinutes?: number;
}

// ── Workflow applicability (field-condition gate) ─────────────────────────────

/**
 * One typed field condition in a workflow's applicability gate. References the immutable
 * fieldDefinitionId; value is a canonical literal (string / number / boolean / string[]),
 * omitted for IS_EMPTY / IS_NOT_EMPTY.
 */
export interface WorkflowFieldConditionDraft
{
    fieldDefinitionId: string;
    fieldKey?: string;
    valueType: FieldValueType;
    operator: FieldOperator;
    value?: unknown;
}

export interface WorkflowApplicabilityDraft
{
    fieldConditions: WorkflowFieldConditionDraft[];
}

export interface WorkflowStepSpecDraft
{
    name?: string;
    type: WorkflowStepType;
    assignees: AssigneeSpecDraft[];
    quorum: QuorumSpecDraft;
    slaMinutes?: number;
    escalation?: EscalationSpecDraft;
    onApprove?: StepOutcomeSpecDraft;
    onReject?: StepOutcomeSpecDraft;
    actionHandlerKey?: string;
    communicationId?: string;
    predicateExpression?: string;
    onTrue?: StepOutcomeSpecDraft;
    onFalse?: StepOutcomeSpecDraft;
    addons: AddonSpecDraft[];
}

export interface WorkflowDesignerState
{
    id?: string;
    name: string;
    summary: string;
    generalTags: string[];
    triggerEvent: string;
    isActive: boolean;
    steps: WorkflowStepSpecDraft[];
    applicability?: WorkflowApplicabilityDraft;
}

// ── Blueprint types ───────────────────────────────────────────────────────────

export type BlueprintScope = 'APP' | 'ORG' | 'PERSONAL';

export interface BlueprintDocumentConfig
{
    title: string;
    restrictedType?: string;
    restrictType?: boolean;
    required?: boolean;
    libraryDocumentId?: string;
}

export interface BlueprintRecipientConfiguration
{
    recipientRoleName?: string;
    recipientConstraintsJson?: string;
    defaultRecipientOrgGroupId?: string;
}

export interface BlueprintParticipantConfig
{
    principalId: string;
    principalKind: 'APP_USER' | 'PRINCIPAL_GROUP';
    roleName: string;
}

/**
 * One default field value carried by a blueprint. Keyed by the stable fieldDefinitionId so it
 * survives schema re-publishing; value is the canonical form applied through the creation-time seam.
 */
export interface BlueprintFieldDefaultConfig
{
    fieldDefinitionId: string;
    valueType: FieldValueType;
    value?: unknown;
    displayOrder?: number;
}

// Scalar form-prefill settings only. Document and participant defaults are typed arrays
// on BlueprintDefinitionSummaryDto / the request types, not embedded in this JSON.
export interface BlueprintConfig
{
    name?: string;
    description?: string;
    initialShareMessage?: string;
    requestRecipientSignIn?: boolean;
    allowDocumentAddition?: boolean;
    allowDocumentDeletion?: boolean;
    allowDocumentDownload?: boolean;
    allowDocumentUpdate?: boolean;
    allowDocumentUpload?: boolean;
    allowedDownloadFormats?: string[];
    recipientConfiguration?: BlueprintRecipientConfiguration;
    allowEditOnExchangeStart?: boolean;
}

export interface BlueprintDefinitionSummaryDto
{
    id: string;
    name: string;
    summary?: string;
    scope: BlueprintScope;
    organizationId?: string;
    createdByAppUserId?: string;
    isActive: boolean;
    isPublished: boolean;
    isTemplate: boolean;
    generalTags: string[];
    sourceTemplateId?: string;
    configJson: string;
    schemaDefinitionId?: string;
    exchangeDocuments: BlueprintDocumentConfig[];
    participants: BlueprintParticipantConfig[];
    fieldDefaults?: BlueprintFieldDefaultConfig[];
    createdAt: string;
    updatedAt: string;
}

export interface BlueprintDefinitionDto extends BlueprintDefinitionSummaryDto
{
    description?: string;
}

export interface CreateBlueprintRequest
{
    name: string;
    summary?: string;
    description?: string;
    configJson: string;
    exchangeDocuments?: BlueprintDocumentConfig[];
    participants?: BlueprintParticipantConfig[];
    schemaDefinitionId?: string;
    fieldDefaults?: BlueprintFieldDefaultConfig[];
    generalTags?: string[];
    isActive?: boolean;
    scope?: BlueprintScope;
    isTemplate?: boolean;
}

export interface UpdateBlueprintRequest
{
    name?: string;
    summary?: string;
    description?: string;
    configJson?: string;
    // null/undefined = leave child collection unchanged; a list (incl. empty) replaces it.
    exchangeDocuments?: BlueprintDocumentConfig[];
    participants?: BlueprintParticipantConfig[];
    // When fieldDefaults is provided the schema linkage is also (re)applied from schemaDefinitionId.
    schemaDefinitionId?: string;
    fieldDefaults?: BlueprintFieldDefaultConfig[];
    generalTags?: string[];
}

// ── Variable / Sequence types ─────────────────────────────────────────────────

export type VariableScope = 'ORG' | 'PERSONAL';
export type SequenceResetPeriod = 'NEVER' | 'YEARLY' | 'MONTHLY';

export interface SystemVariableDto
{
    token: string;
    description: string;
    example: string;
}

export interface SequenceDefinitionDto
{
    id: string;
    organizationId: string;
    name: string;
    key: string;
    currentValue: number;
    padWidth: number;
    prefix?: string;
    suffix?: string;
    resetPeriod: SequenceResetPeriod;
    lastResetAt?: string;
    isActive: boolean;
    createdByAppUserId?: string;
    createdAt: string;
    previewValue: string;
}

export interface VariableDefinitionDto
{
    id: string;
    key: string;
    defaultValue?: string;
    scope: VariableScope;
    organizationId?: string;
    createdByAppUserId: string;
    isActive: boolean;
    createdAt: string;
}

export interface AvailableVariablesDto
{
    system: SystemVariableDto[];
    sequences: SequenceDefinitionDto[];
    org: VariableDefinitionDto[];
    personal: VariableDefinitionDto[];
}

export interface CreateSequenceRequest
{
    name: string;
    key: string;
    padWidth?: number;
    prefix?: string;
    suffix?: string;
    resetPeriod?: SequenceResetPeriod;
}

// ── Communication types ───────────────────────────────────────────────────────

export type CommunicationScope = 'PLATFORM' | 'ORG' | 'PERSONAL';

export interface CommunicationSummaryDto
{
    id: string;
    name: string;
    summary?: string;
    scope: CommunicationScope;
    organizationId?: string;
    createdByAppUserId?: string;
    subject: string;
    isActive: boolean;
    isPublished: boolean;
    isTemplate: boolean;
    generalTags: string[];
    sourceTemplateId?: string;
    createdAt: string;
    updatedAt: string;
}

export interface CommunicationDto extends CommunicationSummaryDto
{
    description?: string;
    body: string;
}

export interface CreateCommunicationRequest
{
    name: string;
    summary?: string;
    description?: string;
    subject: string;
    body: string;
    generalTags?: string[];
    scope: CommunicationScope;
    isActive?: boolean;
}

export interface UpdateCommunicationRequest
{
    name?: string;
    summary?: string;
    description?: string;
    subject?: string;
    body?: string;
    generalTags?: string[];
}

export interface RenderedCommunication
{
    subject: string;
    body: string;
}

export interface UpdateSequenceRequest
{
    name?: string;
    padWidth?: number;
    prefix?: string;
    suffix?: string;
    resetPeriod?: SequenceResetPeriod;
    isActive?: boolean;
}

export interface CreateVariableRequest
{
    key: string;
    defaultValue?: string;
    scope: VariableScope;
}

export interface UpdateVariableRequest
{
    defaultValue?: string;
    isActive?: boolean;
}

// ── Document Library types ─────────────────────────────────────────────────────

export type DocumentLibraryScope = 'APP' | 'ORG' | 'PERSONAL';

export interface DocumentLibraryEntrySummaryDto
{
    id: string;
    title: string;
    description?: string;
    scope: DocumentLibraryScope;
    organizationId?: string;
    createdByAppUserId?: string;
    documentType?: string;
    fileName?: string;
    fileSizeBytes?: number;
    isPublished: boolean;
    isActive: boolean;
    hasFile: boolean;
    restrictType?: boolean;
    restrictedType?: string;
    required?: boolean;
    generalTags: string[];
    sourceDocumentId?: string;
    createdAt: string;
    updatedAt: string;
}

export interface DocumentLibraryEntryDto extends DocumentLibraryEntrySummaryDto
{
    contentHash?: string;
}

export interface CreateDocumentLibraryEntryRequest
{
    title: string;
    description?: string;
    generalTags?: string[];
    scope?: DocumentLibraryScope;
    restrictType?: boolean;
    restrictedType?: string;
    required?: boolean;
}

export interface UpdateDocumentLibraryEntryRequest
{
    title?: string;
    description?: string;
    generalTags?: string[];
    restrictType?: boolean;
    restrictedType?: string;
    required?: boolean;
}

// ── Configurable Fields and Business Schema engine ────────────────────────────

export enum FieldScopeKind
{
    PLATFORM = 'PLATFORM',
    ORGANIZATION = 'ORGANIZATION',
}

export enum FieldLifecycleStatus
{
    DRAFT = 'DRAFT',
    PUBLISHED = 'PUBLISHED',
    RETIRED = 'RETIRED',
}

export enum FieldValueType
{
    SHORT_TEXT = 'SHORT_TEXT',
    LONG_TEXT = 'LONG_TEXT',
    BOOLEAN = 'BOOLEAN',
    INTEGER = 'INTEGER',
    DECIMAL = 'DECIMAL',
    DATE = 'DATE',
    DATE_TIME = 'DATE_TIME',
    SINGLE_SELECT = 'SINGLE_SELECT',
    MULTI_SELECT = 'MULTI_SELECT',
}

/** Type-aware operators for workflow field-applicability conditions. Mirrors the Kotlin FieldOperator. */
export enum FieldOperator
{
    EQUALS = 'EQUALS',
    NOT_EQUALS = 'NOT_EQUALS',
    LESS_THAN = 'LESS_THAN',
    LESS_THAN_OR_EQUAL = 'LESS_THAN_OR_EQUAL',
    GREATER_THAN = 'GREATER_THAN',
    GREATER_THAN_OR_EQUAL = 'GREATER_THAN_OR_EQUAL',
    CONTAINS = 'CONTAINS',
    STARTS_WITH = 'STARTS_WITH',
    IN = 'IN',
    NOT_IN = 'NOT_IN',
    IS_EMPTY = 'IS_EMPTY',
    IS_NOT_EMPTY = 'IS_NOT_EMPTY',
}

export enum FieldDataClassification
{    PUBLIC = 'PUBLIC',
    INTERNAL = 'INTERNAL',
    CONFIDENTIAL = 'CONFIDENTIAL',
    RESTRICTED = 'RESTRICTED',
}

export enum SchemaCompatibility
{
    ADDITIVE = 'ADDITIVE',
    COMPATIBLE = 'COMPATIBLE',
    BREAKING = 'BREAKING',
}

export enum SchemaAssignmentSource
{
    MANUAL = 'MANUAL',
    BLUEPRINT = 'BLUEPRINT',
    API = 'API',
    MIGRATION = 'MIGRATION',
}

export enum FieldOperator
{
    EQUALS = 'EQUALS',
    NOT_EQUALS = 'NOT_EQUALS',
    LESS_THAN = 'LESS_THAN',
    LESS_THAN_OR_EQUAL = 'LESS_THAN_OR_EQUAL',
    GREATER_THAN = 'GREATER_THAN',
    GREATER_THAN_OR_EQUAL = 'GREATER_THAN_OR_EQUAL',
    CONTAINS = 'CONTAINS',
    STARTS_WITH = 'STARTS_WITH',
    IN = 'IN',
    NOT_IN = 'NOT_IN',
    IS_EMPTY = 'IS_EMPTY',
    IS_NOT_EMPTY = 'IS_NOT_EMPTY',
}

export interface FieldConstraints
{
    minLength?: number;
    maxLength?: number;
    pattern?: string;
    minValue?: string;
    maxValue?: string;
    scale?: number;
    minSelections?: number;
    maxSelections?: number;
    minDate?: string;
    maxDate?: string;
}

export interface FieldOption
{
    code: string;
    label: string;
    order?: number;
    active?: boolean;
    externalMappings?: Record<string, string>;
}

export interface FieldContractDto
{
    id: string;
    fieldDefinitionId: string;
    contractVersion: number;
    valueType: FieldValueType;
    typeContractVersion: number;
    label: string;
    description?: string;
    helpText?: string;
    constraints: FieldConstraints;
    options: FieldOption[];
    dataClassification: FieldDataClassification;
    isSearchable: boolean;
    isFilterable: boolean;
    isSortable: boolean;
    isReportable: boolean;
    createdAt: string;
}

export interface FieldDefinitionDto
{
    id: string;
    scopeKind: FieldScopeKind;
    scopeOrgId?: string;
    namespace: string;
    fieldKey: string;
    status: FieldLifecycleStatus;
    contractCount: number;
    latestContract?: FieldContractDto;
    createdAt: string;
}

export interface SchemaFieldBindingDto
{
    id: string;
    fieldContractId: string;
    fieldDefinitionId: string;
    namespace: string;
    fieldKey: string;
    label: string;
    valueType: FieldValueType;
    displayOrder: number;
    section?: string;
    isRequired: boolean;
    isReadOnly: boolean;
    defaultValueJson?: string;
    visibility: FieldDataClassification;
    description?: string;
    helpText?: string;
    constraints: FieldConstraints;
    options: FieldOption[];
}

export interface SchemaVersionDto
{
    id: string;
    schemaDefinitionId: string;
    versionNumber: number;
    status: FieldLifecycleStatus;
    compatibility?: SchemaCompatibility;
    bindings: SchemaFieldBindingDto[];
    publishedAt?: string;
    createdAt: string;
}

export interface SchemaDefinitionDto
{
    id: string;
    scopeKind: FieldScopeKind;
    scopeOrgId?: string;
    namespace: string;
    schemaKey: string;
    displayName: string;
    description?: string;
    targetResourceType: string;
    status: FieldLifecycleStatus;
    draftVersion?: SchemaVersionDto;
    latestPublishedVersion?: SchemaVersionDto;
    createdAt: string;
}

export interface ResolvedSchemaViewDto
{
    schemaDefinitionId: string;
    schemaKey: string;
    namespace: string;
    displayName: string;
    schemaVersionId: string;
    versionNumber: number;
    targetResourceType: string;
    scopeKind: FieldScopeKind;
    fields: SchemaFieldBindingDto[];
}

export interface FieldTypeInfoDto
{
    type: FieldValueType;
    supportsOptions: boolean;
    supportedOperators: FieldOperator[];
}

export interface FieldValueDto
{
    fieldContractId: string;
    schemaFieldBindingId?: string;
    namespace: string;
    fieldKey: string;
    label: string;
    valueType: FieldValueType;
    isEmpty: boolean;
    value: unknown;
}

export interface SchemaAssignmentDto
{
    id: string;
    resourceType: string;
    resourceId: string;
    schemaVersionId: string;
    schemaDefinitionId: string;
    schemaKey: string;
    displayName: string;
    versionNumber: number;
    assignmentSource: SchemaAssignmentSource;
    assignedAt: string;
    fields: FieldValueDto[];
}

// ── Audit projection, exports, and integrity ──────────────────────────────────
// Mirrors com.docuhyphen.app.api.model.dto.AuditProjectionDtos / AuditExportDtos exactly.

export interface AuditEventCursorDto
{
    occurredAt: string;
    eventId: string;
}

export interface AuditEventDto
{
    eventId: string;
    category: string;
    eventTypeKey: string;
    outcome: string;
    occurredAt: string;
    recordedAt: string;
    ledgerTime: string;
    streamId: string;
    streamSequence: number;
    actorKind: string;
    actorId?: string;
    actorRole?: string;
    actorLabel?: string;
    organizationId?: string;
    organizationLabel?: string;
    targetType?: string;
    targetId?: string;
    targetLabel?: string;
    reason?: string;
    payload: Record<string, string>;
    eventHash: string;
    prevHash?: string;
}

export interface AuditEventPageDto
{
    items: AuditEventDto[];
    nextCursor: AuditEventCursorDto | null;
}

export interface AuditExportCreateRequestDto
{
    categories: string[];
    occurredAfter: string;
    occurredBefore: string;
    purpose: string;
    caseReference?: string;
    legalBasis?: string;
    downloadLimit?: number;
}

export interface AuditExportDto
{
    exportId: string;
    organizationId?: string;
    requestedByUserId: string;
    requestedAt: string;
    categories: string[];
    occurredAfter: string;
    occurredBefore: string;
    purpose: string;
    caseReference?: string;
    legalBasis?: string;
    status: string;
    requiredApprovals: number;
    approvalCount: number;
    readyAt?: string;
    expiresAt?: string;
    failedAt?: string;
    failureReason?: string;
    revokedAt?: string;
    downloadCount: number;
    downloadLimit?: number;
    eventCount?: number;
    bundleDigest?: string;
    signingKeyId?: string;
}

export interface AuditExportApprovalDto
{
    approvedByUserId: string;
    approvedAt: string;
    note?: string;
}

export interface AuditStreamIntegrityDto
{
    streamId: string;
    chainValid: boolean;
    chainNote: string;
    segmentsChecked: number;
    segmentsValid: number;
    segmentFailureNotes: string[];
}

export interface AuditOrganizationIntegrityDto
{
    organizationId?: string;
    platformOnly: boolean;
    allValid: boolean;
    streams: AuditStreamIntegrityDto[];
}
