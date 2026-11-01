import type useExchangeInitiatingState from "./hooks/useExchangeInitiatingState.ts";
import type {StartedInformationRequest} from "./startedInformationRequest.ts";

export type ExchangeInitiationFormState = ReturnType<typeof useExchangeInitiatingState>;

export type CreatedExchangeSummary = {
    id?: string;
    name: string;
    recipient: string;
    documents: number;
    requiresSignIn: boolean;
    shareLink?: string;
    informationRequest?: StartedInformationRequest | null;
};

export type ExchangeInitiationTransitionDirection = "forward" | "back" | null;

export type ExchangeInitiationCopyLinkStatus = 'idle' | 'copied' | 'failed';
