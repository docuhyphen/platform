import {useCallback, useEffect, useMemo, useState} from "react";
import {InformationRequestAcceptedFactOfferDto} from "../../models/models.tsx";
import {
    getInformationRequestAcceptedFactOffers,
    recertifyInformationRequestAcceptedFact,
} from "../../../services/informationRequestReuseService.ts";
import {RecertifyReusableAnswer, ReusableAnswerSettings} from "./ReusableAnswerContext.ts";

export const useReusableAnswers = (
    requestId: string,
    responseETag: string,
    accessLinkToken?: string,
): ReusableAnswerSettings =>
{
    const [offers, setOffers] = useState<InformationRequestAcceptedFactOfferDto[]>([]);

    useEffect(() =>
    {
        let current = true;
        getInformationRequestAcceptedFactOffers(requestId, accessLinkToken)
            .then(loaded => current && setOffers(loaded))
            .catch(() => current && setOffers([]));
        return () =>
        {
            current = false;
        };
    }, [accessLinkToken, requestId, responseETag]);

    const recertify = useCallback<RecertifyReusableAnswer>(async (targetRequestId, factId, requirementId, expectedETag) =>
    {
        const result = await recertifyInformationRequestAcceptedFact(
            targetRequestId,
            factId,
            {requirementId, assented: true},
            {expectedETag, idempotencyKey: crypto.randomUUID(), accessLinkToken},
        );
        return result.outcome === "STALE" ? result : {outcome: "SAVED", responseETag: result.responseETag};
    }, [accessLinkToken]);

    return useMemo(() => ({offers, recertify}), [offers, recertify]);
};
