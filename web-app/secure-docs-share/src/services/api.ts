import {
    CompanyRegistrationRequest,
    PersonRegistrationRequest,
    ResponseError,
    SignInCompletionRequest,
    SignInInitiationRequest,
    SignInOtpRegenerationRequest,
    SignUpCompletionRequest,
    SignUpInitiationRequest,
    SignUpOtpRegenerationRequest
} from "./models/models.tsx";
import {
    PasswordResetCompletionRequest,
    PasswordResetInitiationRequest,
    SharingSessionBasicDto,
    SharingSessionInitiationRequest
} from "../app/models/models.tsx";

import apiClient from './apiClient';

export const initiateSignUp = async (request: SignUpInitiationRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-up/initiation`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};
export const completeSignUp = async (request: SignUpCompletionRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-up/completion`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const initiatePasswordReset = async (request: PasswordResetInitiationRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/password-reset/initiation`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};
export const completePasswordReset = async (request: PasswordResetCompletionRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/password-reset/completion`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const regenerateSignUpOtp = async (request: SignUpOtpRegenerationRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-up/otp-regeneration`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};


export const regeneratePasswordResetOtp = async (request: SignUpOtpRegenerationRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-up/otp-regeneration`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const regenerateSignInOtp = async (request: SignInOtpRegenerationRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-in/otp-regeneration`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const initiateSignIn = async (request: SignInInitiationRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-in/initiate`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const completeSignIn = async (request: SignInCompletionRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-in/completion`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const signOut = async (token: string) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-out`, {}, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchAppUser = async (token: string | null) =>
{
    try
    {
        const response = await apiClient.get(`/app-user`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchAppUserPersonCompany = async (appUserId?: string, personId?: string, token?: string) =>
{
    try
    {
        const response = await apiClient.get(`/app-user/${appUserId}/person/${personId}/company`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error
    }
};

export const registerIndividual = async (request: PersonRegistrationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/entity-registration/person`, request, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const registerCompany = async (request: CompanyRegistrationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/entity-registration/company`, request, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });

        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const initiateSharingSession = async (request: SharingSessionInitiationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/sharing-sessions/`, request, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });

        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchSignedInUserAppUserSharingSessions = async (token: string | null):Promise<SharingSessionBasicDto[] | ResponseError> =>
{
    try
    {
        const response = await apiClient.get(`/sharing-sessions/`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });

        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchSignedInUserAppUserSharingSession = async (sessionId: string | null, token: string | null): Promise<SharingSessionBasicDto | ResponseError> =>
{
    try
    {
        const response = await apiClient.get(`/sharing-sessions/${sessionId}`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });

        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};