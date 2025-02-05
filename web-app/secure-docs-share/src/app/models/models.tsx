export enum AppUserRole
{
    USER = 'USER',
    ADMIN = 'ADMIN'
}

export enum MultifactorAuthenticationType
{
    SMS = 'SMS',
    EMAIL = 'EMAIL',
    PASSKEY = 'PASSKEY',
    PASSWORD_RESET = 'PASSWORD_RESET'
}

export enum MultifactorAuthenticationStatus
{
    PENDING = 'PENDING',
    COMPLETED = 'COMPLETED'
}

export enum PersonIDType
{
    PASSPORT = 'PASSPORT',
    NATIONAL_ID = 'NATIONAL_ID',
    DRIVER_LICENSE = 'DRIVER_LICENSE'
}

export enum SharingSessionStatus
{
    INITIATED = "INITIATED",
    ACCEPTED_STARTED = "ACCEPTED_STARTED",
    COMPLETED = "COMPLETED",
    REJECTED = "REJECTED",
}

export enum DocumentEncryptionMode
{
    INTERNAL = "INTERNAL",
    END_TO_END = "END_TO_END",
}

export enum DocumentType
{
    WORD = 'WORD',
    PDF = 'PDF',
    DOCX = 'DOCX',
    DOC = 'DOC',
    XLSX = 'XLSX',
    PPTX = 'PPTX'
}

export enum ImageType
{
    PNG = 'PNG',
    JPG = 'JPG'
}

export enum RequiredDocumentType
{
    PDF = "PDF",
    WORD = "WORD",
    IMAGE = "IMAGE",
}

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

export interface AppUser
{
    id: string;
    isActive: boolean;
    createdDate: string; // ISO format for Timestamp
    email: string;
    password: string;
    passwordSalt: string;
    emailVerificationComplete: boolean;
    signInAttempts: number;
    mfaType: MultifactorAuthenticationType;
    person?: Person;
    role: AppUserRole;
}

export interface Person
{
    id: string;
    createdDate: string; // ISO format for Timestamp
    firstName?: string;
    lastName?: string;
    identificationNumber?: string;
    personIDType?: PersonIDType;
    contactDetails?: ContactDetails;
}

export interface ContactDetails
{
    id: string;
    createdDate: string; // ISO format for Timestamp
    phoneNumber: string;
    email: string;
}

export interface Company
{
    registrationComplete: boolean;
    id: string;
    name: string;
    address?: string;
    // Add more fields as needed
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

export interface PasswordResetRequest
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
    receiverEmail?: string;
    sessionName?: string;
    sessionDocuments?: SharingSessionRequestDocument[];
    requestReceiverSignIn: boolean;
    allowDocumentAddition: boolean;
    allowDocumentDeletion: boolean;
    allowDocumentDownload: boolean;
    allowDocumentUpdate: boolean;
    allowDocumentUpload: boolean;
    participants?: SharingSessionParticipant[];
    status?: SharingSessionStatus;
    rejectionReason?: string;
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
    documentId?: string;
    documentType?: DocumentType | ImageType;
    restrictedType?: DocumentType | ImageType;
}

export interface UploadShareSessionDocumentRequest
{
    file: File;
    documentId: string;
    performedBy: string;
    encryptionMode: DocumentEncryptionMode;
}

export interface DownloadShareSessionDocumentRequest
{
    documentId: string;
    sessionId: string;
}

export interface SharingSessionRequestDocument
{
    title: string;
    restrictedType?: DocumentType | ImageType;
    type?: DocumentType | ImageType;
    restrictType?: boolean;
}

export interface SharingSessionParticipant
{
    id: string;
    role: SharingSessionParticipantRole;
}

export interface UpdateShareSessionDocumentRequest
{
    title?: string;
    restrictedType?: DocumentType | ImageType;
    type?: DocumentType | ImageType;
}