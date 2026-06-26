export const AppUserRoleDisplayNames = {
    'ORG_ADMIN': 'Organization Admin',
    'ORG_MEMBER': 'Organization Member'
};

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

export enum AppUserRole
{
    APPLICATION = "APPLICATION",

    APP_USER = "APP_USER",

    ORG_ADMIN = "ORG_ADMIN",

    ORG_GROUP_ADMIN = "ORG_GROUP_ADMIN",

    ORG_MEMBER = "ORG_MEMBER",
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
    role: AppUserRole;
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
}

export enum ExchangeStatus
{
    INITIATED = "INITIATED",
    ACCEPTED_STARTED = "ACCEPTED_STARTED",
    ENDED = "ENDED",
    REJECTED = "REJECTED"
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
    commentedByEmail?: string
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
    initiator?: AppUserDetailedDto;
    recipient?: AppUserDetailedDto;
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
    maxViews?: number;
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
    role: AppUserRole
    person: PersonDetailedDto
    settings: AppUserSettingsDto
    identityProviders?: IdentityProviderLinkDto[]
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
    performedBy?: AppUserDetailedDto;
    performedByEmail?: string;
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
    type: NotificationType;
    message: string;
    timestamp: string;
    exchangeId?: string;
    documentId?: string;
    commentId?: string;
    userId?: string;
    isRead: boolean;
    data: Record<string, string>;
}

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
    theme: "light" | "dark" | "system";
    tourCompleted: boolean;
}

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
    status: 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED' | 'ESCALATED';
    currentStepIndex: number;
    createdAt: string;
    completedAt?: string;
}

/** Full instance with decoded step timeline. Returned by GET /workflows/instances/{id}. */
export interface WorkflowInstanceDetailDto extends WorkflowInstanceSummaryDto
{
    steps: WorkflowStepInstanceDto[];
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
export type AssigneeKind = 'PRINCIPAL' | 'GROUP_ROLE' | 'ROLE';
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
    roleName?: string;        // ROLE | GROUP_ROLE
    scopeType?: WorkflowScopeType; // ROLE
    scopeIdRef?: string;      // ROLE: literal id or $subject.<field>
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
    exchangeDocuments: BlueprintDocumentConfig[];
    participants: BlueprintParticipantConfig[];
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
