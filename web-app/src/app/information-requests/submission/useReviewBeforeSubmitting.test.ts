/** @vitest-environment jsdom */
import {act, renderHook} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import {
    InformationRequestSubmissionMode,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionStageOrdering,
} from "../../models/models.tsx";
import {PendingWorkBlocker, RespondentPendingWork} from "../shared/respondentPendingWork.ts";
import {useReviewBeforeSubmitting} from "./useReviewBeforeSubmitting.ts";

const preview = (submissionETag: string, stageKey: string | null = null): InformationRequestSubmissionPreviewDto => ({
    informationRequestId: "request-a",
    stageKey,
    submissionMode: stageKey ? InformationRequestSubmissionMode.STAGED : InformationRequestSubmissionMode.WHOLE_PACKAGE,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    scopeItems: [],
    submissionETag,
    ready: true,
    problems: [],
    undisclosedProblemCount: 0,
    groupProblems: [],
    attestations: [],
    stages: [],
    canSubmit: true,
    correctionOpen: false,
    packages: [],
});

const work = (blocker: PendingWorkBlocker | null, unsaved: boolean, flush = vi.fn(() => Promise.resolve(true))): RespondentPendingWork =>
    ({blocker, unsaved, evidenceRevision: 0, flush});

interface Inputs {
    reviewed: InformationRequestSubmissionPreviewDto | null;
    held: boolean;
    pendingWork?: RespondentPendingWork;
}

const render = (initial: Inputs) => renderHook(
    ({reviewed, held, pendingWork}: Inputs) => useReviewBeforeSubmitting(reviewed, held, pendingWork),
    {initialProps: initial},
);

describe("useReviewBeforeSubmitting", () => {
    it("opens the review at once when nothing is pending", () => {
        const view = render({reviewed: preview("\"submission:1\""), held: false, pendingWork: work(null, false)});

        act(() => view.result.current.open());

        expect(view.result.current.reviewing).toBe(true);
    });

    it("saves unsaved answers first and opens only on the preview loaded after the save", async () => {
        const pendingWork = work(null, true);
        const view = render({reviewed: preview("\"submission:1\""), held: false, pendingWork});

        await act(async () => view.result.current.open());

        expect(pendingWork.flush).toHaveBeenCalledTimes(1);
        expect(view.result.current.preparing).toBe(true);
        expect(view.result.current.reviewing).toBe(false);
        view.rerender({reviewed: null, held: true, pendingWork: work("saving", false)});
        expect(view.result.current.reviewing).toBe(false);
        view.rerender({reviewed: preview("\"submission:2\""), held: false, pendingWork: work(null, false)});
        expect(view.result.current.reviewing).toBe(true);
        expect(view.result.current.preparing).toBe(false);
    });

    it("keeps the review closed when saving the unsaved answers fails", async () => {
        const pendingWork = work(null, true, vi.fn(() => Promise.resolve(false)));
        const view = render({reviewed: preview("\"submission:1\""), held: false, pendingWork});

        await act(async () => view.result.current.open());

        expect(view.result.current.preparing).toBe(false);
        expect(view.result.current.reviewing).toBe(false);
    });

    it("stops waiting to review once the save leaves work the respondent has to resolve", async () => {
        const pendingWork = work(null, true, vi.fn(() => new Promise<boolean>(() => {
        })));
        const view = render({reviewed: preview("\"submission:1\""), held: false, pendingWork});
        await act(async () => view.result.current.open());

        view.rerender({reviewed: preview("\"submission:1\""), held: false, pendingWork: work("stale", true)});

        expect(view.result.current.preparing).toBe(false);
        expect(view.result.current.reviewing).toBe(false);
    });

    it("does not open or confirm while work is unresolved", () => {
        const submit = vi.fn();
        const view = render({reviewed: preview("\"submission:1\""), held: false, pendingWork: work("kept", false)});

        act(() => view.result.current.open());
        act(() => view.result.current.confirm(submit));

        expect(view.result.current.reviewing).toBe(false);
        expect(submit).not.toHaveBeenCalled();
    });

    it("saves answers left unsaved at confirmation instead of submitting what was reviewed", async () => {
        const submit = vi.fn();
        const view = render({reviewed: preview("\"submission:1\""), held: false, pendingWork: work(null, false)});
        act(() => view.result.current.open());
        const pendingWork = work(null, true);
        view.rerender({reviewed: preview("\"submission:1\""), held: false, pendingWork});

        await act(async () => view.result.current.confirm(submit));

        expect(submit).not.toHaveBeenCalled();
        expect(pendingWork.flush).toHaveBeenCalledTimes(1);
        expect(view.result.current.reviewing).toBe(false);
    });

    it("submits the reviewed scope and closes the review", () => {
        const submit = vi.fn();
        const view = render({reviewed: preview("\"submission:1\""), held: false, pendingWork: work(null, false)});
        act(() => view.result.current.open());

        act(() => view.result.current.confirm(submit));

        expect(submit).toHaveBeenCalledTimes(1);
        expect(view.result.current.reviewing).toBe(false);
    });

    it("closes the review when the reviewed revision or stage changes under it", () => {
        const view = render({
            reviewed: preview("\"submission:1\"", "first"),
            held: false,
            pendingWork: work(null, false)
        });
        act(() => view.result.current.open());

        view.rerender({reviewed: null, held: true, pendingWork: work(null, false)});
        expect(view.result.current.reviewing).toBe(true);
        view.rerender({reviewed: preview("\"submission:2\"", "first"), held: false, pendingWork: work(null, false)});

        expect(view.result.current.reviewing).toBe(false);
    });
});
