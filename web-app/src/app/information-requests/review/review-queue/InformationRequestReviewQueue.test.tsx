/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {InformationRequestReviewState} from "../../../models/models.tsx";
import InformationRequestReviewQueue from "./InformationRequestReviewQueue.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const shownTime = (value: string): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

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
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
    });

    afterEach(cleanup);

    it("lists the caller's open review assignments and opens the review workspace", async () =>
    {
        vi.mocked(transport.getInformationRequestReviewQueue).mockResolvedValue([{
            assignmentId: "assignment-a",
            reviewId: "review-a",
            informationRequestId: "request-a",
            exchangeId: "exchange-a",
            reviewStageKey: "content-review",
            packageNumber: 2,
            submissionStageKey: "evidence-stage",
            reviewState: InformationRequestReviewState.IN_REVIEW,
            itemCount: 3,
            dueAt: "2026-09-30T12:00:00Z",
            assignedAt: "2026-09-26T08:00:00Z",
        }]);

        renderQueue();

        expect(await screen.findByText("Submission 2, evidence stage")).toBeTruthy();
        expect(screen.getByText("content review, 3 items")).toBeTruthy();
        expect(screen.getByText("In review")).toBeTruthy();
        expect(screen.getByText(`Due ${shownTime("2026-09-30T12:00:00Z")}`)).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Open review"}));
        expect(await screen.findByText("Review opened")).toBeTruthy();
    });

    it("says so when nothing waits for the caller and lists assigned work whatever the caller's own plan", async () =>
    {
        vi.mocked(transport.getInformationRequestReviewQueue).mockResolvedValue([]);
        renderQueue();
        expect(await screen.findByText("No reviews are waiting for you.")).toBeTruthy();
        cleanup();

        vi.clearAllMocks();
        vi.mocked(usePlanFeature).mockReturnValue(availability(false));
        vi.mocked(transport.getInformationRequestReviewQueue).mockResolvedValue([]);
        renderQueue();
        expect(await screen.findByText("No reviews are waiting for you.")).toBeTruthy();
        expect(screen.queryByText("Information Requests are not included in your plan.")).toBeNull();
    });
});
