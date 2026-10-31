import apiClient from "./apiClient.ts";
import {
    CreateInformationRequestSuccessorRequest,
    InformationRequestLineageKind,
    InformationRequestSuccessorResultDto,
} from "../app/models/models.tsx";

export const postInformationRequestSupplement = async (
    requestId: string,
    reasonCode: string | undefined,
    headers: Record<string, string>,
) => {
    const request: CreateInformationRequestSuccessorRequest = {
        kind: InformationRequestLineageKind.SUPPLEMENT,
        ...(reasonCode ? {reasonCode} : {}),
    };
    const response = await apiClient.post<InformationRequestSuccessorResultDto>(
        `/information-requests/${requestId}/successors`, request, {headers},
    );
    const etag: unknown = response.headers.etag ?? response.headers.ETag;
    return {data: response.data, headers: {etag: typeof etag === "string" ? etag : ""}};
};
