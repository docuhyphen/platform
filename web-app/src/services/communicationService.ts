import apiClient from './apiClient';
import {
    CreateCommunicationRequest,
    CommunicationDto,
    CommunicationSummaryDto,
    RenderedCommunication,
    UpdateCommunicationRequest,
} from '../app/models/models';

export interface ListCommunicationsParams
{
    scope?: string;
}

export interface PatchCommunicationStatusRequest
{
    isActive: boolean;
}

export interface PatchCommunicationPublishedRequest
{
    isPublished: boolean;
}

export interface PreviewCommunicationRequest
{
    sampleVariables?: Record<string, string>;
}

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const { data } = await fn();
        return data;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const listCommunications = (
    params?: ListCommunicationsParams,
): Promise<CommunicationSummaryDto[]> =>
    executeRequest(() => apiClient.get('/communications', { params }));

export const getCommunication = (id: string): Promise<CommunicationDto> =>
    executeRequest(() => apiClient.get(`/communications/${id}`));

export const createCommunication = (
    request: CreateCommunicationRequest,
): Promise<CommunicationDto> =>
    executeRequest(() => apiClient.post('/communications', request));

export const updateCommunication = (
    id: string,
    request: UpdateCommunicationRequest,
): Promise<CommunicationDto> =>
    executeRequest(() => apiClient.put(`/communications/${id}`, request));

export const patchCommunicationStatus = (
    id: string,
    request: PatchCommunicationStatusRequest,
): Promise<CommunicationDto> =>
    executeRequest(() => apiClient.patch(`/communications/${id}/status`, request));

export const patchCommunicationPublished = (
    id: string,
    request: PatchCommunicationPublishedRequest,
): Promise<CommunicationDto> =>
    executeRequest(() => apiClient.patch(`/communications/${id}/published`, request));

export const deleteCommunication = (id: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/communications/${id}`));

export const cloneCommunication = (id: string): Promise<CommunicationDto> =>
    executeRequest(() => apiClient.post(`/communications/${id}/clone`, {}));

export const previewCommunication = (
    id: string,
    request: PreviewCommunicationRequest,
): Promise<RenderedCommunication> =>
    executeRequest(() => apiClient.post(`/communications/${id}/preview`, request));
