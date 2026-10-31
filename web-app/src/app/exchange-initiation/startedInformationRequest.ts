import {getExchangeInformationRequests} from "../../services/informationRequestAuthoringService.ts";

export interface StartedInformationRequest {
    id: string;
    title: string;
}

export const findStartedInformationRequest = async (exchangeId: string): Promise<StartedInformationRequest | null> => {
    try {
        const [request] = (await getExchangeInformationRequests(exchangeId)).requests;
        return request ? {id: request.id, title: request.title} : null;
    } catch {
        return null;
    }
};
