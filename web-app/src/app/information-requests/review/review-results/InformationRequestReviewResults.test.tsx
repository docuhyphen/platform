/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestCorrectionState,
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestRespondentReviewDto,
    InformationRequestReviewCommentRole,
    InformationRequestReviewKind,
    InformationRequestReviewState,
    InformationRequestReviewVisibility,
    InformationRequestState,
} from "../../../models/models.tsx";
import InformationRequestReviewResults from "./InformationRequestReviewResults.tsx";

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReviewResults: vi.fn(),
    appealInformationRequestReview: vi.fn(),
    recordInformationRequestReviewComment: vi.fn(),
}));

const returned = (canAppeal: boolean): InformationRequestRespondentReviewDto => ({
    review: {
        id: "review-a",
        informationRequestId: "request-a",
        packageId: "package-a",
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state: InformationRequestReviewState.CHANGES_REQUESTED,
        openedAt: Date.parse("2026-09-26T08:00:00Z"),
        settledAt: Date.parse("2026-09-26T09:00:00Z"),
        reviewETag: "\"review:4\"",
    },
    findings: [{
        id: "finding-a",
        submissionItemId: "item-a",
        requirementId: "requirement-a",
        reasonCode: "record.incomplete",
        narrative: "The record is missing a page",
        severity: InformationRequestFindingSeverity.MAJOR,
        visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
        correctionScope: InformationRequestFindingCorrectionScope.RESPONSE,
        recordedAt: Date.parse("2026-09-26T08:30:00Z"),
        recordedByCaller: false,
        reviewId: "review-a",
    }],
    comments: [],
    correction: {
        id: "correction-a",
        reviewId: "review-a",
        packageId: "package-a",
        state: InformationRequestCorrectionState.OPEN,
        openedAt: Date.parse("2026-09-26T09:00:00Z"),
        requirementIds: ["requirement-a"],
        evidence: [],
        undisclosedItemCount: 1,
    },
    remediations: [],
    canAppeal,
    canComment: false,
});

describe("InformationRequestReviewResults", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() => vi.resetAllMocks());

    afterEach(cleanup);

    it.each(["reply", "appeal"])("recovers an ended respondent session after a refused %s", async action => {
        const onChanged = vi.fn();
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([{
            ...returned(true),
            canComment: true
        }]);
        vi.mocked(transport.recordInformationRequestReviewComment).mockRejectedValue({reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED"});
        vi.mocked(transport.appealInformationRequestReview).mockRejectedValue({reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED"});
        render(<InformationRequestReviewResults requestId={"request-a"}
                                                accessLinkToken={"bootstrap"}
                                                refreshKey={"responses-2"}
                                                requirementLabels={{"requirement-a": "Supporting record"}}
                                                onChanged={onChanged}/>);
        if (action === "reply") {
            fireEvent.click(await screen.findByRole("button", {name: "Reply to the finding on Supporting record"}));
            fireEvent.change(screen.getByLabelText("Your reply"), {target: {value: "Updated record"}});
            fireEvent.click(screen.getByRole("button", {name: "Send reply"}));
        } else {
            fireEvent.click(await screen.findByRole("button", {name: "Appeal Review of submission 1"}));
            fireEvent.change(screen.getByLabelText("Why the decision should change"), {target: {value: "Check the record"}});
            fireEvent.click(screen.getByRole("button", {name: "Send appeal"}));
        }
        await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
    });
    it("shows the reason an appeal was opened only when the review result states it", async () => {
        const appealed = returned(false);
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([
            appealed,
            {
                ...appealed,
                review: {
                    ...appealed.review,
                    id: "review-b",
                    kind: InformationRequestReviewKind.APPEAL,
                    openingReason: "The record already covers the period",
                },
            },
        ]);

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{"requirement-a": "Provide the supporting record"}}
                                                onChanged={vi.fn()}/>);

        expect(await screen.findByText("Reason given: The record already covers the period")).toBeTruthy();
        expect(document.getElementById("information-request-review-result-review-a-reason")).toBeNull();
    });

    it("shows what a settled review returned for correction without naming items the respondent cannot view", async () =>
    {
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([returned(false)]);

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                accessLinkToken={"bootstrap"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{"requirement-a": "Provide the supporting record"}}
                                                onChanged={vi.fn()}/>);

        expect(await screen.findByText("Changes requested")).toBeTruthy();
        expect(transport.getInformationRequestReviewResults).toHaveBeenCalledWith("request-a", "bootstrap");
        expect(screen.getByText("The record is missing a page")).toBeTruthy();
        expect(screen.getByText("Correct and resubmit these items:")).toBeTruthy();
        expect(screen.getAllByText("Provide the supporting record").length).toBeGreaterThan(0);
        expect(screen.getByText("1 other returned items concern parts handled by other parties.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: /^Appeal/})).toBeNull();
    });

    it("names returned files, shows the conversation on each finding, and sends the respondent's reply", async () =>
    {
        const onChanged = vi.fn();
        const settled = returned(false);
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([{
            ...settled,
            correction: {
                ...settled.correction!,
                evidence: [
                    {
                        artifactId: "artifact-a",
                        evidenceVersionId: "version-a",
                        versionNumber: 2,
                        declaredFileName: "statement.pdf"
                    },
                    {artifactId: "artifact-b", evidenceVersionId: "version-b", versionNumber: 1},
                ],
            },
            comments: [{
                id: "comment-a",
                submissionItemId: "item-a",
                requirementId: "requirement-a",
                findingId: "finding-a",
                authorRole: InformationRequestReviewCommentRole.REVIEWER,
                visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                body: "Please attach page 2",
                createdAt: Date.parse("2026-09-26T09:30:00Z"),
                authoredByCaller: false,
            }],
            canComment: true,
        }]);
        vi.mocked(transport.recordInformationRequestReviewComment).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:3\"",
            data: {} as never,
        });

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                accessLinkToken={"bootstrap"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{"requirement-a": "Provide the supporting record"}}
                                                onChanged={onChanged}/>);

        expect(await screen.findByText("Returned files you can replace or withdraw: statement.pdf (version 2), unnamed file (version 1).")).toBeTruthy();
        expect(screen.getByText("Reviewer: Please attach page 2")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Reply to the finding on Provide the supporting record"}));
        fireEvent.change(screen.getByLabelText("Your reply"), {target: {value: "Page 2 is attached"}});
        fireEvent.click(screen.getByRole("button", {name: "Send reply"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewComment).toHaveBeenCalledWith(
            "request-a",
            "review-a",
            {
                submissionItemId: "item-a",
                findingId: "finding-a",
                visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                body: "Page 2 is attached"
            },
            expect.any(String),
            "bootstrap",
        ));
        expect(await screen.findByText("Your reply was sent.")).toBeTruthy();
    });

    it("shows an item comment that belongs to no finding and lets the respondent comment on an item of a settled review", async () => {
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([{
            ...returned(false),
            findings: [],
            comments: [{
                id: "comment-a",
                submissionItemId: "item-a",
                requirementId: "requirement-a",
                authorRole: InformationRequestReviewCommentRole.REVIEWER,
                visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                body: "Page 2 was hard to read",
                createdAt: Date.parse("2026-09-26T08:40:00Z"),
                authoredByCaller: false,
            }],
            items: [{submissionItemId: "item-a", requirementId: "requirement-a"}],
            canComment: true,
        }]);
        vi.mocked(transport.recordInformationRequestReviewComment).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:3\"",
            data: {} as never,
        });

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{"requirement-a": "Provide the supporting record"}}
                                                onChanged={vi.fn()}/>);

        expect(await screen.findByText("Reviewer: Page 2 was hard to read")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Comment on Provide the supporting record"}));
        fireEvent.change(screen.getByLabelText("Your comment"), {target: {value: "A clearer copy is attached"}});
        fireEvent.click(screen.getByRole("button", {name: "Send comment"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewComment).toHaveBeenCalledWith(
            "request-a",
            "review-a",
            {
                submissionItemId: "item-a",
                visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                body: "A clearer copy is attached",
            },
            expect.any(String),
            undefined,
        ));
        expect(await screen.findByText("Your comment was sent.")).toBeTruthy();
    });

    it("keeps a reply that could not be sent with its text and sends it again under the same Idempotency-Key", async () => {
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([{
            ...returned(false),
            canComment: true
        }]);
        vi.mocked(transport.recordInformationRequestReviewComment)
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce({outcome: "SAVED", responseETag: "\"responses:3\"", data: {} as never});

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                accessLinkToken={"bootstrap"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{"requirement-a": "Provide the supporting record"}}
                                                onChanged={vi.fn()}/>);
        const reply = async () => {
            fireEvent.click(await screen.findByRole("button", {name: "Reply to the finding on Provide the supporting record"}));
            fireEvent.change(screen.getByLabelText("Your reply"), {target: {value: "Page 2 is attached"}});
            fireEvent.click(screen.getByRole("button", {name: "Send reply"}));
        };

        await reply();
        expect(await screen.findByText("The connection was lost")).toBeTruthy();
        expect((screen.getByLabelText("Your reply") as HTMLTextAreaElement).value).toBe("Page 2 is attached");
        fireEvent.click(screen.getByRole("button", {name: "Send reply"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewComment).toHaveBeenCalledTimes(2));
        const [first, retried] = vi.mocked(transport.recordInformationRequestReviewComment).mock.calls;
        expect(retried[2]).toEqual(first[2]);
        expect(retried[3]).toBe(first[3]);
        await waitFor(() => expect(screen.queryByLabelText("Your reply")).toBeNull());
    });

    it("appeals the exact review under its revision and reports a stale review", async () =>
    {
        const onChanged = vi.fn();
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([returned(true)]);
        vi.mocked(transport.appealInformationRequestReview)
            .mockResolvedValueOnce({outcome: "STALE"})
            .mockResolvedValueOnce({
                outcome: "SAVED",
                responseETag: "\"responses:3\"",
                data: {
                    requestState: InformationRequestState.ISSUED,
                    responseETag: "\"responses:3\"",
                    review: {
                        ...returned(true).review,
                        id: "review-b",
                        kind: InformationRequestReviewKind.APPEAL,
                        state: InformationRequestReviewState.PENDING
                    },
                },
            });

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{}}
                                                onChanged={onChanged}/>);

        fireEvent.click(await screen.findByRole("button", {name: /^Appeal /}));
        fireEvent.change(screen.getByLabelText("Why the decision should change"), {target: {value: "The page was attached"}});
        const dialog = document.getElementById("information-request-review-appeal-dialog");
        if (!dialog) throw new Error("Missing appeal dialog");
        fireEvent.click(screen.getByRole("button", {name: "Send appeal"}));
        expect(await within(dialog).findByText("This review changed. The latest review is shown; appeal again if it still applies.")).toBeTruthy();
        await waitFor(() => expect(transport.getInformationRequestReviewResults).toHaveBeenCalledTimes(2));
        expect((screen.getByLabelText("Why the decision should change") as HTMLTextAreaElement).value).toBe("The page was attached");
        fireEvent.click(screen.getByRole("button", {name: "Send appeal"}));

        await waitFor(() => expect(onChanged).toHaveBeenCalled());
        expect(transport.appealInformationRequestReview).toHaveBeenLastCalledWith("request-a", "review-a", {reason: "The page was attached"}, {
            expectedETag: "\"review:4\"",
            idempotencyKey: expect.any(String),
            accessLinkToken: undefined,
        });
        expect(await screen.findByText("Your appeal was recorded. A new review will decide it.")).toBeTruthy();
    });

    it("recovers an appeal recorded by an attempt whose answer was lost by sending it again under the same key", async () => {
        const onChanged = vi.fn();
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([returned(true)]);
        let committedKey = "";
        vi.mocked(transport.appealInformationRequestReview).mockImplementation(async (_request, _review, _body, options) => {
            if (!committedKey) {
                committedKey = options.idempotencyKey ?? "";
                throw {errorMessage: "The connection was lost"};
            }
            if (options.idempotencyKey !== committedKey) throw {
                errorMessage: "Only the latest settled review of a current submission that was rejected or returned can be reopened",
                reasonCode: "INFORMATION_REQUEST_REVIEW_NOT_REOPENABLE",
            };
            return {
                outcome: "SAVED",
                responseETag: "\"responses:3\"",
                data: {
                    requestState: InformationRequestState.ISSUED,
                    responseETag: "\"responses:3\"",
                    review: {...returned(true).review, id: "review-b", kind: InformationRequestReviewKind.APPEAL},
                },
            };
        });

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                accessLinkToken={"bootstrap"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{}}
                                                onChanged={onChanged}/>);
        fireEvent.click(await screen.findByRole("button", {name: /^Appeal /}));
        fireEvent.change(screen.getByLabelText("Why the decision should change"), {target: {value: "The page was attached"}});
        fireEvent.click(screen.getByRole("button", {name: "Send appeal"}));
        expect(await screen.findByText("The connection was lost")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Send appeal"}));

        expect(await screen.findByText("Your appeal was recorded. A new review will decide it.")).toBeTruthy();
        const [first, retried] = vi.mocked(transport.appealInformationRequestReview).mock.calls;
        expect(retried.slice(0, 3)).toEqual(first.slice(0, 3));
        expect(retried[3]).toEqual({
            expectedETag: "\"review:4\"",
            idempotencyKey: committedKey,
            accessLinkToken: "bootstrap"
        });
        expect(onChanged).toHaveBeenCalledTimes(1);
    });

    it("sends a changed appeal reason as a separate appeal and keeps the unanswered one's key", async () => {
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([returned(true)]);
        vi.mocked(transport.appealInformationRequestReview).mockRejectedValue({errorMessage: "The connection was lost"});

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{}}
                                                onChanged={vi.fn()}/>);
        fireEvent.click(await screen.findByRole("button", {name: /^Appeal /}));
        for (const reason of ["The page was attached", "Both pages were attached", "The page was attached"]) {
            fireEvent.change(screen.getByLabelText("Why the decision should change"), {target: {value: reason}});
            fireEvent.click(screen.getByRole("button", {name: "Send appeal"}));
            await waitFor(() => expect(screen.getByRole("button", {name: "Send appeal"})).toHaveProperty("disabled", false));
        }

        await waitFor(() => expect(transport.appealInformationRequestReview).toHaveBeenCalledTimes(3));
        const [first, changed, repeated] = vi.mocked(transport.appealInformationRequestReview).mock.calls.map(call => call[3].idempotencyKey);
        expect(changed).not.toBe(first);
        expect(repeated).toBe(first);
    });

    it("states a refused read of the review results with Try again, drops the earlier results, and shows them once a retry loads", async () => {
        const review = returned(false);
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValueOnce([review]);
        const view = render(<InformationRequestReviewResults requestId={"request-a"}
                                                             refreshKey={"\"responses:1\""}
                                                             requirementLabels={{"requirement-a": "Provide the supporting record"}}
                                                             onChanged={vi.fn()}/>);
        await waitFor(() => expect(document.getElementById("information-request-review-result-review-a")).not.toBeNull());
        vi.mocked(transport.getInformationRequestReviewResults).mockRejectedValueOnce({errorMessage: "The review results could not be read."});

        view.rerender(<InformationRequestReviewResults requestId={"request-a"}
                                                       refreshKey={"\"responses:2\""}
                                                       requirementLabels={{"requirement-a": "Provide the supporting record"}}
                                                       onChanged={vi.fn()}/>);

        await waitFor(() => expect(document.getElementById("information-request-review-results-load-error")?.textContent)
            .toContain("The review results could not be read."));
        expect(document.getElementById("information-request-review-result-review-a")).toBeNull();
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValueOnce([review]);
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        await waitFor(() => expect(document.getElementById("information-request-review-result-review-a")).not.toBeNull());
        expect(document.getElementById("information-request-review-results-load-error")).toBeNull();
    });
});

