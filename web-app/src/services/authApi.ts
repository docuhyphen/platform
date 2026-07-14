import apiClient, {addBearerToHeaderToken} from './apiClient';
import {
    OAuthLinkConfirmRequest,
    OAuthTokenExchangeResponse,
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

/**
 * GET /auth/sign-up/email-confirm/{token},  peek at the email a confirmation
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
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

/**
 * POST /auth/sign-up/email-confirm,  complete sign-up via the opaque-token flow.
 * The token is consumed atomically server-side on success.
 */
export const confirmSignUpEmail = async (request: SignUpEmailConfirmRequest): Promise<SignUpEmailConfirmResponse> =>
{
    try
    {
        const response = await apiClient.post(`/auth/sign-up/email-confirm`, request);
        return response.data;
    }
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
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
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

export const createSignInEmailFallbackChallenge = async (email: string, mfaSessionId: string) =>
{
    try
    {
        const response = await apiClient.post(
            `/auth/sign-in/mfa-sessions/${mfaSessionId}/email-challenges`,
            {email},
        );
        return response.data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

export const exchangeOAuthTokenHandoff = async (code: string): Promise<OAuthTokenExchangeResponse> =>
{
    try
    {
        const response = await apiClient.post(`/auth/oauth/token-exchanges`, {code});
        return response.data;
    }
    catch (error: unknown)
    {
        const requestError = error as {response?: {data?: unknown}; message?: string};
        throw requestError.response?.data || requestError.message;
    }
};

export const deleteUserSessionRecord = async (sessionId: string): Promise<void> =>
{
    try
    {
        await apiClient.delete(`/auth/sessions/${sessionId}/record`);
    }
    catch (error: unknown)
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
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};// -- Step-up Re-Auth --

export interface StepUpInitiateResponse
{
    method: 'INTERNAL_EMAIL_OTP' | 'EXTERNAL_RELOGIN';
    message: string;
    mfaSessionId?: string;
    provider?: string;
    authorizeUrl?: string;
    mfaType?: 'EMAIL' | 'GOOGLE_AUTHENTICATOR' | 'MICROSOFT_AUTHENTICATOR';
    emailFallbackEnabled?: boolean;
}

export interface StepUpResult { fresh: boolean; message: string; }

export const initiateStepUp = async (returnTo: string, action?: string | null): Promise<StepUpInitiateResponse> =>
{
    try
    {
        const response = await apiClient.post(`/auth/step-up/initiate`, {returnTo, action: action ?? undefined});
        return response.data;
    }
    catch (error)
    {
        const e = error as { response?: { data?: unknown }; message?: string };
        throw e.response?.data || e.message;
    }
};

export const completeStepUpWithOtp = async (mfaSessionId: string, otp: string): Promise<StepUpResult> =>
{
    try
    {
        const response = await apiClient.post(`/auth/step-up/complete`, {mfaSessionId, otp});
        return response.data;
    }
    catch (error)
    {
        const e = error as { response?: { data?: unknown }; message?: string };
        throw e.response?.data || e.message;
    }
};

export const regenerateStepUpOtp = async (mfaSessionId: string): Promise<StepUpResult> =>
{
    try
    {
        const response = await apiClient.post(`/auth/step-up/otp-regeneration`, {mfaSessionId});
        return response.data;
    }
    catch (error)
    {
        const e = error as { response?: { data?: unknown }; message?: string };
        throw e.response?.data || e.message;
    }
};

// ── Org Auth Session Policy (per-IdP source of truth) ──

export interface OrgAuthSessionPolicyEffective
{
    accessTokenExpiryMinutes: number;
    refreshTokenExpiryMinutes: number;
    maxSessionDurationHours: number;
    idleTimeoutMinutes: number;
}

export interface OrgAuthSessionPolicyGuardrails
{
    minAccessTokenExpiryMinutes: number;
    maxAccessTokenExpiryMinutes: number;
    minRefreshTokenExpiryMinutes: number;
    maxRefreshTokenExpiryMinutes: number;
    minSessionMaxDurationHours: number;
    maxSessionMaxDurationHours: number;
    minIdleTimeoutMinutes: number;
    maxIdleTimeoutMinutes: number;
}

export interface OrgAuthSessionPolicyIdp
{
    configId: string;
    provider: string;
    isActive: boolean;
    accessTokenExpiryMinutes?: number | null;
    refreshTokenExpiryMinutes?: number | null;
    maxSessionDurationHours?: number | null;
    idleTimeoutMinutes?: number | null;
}

export interface OrgAuthSessionPolicySettings
{
    organizationId: string;
    effective: OrgAuthSessionPolicyEffective;
    guardrails: OrgAuthSessionPolicyGuardrails;
    idpConfigs: OrgAuthSessionPolicyIdp[];
    hasActiveIdpConfig: boolean;
}

export interface OrgAuthSessionPolicyUpdateRequest
{
    accessTokenExpiryMinutes?: number | null;
    refreshTokenExpiryMinutes?: number | null;
    maxSessionDurationHours?: number | null;
    idleTimeoutMinutes?: number | null;
}

export const getOrgAuthSessionPolicy = async (orgId: string): Promise<OrgAuthSessionPolicySettings> =>
{
    try
    {
        const response = await apiClient.get(`/organizations/${orgId}/auth/session-policy`);
        return response.data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

export const updateOrgIdpAuthSessionPolicy = async (
    orgId: string,
    configId: string,
    request: OrgAuthSessionPolicyUpdateRequest,
): Promise<OrgAuthSessionPolicyIdp> =>
{
    try
    {
        const response = await apiClient.put(
            `/organizations/${orgId}/auth/session-policy/${configId}`,
            request,
        );
        return response.data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

