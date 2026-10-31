import {useEffect, useState} from "react";
import {updateExchange} from "../../../../services/exchangeApi.ts";
import {publishExchangeUpdate} from "../../../observable/exchangeObservables.ts";
import {
    ExchangeDetailedDto,
    ExchangeStatus,
    InformationRequestExchangeCompletionRefusalDto,
    UpdateExchangeRequest,
} from "../../../models/models.tsx";
import {
    COMPLETION_GATES_UNSATISFIED,
    isCompletionRefusal,
    refusalMessage,
    REMAINING_REQUESTS_REQUIRE_CANCELLATION,
    workflowHeldEndingMessage,
} from "./exchangeEndRefusal.ts";

export interface ExchangeEnding
{
    ending: boolean;
    message: string | null;
    refused: boolean;
    held: boolean;
    cancellationOffered: boolean;
    blockedByRequests: boolean;
    end: () => Promise<void>;
}

export const useExchangeEnding = (
    exchange: ExchangeDetailedDto,
    isOpen: boolean,
    onEnded: (exchange: ExchangeDetailedDto) => void,
): ExchangeEnding =>
{
    const [ending, setEnding] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [refusal, setRefusal] = useState<InformationRequestExchangeCompletionRefusalDto | null>(null);
    const [held, setHeld] = useState(false);
    const cancellationOffered = refusal?.reasonCode === REMAINING_REQUESTS_REQUIRE_CANCELLATION;

    useEffect(() =>
    {
        if (!isOpen) return;
        setMessage(null);
        setRefusal(null);
        setHeld(false);
    }, [isOpen]);

    const end = async () =>
    {
        setEnding(true);
        try
        {
            const request: UpdateExchangeRequest = {
                status: ExchangeStatus.ENDED,
                cancelRemainingInformationRequests: cancellationOffered || undefined,
            };
            const updatedExchange = await updateExchange(exchange.id, request);
            if (!updatedExchange)
            {
                throw new Error("Ended Exchange response was empty");
            }
            publishExchangeUpdate(updatedExchange);
            onEnded(updatedExchange);
        }
        catch (error: unknown)
        {
            const heldMessage = workflowHeldEndingMessage(error);
            if (heldMessage) {
                setHeld(true);
                setMessage(heldMessage);
                return;
            }
            if (isCompletionRefusal(error))
            {
                setRefusal(error);
                setMessage(refusalMessage(error));
                return;
            }
            setMessage("Error ending Exchange");
            console.error("Error ending Exchange", error);
        }
        finally
        {
            setEnding(false);
        }
    };

    return {
        ending,
        message,
        refused: refusal !== null,
        held,
        cancellationOffered,
        blockedByRequests: refusal?.reasonCode === COMPLETION_GATES_UNSATISFIED,
        end,
    };
};
