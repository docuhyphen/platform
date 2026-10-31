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
import {
    useInformationRequestCapabilities
} from "../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {
    capabilitiesWithoutTheFeature,
    informationRequestCapabilities
} from "../../information-requests/shared/testing/capabilityFixtures.ts";
import {duplicateIds} from "../../information-requests/shared/testing/duplicateIds.ts";
import {formatInformationRequestCount} from "../../information-requests/shared/informationRequestFormatting.ts";
import RecordPreservation from "./RecordPreservation.tsx";

const mockSession = vi.fn();
const mockCapability = vi.fn();

vi.mock("../../../services/recordPreservationService.ts", () => ({
    INFORMATION_REQUEST_RECORD_TYPE: "INFORMATION_REQUEST",
    getRecordPreservationHolds: vi.fn(),
    getRecordPreservationHold: vi.fn(),
    releaseRecordPreservationHold: vi.fn(),
    changeRecordPreservationHoldScope: vi.fn(),
    getRecordRetentionSchedule: vi.fn(),
    publishRecordRetentionSchedule: vi.fn(),
    getRecordDisposals: vi.fn(),
}));
vi.mock("../../information-requests/capabilities/useInformationRequestCapabilities.ts", () => ({useInformationRequestCapabilities: vi.fn()}));
vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({currentSession: mockSession(), hasCapability: () => mockCapability()}),
}));

const hold: RecordPreservationHoldDto = {
    id: "hold-a", ownerKind: "USER", resourceType: "INFORMATION_REQUEST", resourceId: "request-a-0000",
    scope: RecordPreservationScope.RESOURCE, status: RecordPreservationHoldStatus.ACTIVE, reason: "Pending review",
    caseReference: "case-7",
    effectiveFrom: Date.parse("2026-09-20T08:00:00Z"),
    placedByPrincipalKind: "USER",
    placedByPrincipalId: "user-a",
    placedAt: Date.parse("2026-09-20T08:00:00Z"),
    holdRevision: 1,
    holdETag: "\"hold-a:1\"",
    events: [],
};

const STALE = {
    errorMessage: "The data changed after it was read; reload it and try again",
    reasonCode: "COMMAND_PRECONDITION_STALE"
};

const recordsPage = () => (
    <MemoryRouter>
        <RecordPreservation/>
    </MemoryRouter>
);

const renderRecords = () => render(recordsPage());

const byId = (id: string): HTMLElement => document.getElementById(id) as HTMLElement;

describe("RecordPreservation", () =>
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
        mockSession.mockReturnValue({activeOrganizationId: null});
        mockCapability.mockReturnValue(false);
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities());
        vi.mocked(records.getRecordPreservationHolds).mockResolvedValue([hold]);
        vi.mocked(records.getRecordRetentionSchedule).mockResolvedValue({
            resourceType: "INFORMATION_REQUEST",
            versions: [],
            scheduleETag: "\"schedule:0\"",
        });
        vi.mocked(records.getRecordDisposals).mockResolvedValue([{
            claimId: "claim-a",
            resourceType: "INFORMATION_REQUEST",
            resourceId: "request-b-0000",
            basis: "RETENTION_SCHEDULE",
            state: RecordDisposalState.FINALIZED,
            claimedAt: Date.parse("2026-09-21T08:00:00Z"),
            attemptCount: 1,
            objects: [],
            tombstone: {
                removedRowCounts: {information_request: 1},
                deletedObjectCount: 2,
                retainedObjectCount: 1,
                disposedAt: Date.parse("2026-09-21T08:05:00Z")
            },
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
        vi.mocked(records.releaseRecordPreservationHold).mockResolvedValue({
            ...hold,
            status: RecordPreservationHoldStatus.RELEASED
        });
        renderRecords();

        expect(await screen.findByText("Pending review (case case-7)")).toBeTruthy();
        expect(records.getRecordPreservationHolds).toHaveBeenCalledWith(RecordPreservationHoldStatus.ACTIVE);
        fireEvent.click(screen.getByRole("button", {name: "Release"}));
        const dialog = within(screen.getByRole("dialog", {name: "Release preservation hold"}));
        fireEvent.change(dialog.getByRole("textbox", {name: /Reason/}), {target: {value: "Review closed"}});
        fireEvent.click(dialog.getByRole("button", {name: "Release hold"}));

        await waitFor(() => expect(records.releaseRecordPreservationHold)
            .toHaveBeenCalledWith("hold-a", "Review closed", "\"hold-a:1\"", expect.any(String)));
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
            "\"hold-a:1\"",
            expect.any(String),
        ));
        await waitFor(() => expect(records.getRecordPreservationHolds).toHaveBeenCalledTimes(2));
    });

    it("reloads a hold that changed after it was opened and says so instead of changing it", async () => {
        vi.mocked(records.changeRecordPreservationHoldScope).mockRejectedValue(STALE);
        renderRecords();

        fireEvent.click(await screen.findByRole("button", {name: "Change scope"}));
        const dialog = await screen.findByRole("dialog", {name: "Change what this hold covers"});
        fireEvent.click(within(dialog).getByRole("radio", {name: "This record, its descendants, and the records it refers to"}));
        fireEvent.change(within(dialog).getByRole("textbox", {name: /Reason/}), {target: {value: "Related records are in scope"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Change scope"}));

        expect(await screen.findByText("This hold changed after you opened it. The latest version is shown; try again.")).toBeTruthy();
        await waitFor(() => expect(records.getRecordPreservationHolds).toHaveBeenCalledTimes(2));
        expect(screen.queryByRole("dialog", {name: "Change what this hold covers"})).toBeNull();
    });

    it("reloads a retention schedule that changed after it was read and keeps the new values to publish again", async () => {
        vi.mocked(records.publishRecordRetentionSchedule).mockRejectedValue(STALE);
        renderRecords();

        await screen.findByText("No retention schedule is published. Finished requests are kept until one is.");
        fireEvent.change(screen.getByRole("textbox", {name: /Keep at least/}), {target: {value: "30"}});
        fireEvent.click(screen.getByRole("button", {name: "Publish new version"}));

        expect(await screen.findByText(
            "The retention schedule changed after it was read. The latest version is shown; publish again if it still needs to change.",
        )).toBeTruthy();
        await waitFor(() => expect(records.getRecordRetentionSchedule).toHaveBeenCalledTimes(2));
        expect((screen.getByRole("textbox", {name: /Keep at least/}) as HTMLInputElement).value).toBe("30");
    });

    it("withholds the retention schedule and keeps it from being published once its reread is refused", async () => {
        vi.mocked(records.publishRecordRetentionSchedule).mockRejectedValue(STALE);
        vi.mocked(records.getRecordRetentionSchedule)
            .mockResolvedValueOnce({resourceType: "INFORMATION_REQUEST", versions: [], scheduleETag: "\"schedule:0\""})
            .mockRejectedValue({
                errorMessage: "Access denied to this owner's retention schedule",
                reasonCode: "RECORD_PRESERVATION_FORBIDDEN"
            });
        renderRecords();

        await screen.findByText("No retention schedule is published. Finished requests are kept until one is.");
        fireEvent.change(screen.getByRole("textbox", {name: /Keep at least/}), {target: {value: "30"}});
        fireEvent.click(screen.getByRole("button", {name: "Publish new version"}));

        expect(await screen.findByText("Access denied to this owner's retention schedule")).toBeTruthy();
        expect(screen.queryByText("No retention schedule is published. Finished requests are kept until one is.")).toBeNull();
        expect(screen.getByRole("button", {name: "Publish new version"}).hasAttribute("disabled")).toBe(true);
        expect(records.publishRecordRetentionSchedule).toHaveBeenCalledTimes(1);
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
        vi.mocked(records.publishRecordRetentionSchedule).mockResolvedValue({
            resourceType: "INFORMATION_REQUEST",
            versions: [],
            scheduleETag: "\"schedule:1\"",
        });
        renderRecords();

        expect(await screen.findByText("No retention schedule is published. Finished requests are kept until one is.")).toBeTruthy();
        const publish = screen.getByRole("button", {name: "Publish new version"});
        fireEvent.change(screen.getByRole("textbox", {name: /Keep at least/}), {target: {value: "30"}});
        fireEvent.change(screen.getByRole("textbox", {name: /Dispose after/}), {target: {value: "10"}});
        expect(publish.hasAttribute("disabled")).toBe(true);
        fireEvent.change(screen.getByRole("textbox", {name: /Dispose after/}), {target: {value: "90"}});
        fireEvent.click(publish);

        await waitFor(() => expect(records.publishRecordRetentionSchedule).toHaveBeenCalledWith(
            "INFORMATION_REQUEST",
            {minimumRetentionDays: 30, disposalAfterDays: 90},
            "\"schedule:0\"",
            expect.any(String),
        ));
    });

    it("bounds retention ages before publishing a schedule", async () => {
        renderRecords();
        await screen.findByText("No retention schedule is published. Finished requests are kept until one is.");
        const publish = screen.getByRole("button", {name: "Publish new version"});
        const minimum = screen.getByRole("textbox", {name: /Keep at least/});
        const disposal = screen.getByRole("textbox", {name: /Dispose after/});
        fireEvent.change(minimum, {target: {value: "365000"}});
        expect(publish.hasAttribute("disabled")).toBe(false);
        fireEvent.change(minimum, {target: {value: "365001"}});
        expect(publish.hasAttribute("disabled")).toBe(true);
        fireEvent.change(minimum, {target: {value: "365000"}});
        fireEvent.change(disposal, {target: {value: "2147483647"}});
        expect(publish.hasAttribute("disabled")).toBe(true);
    });

    it("reports each disposal's progress and what it deleted or kept", async () =>
    {
        renderRecords();

        expect(await screen.findByText("2 stored files deleted, 1 kept because other records share it")).toBeTruthy();
        expect(screen.getByText(/^Disposed by retention schedule, 1 attempt, claimed /)).toBeTruthy();
        expect(screen.getByText("Disposed")).toBeTruthy();
    });

    it("states retention ages with singular days and in the viewer's number format", async () => {
        vi.mocked(records.getRecordRetentionSchedule).mockResolvedValue({
            resourceType: "INFORMATION_REQUEST",
            current: {
                id: "schedule-a",
                versionNumber: 1,
                minimumRetentionDays: 1,
                disposalAfterDays: 1500,
                recordedByPrincipalKind: "USER",
                recordedByPrincipalId: "user-a",
                recordedAt: Date.parse("2026-09-20T08:00:00Z"),
            },
            versions: [],
            scheduleETag: "\"schedule:1\"",
        });
        renderRecords();

        expect(await screen.findByText(
            `Version 1: kept at least 1 day, disposed ${formatInformationRequestCount(1500)} days after finishing.`,
        )).toBeTruthy();
    });

    it("offers Try again when the holds cannot be read and reads them again", async () => {
        vi.mocked(records.getRecordPreservationHolds).mockRejectedValueOnce({errorMessage: "The holds could not be read right now"});
        renderRecords();

        expect((await screen.findByRole("alert")).textContent).toContain("The holds could not be read right now");
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByText("Pending review (case case-7)")).toBeTruthy();
        expect(records.getRecordPreservationHolds).toHaveBeenCalledTimes(2);
    });

    it("announces a refused release as an alert in its dialog", async () => {
        vi.mocked(records.releaseRecordPreservationHold).mockRejectedValue({errorMessage: "The hold could not be released now"});
        renderRecords();

        fireEvent.click(await screen.findByRole("button", {name: "Release"}));
        const dialog = await screen.findByRole("dialog", {name: "Release preservation hold"});
        fireEvent.change(within(dialog).getByRole("textbox", {name: /Reason/}), {target: {value: "Review closed"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Release hold"}));

        await waitFor(() => expect(document.getElementById("release-record-hold-dialog-error-body")?.textContent)
            .toBe("The hold could not be released now"));
        expect(document.getElementById("release-record-hold-dialog-error")?.getAttribute("role")).toBe("alert");
    });

    it("announces a refused retention publish as an alert", async () => {
        vi.mocked(records.publishRecordRetentionSchedule).mockRejectedValue({errorMessage: "Retention needs audit governance"});
        renderRecords();
        await screen.findByText("No retention schedule is published. Finished requests are kept until one is.");

        fireEvent.change(screen.getByRole("textbox", {name: /Keep at least/}), {target: {value: "30"}});
        fireEvent.click(screen.getByRole("button", {name: "Publish new version"}));

        expect((await screen.findByRole("alert")).textContent).toBe("Retention needs audit governance");
        expect(document.getElementById("record-retention-schedule-error-body")?.textContent).toBe("Retention needs audit governance");
    });

    it("names a missing minimum instead of blaming a valid disposal age", async () => {
        renderRecords();
        await screen.findByText("No retention schedule is published. Finished requests are kept until one is.");

        fireEvent.change(screen.getByRole("textbox", {name: /Dispose after/}), {target: {value: "365"}});

        expect(screen.queryByText("Enter whole days no shorter than the minimum, up to 365000")).toBeNull();
        expect(screen.getByText("Enter the days a finished request is kept at least.")).toBeTruthy();
        fireEvent.change(screen.getByRole("textbox", {name: /Keep at least/}), {target: {value: "400"}});
        expect(screen.getByText("Enter whole days no shorter than the minimum, up to 365000")).toBeTruthy();
        expect(screen.queryByText("Enter the days a finished request is kept at least.")).toBeNull();
    });

    it("says the schedule could not be read instead of blaming a valid disposal age", async () => {
        vi.mocked(records.publishRecordRetentionSchedule).mockRejectedValue(STALE);
        vi.mocked(records.getRecordRetentionSchedule)
            .mockResolvedValueOnce({resourceType: "INFORMATION_REQUEST", versions: [], scheduleETag: "\"schedule:0\""})
            .mockRejectedValue({
                errorMessage: "Access denied to this owner's retention schedule",
                reasonCode: "RECORD_PRESERVATION_FORBIDDEN"
            });
        renderRecords();
        await screen.findByText("No retention schedule is published. Finished requests are kept until one is.");
        fireEvent.change(screen.getByRole("textbox", {name: /Keep at least/}), {target: {value: "30"}});
        fireEvent.change(screen.getByRole("textbox", {name: /Dispose after/}), {target: {value: "365"}});
        fireEvent.click(screen.getByRole("button", {name: "Publish new version"}));

        expect(await screen.findByText("The current schedule could not be read, so no new version can be published until it is.")).toBeTruthy();
        expect(screen.queryByText("Enter whole days no shorter than the minimum, up to 365000")).toBeNull();
    });

    it("shows a hold's placement and each scope change with who made it, when, and why", async () => {
        vi.mocked(records.getRecordPreservationHold).mockResolvedValue({
            ...hold,
            scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
            events: [
                {
                    eventNumber: 1,
                    eventKind: "PLACED",
                    scope: RecordPreservationScope.RESOURCE,
                    reason: "Pending review",
                    principalKind: "USER",
                    principalId: "user-a",
                    occurredAt: Date.parse("2026-09-20T08:00:00Z"),
                },
                {
                    eventNumber: 2,
                    eventKind: "SCOPE_CHANGED",
                    scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES,
                    reason: "Related records are in scope",
                    principalKind: "USER",
                    principalId: "user-b",
                    occurredAt: Date.parse("2026-09-22T08:00:00Z"),
                },
            ],
        });
        renderRecords();

        fireEvent.click(await screen.findByRole("button", {name: "History"}));

        const history = await screen.findByRole("list", {name: "Hold history"});
        await waitFor(() => expect(records.getRecordPreservationHold).toHaveBeenCalledWith("hold-a"));
        const [placed, changed] = within(history).getAllByRole("listitem");
        expect(placed.textContent).toMatch(/^Placed .+ by a person \(user-a\), covering this record only: Pending review$/);
        expect(changed.textContent).toMatch(
            /^Scope changed .+ by a person \(user-b\) from this record only to this record, its descendants, and the records it refers to: Related records are in scope$/,
        );
        expect(screen.getByRole("button", {name: "History"}).getAttribute("aria-expanded")).toBe("true");
    });

    it("changes a hold's scope again under the same Idempotency-Key after its dialog is closed and opened again", async () => {
        vi.mocked(records.changeRecordPreservationHoldScope)
            .mockRejectedValueOnce(new Error("Network Error"))
            .mockResolvedValueOnce({...hold, scope: RecordPreservationScope.DESCENDANTS_AND_REFERENCES});
        renderRecords();
        const changeScope = async () => {
            await waitFor(() => expect(document.getElementById("record-hold-hold-a-change-scope")).not.toBeNull());
            fireEvent.click(byId("record-hold-hold-a-change-scope"));
            await waitFor(() => expect(document.getElementById("change-record-hold-scope-reason")).not.toBeNull());
            fireEvent.click(byId("change-record-hold-scope-descendants_and_references"));
            fireEvent.change(byId("change-record-hold-scope-reason"), {target: {value: "Related records are in scope"}});
            fireEvent.click(byId("change-record-hold-scope-confirm"));
        };

        await changeScope();
        await waitFor(() => expect(document.getElementById("change-record-hold-scope-dialog-error")).not.toBeNull());
        fireEvent.click(byId("change-record-hold-scope-cancel"));
        await waitFor(() => expect(document.getElementById("change-record-hold-scope-dialog")).toBeNull());
        await changeScope();

        await waitFor(() => expect(records.changeRecordPreservationHoldScope).toHaveBeenCalledTimes(2));
        const [sent, resent] = vi.mocked(records.changeRecordPreservationHoldScope).mock.calls.map(call => call[4]);
        expect(resent).toBe(sent);
    });

    it("releases a hold again under the same Idempotency-Key after its dialog is closed and opened again", async () => {
        vi.mocked(records.releaseRecordPreservationHold)
            .mockRejectedValueOnce(new Error("Network Error"))
            .mockResolvedValueOnce({...hold, status: RecordPreservationHoldStatus.RELEASED});
        renderRecords();
        const release = async () => {
            await waitFor(() => expect(document.getElementById("record-hold-hold-a-release")).not.toBeNull());
            fireEvent.click(byId("record-hold-hold-a-release"));
            await waitFor(() => expect(document.getElementById("release-record-hold-reason")).not.toBeNull());
            fireEvent.change(byId("release-record-hold-reason"), {target: {value: "Review closed"}});
            fireEvent.click(byId("release-record-hold-confirm"));
        };

        await release();
        await waitFor(() => expect(document.getElementById("release-record-hold-dialog-error")).not.toBeNull());
        fireEvent.click(byId("release-record-hold-cancel"));
        await waitFor(() => expect(document.getElementById("release-record-hold-dialog")).toBeNull());
        await release();

        await waitFor(() => expect(records.releaseRecordPreservationHold).toHaveBeenCalledTimes(2));
        const [sent, resent] = vi.mocked(records.releaseRecordPreservationHold).mock.calls.map(call => call[3]);
        expect(resent).toBe(sent);
    });

    it("publishes a retention version again under the same Idempotency-Key after its answer was lost", async () => {
        vi.mocked(records.publishRecordRetentionSchedule)
            .mockRejectedValueOnce(new Error("Network Error"))
            .mockResolvedValueOnce({resourceType: "INFORMATION_REQUEST", versions: [], scheduleETag: "\"schedule:1\""});
        renderRecords();
        await screen.findByText("No retention schedule is published. Finished requests are kept until one is.");
        fireEvent.change(screen.getByRole("textbox", {name: /Keep at least/}), {target: {value: "30"}});
        fireEvent.click(screen.getByRole("button", {name: "Publish new version"}));
        await screen.findByRole("alert");

        fireEvent.click(screen.getByRole("button", {name: "Publish new version"}));

        await waitFor(() => expect(records.publishRecordRetentionSchedule).toHaveBeenCalledTimes(2));
        const [sent, resent] = vi.mocked(records.publishRecordRetentionSchedule).mock.calls.map(call => call[3]);
        expect(sent).toMatch(/\S/);
        expect(resent).toBe(sent);
    });

    it("stays closed to organization members without audit read access", () =>
    {
        mockSession.mockReturnValue({activeOrganizationId: "organization-a"});
        renderRecords();

        expect(screen.getByText("You do not have access to record preservation for this account.")).toBeTruthy();
        expect(records.getRecordPreservationHolds).not.toHaveBeenCalled();
    });

    it("withholds the earlier workspace's records once the workspace changes and reads them for the new one", async () => {
        mockSession.mockReturnValue({userId: "user-a", activeOrganizationId: null});
        const page = renderRecords();
        expect(await screen.findByText("Pending review (case case-7)")).toBeTruthy();
        const reads = vi.mocked(records.getRecordPreservationHolds).mock.calls.length;
        vi.mocked(records.getRecordPreservationHolds).mockImplementation(() => new Promise(() => undefined));
        mockCapability.mockReturnValue(true);
        mockSession.mockReturnValue({userId: "user-a", activeOrganizationId: "organization-b"});

        page.rerender(recordsPage());

        expect(screen.queryByText("Pending review (case case-7)")).toBeNull();
        expect(screen.queryByText("Disposed")).toBeNull();
        await waitFor(() => expect(records.getRecordPreservationHolds).toHaveBeenCalledTimes(reads + 1));
    });

    it("titles the page as its level one heading", async () => {
        renderRecords();

        expect(screen.getByRole("heading", {level: 1, name: "Record preservation"})).toBeTruthy();
        expect(await screen.findByText("Pending review (case case-7)")).toBeTruthy();
    });

    it("gives every element of the page and its dialogs an id of its own", async () => {
        vi.mocked(records.changeRecordPreservationHoldScope).mockRejectedValue({errorMessage: "The hold could not be changed"});
        renderRecords();
        expect(await screen.findByText("Pending review (case case-7)")).toBeTruthy();

        expect(duplicateIds(document.body)).toEqual([]);
        fireEvent.click(screen.getByRole("button", {name: "Change scope"}));
        const dialog = await screen.findByRole("dialog", {name: "Change what this hold covers"});
        fireEvent.click(within(dialog).getByRole("radio", {name: "This record, its descendants, and the records it refers to"}));
        fireEvent.change(within(dialog).getByRole("textbox", {name: /Reason/}), {target: {value: "Related records are in scope"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Change scope"}));

        await waitFor(() => expect(document.getElementById("change-record-hold-scope-dialog-error-body")?.textContent)
            .toBe("The hold could not be changed"));
        expect(document.getElementById("change-record-hold-scope-dialog-error")?.getAttribute("role")).toBe("alert");
        expect(document.querySelectorAll(".fui-DialogBody:not([id]), .fui-MessageBarBody:not([id])")).toHaveLength(0);
        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("names the notice that keeps the page closed by its own id", () => {
        mockSession.mockReturnValue({activeOrganizationId: "organization-a"});
        renderRecords();

        expect(document.getElementById("record-preservation-forbidden-body")?.textContent)
            .toBe("You do not have access to record preservation for this account.");
    });
});
