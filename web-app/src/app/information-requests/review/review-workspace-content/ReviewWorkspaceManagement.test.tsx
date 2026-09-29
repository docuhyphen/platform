/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as evidence from "../../../../services/informationRequestEvidenceService.ts";
import * as runtime from "../../../../services/informationRequestRuntimeService.ts";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestReviewAggregation,
    InformationRequestReviewAssignmentState,
    InformationRequestReviewCommentRole,
    InformationRequestReviewDecisionKind,
    InformationRequestReviewDto,
    InformationRequestReviewItemStanding,
    InformationRequestReviewKind,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
    InformationRequestReviewTieResolution,
    InformationRequestReviewVisibility,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseWorkspaceDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import ReviewWorkspaceContent from "./ReviewWorkspaceContent.tsx";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

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
vi.mock("../../../../services/informationRequestRuntimeService.ts", () => ({getInformationRequestResponseWorkspace: vi.fn()}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestParties: vi.fn()}));
vi.mock("../../../../services/informationRequestEvidenceService.ts", () => ({openInformationRequestEvidenceContent: vi.fn()}));

const saved = {outcome: "SAVED" as const, responseETag: "\"responses:3\"", data: {} as never};

const review = (state = InformationRequestReviewState.IN_REVIEW): InformationRequestReviewDto => ({
    review: {
        id: "review-a",
        informationRequestId: "request-a",
        packageId: "package-a",
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state,
        openedAt: "2026-09-26T08:00:00Z",
        reviewETag: "\"review:2\"",
    },
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [{
        stageKey: "content",
        title: "Content review",
        position: 1,
        aggregation: InformationRequestReviewAggregation.ALL,
        minimumReviewerCount: 2,
        tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        overridePermitted: true,
        excludesResponseParties: true,
        excludesPriorReviewers: true,
        state: InformationRequestReviewStageState.OPEN,
        items: [{submissionItemId: "item-b", standing: InformationRequestReviewItemStanding.PENDING}],
    }],
    items: [{
        submissionItemId: "item-b",
        requirementId: "requirement-b",
        requirementKey: "supporting-record",
        requirementType: InformationRequestRequirementType.DOCUMENT,
        occurrencePath: "root",
        reviewed: true,
        contentVisible: true,
        disposition: InformationRequestResponseDisposition.PROVIDED,
        evidence: [{artifactId: "artifact-a", evidenceVersionId: "evidence-a", versionNumber: 1, contentLength: 2048, conformance: "CONFORMING"}],
    }],
    assignments: [{
        id: "assignment-a",
        stageKey: "content",
        reviewerPartyId: "party-a",
        state: InformationRequestReviewAssignmentState.ACTIVE,
        assignedAt: "2026-09-26T08:05:00Z",
        callerIsReviewer: true,
    }],
    decisions: [{
        id: "decision-a",
        stageKey: "content",
        submissionItemId: "item-b",
        kind: InformationRequestReviewDecisionKind.REVIEWER,
        assignmentId: "assignment-a",
        outcome: InformationRequestReviewOutcome.CHANGES_REQUIRED,
        decidedAt: "2026-09-26T09:00:00Z",
        decidedByCaller: true,
    }],
    findings: [],
    comments: [{
        id: "comment-a",
        submissionItemId: "item-b",
        requirementId: "requirement-b",
        authorRole: InformationRequestReviewCommentRole.RESPONDENT,
        visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
        body: "Page 2 is attached",
        createdAt: "2026-09-26T09:30:00Z",
        authoredByCaller: false,
    }],
    remediations: [{findingId: "finding-z", remediatedByPackageId: "package-b", remediatedByItemId: "item-c"} as never],
    worksheets: [{assignmentId: "assignment-a", draftETag: "\"draft:1\"", entries: []}],
    canManage: true,
});

const respondentWorkspace = {
    templateVersion: {sections: [{requirements: [{id: "binding-b", requirementKey: "supporting-record", prompt: "Attach the supporting record"}]}]},
    responses: [{informationRequestRequirementId: "requirement-b", sourceTemplateBindingId: "binding-b", occurrencePath: "root"}],
} as unknown as InformationRequestResponseWorkspaceDto;

const choose = (container: HTMLElement, label: string, option: string) =>
{
    const select = within(container).getByLabelText(label) as HTMLSelectElement;
    fireEvent.change(select, {target: {value: Array.from(select.options).find(candidate => candidate.textContent === option)?.value ?? ""}});
};

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
        vi.clearAllMocks();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review());
        vi.mocked(runtime.getInformationRequestResponseWorkspace).mockResolvedValue(respondentWorkspace);
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            partiesETag: "\"parties-1\"",
            parties: [
                {id: "party-a", roleKey: InformationRequestShareRoleKey.REVIEWER, label: "First reviewer"},
                {id: "party-b", roleKey: InformationRequestShareRoleKey.REVIEWER, label: "Second reviewer"},
                {id: "party-c", roleKey: InformationRequestShareRoleKey.CONTRIBUTOR, label: "Respondent"},
            ].map(party => ({...party, informationRequestId: "request-a", active: true, assignedAt: "", partyRevision: 1, partyETag: ""})),
        });
        vi.mocked(evidence.openInformationRequestEvidenceContent).mockResolvedValue(new Blob(["record"]));
        for (const command of [
            transport.overrideInformationRequestReviewItem,
            transport.reconsiderInformationRequestReview,
            transport.recordInformationRequestReviewComment,
            transport.assignInformationRequestReviewer,
            transport.changeInformationRequestReviewAssignment,
        ]) vi.mocked(command).mockResolvedValue(saved);
    });

    afterEach(cleanup);

    it("names every control and region of the reviewer workspace", async () =>
    {
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);
        await screen.findByRole("heading", {name: "Attach the supporting record"});

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("names items by their prompts and previews the exact evidence version", async () =>
    {
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        expect(await screen.findByRole("heading", {name: "Attach the supporting record"})).toBeTruthy();
        expect(screen.getByText("File version 1, 2 KB, conforming")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Preview version 1 of Attach the supporting record"}));

        await waitFor(() => expect(evidence.openInformationRequestEvidenceContent)
            .toHaveBeenCalledWith("request-a", "requirement-b", "artifact-a", "evidence-a", "preview"));
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

    it("states each stage's separation-of-duties rules and the decision and remediation history", async () =>
    {
        render(<ReviewWorkspaceContent requestId={"request-a"}
                                       reviewId={"review-a"}/>);

        expect(await screen.findByText("Anyone who answered this request cannot review it.")).toBeTruthy();
        expect(screen.getByText("Anyone who reviewed an earlier submission cannot review this one.")).toBeTruthy();
        expect(screen.getByText("At least 2 reviewers decide each item.")).toBeTruthy();
        expect(screen.getByText("A manager can override an item's decision.")).toBeTruthy();
        const history = screen.getByRole("region", {name: "Decision history"});
        expect(within(history).getByText(/Attach the supporting record: Changes required by you/)).toBeTruthy();
        expect(within(history).getByText("1 finding was addressed by a later submission.")).toBeTruthy();
    });
});
