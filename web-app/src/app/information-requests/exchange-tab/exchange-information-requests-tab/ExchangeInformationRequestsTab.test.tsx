/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as reviews from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestExchangeListingDto,
    InformationRequestNextAction,
    InformationRequestReviewState,
    InformationRequestShareRoleKey,
    InformationRequestState,
    InformationRequestSummaryDto,
} from "../../../models/models.tsx";
import {ExchangeInformationRequestsState} from "../useExchangeInformationRequests.ts";
import ExchangeInformationRequestsTab from "./ExchangeInformationRequestsTab.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({getInformationRequestReviewQueue: vi.fn()}));
vi.mock("../../authoring/create-information-request-dialog/CreateInformationRequestDialog.tsx", () => ({
    default: ({open}: {open: boolean}) => open ? <p>Creation opened</p> : null,
}));

const summary = (overrides: Partial<InformationRequestSummaryDto> = {}): InformationRequestSummaryDto => ({
    id: "request-a",
    exchangeId: "exchange-a",
    title: "Periodic records request",
    state: InformationRequestState.IN_PROGRESS,
    issuedAt: "2026-09-20T08:00:00Z",
    nextDueAt: "2026-10-01T12:00:00Z",
    completedCount: 2,
    requiredCount: 5,
    callerRoles: [InformationRequestShareRoleKey.CONTRIBUTOR],
    permissions: {canManage: false, canRespond: true, canReview: false},
    nextAction: InformationRequestNextAction.RESPOND,
    ...overrides,
});

const state = (listing: InformationRequestExchangeListingDto | null, error: string | null = null): ExchangeInformationRequestsState => ({
    listing,
    loading: false,
    error,
    visible: true,
    reload: vi.fn(),
});

const renderTab = (tabState: ExchangeInformationRequestsState) => render(
    <MemoryRouter initialEntries={["/exchanges"]}>
        <Routes>
            <Route path={"/exchanges"}
                   element={<ExchangeInformationRequestsTab exchangeId={"exchange-a"}
                                                            state={tabState}/>}/>
            <Route path={"/information-requests/:requestId/respond"}
                   element={<p>Respondent workspace</p>}/>
            <Route path={"/information-requests/:requestId/manage"}
                   element={<p>Author workspace</p>}/>
            <Route path={"/information-requests/:requestId/reviews/:reviewId"}
                   element={<p>Reviewer workspace</p>}/>
        </Routes>
    </MemoryRouter>,
);

describe("ExchangeInformationRequestsTab", () =>
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

    beforeEach(() => vi.clearAllMocks());

    afterEach(cleanup);

    it("lists each request the caller may see with its title, status, due time, progress, and roles, and nothing about others", () =>
    {
        renderTab(state({requests: [summary()], canCreate: false}));

        expect(screen.getByText("Periodic records request")).toBeTruthy();
        expect(screen.getByText("In progress")).toBeTruthy();
        expect(screen.getByText("2 of 5 required answers")).toBeTruthy();
        expect(screen.getByText("Your role: Contributor")).toBeTruthy();
        expect(screen.getByText(/^Due /)).toBeTruthy();
        expect(screen.getAllByRole("listitem")).toHaveLength(1);
        expect(screen.queryByText(/hidden/i)).toBeNull();
    });

    it("names every control and region of the tab", () =>
    {
        renderTab(state({requests: [summary()], canCreate: true}));

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("states progress counts in the viewer's number format", () =>
    {
        renderTab(state({requests: [summary({completedCount: 1000, requiredCount: 1200})], canCreate: false}));

        expect(screen.getByText(`${formatInformationRequestCount(1000)} of ${formatInformationRequestCount(1200)} required answers`)).toBeTruthy();
        expect(formatInformationRequestCount(1000)).not.toBe("1000");
    });

    it("opens the workspace each next action names", async () =>
    {
        vi.mocked(reviews.getInformationRequestReviewQueue).mockResolvedValue([{
            assignmentId: "assignment-c",
            reviewId: "review-c",
            informationRequestId: "request-c",
            exchangeId: "exchange-a",
            reviewStageKey: "content-review",
            packageNumber: 1,
            reviewState: InformationRequestReviewState.IN_REVIEW,
            itemCount: 2,
            assignedAt: "2026-09-26T08:00:00Z",
        }]);
        const listing = {
            requests: [
                summary(),
                summary({id: "request-b", title: "Draft collection", state: InformationRequestState.DRAFT, nextAction: InformationRequestNextAction.COMPLETE_SETUP}),
                summary({id: "request-c", title: "Reviewed collection", nextAction: InformationRequestNextAction.REVIEW}),
            ],
            canCreate: false,
        };

        const respond = renderTab(state(listing));
        fireEvent.click(screen.getByRole("button", {name: "Respond: Periodic records request"}));
        expect(await screen.findByText("Respondent workspace")).toBeTruthy();
        respond.unmount();

        const setup = renderTab(state(listing));
        fireEvent.click(screen.getByRole("button", {name: "Finish setup: Draft collection"}));
        expect(await screen.findByText("Author workspace")).toBeTruthy();
        setup.unmount();

        renderTab(state(listing));
        fireEvent.click(screen.getByRole("button", {name: "Review: Reviewed collection"}));
        expect(await screen.findByText("Reviewer workspace")).toBeTruthy();
    });

    it("offers creation only when the server allows it and keeps retained requests listed without it", () =>
    {
        const offered = renderTab(state({requests: [], canCreate: true}));
        fireEvent.click(screen.getByRole("button", {name: "New Information Request"}));
        expect(screen.getByText("Creation opened")).toBeTruthy();
        offered.unmount();

        renderTab(state({requests: [summary({state: InformationRequestState.CLOSED, nextAction: InformationRequestNextAction.VIEW})], canCreate: false}));

        expect(screen.queryByRole("button", {name: "New Information Request"})).toBeNull();
        expect(screen.getByText("Periodic records request")).toBeTruthy();
        expect(screen.getByRole("button", {name: "View: Periodic records request"})).toBeTruthy();
    });

    it("tells a caller with no visible request so, and states a refusal", () =>
    {
        const empty = renderTab(state({requests: [], canCreate: false}));
        expect(screen.getByText("No Information Requests in this Exchange are shared with you.")).toBeTruthy();
        empty.unmount();

        renderTab(state(null, "Access denied to list Information Requests"));
        expect(screen.getByText("Access denied to list Information Requests")).toBeTruthy();
    });
});
