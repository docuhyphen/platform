import {getSubscriptionDenialMessage} from "../../utils/subscriptionDenialUtils.ts";
import {informationRequestRefusalMessage} from "../information-requests/shared/informationRequestRefusal.ts";

export const EXCHANGE_INITIATION_FALLBACK_REFUSAL = "An unknown error occurred while starting the Exchange";

export const exchangeInitiationRefusalMessage = (error: unknown): string =>
    getSubscriptionDenialMessage(error) ?? informationRequestRefusalMessage(error, EXCHANGE_INITIATION_FALLBACK_REFUSAL);
