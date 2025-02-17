import apiClient from './apiClient';
import {
    PasswordResetCompletionRequest,
    PasswordResetInitiationRequest,
    SignInCompletionRequest,
    SignInInitiationRequest,
    SignInOtpRegenerationRequest,
    SignUpCompletionRequest,
    SignUpInitiationRequest,
    SignUpOtpRegenerationRequest
} from "../app/models/models.tsx";

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