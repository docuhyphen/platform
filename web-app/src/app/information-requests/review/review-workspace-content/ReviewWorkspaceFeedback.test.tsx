/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as evidence from "../../../../services/informationRequestEvidenceService.ts";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {InformationRequestReviewState} from "../../../models/models.tsx";
import {
    chooseReviewOption,
    reviewWorkspaceParties,
    reviewWorkspaceReview,
    savedReviewCommand,
} from "../testing/reviewWorkspaceFixtures.ts";
import ReviewWorkspaceContent from "./ReviewWorkspaceContent.tsx";

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

const FINDING_REQUIRED = "A finding is required before this decision.";
const COMMENTED = "The comment was recorded.";
const ASSIGNED = "The reviewer was assigned.";
const WORKSHEET_STALE =
    "This review changed while you were working. The latest review is shown with your unsaved choices kept; save again.";

const renderWorkspace = () => render(<ReviewWorkspaceContent requestId={"request-a"}
                                                             reviewId={"review-a"}/>);

const message = (): string | null => document.getElementById("information-request-review-message-body")?.textContent ?? null;

const comment = async () => {
    const open = await screen.findByRole("button", {name: "Comment on Attach the supporting record"}) as HTMLButtonElement;
    await waitFor(() => expect(open.disabled).toBe(false));
    fireEvent.click(open);
    const dialog = await screen.findByRole("dialog", {name: "Comment on Attach the supporting record"});
    fireEvent.change(within(dialog).getByLabelText("Comment"), {target: {value: "Check the second page"}});
    fireEvent.click(within(dialog).getByRole("button", {name: "Send comment"}));
    await waitFor(() => expect(message()).toBe(COMMENTED));
};

const assignSecondReviewer = async () => {
    const assignments = await screen.findByRole("region", {name: "Reviewers"});
    fireEvent.click(within(assignments).getByRole("button", {name: "Assign reviewer"}));
    const dialog = await screen.findByRole("dialog", {name: "Assign a reviewer"});
    chooseReviewOption(dialog, "Stage", "Content review");
    chooseReviewOption(dialog, "Reviewer", "Second reviewer");
    fireEvent.click(within(dialog).getByRole("button", {name: "Assign"}));
    await waitFor(() => expect(message()).toBe(ASSIGNED));
};

const recordDecisions = async () => {
    const record = screen.getByRole("button", {name: "Record decisions"}) as HTMLButtonElement;
    await waitFor(() => expect(record.disabled).toBe(false));
    fireEvent.click(record);
    await waitFor(() => expect(transport.recordInformationRequestReviewDecisions).toHaveBeenCalled());
};

const changeWorksheet = async () => {
    fireEvent.click(await screen.findByRole("radio", {name: "Satisfied"}));
    await screen.findByText("1 unsaved changes");
};

describe("reviewer workspace feedback", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() => {
        vi.resetAllMocks();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(reviewWorkspaceReview());
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            partiesETag: "\"parties-1\"",
            parties: reviewWorkspaceParties()
        });
        vi.mocked(evidence.openInformationRequestSubmittedEvidenceContent).mockResolvedValue(new Blob(["record"]));
        for (const command of [
            transport.overrideInformationRequestReviewItem,
            transport.reconsiderInformationRequestReview,
            transport.recordInformationRequestReviewComment,
            transport.assignInformationRequestReviewer,
            transport.changeInformationRequestReviewAssignment,
        ]) vi.mocked(command).mockResolvedValue(savedReviewCommand);
    });

    afterEach(cleanup);

    it("shows a decision refusal when no earlier command left a message", async () => {
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockRejectedValue({errorMessage: FINDING_REQUIRED});
        renderWorkspace();
        await screen.findByRole("button", {name: "Record decisions"});

        await recordDecisions();

        await waitFor(() => expect(message()).toBe(FINDING_REQUIRED));
    });

    it("shows a decision refusal that follows a recorded comment", async () => {
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockRejectedValue({errorMessage: FINDING_REQUIRED});
        renderWorkspace();
        await comment();

        await recordDecisions();

        await waitFor(() => expect(message()).toBe(FINDING_REQUIRED));
    });

    it("shows a stale worksheet that follows an assignment", async () => {
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValue({outcome: "STALE"} as never);
        renderWorkspace();
        await assignSecondReviewer();
        await changeWorksheet();

        fireEvent.click(screen.getByRole("button", {name: "Save worksheet"}));

        await waitFor(() => expect(transport.saveInformationRequestReviewWorksheet).toHaveBeenCalled());
        await waitFor(() => expect(message()).toBe(WORKSHEET_STALE));
    });

    it("shows a finding refusal that follows a recorded comment", async () => {
        vi.mocked(transport.recordInformationRequestReviewFinding).mockRejectedValue({errorMessage: "The finding could not be kept."});
        renderWorkspace();
        await comment();
        fireEvent.click(screen.getByRole("button", {name: "Add finding"}));
        await screen.findByText("Finding for Attach the supporting record");
        const dialog = document.getElementById("information-request-review-finding-dialog") as HTMLElement;
        fireEvent.change(within(dialog).getByLabelText("Reason code"), {target: {value: "record.incomplete"}});
        fireEvent.change(within(dialog).getByLabelText("What needs attention"), {target: {value: "Page 2 is missing"}});

        fireEvent.click(within(dialog).getByRole("button", {name: "Record finding"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewFinding).toHaveBeenCalled());
        expect(await within(dialog).findByText("The finding could not be kept.")).toBeTruthy();
    });

    it("replaces an earlier comment message with a later successful decision", async () => {
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockResolvedValue({
            outcome: "SAVED",
            data: {review: {...reviewWorkspaceReview().review, state: InformationRequestReviewState.SATISFIED}},
        } as never);
        renderWorkspace();
        await comment();

        await recordDecisions();

        await waitFor(() => expect(message()).toBe("Decisions recorded. This review is satisfied."));
    });

    it("replaces an earlier decision refusal with a later recorded comment", async () => {
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockRejectedValue({errorMessage: FINDING_REQUIRED});
        renderWorkspace();
        await screen.findByRole("button", {name: "Record decisions"});
        await recordDecisions();
        await waitFor(() => expect(message()).toBe(FINDING_REQUIRED));

        await comment();

        expect(message()).toBe(COMMENTED);
    });

    it("withdraws an earlier outcome as soon as another command starts", async () => {
        let settle: () => void = () => undefined;
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockReturnValue(new Promise(resolve => {
            settle = () => resolve({outcome: "STALE"} as never);
        }));
        renderWorkspace();
        await comment();

        await recordDecisions();

        await waitFor(() => expect(message()).toBeNull());
        settle();
        await waitFor(() => expect(message()).toBe(WORKSHEET_STALE));
    });
});
