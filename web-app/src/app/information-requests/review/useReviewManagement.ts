import {useEffect, useState} from "react";
import {getInformationRequestParties} from "../../../services/informationRequestAuthoringService.ts";
import {
    assignInformationRequestReviewer,
    changeInformationRequestReviewAssignment,
    InformationRequestReviewAssignmentChange,
    overrideInformationRequestReviewItem,
    reconsiderInformationRequestReview,
    recordInformationRequestReviewComment,
} from "../../../services/informationRequestReviewService.ts";
import {RuntimeCommandResult} from "../../../services/informationRequestRuntimeService.ts";
import {
    AssignInformationRequestReviewerRequest,
    ChangeInformationRequestReviewAssignmentRequest,
    InformationRequestPartyDto,
    InformationRequestReviewCommandResultDto,
    InformationRequestReviewDto,
    OverrideInformationRequestReviewItemRequest,
    RecordInformationRequestReviewCommentRequest,
} from "../../models/models.tsx";
import {submissionErrorMessage} from "../submission/submissionLabels.ts";
import {ReviewMessage} from "./useInformationRequestReview.ts";

const STALE: ReviewMessage = {intent: "warning", text: "This review changed while you were working. The latest review is shown; try again."};

export const useReviewManagement = (requestId: string, review: InformationRequestReviewDto | null, reload: () => Promise<void>) =>
{
    const [parties, setParties] = useState<InformationRequestPartyDto[]>([]);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<ReviewMessage | null>(null);

    useEffect(() =>
    {
        let active = true;
        getInformationRequestParties(requestId)
            .then(listing =>
            {
                if (active) setParties(listing.parties);
            })
            .catch(() => undefined);
        return () =>
        {
            active = false;
        };
    }, [requestId]);

    const run = async (command: () => Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>>, done: string) =>
    {
        setBusy(true);
        setMessage(null);
        try
        {
            const outcome = await command();
            setMessage(outcome.outcome === "STALE" ? STALE : {intent: "success", text: done});
            await reload();
        }
        catch (caught: unknown)
        {
            setMessage({intent: "error", text: submissionErrorMessage(caught, "The change could not be made.")});
        }
        finally
        {
            setBusy(false);
        }
    };

    const options = () => ({expectedETag: review?.review.reviewETag ?? "", idempotencyKey: crypto.randomUUID()});
    const reviewId = review?.review.id ?? "";

    return {
        parties,
        busy,
        message,
        assign: (request: AssignInformationRequestReviewerRequest) =>
            run(() => assignInformationRequestReviewer(requestId, reviewId, request, options()), "The reviewer was assigned."),
        changeAssignment: (assignmentId: string, change: InformationRequestReviewAssignmentChange, request: ChangeInformationRequestReviewAssignmentRequest) =>
            run(() => changeInformationRequestReviewAssignment(requestId, reviewId, assignmentId, change, request, options()), "The assignment was changed."),
        override: (request: OverrideInformationRequestReviewItemRequest) =>
            run(() => overrideInformationRequestReviewItem(requestId, reviewId, request, options()), "The decision was overridden."),
        reconsider: (reason: string) =>
            run(() => reconsiderInformationRequestReview(requestId, reviewId, {reason}, options()), "A reconsideration was opened."),
        comment: (request: RecordInformationRequestReviewCommentRequest) =>
            run(() => recordInformationRequestReviewComment(requestId, reviewId, request, crypto.randomUUID()), "The comment was recorded."),
    };
};

export type ReviewManagement = ReturnType<typeof useReviewManagement>;
