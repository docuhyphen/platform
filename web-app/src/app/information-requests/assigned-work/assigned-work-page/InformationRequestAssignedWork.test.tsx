/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {getAssignedInformationRequests} from "../../../../services/informationRequestAuthoringService.ts";
import {getInformationRequestReviewQueue} from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestAssignedWorkDto,
    InformationRequestExecutionStandingKind,
    InformationRequestNextAction,
    InformationRequestShareRoleKey,
    InformationRequestState,
} from "../../../models/models.tsx";
import InformationRequestAssignedWork from "./InformationRequestAssignedWork.tsx";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getAssignedInformationRequests: vi.fn(),
}));

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReviewQueue: vi.fn(),
}));

const assigned = (overrides: Partial<InformationRequestAssignedWorkDto["request"]> = {}): InformationRequestAssignedWorkDto => ({
    exchangeName: "Process collection",
    request: {
        id: "request-1",
        exchangeId: "exchange-1",
        title: "Quarterly records",
        state: InformationRequestState.ISSUED,
        issuedAt: 1_790_000_000_000,
        nextDueAt: null,
        completedCount: 0,
        requiredCount: 2,
        callerRoles: [InformationRequestShareRoleKey.CONTRIBUTOR],
        permissions: {canManage: false, canRespond: true, canReview: false},
        nextAction: InformationRequestNextAction.RESPOND,
        executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        ...overrides,
    },
});

const renderPage = () => render(
    <MemoryRouter initialEntries={["/information-request-assignments"]}>
        <Routes>
            <Route path={"/information-request-assignments"}
                   element={<InformationRequestAssignedWork/>}/>
            <Route path={"/information-requests/:requestId/respond"}
                   element={<p>Respond workspace</p>}/>
        </Routes>
    </MemoryRouter>,
);

describe("InformationRequestAssignedWork", () => {
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
        vi.mocked(getInformationRequestReviewQueue).mockResolvedValue([]);
    });

    afterEach(() => {
        cleanup();
        vi.clearAllMocks();
    });

    it("lists each assigned request with its Exchange and opens the respondent workspace", async () => {
        vi.mocked(getAssignedInformationRequests).mockResolvedValue([assigned()]);
        renderPage();

        expect(await screen.findByText("Quarterly records")).toBeTruthy();
        expect(screen.getByText("In Process collection")).toBeTruthy();
        expect(screen.getByText("No reviews are waiting for you.")).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Respond: Quarterly records"}));

        expect(await screen.findByText("Respond workspace")).toBeTruthy();
    });

    it("says when nothing is assigned", async () => {
        vi.mocked(getAssignedInformationRequests).mockResolvedValue([]);
        renderPage();

        expect(await screen.findByText("No Information Requests are assigned to you.")).toBeTruthy();
    });

    it("states a refused read and reads again on Try again", async () => {
        vi.mocked(getAssignedInformationRequests)
            .mockRejectedValueOnce({})
            .mockResolvedValueOnce([assigned()]);
        renderPage();

        expect((await screen.findByRole("alert")).textContent).toContain("Your requests could not be loaded.");
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByText("Quarterly records")).toBeTruthy();
    });
});
