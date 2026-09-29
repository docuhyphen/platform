/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import * as records from "../../../../services/recordPreservationService.ts";
import {
    InformationRequestClockEventKind,
    InformationRequestClockState,
    InformationRequestNoticeDeliveryState,
    InformationRequestNoticeKind,
    RecordPreservationHoldStatus,
    RecordPreservationScope,
} from "../../../models/models.tsx";
import InformationRequestOperationsDetail from "./InformationRequestOperationsDetail.tsx";

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({
    getInformationRequestClocks: vi.fn(),
    getInformationRequestNotices: vi.fn(),
    getInformationRequestAuditEvents: vi.fn(),
    getInformationRequestAuditReconciliation: vi.fn(),
    getInformationRequestDisposalStanding: vi.fn(),
    getInformationRequestRecordExports: vi.fn(),
    getInformationRequestRecordExport: vi.fn(),
    createInformationRequestRecordExport: vi.fn(),
}));
vi.mock("../../../../services/recordPreservationService.ts", () => ({
    INFORMATION_REQUEST_RECORD_TYPE: "INFORMATION_REQUEST",
    placeRecordPreservationHold: vi.fn(),
}));
vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));
vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({currentSession: {activeOrganizationId: null}, hasCapability: () => false}),
}));

const renderDetail = () => render(
    <MemoryRouter initialEntries={["/information-request-operations/request-a"]}>
        <Routes>
            <Route path={"/information-request-operations/:requestId"}
                   element={<InformationRequestOperationsDetail/>}/>
            <Route path={"/information-requests/:requestId/manage"}
                   element={<p>Management workspace opened</p>}/>
        </Routes>
    </MemoryRouter>,
);

describe("InformationRequestOperationsDetail", () =>
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
        vi.mocked(usePlanFeature).mockReturnValue({
            isKnown: true, isIncluded: true, isEnforced: true, isDiscoverable: true, isAvailable: true, upgradePlanCode: null,
        });
        vi.mocked(transport.getInformationRequestClocks).mockResolvedValue([{
            id: "clock-a", clockKey: "response-window", policyVersionId: "policy-a", policyVersionNumber: 2,
            clockType: "CALENDAR", urgency: "STANDARD", receivedAt: "2026-09-20T08:00:00Z",
            state: InformationRequestClockState.RUNNING, dueAt: "2026-09-25T08:00:00Z", dueCycle: 1,
            overdueAt: "2026-09-25T08:00:00Z", clockETag: "etag",
            events: [{eventNumber: 1, eventKind: InformationRequestClockEventKind.OVERDUE, dueCycle: 1, occurredAt: "2026-09-25T08:00:00Z"}],
        }]);
        vi.mocked(transport.getInformationRequestNotices).mockResolvedValue([{
            noticeIntentId: "intent-a", noticeKind: InformationRequestNoticeKind.RESPONSE_OVERDUE, partyId: "party-a",
            deliveryState: InformationRequestNoticeDeliveryState.DELIVERED, owedAt: "2026-09-25T08:00:00Z",
            maskedEndpoint: "p***@example.test", endpointState: "RESOLVED", renderedSubject: "A response is overdue",
            attempts: [{attemptNumber: 1, outcome: "DELIVERED", attemptedAt: "2026-09-25T08:01:00Z"}], sequenceAllocations: [],
        }]);
        vi.mocked(transport.getInformationRequestAuditReconciliation).mockResolvedValue({
            requestId: "request-a", reconciled: false, auditedTransitionCount: 3, matchedTransitionCount: 2, unsealedEventCount: 0,
            missing: [{transitionId: "transition-c", sequenceNumber: 3, mutation: "CLOSE", expectedEventTypeKey: "information_request.request.closed"}],
            unmatched: [],
        });
        vi.mocked(transport.getInformationRequestAuditEvents).mockResolvedValue({
            items: [{
                eventId: "event-a", eventTypeKey: "information_request.request.issued", eventClass: "LIFECYCLE", category: "INFORMATION_REQUEST",
                outcome: "SUCCESS", occurredAt: "2026-09-20T08:00:00Z", recordedAt: "2026-09-20T08:00:01Z", actorKind: "USER",
                sealed: true, payload: {state: "ISSUED"}, withheldKeyCount: 2,
            }],
            total: 1, limit: 50, offset: 0,
        });
        vi.mocked(transport.getInformationRequestDisposalStanding).mockResolvedValue({
            requestId: "request-a", eligible: false, reasonCode: "INFORMATION_REQUEST_RECORD_NOT_FINISHED",
            detail: "Only a finished request is disposed", holds: [], retainedObjectCount: 0,
        });
        vi.mocked(transport.getInformationRequestRecordExports).mockResolvedValue([]);
    });

    afterEach(cleanup);

    it("shows the request's clocks, notice history, and audit history with its reconciliation", async () =>
    {
        renderDetail();

        expect(await screen.findByText("response window")).toBeTruthy();
        expect(screen.getByText(/Overdue since/)).toBeTruthy();

        fireEvent.click(screen.getByRole("tab", {name: "Notices"}));
        expect(await screen.findByText("Response overdue")).toBeTruthy();
        expect(screen.getByText("Notice delivered")).toBeTruthy();
        expect(screen.getByText(/To p\*\*\*@example.test/)).toBeTruthy();

        fireEvent.click(screen.getByRole("tab", {name: "Audit history"}));
        expect(await screen.findByText("The audit history does not reconcile")).toBeTruthy();
        expect(screen.getByText("1 audited changes have no audit record, first at change 3.")).toBeTruthy();
        expect(await screen.findByText("state: ISSUED, 2 values withheld")).toBeTruthy();
    });

    it("opens the request's management workspace, where its clocks are paused, resumed, or extended", async () =>
    {
        renderDetail();

        expect(await screen.findByText(/Clock changes are made where the request is managed/)).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Manage this request"}));

        expect(await screen.findByText("Management workspace opened")).toBeTruthy();
    });

    it("explains the disposal standing, places a hold, and creates a record export", async () =>
    {
        vi.mocked(records.placeRecordPreservationHold).mockResolvedValue({
            id: "hold-a", ownerKind: "USER", resourceType: "INFORMATION_REQUEST", resourceId: "request-a",
            scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES, status: RecordPreservationHoldStatus.ACTIVE, reason: "Pending review",
            effectiveFrom: "2026-09-26T08:00:00Z", placedByPrincipalKind: "USER", placedByPrincipalId: "user-a",
            placedAt: "2026-09-26T08:00:00Z", holdRevision: 1, events: [],
        });
        vi.mocked(transport.createInformationRequestRecordExport).mockResolvedValue({
            id: "export-a", exportKind: "REQUEST_RECORD", requestId: "request-a", sourceRequestIds: ["request-a"], schemaVersion: 1,
            contentHashAlgorithm: "SHA-256", contentHash: "abc", contentLength: 10, storageLocation: "primary",
            transferDecision: "NOT_REQUESTED", requestedByPrincipalKind: "USER", requestedByPrincipalId: "user-a",
            requestedAt: "2026-09-26T08:00:00Z", verified: true,
        });
        renderDetail();

        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
        expect(await screen.findByText("Only a finished request is disposed.")).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Place hold"}));
        const dialog = within(screen.getByRole("dialog"));
        fireEvent.change(dialog.getByRole("textbox", {name: /Reason/}), {target: {value: "  Pending review  "}});
        fireEvent.click(dialog.getByRole("button", {name: "Place hold"}));
        await waitFor(() => expect(records.placeRecordPreservationHold).toHaveBeenCalledWith({
            resourceType: "INFORMATION_REQUEST",
            resourceId: "request-a",
            scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
            reason: "Pending review",
            caseReference: undefined,
        }));
        await waitFor(() => expect(transport.getInformationRequestDisposalStanding).toHaveBeenCalledTimes(2));

        fireEvent.click(screen.getByRole("button", {name: "Create export"}));
        await waitFor(() => expect(transport.createInformationRequestRecordExport).toHaveBeenCalledWith("request-a", expect.any(String)));
        await waitFor(() => expect(transport.getInformationRequestRecordExports).toHaveBeenCalledTimes(2));
    });
});
