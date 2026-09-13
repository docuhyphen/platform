import axios from "axios";
import apiClient from "./apiClient.ts";
import {
    CloneInformationRequestTemplateRequest,
    CreateInformationRequestTemplateRequest,
    InformationRequestTemplateConfigurationRequest,
    InformationRequestTemplateDto,
    InformationRequestTemplateNewVersionRequest,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateSummaryDto,
    ResponseError,
} from "../app/models/models.tsx";

export interface ListInformationRequestTemplatesParams
{
    scopeKind?: InformationRequestTemplateScopeKind;
}

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: unknown)
    {
        if (axios.isAxiosError<ResponseError>(error))
        {
            throw error.response?.data ?? error.message;
        }
        if (error instanceof Error) throw error.message;
        throw error;
    }
};

export const listInformationRequestTemplates = (
    params?: ListInformationRequestTemplatesParams,
): Promise<InformationRequestTemplateSummaryDto[]> =>
    executeRequest(() => apiClient.get("/information-request-templates", {params}));

export const createInformationRequestTemplate = (
    request: CreateInformationRequestTemplateRequest,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post("/information-request-templates", request));

export const getInformationRequestTemplate = (id: string): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.get(`/information-request-templates/${id}`));

export const replaceInformationRequestTemplateDraftConfiguration = (
    id: string,
    request: InformationRequestTemplateConfigurationRequest,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.put(`/information-request-templates/${id}/draft/configuration`, request));

export const publishInformationRequestTemplateDraft = (
    id: string,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post(`/information-request-templates/${id}/draft/publication`, {}));

export const createInformationRequestTemplateDraftVersion = (
    id: string,
    request: InformationRequestTemplateNewVersionRequest,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post(`/information-request-templates/${id}/versions`, request));

export const retireInformationRequestTemplateVersion = (
    id: string,
    versionNumber: number,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post(
        `/information-request-templates/${id}/versions/${versionNumber}/retirement`,
        {},
    ));

export const cloneInformationRequestTemplate = (
    id: string,
    request: CloneInformationRequestTemplateRequest,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post(`/information-request-templates/${id}/clones`, request));
