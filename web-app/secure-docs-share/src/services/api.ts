import axios from 'axios';
import {
    CompanyRegistrationRequest,
    PersonRegistrationRequest,
    SignInCompletionRequest,
    SignInInitiationRequest,
    SignUpCompletionRequest,
    SignUpInitiationRequest,
    SignUpOtpRegenerationRequest
} from "./models/models.tsx";

const API_BASE_URL = 'http://localhost:8080'; // Replace with your actual API base URL

export const initiateSignUp = async (request: SignUpInitiationRequest) =>
{
    try
    {
        const response = await axios.post(`${API_BASE_URL}/auth/sign-up/initiation`, request);
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
        const response = await axios.post(`${API_BASE_URL}/auth/sign-up/completion`, request);
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
        const response = await axios.post(`${API_BASE_URL}/auth/sign-up/otp-regeneration`, request);
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
        const response = await axios.post(`${API_BASE_URL}/auth/sign-in/initiate`, request);
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
        const response = await axios.post(`${API_BASE_URL}/auth/sign-in/completion`, request);
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
        const response = await axios.post(`${API_BASE_URL}/auth/sign-out`, {}, {
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
        const response = await axios.get(`${API_BASE_URL}/app-user`, {
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
        const response = await axios.get(`${API_BASE_URL}/app-user/${appUserId}/person/${personId}/company`, {
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
        const response = await axios.post(`${API_BASE_URL}/entity-registration/person`, request, {
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
        const response = await axios.post(`${API_BASE_URL}/entity-registration/company`, request, {
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