import axios from 'axios';
import {attachDpopToAxiosConfig} from './dpop';
import {requestStepUp} from './stepUpBroker';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

const apiClient = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
});

export const addBearerToHeaderToken = (token: string | null): string =>
{
    return `Bearer ${token}`
}

let authToken: string | null = null;
let refreshPromise: Promise<any> | null = null;

export const setApiClientAuthToken = (token: string | null) =>
{
    authToken = token;
};

export const getApiClientAuthToken = (): string | null => authToken;

apiClient.interceptors.request.use(async (config) =>
    {
        if (authToken)
        {
            config.headers['Authorization'] = `Bearer ${authToken}`;
        }

        // DPoP: attach a fresh per-request proof when enabled. No-op when disabled.
        await attachDpopToAxiosConfig(config as any);

        return config;
    },
    (error) =>
    {
        return Promise.reject(error);
    }
);

// Response interceptor: on 401, attempt token refresh and retry
apiClient.interceptors.response.use(
    (response) => response,
    async (error) =>
    {
        const originalRequest = error.config;

        if (
            error.response?.status === 401 &&
            !originalRequest._retry &&
            !originalRequest.url?.includes('/auth/token/refresh') &&
            !originalRequest.url?.includes('/auth/sign-in') &&
            !originalRequest.url?.includes('/auth/sign-up')
        )
        {
            originalRequest._retry = true;

            try
            {
                // Deduplicate concurrent refresh requests
                if (!refreshPromise)
                {
                    refreshPromise = apiClient.post('/auth/token/refresh').finally(() =>
                    {
                        refreshPromise = null;
                    });
                }

                const refreshResponse = await refreshPromise;
                const {accessToken} = refreshResponse.data;

                if (accessToken)
                {
                    setApiClientAuthToken(accessToken);
                    originalRequest.headers['Authorization'] = `Bearer ${accessToken}`;

                    // Notify AuthContext of new tokens via custom event
                    window.dispatchEvent(new CustomEvent('tokens-refreshed', {
                        detail: refreshResponse.data
                    }));

                    return apiClient(originalRequest);
                }
            }
            catch (refreshError)
            {
                // Refresh failed,  redirect to session expired
                window.dispatchEvent(new CustomEvent('auth-session-expired'));
                return Promise.reject(refreshError);
            }
        }

        // 401 with STEP_UP_REQUIRED → drive the global step-up modal, then retry the original request.
        if (
            error.response?.status === 401 &&
            error.response?.data?.reasonCode === 'STEP_UP_REQUIRED' &&
            !originalRequest._stepUpRetry &&
            !originalRequest.url?.includes('/auth/step-up')
        )
        {
            originalRequest._stepUpRetry = true;
            try
            {
                await requestStepUp({
                    action: error.response?.data?.action,
                    message: error.response?.data?.errorMessage,
                });
                return apiClient(originalRequest);
            }
            catch (cancelErr)
            {
                return Promise.reject(error);
            }
        }

        // 403 with a deprovisioning or security reason code → treat as forced session end
        if (error.response?.status === 403)
        {
            const reasonCode: string = error.response?.data?.reasonCode ?? '';
            const deprovisionReasons = ['ACCOUNT_DEPROVISIONED', 'ORG_MEMBERSHIP_INACTIVE', 'SECURITY_SIGN_OUT'];
            if (deprovisionReasons.includes(reasonCode))
            {
                window.dispatchEvent(new CustomEvent('auth-session-expired', {detail: {reason: reasonCode}}));
            }
        }

        return Promise.reject(error);
    }
);

export default apiClient;