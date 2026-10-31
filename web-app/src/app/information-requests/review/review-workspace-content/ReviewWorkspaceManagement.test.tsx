/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as evidence from "../../../../services/informationRequestEvidenceService.ts";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestReviewItemStanding,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
    InformationRequestReviewVisibility,
} from "../../../models/models.tsx";
import {
    chooseReviewOption as choose,
    reviewWorkspaceParties,
    reviewWorkspaceReview as review,
    savedReviewCommand as saved,
} from "../testing/reviewWorkspaceFixtures.ts";
import ReviewWorkspaceContent from "./ReviewWorkspaceContent.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

const shownTime = (value: number): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReview: vi.fn(),
    saveInformationRequestReviewWorksheet: vi.fn(),
    recordInformationRequestReviewDecisions: vi.fn(),
    recordInformationRequestReviewFinding: vi.fn(),
    overrideInformationRequestReviewItem: vi.fn(),
    reconsiderInformationRequestReview: vi.fn(),
    recordInformationRequestReviewComment: vi.fn(),
    assignInformationRequestReviewer: vi.fn(),
    changeInformationRequestReviewAssignment: vi.fn(),
}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestParties: vi.fn()}));
vi.mock("../../../../services/informationRequestEvidenceService.ts", () => ({openInformationRequestSubmittedEvidenceContent: vi.fn()}));

describe("reviewer workspace management", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
        vi.stubGlobal("URL", {...URL, createObjectURL: vi.fn(() => "blob:record"), revokeObjectURL: vi.fn()});
        vi.spyOn(window, "open").mockImplementation(() => null);
    });

    beforeEach(() =>
    {
        vi.resetAllMocks();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review());
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            partiesETag: "\"parties-1\"",
            parties: reviewWorkspaceParties(),
        });
        vi.mocked(evidence.openInformationRequestSubmittedEvidenceContent).mockResolvedValue(new Blob(["record"]));
        for (const command of [
            transport.overrideInformationRequestReviewItem,
            transport.reconsiderInformationRequestReview,
            transport.recordInformationRequestReviewComment,
            transport.assignInformationRequestReviewer,
            transport.changeInformationRequestReviewAssignment,
        ]) vi.mocked(command).mockResolvedValue(saved);
    });

    afterEach(cleanup);

    it("withholds commenting when the server does not permit it", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({...review(), canComment: false});
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        await screen.findByRole("heading", {name: "Attach the supporting record"});
        expect(screen.queryByRole("button", {name: "Comment on Attach the supporting record"})).toBeNull();
    });
    it("reports a failed party listing inside the assignment dialog and retries it", async () => {
        vi.mocked(authoring.getInformationRequestParties).mockRejectedValueOnce({}).mockResolvedValueOnce({
            partiesETag: "parties-1",
            parties: reviewWorkspaceParties()
        });
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        fireEvent.click(await screen.findByRole("button", {name: "Assign reviewer"}));
        const dialog = await screen.findByRole("dialog", {name: "Assign a reviewer"});
        expect(await within(dialog).findByText("Reviewer parties could not be loaded.")).toBeTruthy();
        expect(within(dialog).queryByText("Add a Reviewer party to this request first.")).toBeNull();
        fireEvent.click(within(dialog).getByRole("button", {name: "Try again"}));
        await waitFor(() => expect(within(dialog).getByRole("option", {name: "Second reviewer"})).toBeTruthy());
    });
    it("offers no assignment changes for a settled review", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review(InformationRequestReviewState.SATISFIED));
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        await screen.findByRole("heading", {name: "Attach the supporting record"});
        expect(screen.queryByRole("button", {name: "Assign reviewer"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Recuse from Content review"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Delegate Content review"})).toBeNull();
    });
    it("names every control and region of the reviewer workspace", async () =>
    {
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        await screen.findByRole("heading", {name: "Attach the supporting record"});

        expect(unnamedControls(document.body)).toEqual([]);

        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("names items by their prompts and previews the exact evidence version", async () =>
    {
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        expect(await screen.findByRole("heading", {name: "Attach the supporting record"})).toBeTruthy();
        expect(screen.getByText("File version 1, 2 KiB, conforming")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Preview version 1 of Attach the supporting record"}));

        await waitFor(() => expect(evidence.openInformationRequestSubmittedEvidenceContent)
            .toHaveBeenCalledWith("request-a", "package-a", "evidence-a", "preview"));
    });

    it("lets a manager assign a reviewer party to a stage and the assigned reviewer recuse with a reason", async () =>
    {
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        const assignments = await screen.findByRole("region", {name: "Reviewers"});
        expect(await within(assignments).findByText("First reviewer (you)")).toBeTruthy();
        fireEvent.click(within(assignments).getByRole("button", {name: "Assign reviewer"}));
        const assign = await screen.findByRole("dialog", {name: "Assign a reviewer"});
        choose(assign, "Stage", "Content review");
        choose(assign, "Reviewer", "Second reviewer");
        expect(within(assign).queryByRole("option", {name: "Respondent"})).toBeNull();
        fireEvent.click(within(assign).getByRole("button", {name: "Assign"}));
        await waitFor(() => expect(transport.assignInformationRequestReviewer).toHaveBeenCalledWith(
            "request-a", "review-a", {stageKey: "content", reviewerPartyId: "party-b"},
            {expectedETag: "\"review:2\"", idempotencyKey: expect.any(String)},
        ));

        fireEvent.click(within(assignments).getByRole("button", {name: "Recuse from Content review"}));
        const recuse = await screen.findByRole("dialog", {name: "Recuse from Content review"});
        fireEvent.change(within(recuse).getByLabelText("Reason"), {target: {value: "CONFLICT"}});
        fireEvent.click(within(recuse).getByRole("button", {name: "Recuse"}));
        await waitFor(() => expect(transport.changeInformationRequestReviewAssignment).toHaveBeenCalledWith(
            "request-a", "review-a", "assignment-a", "recusal", {reasonCode: "CONFLICT"},
            {expectedETag: "\"review:2\"", idempotencyKey: expect.any(String)},
        ));
    });

    it("assigns a reviewer with a due time only when the time has not passed and shows each assignment's due time", async () => {
        const loaded = review();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...loaded,
            assignments: [{...loaded.assignments[0], dueAt: Date.parse("2026-10-07T09:00:00Z")}],
        });
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        const assignments = await screen.findByRole("region", {name: "Reviewers"});
        expect(await within(assignments).findByText(`Due ${shownTime(Date.parse("2026-10-07T09:00:00Z"))}`, {exact: false})).toBeTruthy();
        fireEvent.click(within(assignments).getByRole("button", {name: "Assign reviewer"}));
        const assign = await screen.findByRole("dialog", {name: "Assign a reviewer"});
        choose(assign, "Stage", "Content review");
        choose(assign, "Reviewer", "Second reviewer");
        fireEvent.change(within(assign).getByLabelText("Due"), {target: {value: "2000-01-01T09:00"}});

        expect(within(assign).getByText("Choose a time that has not passed yet.")).toBeTruthy();
        expect((within(assign).getByRole("button", {name: "Assign"}) as HTMLButtonElement).disabled).toBe(true);

        fireEvent.change(within(assign).getByLabelText("Due"), {target: {value: "2099-10-06T09:30"}});
        fireEvent.click(within(assign).getByRole("button", {name: "Assign"}));
        await waitFor(() => expect(transport.assignInformationRequestReviewer).toHaveBeenCalledWith(
            "request-a", "review-a",
            {stageKey: "content", reviewerPartyId: "party-b", dueAt: new Date("2099-10-06T09:30").getTime()},
            {expectedETag: "\"review:2\"", idempotencyKey: expect.any(String)},
        ));
    });

    it("offers Reconsider only when the server says this settled review may be reopened", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...review(InformationRequestReviewState.CHANGES_REQUESTED),
            canReconsider: false,
            canComment: true,
        });
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        expect((await screen.findAllByText("Changes requested")).length).toBeGreaterThan(0);
        expect(screen.queryByRole("button", {name: "Reconsider"})).toBeNull();
    });

    it("lets a manager override an item where the stage permits it and reconsider a settled review", async () =>
    {
        const view = render(<ReviewWorkspaceContent requestId={"request-a"}
                                                    reviewId={"review-a"}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Override Attach the supporting record"}));
        const override = await screen.findByRole("dialog", {name: "Override the decision"});
        choose(override, "Outcome", "Satisfied");
        fireEvent.change(within(override).getByLabelText("Why"), {target: {value: "Page 2 confirmed by phone"}});
        fireEvent.click(within(override).getByRole("button", {name: "Override"}));
        await waitFor(() => expect(transport.overrideInformationRequestReviewItem).toHaveBeenCalledWith(
            "request-a", "review-a",
            {stageKey: "content", submissionItemId: "item-b", outcome: InformationRequestReviewOutcome.SATISFIED, narrative: "Page 2 confirmed by phone"},
            {expectedETag: "\"review:2\"", idempotencyKey: expect.any(String)},
        ));
        view.unmount();

        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review(InformationRequestReviewState.CHANGES_REQUESTED));
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        fireEvent.click(await screen.findByRole("button", {name: "Reconsider"}));
        const reconsider = await screen.findByRole("dialog", {name: "Reconsider this review"});
        fireEvent.change(within(reconsider).getByLabelText("Reason"), {target: {value: "New information arrived"}});
        fireEvent.click(within(reconsider).getByRole("button", {name: "Reconsider"}));
        await waitFor(() => expect(transport.reconsiderInformationRequestReview).toHaveBeenCalledWith(
            "request-a", "review-a", {reason: "New information arrived"},
            {expectedETag: "\"review:2\"", idempotencyKey: expect.any(String)},
        ));
    });

    it("shows the conversation on an item and comments for reviewers only", async () =>
    {
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        expect(await screen.findByText("Respondent: Page 2 is attached")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Comment on Attach the supporting record"}));
        const comment = await screen.findByRole("dialog", {name: "Comment on Attach the supporting record"});
        fireEvent.change(within(comment).getByLabelText("Comment"), {target: {value: "Check against the register"}});
        choose(comment, "Who can see it", "Reviewers only");
        fireEvent.click(within(comment).getByRole("button", {name: "Send comment"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewComment).toHaveBeenCalledWith(
            "request-a", "review-a",
            {submissionItemId: "item-b", visibility: InformationRequestReviewVisibility.REVIEWERS_ONLY, body: "Check against the register"},
            expect.any(String),
        ));
    });

    it("keeps a comment that could not be recorded open with its text, sends it again under the same key, and keys the next afresh", {timeout: 15000}, async () => {
        vi.mocked(transport.recordInformationRequestReviewComment)
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValue(saved);
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        const open = async () => {
            const button = await screen.findByRole("button", {name: "Comment on Attach the supporting record"}, {timeout: 5000}) as HTMLButtonElement;
            await waitFor(() => expect(button.disabled).toBe(false));
            fireEvent.click(button);
            return screen.findByRole("dialog", {name: "Comment on Attach the supporting record"});
        };

        const comment = await open();
        fireEvent.change(within(comment).getByLabelText("Comment"), {target: {value: "Check against the register"}});
        fireEvent.click(within(comment).getByRole("button", {name: "Send comment"}));
        expect(await within(comment).findByText("The connection was lost")).toBeTruthy();
        expect((within(comment).getByLabelText("Comment") as HTMLTextAreaElement).value).toBe("Check against the register");
        fireEvent.click(within(comment).getByRole("button", {name: "Send comment"}));
        await waitFor(() => expect(screen.getByText("The comment was recorded.")).toBeTruthy());
        await waitFor(() => expect(screen.queryByRole("dialog", {name: "Comment on Attach the supporting record"})).toBeNull());
        const next = await open();
        fireEvent.change(within(next).getByLabelText("Comment"), {target: {value: "Check against the register"}});
        fireEvent.click(within(next).getByRole("button", {name: "Send comment"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewComment).toHaveBeenCalledTimes(3));
        const [first, retried, fresh] = vi.mocked(transport.recordInformationRequestReviewComment).mock.calls;
        expect(retried[3]).toBe(first[3]);
        expect(fresh[3]).not.toBe(first[3]);
    });

    it("keeps an override and a reconsideration open with their text when they could not be recorded", async () => {
        vi.mocked(transport.overrideInformationRequestReviewItem).mockRejectedValueOnce({errorMessage: "Override refused"});
        const view = render(<ReviewWorkspaceContent requestId={"request-a"}
                                                    reviewId={"review-a"}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Override Attach the supporting record"}));
        const override = await screen.findByRole("dialog", {name: "Override the decision"});
        choose(override, "Outcome", "Satisfied");
        fireEvent.change(within(override).getByLabelText("Why"), {target: {value: "Page 2 confirmed by phone"}});
        fireEvent.click(within(override).getByRole("button", {name: "Override"}));
        expect(await within(override).findByText("Override refused")).toBeTruthy();
        expect((within(override).getByLabelText("Why") as HTMLTextAreaElement).value).toBe("Page 2 confirmed by phone");
        view.unmount();

        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review(InformationRequestReviewState.CHANGES_REQUESTED));
        vi.mocked(transport.reconsiderInformationRequestReview).mockRejectedValueOnce({errorMessage: "Reconsideration refused"});
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        fireEvent.click(await screen.findByRole("button", {name: "Reconsider"}));
        const reconsider = await screen.findByRole("dialog", {name: "Reconsider this review"});
        fireEvent.change(within(reconsider).getByLabelText("Reason"), {target: {value: "New information arrived"}});
        fireEvent.click(within(reconsider).getByRole("button", {name: "Reconsider"}));
        expect(await within(screen.getByRole("dialog", {name: "Reconsider this review"})).findByText("Reconsideration refused")).toBeTruthy();
        expect((within(reconsider).getByLabelText("Reason") as HTMLInputElement).value).toBe("New information arrived");
    });

    it("overrides an item in the open stage that has not decided it rather than in an earlier settled stage", async () => {
        const loaded = review();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...loaded,
            stages: [
                {
                    ...loaded.stages[0],
                    stageKey: "first",
                    title: "First review",
                    state: InformationRequestReviewStageState.SETTLED,
                    items: [{
                        submissionItemId: "item-b",
                        standing: InformationRequestReviewItemStanding.DECIDED,
                        outcome: InformationRequestReviewOutcome.SATISFIED,
                    }],
                },
                {...loaded.stages[0], stageKey: "content", position: 2},
            ],
        });
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Override Attach the supporting record"}));
        const override = await screen.findByRole("dialog", {name: "Override the decision"});
        choose(override, "Outcome", "Satisfied");
        fireEvent.change(within(override).getByLabelText("Why"), {target: {value: "Confirmed with the issuer"}});
        fireEvent.click(within(override).getByRole("button", {name: "Override"}));

        await waitFor(() => expect(transport.overrideInformationRequestReviewItem).toHaveBeenCalledWith(
            "request-a", "review-a",
            {
                stageKey: "content",
                submissionItemId: "item-b",
                outcome: InformationRequestReviewOutcome.SATISFIED,
                narrative: "Confirmed with the issuer"
            },
            {expectedETag: "\"review:2\"", idempotencyKey: expect.any(String)},
        ));
    });

    it("offers no override once the review has settled or every open stage has decided the item", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review(InformationRequestReviewState.CHANGES_REQUESTED));
        const settled = render(<ReviewWorkspaceContent requestId={"request-a"}
                                                       reviewId={"review-a"}/>);
        expect((await screen.findAllByText("Changes requested")).length).toBeGreaterThan(0);
        expect(screen.queryByRole("button", {name: "Override Attach the supporting record"})).toBeNull();
        settled.unmount();

        const loaded = review();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...loaded,
            stages: [{
                ...loaded.stages[0],
                items: [{
                    submissionItemId: "item-b",
                    standing: InformationRequestReviewItemStanding.DECIDED,
                    outcome: InformationRequestReviewOutcome.SATISFIED,
                }],
            }],
        });
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        expect(await screen.findByRole("heading", {name: "Attach the supporting record"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Override Attach the supporting record"})).toBeNull();
    });

    it("lets a manager without an assignment record a finding on a reviewed item", async () => {
        const loaded = review();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...loaded,
            assignments: [],
            worksheets: []
        });
        vi.mocked(transport.recordInformationRequestReviewFinding).mockResolvedValue(saved);
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Add finding on Attach the supporting record"}));
        fireEvent.change(await screen.findByLabelText("Reason code"), {target: {value: "record.incomplete"}});
        fireEvent.change(screen.getByLabelText("What needs attention"), {target: {value: "The record is missing a page"}});
        fireEvent.click(screen.getByRole("button", {name: "Record finding"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewFinding).toHaveBeenCalledWith(
            "request-a",
            "review-a",
            expect.objectContaining({submissionItemId: "item-b", assignmentId: undefined}),
            expect.any(String),
        ));
    });

    it("states each stage's separation-of-duties rules and the decision and remediation history", async () =>
    {
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        expect(await screen.findByText("Anyone who answered this request cannot review it.")).toBeTruthy();
        expect(screen.getByText("Anyone who decided an earlier stage or the earlier review cannot review this stage.")).toBeTruthy();
        expect(screen.getByText("At least 2 reviewers decide each item.")).toBeTruthy();
        expect(screen.getByText("A manager can override an item's decision.")).toBeTruthy();
        const history = screen.getByRole("region", {name: "Decision history"});
        expect(within(history).getByText(/Attach the supporting record: Changes required by you/)).toBeTruthy();
        expect(within(history).getByText("1 finding was addressed by a later submission.")).toBeTruthy();
    });
});
