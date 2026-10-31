/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {InformationRequestExecutionStandingKind, InformationRequestReviewState,} from "../../../models/models.tsx";
import InformationRequestReviewQueue from "./InformationRequestReviewQueue.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const shownTime = (value: number): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({getInformationRequestReviewQueue: vi.fn()}));
vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));

const availability = (isAvailable: boolean) => ({
    isKnown: true,
    isIncluded: isAvailable,
    isEnforced: true,
    isDiscoverable: isAvailable,
    isAvailable,
    upgradePlanCode: null,
});

const renderQueue = () => render(
    <MemoryRouter initialEntries={["/information-request-reviews"]}>
        <Routes>
            <Route path={"/information-request-reviews"}
                   element={<InformationRequestReviewQueue/>}/>
            <Route path={"/information-requests/:requestId/reviews/:reviewId"}
                   element={<p>Review opened</p>}/>
        </Routes>
    </MemoryRouter>,
);

describe("InformationRequestReviewQueue", () =>
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

    beforeEach(() =>
    {
        vi.resetAllMocks();
        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
    });

    afterEach(cleanup);

    it("lets the caller retry a failed queue read", async () => {
        vi.mocked(transport.getInformationRequestReviewQueue).mockRejectedValueOnce({}).mockResolvedValueOnce([]);
        renderQueue();
        await screen.findByText("Your reviews could not be loaded.");
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(await screen.findByText("No reviews are waiting for you.")).toBeTruthy();
    });
    it("lists the caller's open review assignments and opens the review workspace", async () =>
    {
        vi.mocked(transport.getInformationRequestReviewQueue).mockResolvedValue([{
            assignmentId: "assignment-a",
            reviewId: "review-a",
            informationRequestId: "request-a",
            exchangeId: "exchange-a",
            reviewStageKey: "content-review",
            requestTitle: "Record collection",
            exchangeName: "Shared records",
            reviewStageTitle: "Content review",
            packageNumber: 2,
            submissionStageKey: "evidence-stage",
            reviewState: InformationRequestReviewState.IN_REVIEW,
            itemCount: 3,
            dueAt: Date.parse("2026-09-30T12:00:00Z"),
            assignedAt: Date.parse("2026-09-26T08:00:00Z"),
            executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        }]);

        renderQueue();

        expect(await screen.findByText("Submission 2, evidence stage")).toBeTruthy();
        expect(screen.getByText("Record collection")).toBeTruthy();
        expect(screen.getByText("Shared records")).toBeTruthy();
        expect(screen.getByText("Content review, 3 items")).toBeTruthy();
        expect(screen.getByText("In review")).toBeTruthy();
        expect(screen.getByText(`Due ${shownTime(Date.parse("2026-09-30T12:00:00Z"))}`)).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: /^Open review of Record collection/}));
        expect(await screen.findByText("Review opened")).toBeTruthy();
    });

    it("marks a review whose request no longer accepts changes", async () => {
        vi.mocked(transport.getInformationRequestReviewQueue).mockResolvedValue([{
            assignmentId: "assignment-b",
            reviewId: "review-b",
            informationRequestId: "request-b",
            exchangeId: "exchange-a",
            reviewStageKey: "content-review",
            requestTitle: "Record collection",
            exchangeName: "Shared records",
            reviewStageTitle: "Content review",
            packageNumber: 1,
            reviewState: InformationRequestReviewState.IN_REVIEW,
            itemCount: 1,
            assignedAt: Date.parse("2026-09-26T08:00:00Z"),
            executionStanding: {kind: InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED},
        }]);

        renderQueue();

        expect(await screen.findByText("Stopped")).toBeTruthy();
    });

    it("says so when nothing waits for the caller and lists assigned work whatever the caller's own plan", async () =>
    {
        vi.mocked(transport.getInformationRequestReviewQueue).mockResolvedValue([]);
        renderQueue();
        expect(await screen.findByText("No reviews are waiting for you.")).toBeTruthy();
        cleanup();

        vi.resetAllMocks();
        vi.mocked(usePlanFeature).mockReturnValue(availability(false));
        vi.mocked(transport.getInformationRequestReviewQueue).mockResolvedValue([]);
        renderQueue();
        expect(await screen.findByText("No reviews are waiting for you.")).toBeTruthy();
        expect(screen.queryByText("Information Requests are not included in your plan.")).toBeNull();
    });
});
