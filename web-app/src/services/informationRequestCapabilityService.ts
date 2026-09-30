import apiClient from "./apiClient.ts";
import {statedInformationRequestRefusal} from "./informationRequestRuntimeService.ts";
import {InformationRequestCapabilitiesDto} from "../app/models/models.tsx";

export const getInformationRequestCapabilities = async (): Promise<InformationRequestCapabilitiesDto> =>
{
    try
    {
        return (await apiClient.get<InformationRequestCapabilitiesDto>("/information-request-capabilities")).data;
    }
    catch (error: unknown)
    {
        throw statedInformationRequestRefusal(error);
    }
};
