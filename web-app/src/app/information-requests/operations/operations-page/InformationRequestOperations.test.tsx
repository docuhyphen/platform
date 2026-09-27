/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestNoticeDeliveryState,
    InformationRequestOperationsException,
    InformationRequestSlaStatus,
    InformationRequestState,
} from "../../../models/models.tsx";
import InformationRequestOperations from "./InformationRequestOperations.tsx";

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestOperations: vi.fn()}));
vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));
vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({currentSession: {activeOrganizationId: null}, hasCapability: () => false}),
}));

const availability = (isAvailable: boolean) => ({
    isKnown: true,
    isIncluded: isAvailable,
    isEnforced: true,
    isDiscoverable: isAvailable,
    isAvailable,
    upgradePlanCode: null,
});

const row = {
    requestId: "request-a-0000",
    exchangeId: "exchange-a",
    templateVersionId: "version-a",
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    createdAt: "2026-09-20T08:00:00Z",
    ageSeconds: 3 * 86400 + 2 * 3600,
    slaStatus: InformationRequestSlaStatus.OVERDUE,
    nearestDueAt: "2026-09-25T08:00:00Z",
    clockCount: 1,
    reminderCount: 2,
    noticeCounts: {[InformationRequestNoticeDeliveryState.DELIVERED]: 2},
    exceptionCounts: {[InformationRequestOperationsException.NOTICE_UNDELIVERABLE]: 1},
};

const renderOperations = () => render(
    <MemoryRouter initialEntries={["/information-request-operations"]}>
        <Routes>
            <Route path={"/information-request-operations"}
                   element={<InformationRequestOperations/>}/>
            <Route path={"/information-request-operations/:requestId"}
                   element={<p>Request detail opened</p>}/>
        </Routes>
    </MemoryRouter>,
);

describe("InformationRequestOperations", () =>
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

    it("lists each request with its service level, notices, and exceptions and opens its detail", async () =>
    {
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({items: [row], total: 1, limit: 25, offset: 0});

        renderOperations();

        expect(await screen.findByText("Request request-, In progress")).toBeTruthy();
        expect(screen.getByText("Overdue")).toBeTruthy();
        expect(screen.getByText("Notices: 2 delivered")).toBeTruthy();
        expect(screen.getByText("Needs attention: 1 undeliverable notices")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Open"}));
        expect(await screen.findByText("Request detail opened")).toBeTruthy();
    });

    it("asks again for only the requests with exceptions from the first page", async () =>
    {
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({items: [], total: 0, limit: 25, offset: 0});

        renderOperations();
        expect(await screen.findByText("No Information Requests match these filters.")).toBeTruthy();
        fireEvent.click(screen.getByRole("switch", {name: "Exceptions only"}));

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith({
            slaStatus: undefined,
            exceptionsOnly: true,
            limit: 25,
            offset: 0,
        }));
    });

    it("stays closed without the plan feature", () =>
    {
        vi.mocked(usePlanFeature).mockReturnValue(availability(false));

        renderOperations();

        expect(screen.getByText("Information Requests are not included in your plan.")).toBeTruthy();
        expect(transport.getInformationRequestOperations).not.toHaveBeenCalled();
    });
});
