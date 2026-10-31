import {InformationRequestExchangeCompletionRefusalDto} from "../../../models/models.tsx";
import {formatInformationRequestCount} from "../../../information-requests/shared/informationRequestFormatting.ts";

export const REMAINING_REQUESTS_REQUIRE_CANCELLATION = "INFORMATION_REQUEST_REMAINING_REQUESTS_REQUIRE_CANCELLATION";
export const REMAINING_REQUESTS_NOT_CANCELLABLE = "INFORMATION_REQUEST_REMAINING_REQUESTS_NOT_CANCELLABLE";
export const COMPLETION_GATES_UNSATISFIED = "INFORMATION_REQUEST_COMPLETION_GATES_UNSATISFIED";

export const ENDING_HELD_BY_WORKFLOW = "EXCHANGE_ENDING_HELD_BY_WORKFLOW";

export const workflowHeldEndingMessage = (error: unknown): string | null => {
    if (typeof error !== "object" || error === null) return null;
    const refusal = error as { reasonCode?: unknown; errorMessage?: unknown };
    if (refusal.reasonCode !== ENDING_HELD_BY_WORKFLOW) return null;
    return typeof refusal.errorMessage === "string" && refusal.errorMessage.trim()
        ? refusal.errorMessage
        : "A Workflow must approve the ending. The Exchange ends once it does.";
};

export const isCompletionRefusal = (error: unknown): error is InformationRequestExchangeCompletionRefusalDto =>
    typeof error === "object" && error !== null && "reasonCode" in error && "informationRequestIds" in error;

const requestsStillOpen = (count: number): string => {
    if (count === 0) return "Information Requests on this Exchange are still open. Ending the Exchange now cancels them.";
    if (count === 1) return "1 Information Request on this Exchange is still open. Ending the Exchange now cancels it.";
    return `${formatInformationRequestCount(count)} Information Requests on this Exchange are still open. Ending the Exchange now cancels them.`;
};

const requestsMustFinish = (count: number): string => {
    if (count === 0) return "This Exchange cannot end until Information Requests that must finish first are complete.";
    if (count === 1) return "This Exchange cannot end until 1 Information Request that must finish first is complete.";
    return `This Exchange cannot end until ${formatInformationRequestCount(count)} Information Requests that must finish first are complete.`;
};

export const refusalMessage = (refusal: InformationRequestExchangeCompletionRefusalDto): string =>
{
    const count = refusal.informationRequestIds.length;
    if (refusal.reasonCode === REMAINING_REQUESTS_REQUIRE_CANCELLATION) return requestsStillOpen(count);
    if (refusal.reasonCode === COMPLETION_GATES_UNSATISFIED) return requestsMustFinish(count);
    if (refusal.reasonCode === REMAINING_REQUESTS_NOT_CANCELLABLE)
    {
        return "Information Requests on this Exchange are still open, and only the people who manage them can cancel them.";
    }
    return refusal.errorMessage;
};
