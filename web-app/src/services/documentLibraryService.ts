import apiClient from './apiClient';
import {
    CreateDocumentLibraryEntryRequest,
    DocumentLibraryEntryDto,
    DocumentLibraryEntrySummaryDto,
    UpdateDocumentLibraryEntryRequest,
} from '../app/models/models';

export interface PatchDocumentLibraryStatusRequest
{
    isActive: boolean;
}

export interface PatchDocumentLibraryPublishedRequest
{
    isPublished: boolean;
}

export interface CloneDocumentLibraryEntryRequest
{
    newName?: string;
    targetScope?: 'PERSONAL' | 'ORG';
}

export interface ListDocumentLibraryParams
{
    scope?: string;
    tag?: string;
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

export const listDocumentLibraryEntries = (
    params?: ListDocumentLibraryParams,
): Promise<DocumentLibraryEntrySummaryDto[]> =>
    executeRequest(() => apiClient.get('/document-library', { params }));

export const getDocumentLibraryEntry = (id: string): Promise<DocumentLibraryEntryDto> =>
    executeRequest(() => apiClient.get(`/document-library/${id}`));

export const createDocumentLibraryEntry = (
    request: CreateDocumentLibraryEntryRequest,
): Promise<DocumentLibraryEntryDto> =>
    executeRequest(() => apiClient.post('/document-library', request));

export const updateDocumentLibraryEntry = (
    id: string,
    request: UpdateDocumentLibraryEntryRequest,
): Promise<DocumentLibraryEntryDto> =>
    executeRequest(() => apiClient.put(`/document-library/${id}`, request));

export const patchDocumentLibraryEntryStatus = (
    id: string,
    request: PatchDocumentLibraryStatusRequest,
): Promise<DocumentLibraryEntryDto> =>
    executeRequest(() => apiClient.patch(`/document-library/${id}/status`, request));

export const patchDocumentLibraryEntryPublished = (
    id: string,
    request: PatchDocumentLibraryPublishedRequest,
): Promise<DocumentLibraryEntryDto> =>
    executeRequest(() => apiClient.patch(`/document-library/${id}/published`, request));

export const deleteDocumentLibraryEntry = (id: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/document-library/${id}`));

export const cloneDocumentLibraryEntry = (
    id: string,
    request: CloneDocumentLibraryEntryRequest,
): Promise<DocumentLibraryEntryDto> =>
    executeRequest(() => apiClient.post(`/document-library/${id}/clone`, request));

export const uploadDocumentLibraryFile = (
    id: string,
    file: File,
    extension: string,
): Promise<DocumentLibraryEntryDto> =>
{
    const formData = new FormData();
    formData.append('file', file);
    formData.append('extension', extension);
    return executeRequest(() =>
        apiClient.post(`/document-library/${id}/file`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' },
        }),
    );
};

export const downloadDocumentLibraryFile = async (id: string, fileName: string): Promise<void> =>
{
    const response = await apiClient.get(`/document-library/${id}/file`, {
        responseType: 'blob',
    });
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', fileName);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
};
