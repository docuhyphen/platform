/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes, useParams} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as reviews from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestExchangeListingDto,
    InformationRequestExecutionStandingKind,
    InformationRequestNextAction,
    InformationRequestReviewState,
    InformationRequestShareRoleKey,
    InformationRequestStandingReason,
    InformationRequestState,
    InformationRequestSummaryDto,
} from "../../../models/models.tsx";
import {ExchangeInformationRequestsState} from "../useExchangeInformationRequests.ts";
import ExchangeInformationRequestsTab from "./ExchangeInformationRequestsTab.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReviewQueue: vi.fn(),
    listInformationRequestReviews: vi.fn(),
}));
vi.mock("../../authoring/create-information-request-dialog/CreateInformationRequestDialog.tsx", () => ({
    default: ({open}: {open: boolean}) => open ? <p>Creation opened</p> : null,
}));

const ReviewerWorkspaceProbe = () => <p>{`Reviewer workspace ${useParams().reviewId}`}</p>;

const summary = (overrides: Partial<InformationRequestSummaryDto> = {}): InformationRequestSummaryDto => ({
    id: "request-a",
    exchangeId: "exchange-a",
    title: "Periodic records request",
    state: InformationRequestState.IN_PROGRESS,
    issuedAt: Date.parse("2026-09-20T08:00:00Z"),
    nextDueAt: Date.parse("2026-10-01T12:00:00Z"),
    completedCount: 2,
    requiredCount: 5,
    callerRoles: [InformationRequestShareRoleKey.CONTRIBUTOR],
    permissions: {canManage: false, canRespond: true, canReview: false},
    nextAction: InformationRequestNextAction.RESPOND,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
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
                   element={<ReviewerWorkspaceProbe/>}/>
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
        expect(screen.getByText("2 of 5 answers complete")).toBeTruthy();
        expect(screen.getByText("Your role: Contributor")).toBeTruthy();
        expect(screen.getByText(/^Due /)).toBeTruthy();
        expect(screen.getAllByRole("listitem")).toHaveLength(1);
        expect(screen.queryByText(/hidden/i)).toBeNull();
    });

    it("names every control and region of the tab", () =>
    {
        renderTab(state({requests: [summary()], canCreate: true}));

        expect(unnamedControls(document.body)).toEqual([]);

        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("states progress counts in the viewer's number format", () =>
    {
        renderTab(state({requests: [summary({completedCount: 1000, requiredCount: 1200})], canCreate: false}));

        expect(screen.getByText(`${formatInformationRequestCount(1000)} of ${formatInformationRequestCount(1200)} answers complete`)).toBeTruthy();
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
            requestTitle: "Record collection",
            reviewStageTitle: "Content review",
            packageNumber: 1,
            reviewState: InformationRequestReviewState.IN_REVIEW,
            itemCount: 2,
            assignedAt: Date.parse("2026-09-26T08:00:00Z"),
            executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
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
        expect(await screen.findByText("Reviewer workspace review-c")).toBeTruthy();
    });

    it("opens a reviewer-only party's latest review from View once the request or its review has finished", async () => {
        vi.mocked(reviews.listInformationRequestReviews).mockResolvedValue([
            {review: {id: "review-1", openedAt: Date.parse("2026-09-21T08:00:00Z")}},
            {review: {id: "review-2", openedAt: Date.parse("2026-09-25T08:00:00Z")}},
        ] as never);
        renderTab(state({
            requests: [summary({
                state: InformationRequestState.CLOSED,
                nextAction: InformationRequestNextAction.VIEW,
                callerRoles: [InformationRequestShareRoleKey.REVIEWER],
                permissions: {canManage: false, canRespond: false, canReview: true},
            })],
            canCreate: false,
        }));

        fireEvent.click(screen.getByRole("button", {name: "View: Periodic records request"}));

        expect(await screen.findByText("Reviewer workspace review-2")).toBeTruthy();
        expect(reviews.listInformationRequestReviews).toHaveBeenCalledWith("request-a");
    });

    it("offers Manage beside Respond or Review to a caller who also manages the request", async () => {
        renderTab(state({
            requests: [
                summary({permissions: {canManage: true, canRespond: true, canReview: false}}),
                summary({
                    id: "request-b",
                    title: "Draft collection",
                    permissions: {canManage: false, canRespond: true, canReview: false}
                }),
            ],
            canCreate: false,
        }));

        expect(screen.queryByRole("button", {name: "Manage: Draft collection"})).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Manage: Periodic records request"}));

        expect(await screen.findByText("Author workspace")).toBeTruthy();
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

    it("opens a finished request its owner still manages on the manage page and a respondent's on the response page", async () => {
        const owned = summary({
            state: InformationRequestState.CLOSED,
            nextAction: InformationRequestNextAction.VIEW,
            permissions: {canManage: true, canRespond: false, canReview: false},
        });
        const managed = renderTab(state({requests: [owned], canCreate: false}));
        fireEvent.click(screen.getByRole("button", {name: "View: Periodic records request"}));
        expect(await screen.findByText("Author workspace")).toBeTruthy();
        managed.unmount();

        renderTab(state({
            requests: [{...owned, permissions: {canManage: false, canRespond: true, canReview: false}}],
            canCreate: false,
        }));
        fireEvent.click(screen.getByRole("button", {name: "View: Periodic records request"}));
        expect(await screen.findByText("Respondent workspace")).toBeTruthy();
    });

    it("tells an owner why a new request cannot be created", () =>
    {
        renderTab(state({
            requests: [],
            canCreate: false,
            creationUnavailableReason: InformationRequestStandingReason.FEATURE_NOT_INCLUDED,
        }));

        expect(screen.queryByRole("button", {name: "New Information Request"})).toBeNull();
        expect(screen.getByText(/New Information Requests cannot be created/)).toBeTruthy();
        expect(screen.getByText(/does not include Information Requests/)).toBeTruthy();
    });

    it("marks a request whose changes are paused", () =>
    {
        renderTab(state({
            requests: [summary({executionStanding: {kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED}})],
            canCreate: false,
        }));

        expect(screen.getByText("Changes paused")).toBeTruthy();
    });

    it("tells a caller with no visible request so, and states a refusal", () =>
    {
        const empty = renderTab(state({requests: [], canCreate: false}));
        expect(screen.getByText("No Information Requests in this Exchange are shared with you.")).toBeTruthy();
        empty.unmount();

        renderTab(state(null, "Access denied to list Information Requests"));
        expect(screen.getByText("Access denied to list Information Requests")).toBeTruthy();
    });

    it("states a failed listing with Try again, which reads it again", () => {
        const failed = state(null, "The Information Requests could not be loaded.");
        renderTab(failed);

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(failed.reload).toHaveBeenCalledTimes(1);
    });
});
