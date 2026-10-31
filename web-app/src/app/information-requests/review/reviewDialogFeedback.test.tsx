/** @vitest-environment jsdom */
import {cleanup, render, screen, within} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import ReviewCommentDialog from "./review-comment-dialog/ReviewCommentDialog.tsx";
import ReviewOverrideDialog from "./review-override-dialog/ReviewOverrideDialog.tsx";
import ReviewReasonDialog from "./review-reason-dialog/ReviewReasonDialog.tsx";
import AssignmentChangeDialog from "./assignment-change-dialog/AssignmentChangeDialog.tsx";
import AssignReviewerDialog from "./assign-reviewer-dialog/AssignReviewerDialog.tsx";

const refusal = {intent: "error" as const, text: "The current review does not permit this change."};
const callbacks = {busy: false, message: refusal, onConfirm: vi.fn(), onDismiss: vi.fn()};

describe("review dialog feedback", () => {
    beforeAll(() => vi.stubGlobal("ResizeObserver", class {
        observe() {
        }

        unobserve() {
        }

        disconnect() {
        }
    }));
    afterEach(cleanup);
    it.each([
        ["comment", () => <ReviewCommentDialog label={"Recorded detail"}
                                               {...callbacks}/>],
        ["override", () => <ReviewOverrideDialog waiverPermitted={false}
                                                 {...callbacks}/>],
        ["reconsider", () => <ReviewReasonDialog id={"reconsider"}
                                                 title={"Reconsider"}
                                                 explanation={"Opens another review"}
                                                 confirmLabel={"Reconsider"}
                                                 {...callbacks}/>],
        ["assignment change", () => <AssignmentChangeDialog title={"Recuse"}
                                                            change={"recusal"}
                                                            candidates={[]}
                                                            {...callbacks}/>],
        ["assignment", () => <AssignReviewerDialog stages={[]}
                                                   parties={[]}
                                                   {...callbacks}/>],
    ] as const)("keeps %s refusals inside its dialog", (_, content) => {
        render(content());
        expect(within(screen.getByRole("dialog")).getByText(refusal.text)).toBeTruthy();
    });
});