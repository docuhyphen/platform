/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes, useNavigate} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import * as records from "../../../../services/recordPreservationService.ts";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockEventKind,
    InformationRequestClockPolicyDto,
    InformationRequestClockState,
    InformationRequestClockType,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestNoticeDeliveryState,
    InformationRequestNoticeKind,
    InformationRequestOwnerType,
    InformationRequestRecordExportDto,
    InformationRequestStandingReason,
    InformationRequestState,
    RecordPreservationHoldStatus,
    RecordPreservationScope,
} from "../../../models/models.tsx";
import {useInformationRequestCapabilities} from "../../capabilities/useInformationRequestCapabilities.ts";
import {
    capabilitiesWithoutTheFeature,
    informationRequestCapabilities
} from "../../shared/testing/capabilityFixtures.ts";
import {
    formatInformationRequestCount,
    formatInformationRequestSize,
    formatInformationRequestTime,
} from "../../shared/informationRequestFormatting.ts";
import InformationRequestOperationsDetail from "./InformationRequestOperationsDetail.tsx";

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({
    getInformationRequestClocks: vi.fn(),
    getInformationRequestHeader: vi.fn(),
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
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({getInformationRequestClockPolicies: vi.fn()}));
vi.mock("../../capabilities/useInformationRequestCapabilities.ts", () => ({useInformationRequestCapabilities: vi.fn()}));
const mockAuth = vi.fn();

vi.mock("../../../../context/AuthContext.tsx", () => ({useAuth: () => mockAuth()}));

const signedInTo = (organizationId: string | null) => mockAuth.mockReturnValue({
    currentSession: {userId: "user-a", activeOrganizationId: organizationId},
    hasCapability: () => false,
});

const header = (kind: InformationRequestExecutionStandingKind, reason?: InformationRequestStandingReason): InformationRequestDto => ({
    id: "request-a",
    exchangeId: "exchange-a",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    aggregateRevision: 3,
    createdAt: Date.parse("2026-09-20T08:00:00Z"),
    updatedAt: Date.parse("2026-09-21T08:00:00Z"),
    requestETag: "\"aggregate:request-a:3\"",
    executionStanding: {kind, reason},
    conditionEvaluations: [],
});

const detailPage = () => (
    <MemoryRouter initialEntries={["/information-request-operations/request-a"]}>
        <Routes>
            <Route path={"/information-request-operations/:requestId"}
                   element={<InformationRequestOperationsDetail/>}/>
            <Route path={"/information-requests/:requestId/manage"}
                   element={<p>Management workspace opened</p>}/>
        </Routes>
    </MemoryRouter>
);

const renderDetail = () => render(detailPage());

const BackToEarlierRequest = () => {
    const navigate = useNavigate();
    return (
        <button id={"back-to-earlier-request"}
                onClick={() => navigate(-1)}>
            Back to the earlier request
        </button>
    );
};

const renderAfterEarlierRequest = () => render(
    <MemoryRouter
        initialEntries={["/information-request-operations/request-b", "/information-request-operations/request-a"]}
        initialIndex={1}>
        <BackToEarlierRequest/>
        <Routes>
            <Route path={"/information-request-operations/:requestId"}
                   element={<InformationRequestOperationsDetail/>}/>
        </Routes>
    </MemoryRouter>,
);

const goBackToEarlierRequest = () => fireEvent.click(screen.getByRole("button", {name: "Back to the earlier request"}));

const unanswered = <T, >(): Promise<T> => new Promise<T>(() => undefined);

const shown = (text: string): string => text.replace(/\s+/g, " ");

const FORBIDDEN = {
    reasonCode: "INFORMATION_REQUEST_FORBIDDEN",
    errorMessage: "Access denied to this Information Request"
};

const clockPolicy: InformationRequestClockPolicyDto = {
    id: "policy-a",
    policyKey: "standard-response",
    displayName: "Standard response",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    versions: [{
        id: "policy-version-2",
        versionNumber: 2,
        clockType: InformationRequestClockType.CALENDAR,
        businessTimezone: "UTC",
        standardDurationMinutes: 1440,
        urgentDurationMinutes: 240,
        dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
        workingPeriods: [],
        holidays: [],
        reminderMinutesBeforeDue: [],
        publishedAt: Date.parse("2026-09-01T08:00:00Z"),
    }],
};

const settled = () => act(() => new Promise<void>(resolve => setTimeout(resolve, 0)));

const recordExportOf = (requestId: string): InformationRequestRecordExportDto => ({
    id: `export-${requestId}`,
    exportKind: "REQUEST_RECORD",
    requestId,
    sourceRequestIds: [requestId],
    schemaVersion: 4,
    contentHashAlgorithm: "SHA-256",
    contentHash: "a".repeat(64),
    contentLength: 10,
    storageLocation: "primary",
    transferDecision: "NOT_REQUESTED",
    requestedByPrincipalKind: "USER",
    requestedByPrincipalId: "user-a",
    requestedAt: Date.parse("2026-10-12T08:00:00Z"),
    verified: true,
    containsConfidentialContent: false,
    withheldRelatedRequestCount: 0,
});

const auditCallsFor = (requestId: string) =>
    vi.mocked(transport.getInformationRequestAuditEvents).mock.calls.filter(call => call[0] === requestId);

const exportListCallsFor = (requestId: string) =>
    vi.mocked(transport.getInformationRequestRecordExports).mock.calls.filter(call => call[0] === requestId);

describe("InformationRequestOperationsDetail", () =>
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
        vi.clearAllMocks();
        signedInTo(null);
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities());
        vi.mocked(transport.getInformationRequestHeader).mockResolvedValue(header(InformationRequestExecutionStandingKind.ACTIVE));
        vi.mocked(transport.getInformationRequestClocks).mockResolvedValue([{
            id: "clock-a", clockKey: "response-window", policyVersionId: "policy-version-2", policyVersionNumber: 2,
            clockType: "CALENDAR", urgency: "STANDARD", receivedAt: Date.parse("2026-09-20T08:00:00Z"),
            state: InformationRequestClockState.RUNNING, dueAt: Date.parse("2026-09-25T08:00:00Z"), dueCycle: 1,
            overdueAt: Date.parse("2026-09-25T08:00:00Z"), clockETag: "etag",
            events: [{
                eventNumber: 1,
                eventKind: InformationRequestClockEventKind.OVERDUE,
                dueCycle: 1,
                occurredAt: Date.parse("2026-09-25T08:00:00Z")
            }],
        }]);
        vi.mocked(transport.getInformationRequestNotices).mockResolvedValue([{
            noticeIntentId: "intent-a",
            noticeKind: InformationRequestNoticeKind.RESPONSE_OVERDUE,
            partyId: "party-a",
            deliveryState: InformationRequestNoticeDeliveryState.DELIVERED,
            owedAt: Date.parse("2026-09-25T08:00:00Z"),
            maskedEndpoint: "p***@example.test",
            endpointState: "RESOLVED",
            renderedSubject: "A response is overdue",
            attempts: [{attemptNumber: 1, outcome: "DELIVERED", attemptedAt: Date.parse("2026-09-25T08:01:00Z")}],
            sequenceAllocations: [],
        }]);
        vi.mocked(transport.getInformationRequestAuditReconciliation).mockResolvedValue({
            requestId: "request-a",
            reconciled: false,
            auditedTransitionCount: 3,
            matchedTransitionCount: 2,
            unsealedEventCount: 0,
            missing: [{
                transitionId: "transition-c",
                sequenceNumber: 3,
                mutation: "CLOSE",
                expectedEventTypeKey: "information_request.request.closed"
            }],
            unmatched: [],
        });
        vi.mocked(transport.getInformationRequestAuditEvents).mockResolvedValue({
            items: [{
                eventId: "event-a",
                eventTypeKey: "information_request.request.issued",
                eventClass: "LIFECYCLE",
                category: "INFORMATION_REQUEST",
                outcome: "SUCCESS",
                occurredAt: Date.parse("2026-09-20T08:00:00Z"),
                recordedAt: Date.parse("2026-09-20T08:00:01Z"),
                actorKind: "USER",
                sealed: true,
                payload: {state: "ISSUED"},
                withheldKeyCount: 2,
            }],
            total: 1, limit: 50, offset: 0,
        });
        vi.mocked(transport.getInformationRequestDisposalStanding).mockResolvedValue({
            requestId: "request-a", eligible: false, reasonCode: "INFORMATION_REQUEST_RECORD_NOT_FINISHED",
            detail: "Only a finished request is disposed", holds: [], withheldHolds: [], retainedObjectCount: 0,
        });
        vi.mocked(transport.getInformationRequestRecordExports).mockResolvedValue([]);
        vi.mocked(administration.getInformationRequestClockPolicies).mockResolvedValue([clockPolicy]);
    });

    afterEach(cleanup);

    it("keeps a request's operations readable after its owner loses the feature", async () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature({holdsRequests: true}));

        renderDetail();

        expect(await screen.findByText("response window")).toBeTruthy();
        expect(document.getElementById("information-request-operations-detail-scope-standing")?.textContent)
            .toMatch(/Your plan does not include creating Information Requests/);
    });

    it("says why a request continues after its owner's plan lapsed", async () => {
        vi.mocked(transport.getInformationRequestHeader).mockResolvedValue(header(
            InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
            InformationRequestStandingReason.TRIAL_ENDED,
        ));
        renderDetail();

        expect(await screen.findByText(/This request continues as it was issued/)).toBeTruthy();
        expect(screen.getByText(/The owner's trial has ended\./)).toBeTruthy();
        expect(transport.getInformationRequestHeader).toHaveBeenCalledWith("request-a");
    });

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
        expect(screen.getByText("1 audited change has no audit record, first at change 3.")).toBeTruthy();
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
            id: "hold-a",
            ownerKind: "USER",
            resourceType: "INFORMATION_REQUEST",
            resourceId: "request-a",
            scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
            status: RecordPreservationHoldStatus.ACTIVE,
            reason: "Pending review",
            effectiveFrom: Date.parse("2026-09-26T08:00:00Z"),
            placedByPrincipalKind: "USER",
            placedByPrincipalId: "user-a",
            placedAt: Date.parse("2026-09-26T08:00:00Z"),
            holdRevision: 1,
            holdETag: "\"hold-a:1\"",
            events: [],
        });
        vi.mocked(transport.createInformationRequestRecordExport).mockResolvedValue({
            id: "export-a",
            exportKind: "REQUEST_RECORD",
            requestId: "request-a",
            sourceRequestIds: ["request-a"],
            schemaVersion: 1,
            contentHashAlgorithm: "SHA-256",
            contentHash: "abc",
            contentLength: 10,
            storageLocation: "primary",
            transferDecision: "NOT_REQUESTED",
            requestedByPrincipalKind: "USER",
            requestedByPrincipalId: "user-a",
            requestedAt: Date.parse("2026-09-26T08:00:00Z"),
            verified: true,
            containsConfidentialContent: false,
            withheldRelatedRequestCount: 0,
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
        }, expect.any(String)));
        await waitFor(() => expect(transport.getInformationRequestDisposalStanding).toHaveBeenCalledTimes(2));

        fireEvent.click(screen.getByRole("button", {name: "Create export"}));
        await waitFor(() => expect(transport.createInformationRequestRecordExport).toHaveBeenCalledWith("request-a", expect.any(String)));
        await waitFor(() => expect(transport.getInformationRequestRecordExports).toHaveBeenCalledTimes(2));
    });

    it("marks a record export that includes confidential information", async () => {
        vi.mocked(transport.getInformationRequestRecordExports).mockResolvedValue([{
            id: "export-c",
            exportKind: "REQUEST_RECORD",
            requestId: "request-a",
            sourceRequestIds: ["request-a"],
            schemaVersion: 4,
            contentHashAlgorithm: "SHA-256",
            contentHash: "abc",
            contentLength: 10,
            storageLocation: "primary",
            transferDecision: "NOT_REQUESTED",
            requestedByPrincipalKind: "USER",
            requestedByPrincipalId: "user-a",
            requestedAt: Date.parse("2026-09-26T08:00:00Z"),
            verified: true,
            containsConfidentialContent: true,
            withheldRelatedRequestCount: 0,
        }]);
        renderDetail();

        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));

        expect(await screen.findByText("Includes confidential information; opening it asks you to verify again.")).toBeTruthy();
    });

    it("states a hold whose details the viewer may not read without its reason", async () => {
        vi.mocked(transport.getInformationRequestDisposalStanding).mockResolvedValue({
            requestId: "request-a", eligible: false, reasonCode: "INFORMATION_REQUEST_RECORD_HELD",
            detail: "A preservation hold keeps this record", holds: [], retainedObjectCount: 0,
            withheldHolds: [{
                resourceType: "EXCHANGE",
                scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                status: RecordPreservationHoldStatus.ACTIVE,
                effectiveFrom: Date.parse("2026-09-20T08:00:00Z"),
            }],
        });
        renderDetail();

        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));

        expect(await screen.findByText(/Only people who can read the owner's holds see why\./)).toBeTruthy();
    });

    it("withholds the earlier request's retention standing while the next request's standing loads", async () => {
        vi.mocked(transport.getInformationRequestDisposalStanding).mockImplementation(id => id === "request-a"
            ? Promise.resolve({
                requestId: id, eligible: false, detail: "Kept for the earlier request", holds: [], withheldHolds: [],
                retainedObjectCount: 0,
            })
            : unanswered());
        renderAfterEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
        expect(await screen.findByText("Kept for the earlier request.")).toBeTruthy();

        goBackToEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));

        await waitFor(() => expect(transport.getInformationRequestDisposalStanding).toHaveBeenCalledWith("request-b"));
        expect(screen.queryByText("Kept for the earlier request.")).toBeNull();
        expect(document.getElementById("information-request-record-standing-loading")).not.toBeNull();
    });

    it("withholds the earlier request's record exports while the next request's exports load", async () => {
        vi.mocked(transport.getInformationRequestRecordExports).mockImplementation(id =>
            id === "request-a" ? Promise.resolve([recordExportOf(id)]) : unanswered());
        renderAfterEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
        await waitFor(() => expect(document.getElementById("information-request-record-export-export-request-a")).not.toBeNull());

        goBackToEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));

        await waitFor(() => expect(transport.getInformationRequestRecordExports).toHaveBeenCalledWith("request-b"));
        expect(document.getElementById("information-request-record-export-export-request-a")).toBeNull();
        expect(screen.queryByRole("button", {name: "Download"})).toBeNull();
    });

    it("closes a hold drafted for the earlier request instead of offering it for the next request", async () => {
        renderAfterEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
        fireEvent.click(await screen.findByRole("button", {name: "Place hold"}));
        fireEvent.change(within(screen.getByRole("dialog")).getByRole("textbox", {name: /Reason/}),
            {target: {value: "Preserve the earlier request"}});

        goBackToEarlierRequest();
        await waitFor(() => expect(transport.getInformationRequestHeader).toHaveBeenCalledWith("request-b"));
        await settled();
        const confirm = document.getElementById("place-record-hold-confirm");
        if (confirm) fireEvent.click(confirm);
        await settled();

        expect(records.placeRecordPreservationHold).not.toHaveBeenCalled();
        expect(document.getElementById("place-record-hold-dialog")).toBeNull();
    });

    it("keeps a late export answer for the earlier request from changing the next request", async () => {
        let answer: (value: InformationRequestRecordExportDto) => void = () => undefined;
        vi.mocked(transport.createInformationRequestRecordExport).mockImplementation(() =>
            new Promise(resolve => {
                answer = resolve;
            }));
        renderAfterEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
        fireEvent.click(await screen.findByRole("button", {name: "Create export"}));
        await waitFor(() => expect(transport.createInformationRequestRecordExport).toHaveBeenCalledWith("request-a", expect.any(String)));

        goBackToEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
        await waitFor(() => expect(exportListCallsFor("request-b")).toHaveLength(1));
        expect((document.getElementById("information-request-record-exports-create-btn") as HTMLButtonElement).disabled).toBe(false);

        await act(async () => answer(recordExportOf("request-a")));
        await settled();

        expect(exportListCallsFor("request-b")).toHaveLength(1);
        expect((document.getElementById("information-request-record-exports-create-btn") as HTMLButtonElement).disabled).toBe(false);
    });

    it("starts the next request's audit history at its first page after paging the earlier request", async () => {
        vi.mocked(transport.getInformationRequestAuditEvents).mockImplementation(async (id, page) => ({
            items: [{
                eventId: `${id}-${page.offset}`,
                eventTypeKey: "information_request.request.issued",
                eventClass: "LIFECYCLE",
                category: "INFORMATION_REQUEST",
                outcome: "SUCCESS",
                occurredAt: Date.parse("2026-10-12T08:00:00Z"),
                recordedAt: Date.parse("2026-10-12T08:00:00Z"),
                actorKind: "USER",
                sealed: true,
                payload: {request: id},
                withheldKeyCount: 0,
            }],
            total: id === "request-a" ? 101 : 1, limit: page.limit, offset: page.offset,
        }));
        renderAfterEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Audit history"}));
        fireEvent.click(await screen.findByRole("button", {name: "Next"}));
        await waitFor(() => expect(transport.getInformationRequestAuditEvents).toHaveBeenCalledWith("request-a", {
            limit: 50,
            offset: 50
        }));

        goBackToEarlierRequest();
        fireEvent.click(await screen.findByRole("tab", {name: "Audit history"}));

        await waitFor(() => expect(auditCallsFor("request-b")).not.toHaveLength(0));
        expect(auditCallsFor("request-b")).toEqual([["request-b", {limit: 50, offset: 0}]]);
    });

    it("withholds the earlier request's execution standing when reading the next request is refused", async () => {
        vi.mocked(transport.getInformationRequestHeader).mockImplementation(async id => {
            if (id === "request-b") throw {errorMessage: "Reading this request is refused"};
            return header(InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE, InformationRequestStandingReason.TRIAL_ENDED);
        });
        renderAfterEarlierRequest();
        expect(await screen.findByText(/This request continues as it was issued/)).toBeTruthy();

        goBackToEarlierRequest();

        await waitFor(() => expect(transport.getInformationRequestHeader).toHaveBeenCalledWith("request-b"));
        await settled();
        expect(screen.queryByText(/This request continues as it was issued/)).toBeNull();
    });

    it("withholds the request's operations once the workspace changes and reads them again for the new workspace", async () => {
        signedInTo("organization-a");
        const detail = renderDetail();
        expect(await screen.findByText("response window")).toBeTruthy();
        const reads = vi.mocked(transport.getInformationRequestClocks).mock.calls.length;
        vi.mocked(transport.getInformationRequestClocks).mockImplementation(() => unanswered());

        signedInTo("organization-b");
        detail.rerender(detailPage());

        expect(screen.queryByText("response window")).toBeNull();
        await waitFor(() => expect(transport.getInformationRequestClocks).toHaveBeenCalledTimes(reads + 1));
    });

    it("titles the page as its level one heading", async () => {
        renderDetail();

        expect(screen.getByRole("heading", {level: 1, name: "Request request-"})).toBeTruthy();
        expect(await screen.findByText("response window")).toBeTruthy();
    });

    it("says once that only the request's managers open its tabs instead of a refusal on each tab", async () => {
        vi.mocked(transport.getInformationRequestClocks).mockRejectedValue(FORBIDDEN);
        renderDetail();

        expect(await screen.findByText(
            "This request's clocks, notices, audit history, and records open only for someone who manages it: "
            + "the Exchange owner or a decision maker on the request.",
        )).toBeTruthy();
        expect(screen.queryByRole("tab", {name: "Notices"})).toBeNull();
        expect(screen.queryByText("Access denied to this Information Request")).toBeNull();
    });

    it("shows each clock's policy, urgency, received time, stop time, and the due time after each event", async () => {
        vi.mocked(transport.getInformationRequestClocks).mockResolvedValue([{
            id: "clock-a", clockKey: "response-window", policyVersionId: "policy-version-2", policyVersionNumber: 2,
            clockType: "CALENDAR", urgency: "URGENT", receivedAt: Date.parse("2026-09-20T08:00:00Z"),
            state: InformationRequestClockState.STOPPED, dueAt: Date.parse("2026-09-27T08:00:00Z"), dueCycle: 1,
            stoppedAt: Date.parse("2026-09-26T08:00:00Z"), clockETag: "etag",
            events: [
                {
                    eventNumber: 1, eventKind: InformationRequestClockEventKind.REMINDED, dueCycle: 1, pointOrdinal: 1,
                    dueAt: Date.parse("2026-09-25T08:00:00Z"), occurredAt: Date.parse("2026-09-24T08:00:00Z"),
                },
                {
                    eventNumber: 2,
                    eventKind: InformationRequestClockEventKind.EXTENDED,
                    dueCycle: 1,
                    reasonCode: "extra-time-agreed",
                    dueAt: Date.parse("2026-09-27T08:00:00Z"),
                    occurredAt: Date.parse("2026-09-24T09:00:00Z"),
                },
            ],
        }]);
        renderDetail();

        const clock = (await screen.findByText("response window")).closest("li") as HTMLElement;
        const time = (iso: string) => formatInformationRequestTime(Date.parse(iso));
        expect(within(clock).getByText(shown(`Urgent duration in calendar time, received ${time("2026-09-20T08:00:00Z")}`))).toBeTruthy();
        expect(await within(clock).findByText(
            "Standard response version 2: Calendar time in UTC. Standard 24 hours, urgent 4 hours. When due, the request is marked overdue.",
        )).toBeTruthy();
        expect(within(clock).getByText(shown(`Stopped ${time("2026-09-26T08:00:00Z")}`))).toBeTruthy();
        expect(within(clock).getByText(shown(
            `Reminder due ${time("2026-09-24T08:00:00Z")}, reminder point 1, due ${time("2026-09-25T08:00:00Z")}`,
        ))).toBeTruthy();
        expect(within(clock).getByText(shown(
            `Extended ${time("2026-09-24T09:00:00Z")}, extra time agreed, due ${time("2026-09-27T08:00:00Z")}`,
        ))).toBeTruthy();
    });

    it("names only the policy version of a clock whose policy cannot be read", async () => {
        vi.mocked(administration.getInformationRequestClockPolicies).mockRejectedValue(FORBIDDEN);
        renderDetail();

        const clock = (await screen.findByText("response window")).closest("li") as HTMLElement;
        expect(await within(clock).findByText("Policy version 2")).toBeTruthy();
        expect(screen.queryByRole("alert")).toBeNull();
    });

    it("counts a single withheld value and retention day in the singular", async () => {
        vi.mocked(transport.getInformationRequestAuditEvents).mockResolvedValue({
            items: [{
                eventId: "event-a",
                eventTypeKey: "information_request.request.issued",
                eventClass: "LIFECYCLE",
                category: "INFORMATION_REQUEST",
                outcome: "SUCCESS",
                occurredAt: Date.parse("2026-09-20T08:00:00Z"),
                recordedAt: Date.parse("2026-09-20T08:00:01Z"),
                actorKind: "USER",
                sealed: true,
                payload: {},
                withheldKeyCount: 1,
            }],
            total: 1, limit: 50, offset: 0,
        });
        vi.mocked(transport.getInformationRequestDisposalStanding).mockResolvedValue({
            requestId: "request-a",
            eligible: false,
            detail: "Only a finished request is disposed",
            holds: [],
            withheldHolds: [],
            retainedObjectCount: 0,
            schedule: {
                id: "schedule-a",
                versionNumber: 1,
                minimumRetentionDays: 1,
                disposalAfterDays: 1500,
                recordedByPrincipalKind: "USER",
                recordedByPrincipalId: "user-a",
                recordedAt: Date.parse("2026-09-20T08:00:00Z"),
            },
        });
        renderDetail();

        fireEvent.click(await screen.findByRole("tab", {name: "Audit history"}));
        expect(await screen.findByText("1 value withheld")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Records"}));
        expect(await screen.findByText(
            `Retention schedule version 1: kept at least 1 day, disposed ${formatInformationRequestCount(1500)} days after it finishes`,
        )).toBeTruthy();
    });

    it("states each export's size in binary units, what its hash covers, and its transfer decision", async () => {
        vi.mocked(transport.getInformationRequestRecordExports).mockResolvedValue([
            {...recordExportOf("request-a"), contentLength: 2400000},
            {
                ...recordExportOf("request-a"),
                id: "export-b",
                contentLength: 1,
                transferDecision: "PERMITTED",
                transferRegion: "EU"
            },
        ]);
        renderDetail();

        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));

        expect(await screen.findByText(
            `SHA-256 ${"a".repeat(16)} of the stored record, ${formatInformationRequestSize(2400000)}, stored in primary, no transfer requested`,
        )).toBeTruthy();
        expect(screen.getByText(
            `SHA-256 ${"a".repeat(16)} of the stored record, ${formatInformationRequestSize(1)}, stored in primary, transfer to EU permitted`,
        )).toBeTruthy();
        expect(screen.getByText(
            "Each hash covers the record as it is stored. A downloaded file is laid out for reading, so its own checksum differs from the hash shown.",
        )).toBeTruthy();
    });

    it("sends an optional transfer region with a new record export", async () => {
        vi.mocked(transport.createInformationRequestRecordExport).mockResolvedValue(recordExportOf("request-a"));
        renderDetail();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));

        fireEvent.change(await screen.findByRole("textbox", {name: /Transfer region/}), {target: {value: " EU "}});
        fireEvent.click(screen.getByRole("button", {name: "Create export"}));

        await waitFor(() => expect(transport.createInformationRequestRecordExport).toHaveBeenCalledWith("request-a", expect.any(String), "EU"));
    });

    it("announces a refused record export as an alert", async () => {
        vi.mocked(transport.createInformationRequestRecordExport).mockRejectedValue({errorMessage: "Too many record exports today"});
        renderDetail();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));

        fireEvent.click(await screen.findByRole("button", {name: "Create export"}));

        expect((await screen.findByRole("alert")).textContent).toBe("Too many record exports today");
    });

    it("creates a record export again under the same Idempotency-Key after the Records tab was left", async () => {
        vi.mocked(transport.createInformationRequestRecordExport)
            .mockRejectedValueOnce(new Error("Network Error"))
            .mockResolvedValueOnce(recordExportOf("request-a"));
        renderDetail();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
        fireEvent.click(await screen.findByRole("button", {name: "Create export"}));
        await screen.findByRole("alert");

        fireEvent.click(screen.getByRole("tab", {name: "Clocks"}));
        expect(await screen.findByText("response window")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Records"}));
        fireEvent.click(await screen.findByRole("button", {name: "Create export"}));

        await waitFor(() => expect(transport.createInformationRequestRecordExport).toHaveBeenCalledTimes(2));
        const [sent, resent] = vi.mocked(transport.createInformationRequestRecordExport).mock.calls.map(call => call[1]);
        expect(resent).toBe(sent);
    });

    it("places a hold again under the same Idempotency-Key after its dialog is closed and opened again", async () => {
        vi.mocked(records.placeRecordPreservationHold)
            .mockRejectedValueOnce(new Error("Network Error"))
            .mockResolvedValueOnce({
                id: "hold-a",
                ownerKind: "USER",
                resourceType: "INFORMATION_REQUEST",
                resourceId: "request-a",
                scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                status: RecordPreservationHoldStatus.ACTIVE,
                reason: "Pending review",
                effectiveFrom: Date.parse("2026-09-26T08:00:00Z"),
                placedByPrincipalKind: "USER",
                placedByPrincipalId: "user-a",
                placedAt: Date.parse("2026-09-26T08:00:00Z"),
                holdRevision: 1,
                holdETag: "\"hold-a:1\"",
                events: [],
            });
        renderDetail();
        fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
        const byId = (id: string) => document.getElementById(id) as HTMLElement;
        const place = async () => {
            await waitFor(() => expect(document.getElementById("information-request-place-hold-btn")).not.toBeNull());
            fireEvent.click(byId("information-request-place-hold-btn"));
            await waitFor(() => expect(document.getElementById("place-record-hold-reason")).not.toBeNull());
            fireEvent.change(byId("place-record-hold-reason"), {target: {value: "Pending review"}});
            fireEvent.click(byId("place-record-hold-confirm"));
        };

        await place();
        await waitFor(() => expect(document.getElementById("place-record-hold-dialog-error")).not.toBeNull());
        fireEvent.click(byId("place-record-hold-cancel"));
        await waitFor(() => expect(document.getElementById("place-record-hold-dialog")).toBeNull());
        await place();

        await waitFor(() => expect(records.placeRecordPreservationHold).toHaveBeenCalledTimes(2));
        const [sent, resent] = vi.mocked(records.placeRecordPreservationHold).mock.calls.map(call => call[1]);
        expect(resent).toBe(sent);
    });

    describe("downloading a record export", () => {
        let saved: { href: string; download: string }[];
        let answerRead: (record: InformationRequestRecordExportDto) => void;

        const withContent = (requestId: string): InformationRequestRecordExportDto =>
            ({...recordExportOf(requestId), content: {request: requestId}});

        const holdRead = () => vi.mocked(transport.getInformationRequestRecordExport).mockImplementation(() =>
            new Promise(answer => {
                answerRead = answer;
            }));

        const startDownload = async () => {
            fireEvent.click(await screen.findByRole("tab", {name: "Records"}));
            fireEvent.click(await screen.findByRole("button", {name: "Download"}));
            await waitFor(() => expect(transport.getInformationRequestRecordExport)
                .toHaveBeenCalledWith("request-a", "export-request-a"));
        };

        const deliverEarlierRecord = async () => {
            await act(async () => answerRead(withContent("request-a")));
            await settled();
        };

        const expectNothingSaved = () => {
            expect.soft(URL.createObjectURL).not.toHaveBeenCalled();
            expect.soft(saved).toEqual([]);
        };

        beforeEach(() => {
            saved = [];
            Object.assign(URL, {createObjectURL: vi.fn(() => "blob:request-record"), revokeObjectURL: vi.fn()});
            vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
                saved.push({href: this.href, download: this.download});
            });
            vi.mocked(transport.getInformationRequestRecordExports).mockImplementation(async id => [recordExportOf(id)]);
        });

        afterEach(() => vi.restoreAllMocks());

        it("saves the verified record of the request on screen", async () => {
            vi.mocked(transport.getInformationRequestRecordExport).mockResolvedValue(withContent("request-a"));
            renderDetail();

            await startDownload();

            await waitFor(() => expect(saved)
                .toEqual([{
                    href: "blob:request-record",
                    download: "information-request-record-export-request-a.json"
                }]));
        });

        it("says how many related requests the saved file leaves out because they cannot be opened", async () => {
            vi.mocked(transport.getInformationRequestRecordExport).mockResolvedValue({
                ...withContent("request-a"),
                withheldRelatedRequestCount: 1,
            });
            renderDetail();

            await startDownload();

            expect(await screen.findByText("The saved file leaves out 1 related request that you cannot open.")).toBeTruthy();
            expect(saved).toHaveLength(1);
        });

        it("saves no record still being read once another request is opened", async () => {
            holdRead();
            renderAfterEarlierRequest();
            await startDownload();

            goBackToEarlierRequest();
            await waitFor(() => expect(transport.getInformationRequestHeader).toHaveBeenCalledWith("request-b"));
            expect(screen.queryByRole("button", {name: "Download"})).toBeNull();
            await deliverEarlierRecord();

            expectNothingSaved();
        });

        it("saves no record still being read once the workspace changes", async () => {
            holdRead();
            signedInTo("organization-a");
            const detail = renderDetail();
            await startDownload();

            signedInTo("organization-b");
            detail.rerender(detailPage());
            await waitFor(() => expect(screen.queryByRole("button", {name: "Download"})).toBeNull());
            await deliverEarlierRecord();

            expectNothingSaved();
        });

        it("saves no record still being read once the Records tab is left", async () => {
            holdRead();
            renderDetail();
            await startDownload();

            fireEvent.click(screen.getByRole("tab", {name: "Clocks"}));
            expect(await screen.findByText(/Clock changes are made where the request is managed/)).toBeTruthy();
            await deliverEarlierRecord();

            expectNothingSaved();
        });
    });
});
