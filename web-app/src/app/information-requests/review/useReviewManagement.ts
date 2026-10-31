import {useState} from "react";
import {getInformationRequestParties} from "../../../services/informationRequestAuthoringService.ts";
import {
    assignInformationRequestReviewer,
    changeInformationRequestReviewAssignment,
    InformationRequestReviewAssignmentChange,
    overrideInformationRequestReviewItem,
    reconsiderInformationRequestReview,
    recordInformationRequestReviewComment,
} from "../../../services/informationRequestReviewService.ts";
import {RuntimeCommandOptions, RuntimeCommandResult} from "../../../services/informationRequestRuntimeService.ts";
import {
    AssignInformationRequestReviewerRequest,
    ChangeInformationRequestReviewAssignmentRequest,
    InformationRequestReviewCommandResultDto,
    InformationRequestReviewDto,
    InformationRequestReviewSummaryDto,
    OverrideInformationRequestReviewItemRequest,
    RecordInformationRequestReviewCommentRequest,
} from "../../models/models.tsx";
import {useScopeGuard} from "../shared/useScopeGuard.ts";
import {useServerRead} from "../shared/useServerRead.ts";
import {useStableIdempotencyKey} from "../shared/useStableIdempotencyKey.ts";
import {submissionErrorMessage} from "../submission/submissionLabels.ts";
import {reviewScopeOf} from "./useInformationRequestReview.ts";
import {ReviewFeedback, ReviewMessage, useReviewFeedback} from "./useReviewFeedback.ts";

const STALE: ReviewMessage = {
    intent: "warning",
    text: "This review changed while you were working. The latest review is shown; try again."
};

type ReviewCommandOutcome = RuntimeCommandResult<InformationRequestReviewCommandResultDto>;

export const useReviewManagement = (
    requestId: string,
    review: InformationRequestReviewDto | null,
    reload: () => Promise<void>,
    sharedFeedback?: ReviewFeedback,
) => {
    const managed = review?.review.informationRequestId === requestId ? review.review : null;
    const scopeKey = reviewScopeOf(managed?.informationRequestId, managed?.id);
    const reviewGuard = useScopeGuard(scopeKey);
    const parties = useServerRead(requestId, () => getInformationRequestParties(requestId).then(listing => listing.parties),
        caught => submissionErrorMessage(caught, "Reviewer parties could not be loaded."));
    const [busyScope, setBusyScope] = useState("");
    const ownFeedback = useReviewFeedback(scopeKey);
    const feedback = sharedFeedback ?? ownFeedback;
    const keyed = useStableIdempotencyKey();

    const run = async (owner: string, command: () => Promise<ReviewCommandOutcome>, done: string): Promise<boolean> =>
    {
        setBusyScope(owner);
        feedback.clear(owner);
        try
        {
            const outcome = await command();
            if (!reviewGuard.isCurrent(owner)) return false;
            feedback.say(owner, outcome.outcome === "STALE" ? STALE : {intent: "success", text: done});
            await reload();
            return outcome.outcome !== "STALE";
        }
        catch (caught: unknown)
        {
            if (reviewGuard.isCurrent(owner)) {
                feedback.say(owner, {
                    intent: "error",
                    text: submissionErrorMessage(caught, "The change could not be made.")
                });
            }
            return false;
        }
        finally
        {
            setBusyScope(current => current === owner ? "" : current);
        }
    };

    const command = (
        action: string,
        body: unknown,
        send: (target: InformationRequestReviewSummaryDto, options: RuntimeCommandOptions) => Promise<ReviewCommandOutcome>,
        done: string,
    ): Promise<boolean> => {
        if (!managed) return Promise.resolve(false);
        const target = managed;
        const owner = reviewScopeOf(target.informationRequestId, target.id);
        return run(owner, () => keyed(`${action}|${owner}`, {body, expectedETag: target.reviewETag}, idempotencyKey =>
            send(target, {expectedETag: target.reviewETag, idempotencyKey})), done);
    };

    return {
        parties: parties.value ?? [],
        partiesLoading: parties.loading,
        partiesError: parties.error,
        retryParties: parties.reload,
        busy: busyScope !== "" && busyScope === scopeKey,
        message: feedback.message,
        assign: (request: AssignInformationRequestReviewerRequest) =>
            command("assign", request, (target, options) =>
                assignInformationRequestReviewer(target.informationRequestId, target.id, request, options), "The reviewer was assigned."),
        changeAssignment: (assignmentId: string, change: InformationRequestReviewAssignmentChange, request: ChangeInformationRequestReviewAssignmentRequest) =>
            command(`change|${assignmentId}|${change}`, request, (target, options) =>
                    changeInformationRequestReviewAssignment(target.informationRequestId, target.id, assignmentId, change, request, options),
                "The assignment was changed."),
        override: (request: OverrideInformationRequestReviewItemRequest) =>
            command("override", request, (target, options) =>
                overrideInformationRequestReviewItem(target.informationRequestId, target.id, request, options), "The decision was overridden."),
        reconsider: (reason: string) =>
            command("reconsider", {reason}, (target, options) =>
                reconsiderInformationRequestReview(target.informationRequestId, target.id, {reason}, options), "A reconsideration was opened."),
        comment: (request: RecordInformationRequestReviewCommentRequest): Promise<boolean> => {
            if (!managed) return Promise.resolve(false);
            const target = managed;
            const owner = reviewScopeOf(target.informationRequestId, target.id);
            return run(owner, () => keyed(`comment|${owner}`, request, key =>
                recordInformationRequestReviewComment(target.informationRequestId, target.id, request, key)), "The comment was recorded.");
        },
    };
};

export type ReviewManagement = ReturnType<typeof useReviewManagement>;
