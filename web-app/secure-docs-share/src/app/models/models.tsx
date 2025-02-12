// src/app/models/models.ts
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

export interface ResponseError
{
    errorMessage?: string;
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

export interface SignInCompletionRequest
{
    otp?: string;
    email?: string;
}

export interface SignInCompletionResponse
{
    token?: string;
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

export interface SignUpCompletionResponse
{
    message?: string;
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
    company?: Company;
    person?: Person;
}

export interface Company
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
    USER = "USER",
    ADMIN = "ADMIN"
}

export interface AppUser
{
    id: string;
    isActive: boolean;
    createdDate: string;
    email: string;
    password?: string;
    passwordSalt?: string;
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

export interface CompanyRegistrationRequest
{
    name?: string;
    registrationNumber?: string;
}

export interface CompanyRegistrationResponse
{
    company: Company;
}

export interface SharingSessionInitiationRequest
{
    initialShareMessage?: string;
    description?: string;
    recipientEmail?: string;
    sessionName?: string;
    sessionDocuments?: SharingSessionRequestDocumentRequest[];
    requestRecipientSignIn: boolean;
    allowDocumentAddition: boolean;
    allowDocumentDeletion: boolean;
    allowDocumentDownload: boolean;
    allowDocumentUpdate: boolean;
    allowDocumentUpload: boolean;
    participants?: SharingSessionParticipantRequest[];
    status?: SharingSessionStatus;
    rejectionReason?: string;
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
    allowDocumentAddition: boolean;
    allowDocumentDeletion: boolean;
    allowDocumentDownload: boolean;
    allowDocumentUpdate: boolean;
    allowDocumentUpload: boolean;
    status?: SharingSessionStatus;
    rejectionReason?: string;
}

export interface AddSharingSessionDocumentRequest
{
    title?: string;
    documentType?: DocumentType;
    restrictedType?: DocumentType;
}

export interface DownloadSharingSessionDocumentRequest
{
    documentId: string;
    sessionId: string;
}

export interface SharingSessionRequestDocumentRequest
{
    title: string;
    restrictedType?: DocumentType;
    type?: DocumentType;
    restrictType?: DocumentType;
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
    type?: DocumentType;
}

export interface CommentRequest
{
    commentText: string;
    commentedBy: string;
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
    status?: string;
    recipientEmail?: string;
    recipientFirstName?: string;
    recipientLastName?: string;
    recipientCompanyName?: string;
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
    title?: string;
    type?: string;
    restrictedType?: string;
    hash?: string;
}

// src/app/models/DetailedDtos.ts

export interface DocumentCommentDetailedDto
{
    id?: string;
    createdDate?: string;
    text?: string;
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
    comments?: DocumentCommentDetailedDto[];
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
    status?: string;
    documents?: DocumentDetailedDto[];
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
    id?: string;
    createdDate?: string;
    isActive: boolean;
    email: string;
    person?: PersonDetailedDto;
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
    WORD = "WORD",
    PDF = "PDF",
    DOCX = "DOCX",
    DOC = "DOC",
    XLSX = "XLSX",
    PPTX = "PPTX",
    PNG = "PNG",
    JPG = "JPG"
}
export enum ImageType {
    PNG = "PNG",
    JPG = "JPG"
}
