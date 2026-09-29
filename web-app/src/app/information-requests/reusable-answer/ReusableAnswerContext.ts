import {createContext, useContext} from "react";
import {InformationRequestAcceptedFactOfferDto} from "../../models/models.tsx";

export type ReusableAnswerResult =
    | { outcome: "SAVED"; responseETag: string }
    | { outcome: "STALE" };

export type RecertifyReusableAnswer = (
    requestId: string,
    factId: string,
    requirementId: string,
    responseETag: string,
) => Promise<ReusableAnswerResult>;

export interface ReusableAnswerSettings
{
    offers: InformationRequestAcceptedFactOfferDto[];
    recertify: RecertifyReusableAnswer;
}

export const ReusableAnswerContext = createContext<ReusableAnswerSettings | null>(null);

export const useReusableAnswerSettings = (): ReusableAnswerSettings | null => useContext(ReusableAnswerContext);
