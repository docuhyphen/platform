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
    InformationRequestTemplateVersionDto,
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

const keyed = (idempotencyKey: string) => ({headers: {"Idempotency-Key": idempotencyKey}});

export const createInformationRequestTemplate = (
    request: CreateInformationRequestTemplateRequest,
    idempotencyKey: string,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post("/information-request-templates", request, keyed(idempotencyKey)));

export const getInformationRequestTemplate = (id: string): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.get(`/information-request-templates/${id}`));

export const getInformationRequestTemplateVersion = (
    id: string,
    versionNumber: number,
): Promise<InformationRequestTemplateVersionDto> =>
    executeRequest(() => apiClient.get(`/information-request-templates/${id}/versions/${versionNumber}`));

export const replaceInformationRequestTemplateDraftConfiguration = (
    id: string,
    request: InformationRequestTemplateConfigurationRequest,
    draftETag: string,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.put(
        `/information-request-templates/${id}/draft/configuration`,
        request,
        {headers: {"If-Match": draftETag}},
    ));

export const publishInformationRequestTemplateDraft = (
    id: string,
    draftETag: string,
    idempotencyKey: string,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post(
        `/information-request-templates/${id}/draft/publication`,
        {},
        {headers: {"If-Match": draftETag, "Idempotency-Key": idempotencyKey}},
    ));

export const createInformationRequestTemplateDraftVersion = (
    id: string,
    request: InformationRequestTemplateNewVersionRequest,
    idempotencyKey: string,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post(`/information-request-templates/${id}/versions`, request, keyed(idempotencyKey)));

export const retireInformationRequestTemplateVersion = (
    id: string,
    versionNumber: number,
    idempotencyKey: string,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post(
        `/information-request-templates/${id}/versions/${versionNumber}/retirement`,
        {},
        keyed(idempotencyKey),
    ));

export const cloneInformationRequestTemplate = (
    id: string,
    request: CloneInformationRequestTemplateRequest,
    idempotencyKey: string,
): Promise<InformationRequestTemplateDto> =>
    executeRequest(() => apiClient.post(`/information-request-templates/${id}/clones`, request, keyed(idempotencyKey)));
