import apiClient, {addBearerToHeaderToken} from './apiClient';
import {
    OAuthLinkConfirmRequest,
    OrgMemberCapacityResponse,
    PasswordResetCompletionRequest,
    PasswordResetInitiationRequest,
    SetupPasswordRequest,
    SignInCompletionRequest,
    SignInInitiationRequest,
    SignInLookupRequest,
    SignInOtpRegenerationRequest,
    SignUpCompletionRequest,
    SignUpEmailConfirmCheckResponse,
    SignUpEmailConfirmRequest,
    SignUpEmailConfirmResponse,
    SignUpInitiationRequest,
    SignUpOtpRegenerationRequest,
    UserSessionListResponse,
} from "../app/models/models.tsx";

export const lookupSignInMethod = async (request: SignInLookupRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-in/lookup`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const refreshTokens = async () =>
{
    try
    {
        const response = await apiClient.post(`/auth/token/refresh`);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const confirmOAuthLink = async (request: OAuthLinkConfirmRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/oauth/link-confirm`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

// ── Identity Provider Management ──

export const getIdentityProviders = async () =>
{
    try
    {
        const response = await apiClient.get(`/auth/identity-providers`);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const initiateLinkProvider = async (provider: string) =>
{
    try
    {
        const response = await apiClient.post(`/auth/identity-providers/link/initiate`, {provider});
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const unlinkProvider = async (provider: string) =>
{
    try
    {
        const response = await apiClient.delete(`/auth/identity-providers/${provider}`);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const setupPassword = async (request: SetupPasswordRequest) =>
{
    try
    {
        const response = await apiClient.post(`/auth/identity-providers/internal/setup-password`, request);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

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

/**
 * GET /auth/sign-up/email-confirm/{token} — peek at the email a confirmation
 * token belongs to without consuming the token. Used to validate the link
 * and show context on the email-confirm page before the user submits.
 */
export const checkSignUpEmailConfirmToken = async (token: string): Promise<SignUpEmailConfirmCheckResponse> =>
{
    try
    {
        const response = await apiClient.get(`/auth/sign-up/email-confirm/${encodeURIComponent(token)}`);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

/**
 * POST /auth/sign-up/email-confirm — complete sign-up via the opaque-token flow.
 * The token is consumed atomically server-side on success.
 */
export const confirmSignUpEmail = async (request: SignUpEmailConfirmRequest): Promise<SignUpEmailConfirmResponse> =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-up/email-confirm`, request);
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

export const signOut = async (outOfAllDevices: boolean, token: string) =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-out?outOfAllDevices=${outOfAllDevices}`, {}, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

// ── Session Management ──

export const listUserSessions = async (): Promise<UserSessionListResponse> =>
{
    try
    {
        const response = await apiClient.get(`/auth/sessions`);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const revokeUserSession = async (sessionId: string): Promise<void> =>
{
    try
    {
        await apiClient.delete(`/auth/sessions/${sessionId}`);
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

// ── Org Capacity ──

export const getOrgMemberCapacity = async (orgId: string): Promise<OrgMemberCapacityResponse> =>
{
    try
    {
        const response = await apiClient.get(`/auth/organizations/${orgId}/member-capacity`);
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};