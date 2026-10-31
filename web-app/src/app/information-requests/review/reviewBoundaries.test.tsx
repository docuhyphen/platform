/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import ReviewItemDecisionControls from "./review-item-decision/ReviewItemDecisionControls.tsx";
import ReviewAssignmentRow from "./review-assignment-row/ReviewAssignmentRow.tsx";
import ReviewStageSummary from "./review-stage-summary/ReviewStageSummary.tsx";
import ReviewWorksheetActions from "./review-worksheet-actions/ReviewWorksheetActions.tsx";
import ReviewFindingDialog from "./review-finding-dialog/ReviewFindingDialog.tsx";
import {InformationRequestReviewAssignmentState} from "../../models/models.tsx";
import {reviewWorkspaceReview} from "./testing/reviewWorkspaceFixtures.ts";

describe("review boundaries", () => {
    beforeAll(() => vi.stubGlobal("ResizeObserver", class {
        observe() {
        }

        unobserve() {
        }

        disconnect() {
        }
    }));
    afterEach(cleanup);

    it("offers a waiver only when the item permits it", () => {
        render(<ReviewItemDecisionControls itemId={"item-a"}
                                           busy={false}
                                           hasFinding={false}
                                           waiverPermitted={false}
                                           onChange={vi.fn()}
                                           onAddFinding={vi.fn()}/>);
        expect(screen.queryByRole("radio", {name: "Waived"})).toBeNull();
        expect(screen.getByRole("radio", {name: "Satisfied"})).toBeTruthy();
    });
    it("lets a manager delegate an assignment held by another reviewer", () => {
        render(<ReviewAssignmentRow assignment={{...reviewWorkspaceReview().assignments[0], callerIsReviewer: false}}
                                    reviewerLabel={"First reviewer"}
                                    stageTitle={"Content review"}
                                    canManage={true}
                                    onChange={vi.fn()}/>);
        expect(screen.getByRole("button", {name: "Delegate Content review"})).toBeTruthy();
    });

    it("describes the earlier-stage and reopened-review exclusion accurately", () => {
        render(<ReviewStageSummary stages={reviewWorkspaceReview().stages}/>);
        expect(screen.getByText("Anyone who decided an earlier stage or the earlier review cannot review this stage.")).toBeTruthy();
    });

    it("announces worksheet save standing politely", () => {
        render(<ReviewWorksheetActions busy={false}
                                       changedCount={2}
                                       onSave={vi.fn()}
                                       onRecord={vi.fn()}/>);
        expect(screen.getByText("2 unsaved changes").getAttribute("aria-live")).toBe("polite");
    });

    it("offers an optional exact file for a response finding and distinguishes file names", () => {
        const item = reviewWorkspaceReview().items[0];
        const confirm = vi.fn();
        render(<ReviewFindingDialog item={{
            ...item, evidence: [
                {...item.evidence[0], declaredFileName: "first-record.pdf"},
                {...item.evidence[0], evidenceVersionId: "evidence-b", declaredFileName: "second-record.pdf"},
            ]
        }}
                                    label={"Supporting record"}
                                    earlierFindings={[]}
                                    busy={false}
                                    onConfirm={confirm}
                                    onDismiss={vi.fn()}/>);
        const select = screen.getByLabelText("File (optional)");
        fireEvent.click(select);
        expect(screen.getByRole("option", {name: "first-record.pdf, version 1"})).toBeTruthy();
        expect(screen.getByRole("option", {name: "second-record.pdf, version 1"})).toBeTruthy();
        fireEvent.click(screen.getByRole("option", {name: "second-record.pdf, version 1"}));
        fireEvent.change(screen.getByLabelText("Reason code"), {target: {value: "record.incomplete"}});
        fireEvent.change(screen.getByLabelText("What needs attention"), {target: {value: "Confirm the recorded detail"}});
        fireEvent.click(screen.getByRole("button", {name: "Record finding"}));
        expect(confirm).toHaveBeenCalledWith(expect.objectContaining({
            evidenceVersionId: "evidence-b",
            correctionScope: "RESPONSE"
        }));
    });
    it("shows when and why an assignment changed", () => {
        render(<ReviewAssignmentRow assignment={{
            ...reviewWorkspaceReview().assignments[0],
            state: InformationRequestReviewAssignmentState.RECUSED, changedAt: Date.parse("2026-10-20T09:00:00Z"),
            changeReasonCode: "conflict", changeNarrative: "Prepared the recorded detail"
        }}
                                    reviewerLabel={"First reviewer"}
                                    stageTitle={"Content review"}
                                    canManage={true}
                                    onChange={vi.fn()}/>);
        expect(screen.getByText(/Reason: conflict/)).toBeTruthy();
        expect(screen.getByText(/Prepared the recorded detail/)).toBeTruthy();
        expect(screen.getByText(/Changed/)).toBeTruthy();
    });
});