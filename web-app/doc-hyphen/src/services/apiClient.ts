import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

const apiClient = axios.create({
    baseURL: API_BASE_URL,
});

export const addBearerToHeaderToken = (token: string): string =>
{
    return `Bearer ${token}`
}

let authToken: string | null = null;

export const setApiClientAuthToken = (token: string | null) =>
{
    authToken = token;
};

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

export default apiClient;