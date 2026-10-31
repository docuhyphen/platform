/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {getInformationRequestParties} from "../../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestReviewAggregation,
    InformationRequestReviewAssignmentState,
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
    InformationRequestState,
} from "../../../models/models.tsx";
import InformationRequestReviewWorkspace from "./InformationRequestReviewWorkspace.tsx";

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReview: vi.fn(),
    saveInformationRequestReviewWorksheet: vi.fn(),
    recordInformationRequestReviewDecisions: vi.fn(),
    recordInformationRequestReviewFinding: vi.fn(),
}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getInformationRequestParties: vi.fn(),
}));

const summary = {
    id: "review-a",
    informationRequestId: "request-a",
    packageId: "package-a",
    packageNumber: 1,
    reviewNumber: 1,
    kind: InformationRequestReviewKind.INITIAL,
    state: InformationRequestReviewState.IN_REVIEW,
    openedAt: Date.parse("2026-09-26T08:00:00Z"),
    reviewETag: "\"review:4\"",
};

const stage = (stageKey: string, title: string, position: number, state: InformationRequestReviewStageState) => ({
    stageKey,
    title,
    position,
    aggregation: InformationRequestReviewAggregation.ANY,
    minimumReviewerCount: 1,
    tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
    overridePermitted: false,
    excludesResponseParties: false,
    excludesPriorReviewers: false,
    state,
    items: [{submissionItemId: "item-a", standing: InformationRequestReviewItemStanding.PENDING}],
});

const assignment = (id: string, stageKey: string, decidedAt?: number) => ({
    id,
    stageKey,
    reviewerPartyId: "party-a",
    state: InformationRequestReviewAssignmentState.ACTIVE,
    assignedAt: Date.parse("2026-09-26T08:05:00Z"),
    decidedAt,
    callerIsReviewer: true,
    delegationCandidates: [],
});

const sheet = (assignmentId: string, revision: number, entries: InformationRequestReviewDto["worksheets"][number]["entries"] = []) =>
    ({assignmentId, draftETag: `"${assignmentId}:${revision}"`, draftRevision: revision, entries});

const base = (): InformationRequestReviewDto => ({
    review: summary,
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [],
    items: [{
        submissionItemId: "item-a",
        requirementId: "requirement-a",
        requirementKey: "supporting-record",
        requirementType: InformationRequestRequirementType.FIELD,
        occurrencePath: "root",
        reviewed: true,
        contentVisible: true,
        waiverPermitted: false,
        disposition: InformationRequestResponseDisposition.PROVIDED,
        fieldValue: "Synthetic record",
        evidence: [],
    }],
    assignments: [],
    decisions: [],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [],
    canManage: false,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: false,
    canComment: true,
    itemsAwaitingStepUp: 0,
});

const sequential = (): InformationRequestReviewDto => ({
    ...base(),
    stages: [
        stage("first-check", "Initial check", 1, InformationRequestReviewStageState.SETTLED),
        stage("further-check", "Further check", 2, InformationRequestReviewStageState.OPEN),
    ],
    assignments: [assignment("assignment-first", "first-check", Date.parse("2026-09-26T09:00:00Z")), assignment("assignment-further", "further-check")],
    decisions: [{
        id: "decision-first",
        stageKey: "first-check",
        submissionItemId: "item-a",
        kind: InformationRequestReviewDecisionKind.REVIEWER,
        assignmentId: "assignment-first",
        outcome: InformationRequestReviewOutcome.SATISFIED,
        narrative: "Checked against the request",
        decidedAt: Date.parse("2026-09-26T09:00:00Z"),
        decidedByCaller: true,
    }],
    worksheets: [
        sheet("assignment-first", 2, [{
            submissionItemId: "item-a",
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "Checked against the request"
        }]),
        sheet("assignment-further", 1),
    ],
});

const parallel = (): InformationRequestReviewDto => ({
    ...base(),
    reviewStageOrdering: InformationRequestReviewStageOrdering.PARALLEL,
    stages: [
        stage("content-check", "Content check", 1, InformationRequestReviewStageState.OPEN),
        stage("record-check", "Record check", 2, InformationRequestReviewStageState.OPEN),
    ],
    assignments: [assignment("assignment-records", "record-check"), assignment("assignment-content", "content-check")],
    findings: [{
        id: "finding-records",
        submissionItemId: "item-a",
        requirementId: "requirement-a",
        reasonCode: "record.incomplete",
        narrative: "A page is missing",
        severity: InformationRequestFindingSeverity.MAJOR,
        visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
        correctionScope: InformationRequestFindingCorrectionScope.RESPONSE,
        recordedAt: Date.parse("2026-09-26T08:30:00Z"),
        recordedByCaller: true,
        reviewId: "review-a",
        assignmentId: "assignment-records",
    }],
    worksheets: [sheet("assignment-records", 1), sheet("assignment-content", 1)],
});

const renderWorkspace = () => render(
    <MemoryRouter initialEntries={["/information-requests/request-a/reviews/review-a"]}>
        <Routes>
            <Route path={"/information-requests/:requestId/reviews/:reviewId"}
                   element={<InformationRequestReviewWorkspace/>}/>
        </Routes>
    </MemoryRouter>,
);

const decision = async (): Promise<HTMLElement> => {
    await screen.findByText("supporting record");
    const controls = document.getElementById("information-request-review-item-item-a-decision");
    expect(controls, "the item offers a decision in the stage being worked").not.toBeNull();
    return controls as HTMLElement;
};

const checked = (container: HTMLElement, label: string): boolean =>
    (within(container).getByLabelText(label) as HTMLInputElement).checked;

describe("InformationRequestReviewWorkspace with several assignments", () => {
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
        vi.mocked(getInformationRequestParties).mockResolvedValue({parties: [], partiesETag: ""});
    });

    afterEach(cleanup);

    it("decides the stage still open after the reviewer recorded an earlier one and records it under that assignment", async () => {
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(sequential())
            .mockResolvedValue({...sequential(), review: {...summary, state: InformationRequestReviewState.SATISFIED}});
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"assignment-further:2\"",
            data: sheet("assignment-further", 2, [{
                submissionItemId: "item-a",
                outcome: InformationRequestReviewOutcome.SATISFIED
            }]),
        });
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:3\"",
            data: {
                requestState: InformationRequestState.CLOSED,
                responseETag: "\"responses:3\"",
                review: {...summary, state: InformationRequestReviewState.SATISFIED},
            },
        });
        renderWorkspace();

        const controls = await decision();
        expect(checked(controls, "Satisfied")).toBe(false);
        expect(screen.queryByLabelText("Stage you are deciding")).toBeNull();
        fireEvent.click(within(controls).getByLabelText("Satisfied"));
        fireEvent.click(screen.getByRole("button", {name: "Record decisions"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewDecisions).toHaveBeenCalled());
        expect(transport.saveInformationRequestReviewWorksheet).toHaveBeenCalledWith("request-a", "review-a", "assignment-further", {
            entries: [{
                submissionItemId: "item-a",
                outcome: InformationRequestReviewOutcome.SATISFIED,
                narrative: undefined
            }],
        }, "\"assignment-further:1\"");
        expect(transport.recordInformationRequestReviewDecisions).toHaveBeenCalledWith("request-a", "review-a", "assignment-further", {
            expectedETag: "\"assignment-further:2\"",
            idempotencyKey: expect.any(String),
        });
    });

    it("lets the reviewer choose between open stages, keeps each stage's unsaved choices, and saves only the chosen stage", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(parallel());
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"assignment-content:2\"",
            data: sheet("assignment-content", 2, [{
                submissionItemId: "item-a",
                outcome: InformationRequestReviewOutcome.CHANGES_REQUIRED
            }]),
        });
        renderWorkspace();
        const stagePicker = await screen.findByLabelText("Stage you are deciding") as HTMLSelectElement;

        expect(Array.from(stagePicker.options).map(option => [option.value, option.textContent])).toEqual([
            ["assignment-content", "1. Content check"],
            ["assignment-records", "2. Record check"],
        ]);
        expect(stagePicker.value).toBe("assignment-content");
        fireEvent.click(within(await decision()).getByLabelText("Changes required"));
        expect(screen.getByText("Record a finding that says what must change before recording this decision.")).toBeTruthy();

        fireEvent.change(stagePicker, {target: {value: "assignment-records"}});
        expect(checked(await decision(), "Changes required")).toBe(false);
        fireEvent.click(within(await decision()).getByLabelText("Changes required"));
        expect(screen.queryByText("Record a finding that says what must change before recording this decision.")).toBeNull();
        expect(Array.from(stagePicker.options).map(option => option.textContent)).toEqual([
            "1. Content check (1 unsaved)",
            "2. Record check (1 unsaved)",
        ]);

        fireEvent.change(stagePicker, {target: {value: "assignment-content"}});
        expect(checked(await decision(), "Changes required")).toBe(true);
        fireEvent.click(screen.getByRole("button", {name: "Save worksheet"}));

        await waitFor(() => expect(transport.saveInformationRequestReviewWorksheet).toHaveBeenCalledTimes(1));
        expect(transport.saveInformationRequestReviewWorksheet).toHaveBeenCalledWith("request-a", "review-a", "assignment-content", {
            entries: [{
                submissionItemId: "item-a",
                outcome: InformationRequestReviewOutcome.CHANGES_REQUIRED,
                narrative: undefined
            }],
        }, "\"assignment-content:1\"");
        await waitFor(() => expect(Array.from(stagePicker.options).map(option => option.textContent)).toEqual([
            "1. Content check",
            "2. Record check (1 unsaved)",
        ]));
    });
});
