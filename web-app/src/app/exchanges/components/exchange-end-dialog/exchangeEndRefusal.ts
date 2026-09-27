import {InformationRequestExchangeCompletionRefusalDto} from "../../../models/models.tsx";

export const REMAINING_REQUESTS_REQUIRE_CANCELLATION = "INFORMATION_REQUEST_REMAINING_REQUESTS_REQUIRE_CANCELLATION";
export const COMPLETION_GATES_UNSATISFIED = "INFORMATION_REQUEST_COMPLETION_GATES_UNSATISFIED";

export const isCompletionRefusal = (error: unknown): error is InformationRequestExchangeCompletionRefusalDto =>
    typeof error === "object" && error !== null && "reasonCode" in error && "informationRequestIds" in error;

export const refusalMessage = (refusal: InformationRequestExchangeCompletionRefusalDto): string =>
{
    const count = refusal.informationRequestIds.length;
    if (refusal.reasonCode === REMAINING_REQUESTS_REQUIRE_CANCELLATION)
    {
        return `${count} Information Requests on this Exchange are still open. Ending the Exchange now cancels them.`;
    }
    if (refusal.reasonCode === COMPLETION_GATES_UNSATISFIED)
    {
        return `This Exchange cannot end until ${count} Information Requests that must finish first are complete.`;
    }
    return refusal.errorMessage;
};
