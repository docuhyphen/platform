import apiClient from "./apiClient.ts";
import {
    InformationRequestAcceptedFactOfferDto,
    InformationRequestFactRecertificationDto,
    RecertifyInformationRequestAcceptedFactRequest,
} from "../app/models/models.tsx";
import {
    informationRequestBasePath,
    informationRequestCommandHeaders,
    informationRequestReadHeaders,
    isStaleInformationRequestRefusal,
    RuntimeCommandOptions,
    RuntimeCommandResult,
    statedInformationRequestRefusal,
} from "./informationRequestRuntimeService.ts";

export const getInformationRequestAcceptedFactOffers = async (
    requestId: string,
    accessLinkToken?: string,
): Promise<InformationRequestAcceptedFactOfferDto[]> =>
{
    try
    {
        const response = await apiClient.get<InformationRequestAcceptedFactOfferDto[]>(
            `${informationRequestBasePath(requestId, accessLinkToken)}/accepted-fact-offers`,
            {headers: informationRequestReadHeaders(requestId, accessLinkToken)},
        );
        return response.data;
    }
    catch (error: unknown)
    {
        throw statedInformationRequestRefusal(error);
    }
};

export const recertifyInformationRequestAcceptedFact = async (
    requestId: string,
    factId: string,
    request: RecertifyInformationRequestAcceptedFactRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestFactRecertificationDto>> =>
{
    try
    {
        const {data, headers} = await apiClient.post<InformationRequestFactRecertificationDto>(
            `${informationRequestBasePath(requestId, options.accessLinkToken)}/accepted-fact-offers/${factId}/recertifications`,
            request,
            {headers: informationRequestCommandHeaders(requestId, options)},
        );
        const etag = headers.etag ?? headers.ETag;
        return {outcome: "SAVED", responseETag: typeof etag === "string" ? etag : "", data};
    }
    catch (error: unknown)
    {
        if (isStaleInformationRequestRefusal(error)) return {outcome: "STALE"};
        throw statedInformationRequestRefusal(error);
    }
};
