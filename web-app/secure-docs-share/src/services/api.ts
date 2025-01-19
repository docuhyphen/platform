import axios from 'axios';
import {
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
    catch (error)
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
    catch (error)
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
    catch (error)
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
    catch (error)
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
    catch (error)
    {
        throw error.response?.data || error.message;
    }
};