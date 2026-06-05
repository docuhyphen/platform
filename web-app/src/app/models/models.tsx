export const AppUserRoleDisplayNames = {
    'ORG_ADMIN': 'Organization Admin',
    'ORG_MEMBER': 'Organization Member'
};

export enum SharingSessionParticipantRole
{
    VIEWER = "VIEWER",
    FULL_ACCESS = "FULL_ACCESS",
    EDITOR = "EDITOR",
    COMMENTER = "COMMENTER",
    OWNER = "OWNER",
    UPLOADER = "UPLOADER",
    DOWNLOADER = "DOWNLOADER"
}

export enum SharingSessionParticipantType
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

export enum SharingSessionRecipientType
{
    APP_USER = 'APP_USER',
    GROUP = 'GROUP',
    EMAIL = 'EMAIL'
}

export interface SharingSessionInitiationRequest
{
    initialShareMessage?: string;
    description?: string;
    recipientType?: SharingSessionRecipientType;
    recipientEmail?: string;
    recipientFirstName?: string;
    recipientLastName?: string;
    sessionName?: string;
    sessionDocuments?: SharingSessionRequestDocumentRequest[];
    requestRecipientSignIn?: boolean;
    allowDocumentAddition?: boolean;
    allowDocumentDeletion?: boolean;
    allowDocumentDownload?: boolean;
    allowDocumentUpdate?: boolean;
    allowDocumentUpload?: boolean;
    participants?: SharingSessionParticipantRequest[];
    status?: SharingSessionStatus;
    rejectionReason?: string;
    recipientRoleName?: string;
    recipientConstraintsJson?: string;
    allowedDownloadFormats?: string[];
}

export enum SharingSessionStatus
{
    INITIATED = "INITIATED",
    ACCEPTED_STARTED = "ACCEPTED_STARTED",
    ENDED = "ENDED",
    REJECTED = "REJECTED"
}

export interface UpdateSharingSessionRequest
{
    initialShareMessage?: string;
    description?: string;
    sessionName?: string;
    requireRecipientSignIn?: boolean;
    allowDocumentAddition?: boolean;
    allowDocumentDeletion?: boolean;
    allowDocumentDownload?: boolean;
    allowDocumentUpdate?: boolean;
    allowDocumentUpload?: boolean;
    noAuthAccessValidityDays?: number;
    status?: SharingSessionStatus;
    rejectionReason?: string;
    allowedDownloadFormats?: string[];
}

export interface UpdateNoAuthSharingSessionRequest
{
    otp?: string;
    status?: SharingSessionStatus;
    rejectReason?: string;
    rejectionReason?: string;
}

export interface SharingSessionRequestDocumentRequest
{
    title: string;
    restrictedType?: DocumentType | ImageType;
    type?: DocumentType;
    restrictType?: boolean;
}

export interface DownloadDocumentsZipRequest
{
    documentIds: string[];
}

export interface SharingSessionParticipantRequest
{
    id: string;
    role: SharingSessionParticipantRole;
}

export interface UpdateShareSessionDocumentRequest
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

export interface SharingSessionBasicDto
{
    id: string;
    createdDate: string;
    lastActivity: string;
    sessionName?: string;
    initialShareMessage?: string;
    description?: string;
    initiator?: string;
    recipientId?: string;
    status?: SharingSessionStatus;
    recipientEmail?: string;
    recipientFirstName?: string;
    recipientLastName?: string;
    recipientOrganizationName?: string;
}

export interface NoAuthSharingSessionBasicDto
{
    id: string;
    createdDate: string;
    lastActivity: string;
    sessionName?: string;
    initialShareMessage?: string;
    status?: SharingSessionStatus;
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
}

export interface SharingSessionDetailedDto
{
    id: string;
    createdDate: string;
    endDate: string;
    lastActivity: string;
    sessionName?: string;
    initialShareMessage?: string;
    description?: string;
    initiator?: AppUserDetailedDto;
    recipient?: AppUserDetailedDto;
    status?: SharingSessionStatus;
    documents?: DocumentDetailedDto[];
    requestRecipientSignIn?: boolean;
    allowDocumentAddition?: boolean;
    allowDocumentDeletion?: boolean
    allowDocumentDownload?: boolean
    allowDocumentUpdate?: boolean
    allowDocumentUpload?: boolean
    noAuthAccessValidityDays?: number;
    participants?: SharingSessionParticipantDetailedDto[];
    watermark?: boolean;
    maxViews?: number;
    requireMfa?: boolean;
    allowedDownloadFormats?: string[];   // mirrors backend DTO
}

export interface SharingSessionParticipantDetailedDto
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
    PPT = "PPT",
    PNG = "PNG",
    JPG = "JPG"
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
}

export enum NotificationType
{
    NEW_COMMENT = 'NEW_COMMENT',
    NEW_SESSION = 'NEW_SESSION',
    DOCUMENT_ADDED = 'DOCUMENT_ADDED',
    DOCUMENT_UPDATED = 'DOCUMENT_UPDATED',
    SESSION_ENDED = 'SESSION_ENDED',
    SESSION_INITIATED = 'SESSION_INITIATED',
    // New types from Plans 01–05
    WORKFLOW_STEP_ASSIGNED = 'workflow.step_assigned',
    WORKFLOW_ESCALATED = 'workflow.escalated',
    SESSION_ACTIVATED = 'session.activated',
    SESSION_REJECTED = 'session.rejected',
}

export interface NotificationDto
{
    id: string;
    type: NotificationType;
    message: string;
    timestamp: string;
    sessionId?: string;
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
}

export interface OrganizationSettingsDto
{
    id?: string;
    allowShareWithoutPairing: boolean;
    allowExternalCustomerSharing: boolean;
    allowProfileUpdate: boolean;
    allowEmailUpdate: boolean;
}


export interface OrganizationGroupMemberPermissionDto
{
    allowSessionAccept: boolean;
    allowSessionReject: boolean;
    allowSessionEdit: boolean;
    allowSessionDelete: boolean;
    allowSessionEnd: boolean;
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

export interface OrganizationSharingSessionLinkBasicDto
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