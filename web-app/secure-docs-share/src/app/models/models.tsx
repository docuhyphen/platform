// Enums
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

// Models
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
    id: string;
    name: string;
    address?: string;
    // Add more fields as needed
}
