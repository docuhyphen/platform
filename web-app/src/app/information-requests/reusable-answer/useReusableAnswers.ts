import {useCallback, useMemo} from "react";
import {
    getInformationRequestAcceptedFactOffers,
    recertifyInformationRequestAcceptedFact,
} from "../../../services/informationRequestReuseService.ts";
import {informationRequestRefusalMessage} from "../shared/informationRequestRefusal.ts";
import {useReloadWhenChanged} from "../shared/useReloadWhenChanged.ts";
import {useServerRead} from "../shared/useServerRead.ts";
import {useStableIdempotencyKey} from "../shared/useStableIdempotencyKey.ts";
import {RecertifyReusableAnswer, ReusableAnswerSettings} from "./ReusableAnswerContext.ts";

const LOAD_FAILURE = "Answers accepted earlier could not be loaded for reuse.";

export const useReusableAnswers = (
    requestId: string,
    responseETag: string,
    accessLinkToken?: string,
): ReusableAnswerSettings =>
{
    const read = useServerRead(
        `${requestId}|${accessLinkToken ?? ""}`,
        () => getInformationRequestAcceptedFactOffers(requestId, accessLinkToken),
        refusal => informationRequestRefusalMessage(refusal, LOAD_FAILURE),
    );
    const keyed = useStableIdempotencyKey();

    useReloadWhenChanged(responseETag, read.reload);

    const recertify = useCallback<RecertifyReusableAnswer>(async (targetRequestId, factId, requirementId, expectedETag, confirmedHiddenResponseClearRequirementIds) =>
    {
        const confirmation = {
            requirementId, assented: true,
            ...(confirmedHiddenResponseClearRequirementIds?.length ? {confirmedHiddenResponseClearRequirementIds} : {})
        };
        const result = await keyed(`recertify|${targetRequestId}|${factId}`, {
            confirmation,
            expectedETag
        }, idempotencyKey =>
            recertifyInformationRequestAcceptedFact(targetRequestId, factId, confirmation, {
                expectedETag,
                idempotencyKey,
                accessLinkToken
            }));
        return result.outcome === "STALE" ? result : {outcome: "SAVED", responseETag: result.responseETag};
    }, [accessLinkToken, keyed]);

    return useMemo(
        () => ({offers: read.value ?? [], error: read.error, reload: read.reload, recertify}),
        [read.error, read.reload, read.value, recertify],
    );
};
