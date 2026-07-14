import apiClient, {addBearerToHeaderToken} from './apiClient';
import {
    AppUserSettingsDto,
    AuthenticatorEnrollment,
    CurrentSessionDto,
    MfaConfiguration,
    MfaMethod,
    OrganizationRegistrationRequest,
    PersonRegistrationRequest
} from "../app/models/models.tsx";

export const fetchAppUser = async (token: string | null) =>
{
    try
    {
        const response = await apiClient.get(`/app-user`, {
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

export const fetchCurrentSession = async (): Promise<CurrentSessionDto> =>
{
    const response = await apiClient.get('/app-user/session');
    return response.data;
};

export const fetchAppUserPersonOrganization = async (appUserId?: string, personId?: string, token?: string) =>
{
    try
    {
        const response = await apiClient.get(`/app-user/${appUserId}/person/${personId}/organization`, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
        return response.data;
    }
    catch (error: unknown)
    {
        throw error;
    }
};

export const registerIndividual = async (request: PersonRegistrationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/entity-registration/person`, request, {
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

export const registerOrganization = async (request: OrganizationRegistrationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/entity-registration/organization`, request, {
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

export const updateAppUserSettings = async (request: AppUserSettingsDto, token: string | null) =>
{
    try
    {
        const response = await apiClient.put(`/app-user/settings`, request, {
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
}

export const getMfaConfiguration = async (): Promise<MfaConfiguration> =>
{
    const response = await apiClient.get('/app-users/current/mfa-configurations');
    return response.data;
};

export const startAuthenticatorEnrollment = async (
    provider: Exclude<MfaMethod, 'EMAIL'>,
): Promise<AuthenticatorEnrollment> =>
{
    const response = await apiClient.post('/app-users/current/authenticator-enrollments', {provider});
    return response.data;
};

export const verifyAuthenticatorEnrollment = async (
    enrollmentId: string,
    code: string,
    emailFallbackEnabled: boolean,
): Promise<MfaConfiguration> =>
{
    const response = await apiClient.post(
        `/app-users/current/authenticator-enrollments/${enrollmentId}/verifications`,
        {code, emailFallbackEnabled},
    );
    return response.data;
};

export const updateMfaConfiguration = async (emailFallbackEnabled: boolean): Promise<MfaConfiguration> =>
{
    const response = await apiClient.patch('/app-users/current/mfa-configurations', {emailFallbackEnabled});
    return response.data;
};

export const removeAuthenticatorEnrollment = async (): Promise<MfaConfiguration> =>
{
    const response = await apiClient.delete('/app-users/current/authenticator-enrollments');
    return response.data;
};

export const initiateAppUserEmailUpdate = async (email: string, token: string | null) =>
{
    try
    {
        const response = await apiClient.put(
            `/app-user/email/update-initiation`,
            {email},
            {
                headers: {
                    Authorization: addBearerToHeaderToken(token)
                }
            }
        );
        return response.data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

export const confirmOldAppUserEmailForUpdate = async (verificationCode: string, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(
            `/app-user/email/update-confirm-old`,
            {verificationCode},
            {
                headers: {
                    Authorization: addBearerToHeaderToken(token)
                }
            }
        );
        return response.data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

export const completeAppUserEmailUpdate = async (email: string, verificationCode: string, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(
            `/app-user/email/update-completion`,
            {email, verificationCode},
            {
                headers: {
                    Authorization: addBearerToHeaderToken(token)
                }
            }
        );
        return response.data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};
