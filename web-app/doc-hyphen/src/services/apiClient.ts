import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080';

const apiClient = axios.create({
    baseURL: API_BASE_URL,
});

let authToken: string | null = null;

export const setApiClientAuthToken = (token: string | null) => {
    authToken = token;
};

apiClient.interceptors.request.use(
    (config) => {
        if (authToken) {
            config.headers['Authorization'] = `Bearer ${authToken}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

export default apiClient;