/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as reviewService from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestReviewAssignmentDto,
    InformationRequestReviewAssignmentState,
    InformationRequestReviewDto,
    InformationRequestReviewKind,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewState,
} from "../../../models/models.tsx";
import RequestReviewsPanel from "./RequestReviewsPanel.tsx";

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    listInformationRequestReviews: vi.fn(),
}));

const assignment = (state = InformationRequestReviewAssignmentState.ACTIVE): InformationRequestReviewAssignmentDto => ({
    id: `assignment-${state.toLowerCase()}`,
    stageKey: "first-check",
    reviewerPartyId: "party-a",
    state,
    assignedAt: Date.parse("2026-10-02T09:00:00Z"),
    callerIsReviewer: false,
    delegationCandidates: [],
});

const review = (
    id: string,
    reviewNumber: number,
    kind: InformationRequestReviewKind,
    state: InformationRequestReviewState,
    assignments: InformationRequestReviewAssignmentDto[] = [],
): InformationRequestReviewDto => ({
    review: {
        id,
        informationRequestId: "request-a",
        packageId: `package-${reviewNumber}`,
        packageNumber: reviewNumber,
        reviewNumber,
        kind,
        state,
        openedAt: Date.parse("2026-10-01T09:00:00Z"),
        reviewETag: `"${id}:1"`,
    },
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [],
    items: [],
    assignments,
    decisions: [],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [],
    canManage: true,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: true,
    canComment: true,
    itemsAwaitingStepUp: 0,
});

const renderPanel = () =>
    render(
        <MemoryRouter initialEntries={["/information-requests/request-a/manage"]}>
            <Routes>
                <Route path={"/information-requests/:requestId/manage"}
                       element={<RequestReviewsPanel requestId={"request-a"}/>}/>
                <Route path={"/information-requests/:requestId/reviews/:reviewId"}
                       element={<p id={"opened-review"}>Opened review</p>}/>
            </Routes>
        </MemoryRouter>,
    );

describe("RequestReviewsPanel", () => {
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

    beforeEach(() => vi.resetAllMocks());
    afterEach(cleanup);

    it("lists each review with its state and says when one has no reviewer yet", async () => {
        vi.mocked(reviewService.listInformationRequestReviews).mockResolvedValue([
            review("review-a", 1, InformationRequestReviewKind.INITIAL, InformationRequestReviewState.CHANGES_REQUESTED, [
                assignment(),
                assignment(InformationRequestReviewAssignmentState.REVOKED),
            ]),
            review("review-b", 2, InformationRequestReviewKind.RESUBMISSION, InformationRequestReviewState.PENDING),
        ]);

        renderPanel();

        expect(await screen.findByText("Review 2")).toBeTruthy();
        const pending = document.getElementById("information-request-review-review-b") as HTMLElement;
        expect(pending.textContent).toContain("Review of a resubmission, submission 2");
        expect(pending.textContent).toContain("Waiting for a reviewer");
        expect(pending.textContent).toContain("No reviewer assigned yet");
        const assigned = document.getElementById("information-request-review-review-a") as HTMLElement;
        expect(assigned.textContent).toContain("Changes requested");
        expect(assigned.textContent).toContain("1 reviewer assigned");
        expect(reviewService.listInformationRequestReviews).toHaveBeenCalledWith("request-a");
    });

    it("opens a review from the list so a manager can assign its reviewer", async () => {
        vi.mocked(reviewService.listInformationRequestReviews).mockResolvedValue([
            review("review-b", 2, InformationRequestReviewKind.RESUBMISSION, InformationRequestReviewState.PENDING),
        ]);
        renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Open review 2"}));

        expect(await screen.findByText("Opened review")).toBeTruthy();
    });

    it("shows nothing when the request has no reviews or the caller may not read them", async () => {
        vi.mocked(reviewService.listInformationRequestReviews).mockResolvedValueOnce([]);
        renderPanel();
        await waitFor(() => expect(reviewService.listInformationRequestReviews).toHaveBeenCalledTimes(1));
        expect(document.getElementById("information-request-reviews")).toBeNull();
        cleanup();

        vi.mocked(reviewService.listInformationRequestReviews).mockRejectedValueOnce({
            errorMessage: "Access denied",
            reasonCode: "INFORMATION_REQUEST_FORBIDDEN",
        });
        renderPanel();
        await waitFor(() => expect(reviewService.listInformationRequestReviews).toHaveBeenCalledTimes(2));
        expect(document.getElementById("information-request-reviews")).toBeNull();
    });

    it("states a failed read under the Reviews heading and lists the reviews once Try again reads them", async () => {
        vi.mocked(reviewService.listInformationRequestReviews)
            .mockRejectedValueOnce({status: 500})
            .mockResolvedValueOnce([
                review("review-b", 2, InformationRequestReviewKind.RESUBMISSION, InformationRequestReviewState.PENDING),
            ]);
        renderPanel();

        expect(await screen.findByText("The reviews of this request could not be loaded.")).toBeTruthy();
        expect(screen.getByRole("heading", {name: "Reviews"})).toBeTruthy();
        expect(document.getElementById("information-request-reviews-list")).toBeNull();

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByText("No reviewer assigned yet")).toBeTruthy();
        expect(screen.queryByText("The reviews of this request could not be loaded.")).toBeNull();
        expect(reviewService.listInformationRequestReviews).toHaveBeenCalledTimes(2);
    });
});
