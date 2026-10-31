import {useEffect, useState} from "react";
import {
    appealInformationRequestReview,
    getInformationRequestReviewResults,
    recordInformationRequestReviewComment,
} from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestRespondentReviewDto,
    InformationRequestReviewFindingDto,
    InformationRequestReviewResultItemDto,
    InformationRequestReviewVisibility,
    RecordInformationRequestReviewCommentRequest,
} from "../../../models/models.tsx";
import {
    informationRequestRefusalMessage,
    isInformationRequestAccessSessionEnded
} from "../../shared/informationRequestRefusal.ts";
import {useReloadWhenChanged} from "../../shared/useReloadWhenChanged.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {useStableIdempotencyKey} from "../../shared/useStableIdempotencyKey.ts";
import {submissionErrorMessage} from "../../submission/submissionLabels.ts";
import {ReviewMessage} from "../useInformationRequestReview.ts";

const LOAD_FAILURE = "The review results could not be loaded.";

export const useRespondentReviewResults = (
    requestId: string,
    accessLinkToken: string | undefined,
    refreshKey: string,
    onChanged: () => void,
) => {
    const read = useServerRead(
        `${requestId}|${accessLinkToken ?? ""}`,
        () => getInformationRequestReviewResults(requestId, accessLinkToken),
        refusal => informationRequestRefusalMessage(refusal, LOAD_FAILURE),
    );
    const [appealing, setAppealing] = useState<InformationRequestRespondentReviewDto | null>(null);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<ReviewMessage | null>(null);
    const keyed = useStableIdempotencyKey();

    useReloadWhenChanged(refreshKey, read.reload);
    useEffect(() => {
        if (!read.value) return;
        setAppealing(current => current ? read.value?.find(result => result.review.id === current.review.id) ?? current : null);
    }, [read.value]);

    const appeal = async (reason: string) => {
        if (!appealing || !appealing.canAppeal) return;
        setBusy(true);
        setMessage(null);
        try {
            const {id, reviewETag: expectedETag} = appealing.review;
            const outcome = await keyed(`appeal|${requestId}|${id}`, {reason, expectedETag}, idempotencyKey =>
                appealInformationRequestReview(requestId, id, {reason}, {
                    expectedETag,
                    idempotencyKey,
                    accessLinkToken
                }));
            setMessage(outcome.outcome === "SAVED"
                ? {intent: "success", text: "Your appeal was recorded. A new review will decide it."}
                : {
                    intent: "warning",
                    text: "This review changed. The latest review is shown; appeal again if it still applies."
                });
            if (outcome.outcome === "SAVED") setAppealing(null);
            if (outcome.outcome === "SAVED") onChanged();
            void read.reload();
        } catch (caught: unknown) {
            setMessage({intent: "error", text: submissionErrorMessage(caught, "The appeal could not be recorded.")});
            if (isInformationRequestAccessSessionEnded(caught)) onChanged();
        } finally {
            setBusy(false);
        }
    };

    const send = async (
        result: InformationRequestRespondentReviewDto,
        request: RecordInformationRequestReviewCommentRequest,
        sent: string,
        failed: string,
    ): Promise<boolean> => {
        setBusy(true);
        setMessage(null);
        try {
            await keyed(result.review.id, request, key =>
                recordInformationRequestReviewComment(requestId, result.review.id, request, key, accessLinkToken));
            setMessage({intent: "success", text: sent});
            void read.reload();
            return true;
        } catch (caught: unknown) {
            setMessage({intent: "error", text: submissionErrorMessage(caught, failed)});
            if (isInformationRequestAccessSessionEnded(caught)) onChanged();
            return false;
        } finally {
            setBusy(false);
        }
    };

    const reply = (result: InformationRequestRespondentReviewDto, finding: InformationRequestReviewFindingDto, body: string) =>
        send(result, {
            submissionItemId: finding.submissionItemId,
            findingId: finding.id,
            visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
            body,
        }, "Your reply was sent.", "The reply could not be sent.");

    const comment = (result: InformationRequestRespondentReviewDto, item: InformationRequestReviewResultItemDto, body: string) =>
        send(result, {
            submissionItemId: item.submissionItemId,
            visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
            body,
        }, "Your comment was sent.", "The comment could not be sent.");

    return {
        results: read.value ?? [],
        loadError: read.error,
        retry: read.reload,
        appealing,
        setAppealing,
        busy,
        message,
        appeal,
        reply,
        comment,
    };
};
