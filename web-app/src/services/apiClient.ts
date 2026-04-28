import axios from 'axios';

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

apiClient.interceptors.request.use((config) =>
    {
        if (authToken)
        {
            config.headers['Authorization'] = `Bearer ${authToken}`;
        }

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
                // Refresh failed — redirect to session expired
                window.dispatchEvent(new CustomEvent('auth-session-expired'));
                return Promise.reject(refreshError);
            }
        }

        return Promise.reject(error);
    }
);

export default apiClient;