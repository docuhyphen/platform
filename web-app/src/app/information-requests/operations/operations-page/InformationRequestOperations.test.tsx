/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import {
    Capability,
    InformationRequestNoticeDeliveryState,
    InformationRequestOperationsException,
    InformationRequestOperationsRowDto,
    InformationRequestShareRoleKey,
    InformationRequestSlaStatus,
    InformationRequestState,
} from "../../../models/models.tsx";
import InformationRequestOperations from "./InformationRequestOperations.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

const mockAuth = vi.fn();

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestOperations: vi.fn()}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({sendInformationRequestReminders: vi.fn()}));
vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));
vi.mock("../../../../context/AuthContext.tsx", () => ({useAuth: () => mockAuth()}));
vi.mock("../clock-policies/ClockPoliciesPanel.tsx", () => ({
    default: ({canManage}: {canManage: boolean}) => <p>{canManage ? "Policies you manage" : "Policies you view"}</p>,
}));
vi.mock("../privacy/PrivacyPanel.tsx", () => ({default: () => <p>Privacy records</p>}));
vi.mock("../audit-search/AuditSearchPanel.tsx", () => ({default: () => <p>Audit search results</p>}));

const availability = (isAvailable: boolean) => ({
    isKnown: true,
    isIncluded: isAvailable,
    isEnforced: true,
    isDiscoverable: isAvailable,
    isAvailable,
    upgradePlanCode: null,
});

const row: InformationRequestOperationsRowDto = {
    requestId: "request-a-0000",
    exchangeId: "exchange-a",
    title: "Periodic records request",
    assignees: [{roleKey: InformationRequestShareRoleKey.CONTRIBUTOR, principalKind: "USER", principalId: "user-b", label: "Morgan Lee"}],
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

const second: InformationRequestOperationsRowDto = {
    ...row,
    requestId: "request-b-0000",
    title: "Quarterly evidence request",
    assignees: [],
    noticeCounts: {},
    exceptionCounts: {},
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

const signedInPersonally = () => mockAuth.mockReturnValue({
    appUser: {id: "user-a"},
    currentSession: {activeOrganizationId: null},
    hasCapability: () => false,
});

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
        signedInPersonally();
        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({items: [row, second], total: 2, limit: 25, offset: 0});
    });

    afterEach(cleanup);

    it("lists each request by its title with its state, assignees, service level, notices, and exceptions and opens its detail", async () =>
    {
        renderOperations();

        const item = (await screen.findByText("Periodic records request")).closest("li") as HTMLElement;
        expect(within(item).getByText("In progress, request request-")).toBeTruthy();
        expect(within(item).getByText("Assigned to Morgan Lee (Contributor)")).toBeTruthy();
        expect(within(item).getByText("Overdue")).toBeTruthy();
        expect(within(item).getByText("Notices: 2 delivered")).toBeTruthy();
        expect(within(item).getByText("Needs attention: 1 undeliverable notices")).toBeTruthy();
        fireEvent.click(within(item).getByRole("button", {name: "Open Periodic records request"}));
        expect(await screen.findByText("Request detail opened")).toBeTruthy();
    });

    it("states the page range of a long queue in the viewer's number format", async () =>
    {
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({items: [row, second], total: 1500, limit: 25, offset: 0});
        renderOperations();

        expect(await screen.findByText(`1 to 25 of ${formatInformationRequestCount(1500)}`)).toBeTruthy();
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

    it("searches by title or request id and filters by an assignee seen in the queue", async () =>
    {
        renderOperations();
        await screen.findByText("Periodic records request");

        fireEvent.change(screen.getByRole("searchbox", {name: "Search"}), {target: {value: "records"}});
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({search: "records", offset: 0}),
        ));

        fireEvent.click(screen.getByRole("combobox", {name: "Assigned to"}));
        fireEvent.click(await screen.findByRole("option", {name: "Morgan Lee"}));
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({search: "records", assigneeId: "user-b", offset: 0}),
        ));
    });

    it("sends reminders for the selected requests after confirmation and states what was queued", async () =>
    {
        vi.mocked(administration.sendInformationRequestReminders).mockResolvedValue([
            {requestId: "request-a-0000", noticeCount: 2},
            {requestId: "request-b-0000", noticeCount: 1},
        ]);
        renderOperations();

        fireEvent.click(await screen.findByRole("checkbox", {name: "Select Periodic records request"}));
        fireEvent.click(screen.getByRole("checkbox", {name: "Select Quarterly evidence request"}));
        expect(screen.getByText("2 selected")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Send reminders"}));
        const dialog = await screen.findByRole("dialog", {name: "Send reminders"});
        fireEvent.click(within(dialog).getByRole("button", {name: "Send reminders"}));

        await waitFor(() => expect(administration.sendInformationRequestReminders).toHaveBeenCalledWith(
            ["request-a-0000", "request-b-0000"],
            expect.any(String),
        ));
        expect(await screen.findByText("3 reminder notices were queued for 2 requests.")).toBeTruthy();
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenCalledTimes(2));
        expect(screen.queryByText("2 selected")).toBeNull();
    });

    it("states a refused reminder batch and keeps the selection", async () =>
    {
        vi.mocked(administration.sendInformationRequestReminders).mockRejectedValue({
            errorMessage: "Only open requests of an accepted Exchange take a reminder",
            reasonCode: "INFORMATION_REQUEST_STATE_INVALID",
        });
        renderOperations();

        fireEvent.click(await screen.findByRole("checkbox", {name: "Select Periodic records request"}));
        fireEvent.click(screen.getByRole("button", {name: "Send reminders"}));
        fireEvent.click(within(await screen.findByRole("dialog", {name: "Send reminders"})).getByRole("button", {name: "Send reminders"}));

        expect((await screen.findByRole("alert")).textContent).toContain("Only open requests of an accepted Exchange take a reminder");
        expect(screen.getByText("1 selected")).toBeTruthy();
    });

    it("offers no selection or reminders to a member without the operations manage capability", async () =>
    {
        mockAuth.mockReturnValue({
            appUser: {id: "user-a"},
            currentSession: {activeOrganizationId: "organization-a"},
            hasCapability: (capability: Capability) => capability === Capability.INFORMATION_REQUEST_OPERATIONS_READ,
        });
        renderOperations();

        expect(await screen.findByText("Periodic records request")).toBeTruthy();
        expect(screen.queryByRole("checkbox")).toBeNull();
        expect(screen.queryByRole("button", {name: "Send reminders"})).toBeNull();
        expect(screen.getByRole("button", {name: "Export CSV"})).toBeTruthy();
    });

    it("exports every matching request of the authorized queue as CSV", async () =>
    {
        const many = Array.from({length: 200}, (_, index) => ({...second, requestId: `request-${index}`, title: `Request ${index}`}));
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(async filter =>
            filter.limit === 200
                ? {items: filter.offset === 0 ? many : [row], total: 201, limit: 200, offset: filter.offset}
                : {items: [row, second], total: 2, limit: 25, offset: 0});
        const created = vi.fn((blob: Blob) => (blob.size > 0 ? "blob:operations" : ""));
        URL.createObjectURL = created;
        URL.revokeObjectURL = vi.fn();
        const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
        renderOperations();

        fireEvent.click(await screen.findByRole("button", {name: "Export CSV"}));

        await waitFor(() => expect(click).toHaveBeenCalledTimes(1));
        expect(transport.getInformationRequestOperations).toHaveBeenCalledWith(expect.objectContaining({limit: 200, offset: 0}));
        expect(transport.getInformationRequestOperations).toHaveBeenCalledWith(expect.objectContaining({limit: 200, offset: 200}));
        const blob = created.mock.calls[0][0];
        expect(blob.type).toBe("text/csv;charset=utf-8");
        const text = await new Promise<string>(resolve =>
        {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.readAsText(blob);
        });
        const lines = text.split("\r\n");
        expect(lines).toHaveLength(202);
        expect(lines[201]).toContain("Periodic records request");
        expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe("information-requests.csv");
        click.mockRestore();
    });

    it("offers due date policies, privacy, and audit search beside the queue to an owner who manages them", async () =>
    {
        renderOperations();
        await screen.findByText("Periodic records request");

        fireEvent.click(screen.getByRole("tab", {name: "Due date policies"}));
        expect(await screen.findByText("Policies you manage")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Privacy"}));
        expect(await screen.findByText("Privacy records")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Audit search"}));
        expect(await screen.findByText("Audit search results")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Queue"}));
        expect(await screen.findByText("Periodic records request")).toBeTruthy();
    });

    it("shows due date policies read-only and no privacy tab to a member without those capabilities", async () =>
    {
        mockAuth.mockReturnValue({
            appUser: {id: "user-a"},
            currentSession: {activeOrganizationId: "organization-a"},
            hasCapability: (capability: Capability) => capability === Capability.INFORMATION_REQUEST_OPERATIONS_READ,
        });
        renderOperations();
        await screen.findByText("Periodic records request");

        expect(screen.queryByRole("tab", {name: "Privacy"})).toBeNull();
        fireEvent.click(screen.getByRole("tab", {name: "Due date policies"}));
        expect(await screen.findByText("Policies you view")).toBeTruthy();
    });

    it("names every control and region of the queue", async () =>
    {
        renderOperations();
        await screen.findByText("Periodic records request");

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("stays closed without the plan feature", () =>
    {
        vi.mocked(usePlanFeature).mockReturnValue(availability(false));

        renderOperations();

        expect(screen.getByText("Information Requests are not included in your plan.")).toBeTruthy();
        expect(transport.getInformationRequestOperations).not.toHaveBeenCalled();
    });
});
