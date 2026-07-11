import apiClient, {addBearerToHeaderToken} from './apiClient';
import {ResponseError} from "../app/models/models.tsx";

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

const getAuthHeaders = (token: string | null) => ({
    Authorization: token ? addBearerToHeaderToken(token) : ''
});

export const initiatePhoneAddition = (contactDetailsId: string, phoneNumber: string, token: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/contact-details/${contactDetailsId}/phone/addition-initiation`,
            {phoneNumber},
            {headers: getAuthHeaders(token)}
        )
    );

export const completePhoneAddition = (contactDetailsId: string, phoneNumber: string, verificationCode: string, token: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/contact-details/${contactDetailsId}/phone/addition-completion`,
            {phoneNumber, verificationCode},
            {headers: getAuthHeaders(token)}
        )
    );

export const initiatePhoneUpdate = (contactDetailsId: string, phoneNumber: string, token: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/contact-details/${contactDetailsId}/phone/initiate-update`,
            {phoneNumber},
            {headers: getAuthHeaders(token)}
        )
    );

export const completePhoneUpdate = (contactDetailsId: string, phoneNumber: string, verificationCode: string, token: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/contact-details/${contactDetailsId}/phone/complete-update`,
            {phoneNumber, verificationCode},
            {headers: getAuthHeaders(token)}
        )
    );

export const initiateEmailAddition = (contactDetailsId: string, email: string, token: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/contact-details/${contactDetailsId}/email/addition-initiation`,
            {email},
            {headers: getAuthHeaders(token)}
        )
    );

export const completeEmailAddition = (contactDetailsId: string, email: string, verificationCode: string, token: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/contact-details/${contactDetailsId}/email/addition-completion`,
            {email, verificationCode},
            {headers: getAuthHeaders(token)}
        )
    );

export const initiateEmailUpdate = (contactDetailsId: string, email: string, token: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/contact-details/${contactDetailsId}/email/initiate-update`,
            {email},
            {headers: getAuthHeaders(token)}
        )
    );

export const completeEmailUpdate = (contactDetailsId: string, email: string, verificationCode: string, token: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/contact-details/${contactDetailsId}/email/complete-update`,
            {email, verificationCode},
            {headers: getAuthHeaders(token)}
        )
    );