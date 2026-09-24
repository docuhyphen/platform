import type {RealtimeTicketDto} from "../app/models/models.tsx";
import apiClient from "./apiClient.ts";

export const issueRealtimeTicket = async (): Promise<RealtimeTicketDto> =>
{
    const response = await apiClient.post<RealtimeTicketDto>('/auth/realtime-tickets');
    return response.data;
};
