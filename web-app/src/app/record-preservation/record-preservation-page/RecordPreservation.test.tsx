/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as records from "../../../services/recordPreservationService.ts";
import {
    RecordDisposalState,
    RecordPreservationHoldDto,
    RecordPreservationHoldStatus,
    RecordPreservationScope,
} from "../../models/models.tsx";
import {useInformationRequestCapabilities} from "../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {capabilitiesWithoutTheFeature, informationRequestCapabilities} from "../../information-requests/shared/testing/capabilityFixtures.ts";
import RecordPreservation from "./RecordPreservation.tsx";

const mockSession = vi.fn();

vi.mock("../../../services/recordPreservationService.ts", () => ({
    INFORMATION_REQUEST_RECORD_TYPE: "INFORMATION_REQUEST",
    getRecordPreservationHolds: vi.fn(),
    releaseRecordPreservationHold: vi.fn(),
    changeRecordPreservationHoldScope: vi.fn(),
    getRecordRetentionSchedule: vi.fn(),
    publishRecordRetentionSchedule: vi.fn(),
    getRecordDisposals: vi.fn(),
}));
vi.mock("../../information-requests/capabilities/useInformationRequestCapabilities.ts", () => ({useInformationRequestCapabilities: vi.fn()}));
vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({currentSession: mockSession(), hasCapability: () => false}),
}));

const hold: RecordPreservationHoldDto = {
    id: "hold-a", ownerKind: "USER", resourceType: "INFORMATION_REQUEST", resourceId: "request-a-0000",
    scope: RecordPreservationScope.RESOURCE, status: RecordPreservationHoldStatus.ACTIVE, reason: "Pending review",
    caseReference: "case-7", effectiveFrom: "2026-09-20T08:00:00Z", placedByPrincipalKind: "USER",
    placedByPrincipalId: "user-a", placedAt: "2026-09-20T08:00:00Z", holdRevision: 1, events: [],
};

const renderRecords = () => render(
    <MemoryRouter>
        <RecordPreservation/>
    </MemoryRouter>,
);

describe("RecordPreservation", () =>
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
        mockSession.mockReturnValue({activeOrganizationId: null});
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities());
        vi.mocked(records.getRecordPreservationHolds).mockResolvedValue([hold]);
        vi.mocked(records.getRecordRetentionSchedule).mockResolvedValue({resourceType: "INFORMATION_REQUEST", versions: []});
        vi.mocked(records.getRecordDisposals).mockResolvedValue([{
            claimId: "claim-a", resourceType: "INFORMATION_REQUEST", resourceId: "request-b-0000", basis: "RETENTION_SCHEDULE",
            state: RecordDisposalState.FINALIZED, claimedAt: "2026-09-21T08:00:00Z", attemptCount: 1, objects: [],
            tombstone: {removedRowCounts: {information_request: 1}, deletedObjectCount: 2, retainedObjectCount: 1, disposedAt: "2026-09-21T08:05:00Z"},
        }]);
    });

    afterEach(cleanup);

    it("keeps holds readable after the owner loses the feature", async () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature({holdsRequests: true}));

        renderRecords();

        expect(await screen.findByText("Pending review (case case-7)")).toBeTruthy();
        expect(document.getElementById("record-preservation-scope-standing")?.textContent)
            .toMatch(/Your plan does not include creating Information Requests/);
    });

    it("lists active holds and releases one with a stated reason", async () =>
    {
        vi.mocked(records.releaseRecordPreservationHold).mockResolvedValue({...hold, status: RecordPreservationHoldStatus.RELEASED});
        renderRecords();

        expect(await screen.findByText("Pending review (case case-7)")).toBeTruthy();
        expect(records.getRecordPreservationHolds).toHaveBeenCalledWith(RecordPreservationHoldStatus.ACTIVE);
        fireEvent.click(screen.getByRole("button", {name: "Release"}));
        const dialog = within(screen.getByRole("dialog"));
        fireEvent.change(dialog.getByRole("textbox", {name: /Reason/}), {target: {value: "Review closed"}});
        fireEvent.click(dialog.getByRole("button", {name: "Release hold"}));

        await waitFor(() => expect(records.releaseRecordPreservationHold).toHaveBeenCalledWith("hold-a", "Review closed"));
        await waitFor(() => expect(records.getRecordPreservationHolds).toHaveBeenCalledTimes(2));
    });

    it("changes what an active hold covers with a stated reason", async () =>
    {
        vi.mocked(records.changeRecordPreservationHoldScope).mockResolvedValue({
            ...hold,
            scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
        });
        renderRecords();

        fireEvent.click(await screen.findByRole("button", {name: "Change scope"}));
        const dialog = await screen.findByRole("dialog", {name: "Change what this hold covers"});
        const confirm = within(dialog).getByRole("button", {name: "Change scope"});
        fireEvent.click(within(dialog).getByRole("radio", {name: "This record, its descendants, and the records it refers to"}));
        expect(confirm.hasAttribute("disabled")).toBe(true);
        fireEvent.change(within(dialog).getByRole("textbox", {name: /Reason/}), {target: {value: "Related records are in scope"}});
        fireEvent.click(confirm);

        await waitFor(() => expect(records.changeRecordPreservationHoldScope).toHaveBeenCalledWith(
            "hold-a",
            RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
            "Related records are in scope",
        ));
        await waitFor(() => expect(records.getRecordPreservationHolds).toHaveBeenCalledTimes(2));
    });

    it("keeps the scope choice closed until it differs from the hold's current scope", async () =>
    {
        renderRecords();

        fireEvent.click(await screen.findByRole("button", {name: "Change scope"}));
        const dialog = await screen.findByRole("dialog", {name: "Change what this hold covers"});
        fireEvent.change(within(dialog).getByRole("textbox", {name: /Reason/}), {target: {value: "No change"}});
        expect(within(dialog).getByRole("button", {name: "Change scope"}).hasAttribute("disabled")).toBe(true);
        expect(records.changeRecordPreservationHoldScope).not.toHaveBeenCalled();
    });

    it("publishes a retention version only when disposal never precedes the minimum", async () =>
    {
        vi.mocked(records.publishRecordRetentionSchedule).mockResolvedValue({resourceType: "INFORMATION_REQUEST", versions: []});
        renderRecords();

        expect(await screen.findByText("No retention schedule is published. Finished requests are kept until one is.")).toBeTruthy();
        const publish = screen.getByRole("button", {name: "Publish new version"});
        fireEvent.change(screen.getByRole("textbox", {name: /Keep at least/}), {target: {value: "30"}});
        fireEvent.change(screen.getByRole("textbox", {name: /Dispose after/}), {target: {value: "10"}});
        expect(publish.hasAttribute("disabled")).toBe(true);
        fireEvent.change(screen.getByRole("textbox", {name: /Dispose after/}), {target: {value: "90"}});
        fireEvent.click(publish);

        await waitFor(() => expect(records.publishRecordRetentionSchedule).toHaveBeenCalledWith(
            "INFORMATION_REQUEST", {minimumRetentionDays: 30, disposalAfterDays: 90}));
    });

    it("reports each disposal's progress and what it deleted or kept", async () =>
    {
        renderRecords();

        expect(await screen.findByText("2 stored files deleted, 1 kept because other records share them")).toBeTruthy();
        expect(screen.getByText("Disposed")).toBeTruthy();
    });

    it("stays closed to organization members without audit read access", () =>
    {
        mockSession.mockReturnValue({activeOrganizationId: "organization-a"});
        renderRecords();

        expect(screen.getByText("You do not have access to record preservation for this account.")).toBeTruthy();
        expect(records.getRecordPreservationHolds).not.toHaveBeenCalled();
    });
});
