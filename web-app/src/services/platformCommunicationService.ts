import {
    CommunicationDto,
    CommunicationSummaryDto,
} from "../app/models/models.tsx";
import {
    deleteCommunication,
    getCommunication,
    listCommunications,
    patchCommunicationPublished,
    patchCommunicationStatus,
} from "./communicationService.ts";

const requirePlatformCommunication = <T extends CommunicationSummaryDto>(
    communication: T,
): T =>
{
    if (communication.scope !== "PLATFORM")
        throw new Error("Platform Administration can manage only PLATFORM-scoped communications.");
    return communication;
};

export const listPlatformCommunications = async (): Promise<CommunicationSummaryDto[]> =>
{
    const communications = await listCommunications({scope: "PLATFORM"});
    communications.forEach(requirePlatformCommunication);
    return communications;
};

export const getPlatformCommunication = async (
    communication: CommunicationSummaryDto,
): Promise<CommunicationDto> =>
{
    requirePlatformCommunication(communication);
    return requirePlatformCommunication(await getCommunication(communication.id));
};

export const setPlatformCommunicationPublished = (
    communication: CommunicationSummaryDto,
): Promise<CommunicationDto> =>
{
    requirePlatformCommunication(communication);
    return patchCommunicationPublished(communication.id, {
        isPublished: !communication.isPublished,
    });
};

export const setPlatformCommunicationActive = (
    communication: CommunicationSummaryDto,
): Promise<CommunicationDto> =>
{
    requirePlatformCommunication(communication);
    return patchCommunicationStatus(communication.id, {
        isActive: !communication.isActive,
    });
};

export const deletePlatformCommunication = (
    communication: CommunicationSummaryDto,
): Promise<void> =>
{
    requirePlatformCommunication(communication);
    return deleteCommunication(communication.id);
};
