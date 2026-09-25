import {AxiosProgressEvent} from "axios";
import apiClient from "./apiClient.ts";
import {
    InformationRequestEvidenceCommandResultDto,
    InformationRequestEvidenceListDto,
} from "../app/models/models.tsx";
import {
    informationRequestBasePath,
    informationRequestCommandHeaders,
    informationRequestReadHeaders,
    isStaleInformationRequestRefusal,
    RuntimeCommandOptions,
    statedInformationRequestRefusal,
} from "./informationRequestRuntimeService.ts";

export interface InformationRequestEvidenceAttributesInput
{
    issuer?: string;
    jurisdiction?: string;
    language?: string;
    issuedOn?: string;
    expiresOn?: string;
    coverageStartsOn?: string;
    coverageEndsOn?: string;
    certificationReference?: string;
    signatureReference?: string;
}

export interface InformationRequestEvidenceUploadOptions extends RuntimeCommandOptions
{
    attributes?: InformationRequestEvidenceAttributesInput;
    onProgress?: (percent: number) => void;
}

export type InformationRequestEvidenceCommandOutcome =
    | { outcome: "SAVED"; result: InformationRequestEvidenceCommandResultDto }
    | { outcome: "STALE" };

export type InformationRequestEvidenceContentUse = "content" | "preview";

const ENCRYPTION_MODE = "INTERNAL";

const evidencePath = (requestId: string, requirementId: string, accessLinkToken?: string): string =>
    `${informationRequestBasePath(requestId, accessLinkToken)}/requirements/${requirementId}/evidence-artifacts`;

const evidenceForm = (file: File, attributes?: InformationRequestEvidenceAttributesInput): FormData =>
{
    const form = new FormData();
    form.append("file", file);
    form.append("encryptionMode", ENCRYPTION_MODE);
    Object.entries(attributes ?? {}).forEach(([name, value]) =>
    {
        if (typeof value === "string" && value.trim()) form.append(name, value.trim());
    });
    return form;
};

const progressHandler = (onProgress?: (percent: number) => void) =>
    (event: AxiosProgressEvent) =>
    {
        if (onProgress && event.total) onProgress(Math.round((event.loaded / event.total) * 100));
    };

const executeEvidenceCommand = async (
    request: () => Promise<{data: InformationRequestEvidenceCommandResultDto}>,
): Promise<InformationRequestEvidenceCommandOutcome> =>
{
    try
    {
        const {data} = await request();
        return {outcome: "SAVED", result: data};
    }
    catch (error: unknown)
    {
        if (isStaleInformationRequestRefusal(error)) return {outcome: "STALE"};
        throw statedInformationRequestRefusal(error);
    }
};

export const listInformationRequestEvidence = async (
    requestId: string,
    requirementId: string,
    accessLinkToken?: string,
): Promise<InformationRequestEvidenceListDto> =>
{
    try
    {
        const {data} = await apiClient.get<InformationRequestEvidenceListDto>(
            evidencePath(requestId, requirementId, accessLinkToken),
            {headers: informationRequestReadHeaders(requestId, accessLinkToken)},
        );
        return data;
    }
    catch (error: unknown)
    {
        throw statedInformationRequestRefusal(error);
    }
};

export const uploadInformationRequestEvidence = (
    requestId: string,
    requirementId: string,
    file: File,
    options: InformationRequestEvidenceUploadOptions,
): Promise<InformationRequestEvidenceCommandOutcome> =>
    executeEvidenceCommand(() => apiClient.post(
        evidencePath(requestId, requirementId, options.accessLinkToken),
        evidenceForm(file, options.attributes),
        {
            headers: {...informationRequestCommandHeaders(requestId, options), "Content-Type": "multipart/form-data"},
            onUploadProgress: progressHandler(options.onProgress),
        },
    ));

export const replaceInformationRequestEvidence = (
    requestId: string,
    requirementId: string,
    artifactId: string,
    file: File,
    options: InformationRequestEvidenceUploadOptions,
): Promise<InformationRequestEvidenceCommandOutcome> =>
    executeEvidenceCommand(() => apiClient.post(
        `${evidencePath(requestId, requirementId, options.accessLinkToken)}/${artifactId}/versions`,
        evidenceForm(file, options.attributes),
        {
            headers: {...informationRequestCommandHeaders(requestId, options), "Content-Type": "multipart/form-data"},
            onUploadProgress: progressHandler(options.onProgress),
        },
    ));

export const withdrawInformationRequestEvidence = (
    requestId: string,
    requirementId: string,
    artifactId: string,
    reason: string,
    options: RuntimeCommandOptions,
): Promise<InformationRequestEvidenceCommandOutcome> =>
    executeEvidenceCommand(() => apiClient.post(
        `${evidencePath(requestId, requirementId, options.accessLinkToken)}/${artifactId}/withdrawals`,
        {reason},
        {headers: informationRequestCommandHeaders(requestId, options)},
    ));

export const openInformationRequestEvidenceContent = async (
    requestId: string,
    requirementId: string,
    artifactId: string,
    versionId: string,
    use: InformationRequestEvidenceContentUse,
    accessLinkToken?: string,
): Promise<Blob> =>
{
    try
    {
        const {data} = await apiClient.get<Blob>(
            `${evidencePath(requestId, requirementId, accessLinkToken)}/${artifactId}/versions/${versionId}/${use}`,
            {headers: informationRequestReadHeaders(requestId, accessLinkToken), responseType: "blob"},
        );
        return data;
    }
    catch (error: unknown)
    {
        throw statedInformationRequestRefusal(error);
    }
};
