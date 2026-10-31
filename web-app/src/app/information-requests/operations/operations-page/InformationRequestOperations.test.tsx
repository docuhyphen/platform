/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as exchanges from "../../../../services/exchangeApi.ts";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import * as records from "../../../../services/recordPreservationService.ts";
import {
    Capability,
    InformationRequestExecutionStandingKind,
    InformationRequestNoticeDeliveryState,
    InformationRequestOperationsException,
    InformationRequestOperationsRowDto,
    InformationRequestShareRoleKey,
    InformationRequestSlaStatus,
    InformationRequestStandingReason,
    InformationRequestState,
} from "../../../models/models.tsx";
import {useInformationRequestCapabilities} from "../../capabilities/useInformationRequestCapabilities.ts";
import InformationRequestOperations from "./InformationRequestOperations.tsx";
import {
    capabilitiesWithoutTheFeature,
    informationRequestCapabilities
} from "../../shared/testing/capabilityFixtures.ts";
import {
    formatInformationRequestCount,
    formatInformationRequestTime
} from "../../shared/informationRequestFormatting.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

const mockAuth = vi.fn();

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestOperations: vi.fn()}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({sendInformationRequestReminders: vi.fn()}));
vi.mock("../../../../services/exchangeApi.ts", () => ({searchExchanges: vi.fn()}));
vi.mock("../../capabilities/useInformationRequestCapabilities.ts", () => ({useInformationRequestCapabilities: vi.fn()}));
vi.mock("../../../../context/AuthContext.tsx", () => ({useAuth: () => mockAuth()}));
vi.mock("../clock-policies/ClockPoliciesPanel.tsx", () => ({
    default: ({canManage}: {canManage: boolean}) => <p>{canManage ? "Policies you manage" : "Policies you view"}</p>,
}));
vi.mock("../privacy/PrivacyPanel.tsx", async () => {
    const {useState} = await import("react");
    const PrivacyRecords = () => {
        const [mount] = useState(() => crypto.randomUUID());
        return <p data-mount={mount}>Privacy records</p>;
    };
    return {default: PrivacyRecords};
});
vi.mock("../audit-search/AuditSearchPanel.tsx", () => ({default: () => <p>Audit search results</p>}));
vi.mock("../../../../services/recordPreservationService.ts", async importOriginal => ({
    ...await importOriginal<typeof import("../../../../services/recordPreservationService.ts")>(),
    placeRecordPreservationHold: vi.fn(),
}));


const row: InformationRequestOperationsRowDto = {
    requestId: "request-a-0000",
    exchangeId: "exchange-a",
    title: "Periodic records request",
    assignees: [{roleKey: InformationRequestShareRoleKey.CONTRIBUTOR, principalKind: "USER", principalId: "user-b", label: "Morgan Lee"}],
    templateVersionId: "version-a",
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    createdAt: Date.parse("2026-09-20T08:00:00Z"),
    ageSeconds: 3 * 86400 + 2 * 3600,
    slaStatus: InformationRequestSlaStatus.OVERDUE,
    nearestDueAt: Date.parse("2026-09-25T08:00:00Z"),
    clockCount: 1,
    reminderCount: 2,
    noticeCounts: {[InformationRequestNoticeDeliveryState.DELIVERED]: 2},
    exceptionCounts: {[InformationRequestOperationsException.NOTICE_UNDELIVERABLE]: 1},
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
};

const second: InformationRequestOperationsRowDto = {
    ...row,
    requestId: "request-b-0000",
    title: "Quarterly evidence request",
    assignees: [],
    noticeCounts: {},
    exceptionCounts: {},
};

const OPEN_STATES = [InformationRequestState.DRAFT, InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS];

const exchangePage = (items: { id: string; name: string }[]) => ({
    content: items.map(item => ({...item, createdDate: "2026-09-01T08:00:00Z", lastActivity: "2026-09-01T08:00:00Z"})),
    totalElements: items.length,
    totalPages: 1,
    currentPage: 0,
    pageSize: 10,
});

const operationsPage = () => (
    <MemoryRouter initialEntries={["/information-request-operations"]}>
        <Routes>
            <Route path={"/information-request-operations"}
                   element={<InformationRequestOperations/>}/>
            <Route path={"/information-request-operations/:requestId"}
                   element={<p>Request detail opened</p>}/>
        </Routes>
    </MemoryRouter>
);

const renderOperations = () => render(operationsPage());

type QueuePage = Awaited<ReturnType<typeof transport.getInformationRequestOperations>>;

const unansweredQueue = () => new Promise<QueuePage>(() => undefined);

interface PendingQueue {
    read: Promise<QueuePage>;
    answer: (page: QueuePage) => void;
}

const pendingQueue = (): PendingQueue => {
    let answer: (page: QueuePage) => void = () => undefined;
    const read = new Promise<QueuePage>(resolve => {
        answer = resolve;
    });
    return {read, answer};
};

const QUEUE_REFUSAL = {
    errorMessage: "Access denied to this owner's queue",
    reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
};

const settled = () => act(() => new Promise<void>(resolve => setTimeout(resolve, 0)));

const shown = (text: string): string => text.replace(/\s+/g, " ");

const signedInPersonally = () => mockAuth.mockReturnValue({
    appUser: {id: "user-a"},
    currentSession: {activeOrganizationId: null},
    hasCapability: () => false,
});

const signedInTo = (organizationId: string) => mockAuth.mockReturnValue({
    appUser: {id: "user-a"},
    currentSession: {userId: "user-a", activeOrganizationId: organizationId},
    hasCapability: () => true,
});

const switchingWorkspace = () => mockAuth.mockReturnValue({
    appUser: {id: "user-a"},
    currentSession: null,
    hasCapability: () => false,
});

const nextWorkspaceRow: InformationRequestOperationsRowDto = {
    ...second,
    requestId: "request-c-0000",
    title: "Next workspace request",
};

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
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities());
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({items: [row, second], total: 2, limit: 25, offset: 0});
        vi.mocked(exchanges.searchExchanges).mockResolvedValue(exchangePage([]));
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
        expect(within(item).getByText("Needs attention: 1 undeliverable notice")).toBeTruthy();
        fireEvent.click(within(item).getByRole("button", {name: "Open Periodic records request"}));
        expect(await screen.findByText("Request detail opened")).toBeTruthy();
    });

    it("marks a request that continues after its owner's plan lapsed, naming why", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({
            items: [{
                ...row,
                executionStanding: {
                    kind: InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
                    reason: InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE,
                },
            }, second],
            total: 2,
            limit: 25,
            offset: 0,
        });
        renderOperations();

        const item = (await screen.findByText("Periodic records request")).closest("li") as HTMLElement;
        const badge = within(item).getByText("Continuing as issued");
        expect(badge.getAttribute("title")).toContain("Payment for the owner's subscription is overdue.");
        const quiet = screen.getByText("Quarterly evidence request").closest("li") as HTMLElement;
        expect(within(quiet).queryByText("Continuing as issued")).toBeNull();
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
            states: OPEN_STATES,
            slaStatus: undefined,
            exceptions: [],
            exceptionsOnly: true,
            limit: 25,
            offset: 0,
        }));
    });

    it("lists draft, issued, and in progress requests until the viewer chooses other states", async () => {
        renderOperations();
        await screen.findByText("Periodic records request");
        expect(transport.getInformationRequestOperations).toHaveBeenCalledWith(expect.objectContaining({states: OPEN_STATES}));

        fireEvent.click(screen.getByRole("combobox", {name: "State"}));
        fireEvent.click(await screen.findByRole("menuitemcheckbox", {name: "Closed"}));

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({states: [...OPEN_STATES, InformationRequestState.CLOSED], offset: 0}),
        ));
    });

    it("narrows the queue to requests with the chosen kinds of exception", async () => {
        renderOperations();
        await screen.findByText("Periodic records request");

        fireEvent.click(screen.getByRole("combobox", {name: "Exception kinds"}));
        fireEvent.click(await screen.findByRole("menuitemcheckbox", {name: "Failed notices"}));
        fireEvent.click(await screen.findByRole("menuitemcheckbox", {name: "Escalated clocks"}));

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(expect.objectContaining({
            exceptions: [InformationRequestOperationsException.NOTICE_FAILED, InformationRequestOperationsException.CLOCK_ESCALATED],
        })));
    });

    it("finds an Exchange by name and lists only that Exchange's requests", async () => {
        vi.mocked(exchanges.searchExchanges).mockImplementation(async query =>
            exchangePage(query === "Quarterly" ? [{id: "exchange-q", name: "Quarterly review"}] : []));
        renderOperations();
        await screen.findByText("Periodic records request");

        const exchange = screen.getByRole("combobox", {name: "Exchange"});
        fireEvent.change(exchange, {target: {value: "Quarterly"}});
        await waitFor(() => expect(exchanges.searchExchanges).toHaveBeenLastCalledWith("Quarterly", undefined, undefined, 0, 10));
        expect((exchange as HTMLInputElement).value).toBe("Quarterly");
        fireEvent.click(exchange);
        fireEvent.click(await screen.findByRole("option", {name: "Quarterly review"}));

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({exchangeId: "exchange-q", offset: 0}),
        ));
        expect((exchange as HTMLInputElement).value).toBe("Quarterly review");

        fireEvent.click(exchange);
        fireEvent.click(await screen.findByRole("option", {name: "Any Exchange"}));
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({exchangeId: undefined}),
        ));
    });

    it("returns to the first page when a filter changes", {timeout: 15000}, async () => {
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({
            items: [row, second],
            total: 60,
            limit: 25,
            offset: 0
        });
        renderOperations();

        fireEvent.click(await screen.findByRole("button", {name: "Next"}));
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({offset: 25}),
        ));
        fireEvent.click(screen.getByRole("combobox", {name: "State"}));
        fireEvent.click(await screen.findByRole("menuitemcheckbox", {name: "Expired"}));

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({states: [...OPEN_STATES, InformationRequestState.EXPIRED], offset: 0}),
        ));
    });

    it("keeps the shown requests and the pager while the next page of the same filters loads", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => filter.offset === 0
            ? Promise.resolve({items: [row, second], total: 60, limit: 25, offset: 0})
            : unansweredQueue());
        renderOperations();
        const next = await screen.findByRole("button", {name: "Next"});
        next.focus();

        fireEvent.click(next);

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({offset: 25}),
        ));
        expect(screen.getByText("Periodic records request")).toBeTruthy();
        expect(document.activeElement).toBe(next);
    });

    it("withholds the requests listed for earlier filters while the changed filters load", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => filter.exceptionsOnly
            ? unansweredQueue()
            : Promise.resolve({items: [row, second], total: 2, limit: 25, offset: 0}));
        renderOperations();
        expect(await screen.findByText("Periodic records request")).toBeTruthy();

        fireEvent.click(screen.getByRole("switch", {name: "Exceptions only"}));

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({exceptionsOnly: true}),
        ));
        expect(screen.queryByText("Periodic records request")).toBeNull();
    });

    it("withholds the listed requests, their controls, and the pager once the next page of the same filters is refused", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => filter.offset === 0
            ? Promise.resolve({items: [row, second], total: 60, limit: 25, offset: 0})
            : Promise.reject({
                errorMessage: "Access denied to this owner's queue",
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
            }));
        renderOperations();

        fireEvent.click(await screen.findByRole("button", {name: "Next"}));

        expect((await screen.findByRole("alert")).textContent).toContain("Access denied to this owner's queue");
        expect(screen.queryByRole("list", {name: "Information Requests"})).toBeNull();
        expect(screen.queryByText("Periodic records request")).toBeNull();
        expect(screen.queryByRole("button", {name: "Open Periodic records request"})).toBeNull();
        expect(screen.queryByRole("checkbox", {name: "Select Periodic records request"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Next"})).toBeNull();
        expect(document.getElementById("information-request-operations-range")).toBeNull();
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

    it("withholds the assignee names learned from the queue once a read of it is refused", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => filter.offset === 0
            ? Promise.resolve({items: [row, second], total: 60, limit: 25, offset: 0})
            : Promise.reject(QUEUE_REFUSAL));
        renderOperations();

        fireEvent.click(await screen.findByRole("button", {name: "Next"}));
        await screen.findByRole("alert");
        fireEvent.click(screen.getByRole("combobox", {name: "Assigned to"}));

        expect(await screen.findByRole("option", {name: "Anyone"})).toBeTruthy();
        expect(screen.getByRole("option", {name: "Me"})).toBeTruthy();
        expect(screen.queryByRole("option", {name: "Morgan Lee"})).toBeNull();
    });

    it("keeps learned assignee names withheld while the queue is read again after a refusal and offers them once it answers", async () => {
        let answer: (page: Awaited<ReturnType<typeof transport.getInformationRequestOperations>>) => void = () => undefined;
        const retried = new Promise<Awaited<ReturnType<typeof transport.getInformationRequestOperations>>>(resolve => {
            answer = resolve;
        });
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => {
            if (filter.search) return retried;
            return filter.offset === 0
                ? Promise.resolve({items: [row, second], total: 60, limit: 25, offset: 0})
                : Promise.reject(QUEUE_REFUSAL);
        });
        renderOperations();
        fireEvent.click(await screen.findByRole("button", {name: "Next"}));
        await screen.findByRole("alert");

        fireEvent.change(screen.getByRole("searchbox", {name: "Search"}), {target: {value: "records"}});
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({search: "records", offset: 0}),
        ));
        fireEvent.click(screen.getByRole("combobox", {name: "Assigned to"}));

        expect(await screen.findByRole("option", {name: "Anyone"})).toBeTruthy();
        expect(screen.queryByRole("option", {name: "Morgan Lee"})).toBeNull();
        answer({items: [second], total: 1, limit: 25, offset: 0});
        expect(await screen.findByRole("option", {name: "Morgan Lee"})).toBeTruthy();
    });

    it("shows an assignee chosen before a refusal as Chosen party until the queue is read again", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => filter.offset === 0
            ? Promise.resolve({items: [row, second], total: 60, limit: 25, offset: 0})
            : Promise.reject(QUEUE_REFUSAL));
        renderOperations();
        fireEvent.click(await screen.findByRole("combobox", {name: "Assigned to"}));
        fireEvent.click(await screen.findByRole("option", {name: "Morgan Lee"}));
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({assigneeId: "user-b", offset: 0}),
        ));

        fireEvent.click(await screen.findByRole("button", {name: "Next"}));
        await screen.findByRole("alert");

        const assignee = screen.getByRole("combobox", {name: "Assigned to"});
        expect(assignee.textContent).toBe("Chosen party");
        expect(screen.queryByText("Morgan Lee")).toBeNull();
    });

    it("keeps the chosen assignee's name and the learned names while the queue reads that assignee's requests", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => filter.assigneeId
            ? unansweredQueue()
            : Promise.resolve({items: [row, second], total: 2, limit: 25, offset: 0}));
        renderOperations();
        fireEvent.click(await screen.findByRole("combobox", {name: "Assigned to"}));
        fireEvent.click(await screen.findByRole("option", {name: "Morgan Lee"}));

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({assigneeId: "user-b"}),
        ));
        expect(screen.getByRole("combobox", {name: "Assigned to"}).textContent).toBe("Morgan Lee");
    });

    it("keeps the learned assignee names while the next page of the same filters loads", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => filter.offset === 0
            ? Promise.resolve({items: [row, second], total: 60, limit: 25, offset: 0})
            : unansweredQueue());
        renderOperations();

        fireEvent.click(await screen.findByRole("button", {name: "Next"}));
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({offset: 25}),
        ));
        fireEvent.click(screen.getByRole("combobox", {name: "Assigned to"}));

        expect(await screen.findByRole("option", {name: "Morgan Lee"})).toBeTruthy();
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

    it("lets an organization's hold manager place a hold on a listed request without opening its detail", async () => {
        mockAuth.mockReturnValue({
            appUser: {id: "user-a"},
            currentSession: {activeOrganizationId: "organization-a"},
            hasCapability: (capability: Capability) =>
                capability === Capability.INFORMATION_REQUEST_OPERATIONS_READ || capability === Capability.AUDIT_LEGAL_HOLD_MANAGE,
        });
        vi.mocked(records.placeRecordPreservationHold).mockResolvedValue({} as Awaited<ReturnType<typeof records.placeRecordPreservationHold>>);
        renderOperations();

        fireEvent.click(await screen.findByRole("button", {name: "Place a hold on Periodic records request"}));
        fireEvent.change(await screen.findByLabelText(/Reason/), {target: {value: "Records are needed for a dispute"}});
        fireEvent.click(screen.getByRole("button", {name: "Place hold"}));

        await waitFor(() => expect(records.placeRecordPreservationHold).toHaveBeenCalledTimes(1));
        expect(vi.mocked(records.placeRecordPreservationHold).mock.calls[0][0]).toMatchObject({
            resourceType: "INFORMATION_REQUEST",
            resourceId: "request-a-0000",
            reason: "Records are needed for a dispute",
        });
        expect(await screen.findByText("Hold placed on Periodic records request.")).toBeTruthy();
    });

    it("offers no hold placement to a member who does not manage holds", async () => {
        mockAuth.mockReturnValue({
            appUser: {id: "user-a"},
            currentSession: {activeOrganizationId: "organization-a"},
            hasCapability: (capability: Capability) => capability === Capability.INFORMATION_REQUEST_OPERATIONS_READ,
        });
        renderOperations();

        expect(await screen.findByText("Periodic records request")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Place a hold on Periodic records request"})).toBeNull();
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

    it("exports the requests that match the filters the queue shows", async () => {
        URL.createObjectURL = vi.fn(() => "blob:operations");
        URL.revokeObjectURL = vi.fn();
        const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
        renderOperations();
        await screen.findByText("Periodic records request");
        fireEvent.click(screen.getByRole("combobox", {name: "Exception kinds"}));
        fireEvent.click(await screen.findByRole("menuitemcheckbox", {name: "Undeliverable notices"}));

        fireEvent.click(screen.getByRole("button", {name: "Export CSV"}));

        await waitFor(() => expect(click).toHaveBeenCalledTimes(1));
        expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(expect.objectContaining({
            states: OPEN_STATES,
            exceptions: [InformationRequestOperationsException.NOTICE_UNDELIVERABLE],
            limit: 200,
            offset: 0,
        }));
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

        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("keeps the queue readable and says why new work is unavailable when the plan lacks the feature", async () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature({holdsRequests: true}));

        renderOperations();

        expect(await screen.findByText("Periodic records request")).toBeTruthy();
        expect(document.getElementById("information-request-operations-scope-standing")?.textContent)
            .toMatch(/Your plan does not include creating Information Requests/);
    });

    it("withholds the previous workspace's queue and reads the queue of the workspace switched to", async () => {
        signedInTo("organization-a");
        const page = renderOperations();
        await screen.findByText("Periodic records request");
        const reads = vi.mocked(transport.getInformationRequestOperations).mock.calls.length;
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(unansweredQueue);

        signedInTo("organization-b");
        page.rerender(operationsPage());

        expect(screen.queryByText("Periodic records request")).toBeNull();
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenCalledTimes(reads + 1));
    });

    it("shows no queue and reads nothing while a workspace switch has no signed-in session yet", async () => {
        signedInTo("organization-a");
        const page = renderOperations();
        await screen.findByText("Periodic records request");
        const reads = vi.mocked(transport.getInformationRequestOperations).mock.calls.length;

        switchingWorkspace();
        page.rerender(operationsPage());
        await settled();

        expect(screen.queryByText("Periodic records request")).toBeNull();
        expect(screen.queryByRole("button", {name: "Export CSV"})).toBeNull();
        expect(document.getElementById("information-request-operations-workspace-loading")).not.toBeNull();
        expect(transport.getInformationRequestOperations).toHaveBeenCalledTimes(reads);
    });

    it("keeps a late answer for the previous workspace out of the queue of the workspace switched to", async () => {
        const earlier = pendingQueue();
        const next = pendingQueue();
        const answers = [earlier.read, next.read];
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(() => answers.shift() ?? unansweredQueue());
        signedInTo("organization-a");
        const page = renderOperations();
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenCalledTimes(1));

        signedInTo("organization-b");
        page.rerender(operationsPage());
        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenCalledTimes(2));
        await act(async () => earlier.answer({items: [row], total: 1, limit: 25, offset: 0}));

        expect(screen.queryByText("Periodic records request")).toBeNull();
        await act(async () => next.answer({items: [nextWorkspaceRow], total: 1, limit: 25, offset: 0}));
        expect(await screen.findByText("Next workspace request")).toBeTruthy();
        expect(screen.queryByText("Periodic records request")).toBeNull();
    });

    it("drops the selection and the assignee names learned in the previous workspace", async () => {
        signedInTo("organization-a");
        const page = renderOperations();
        fireEvent.click(await screen.findByRole("checkbox", {name: "Select Periodic records request"}));
        expect(screen.getByText("1 selected")).toBeTruthy();
        vi.mocked(transport.getInformationRequestOperations)
            .mockResolvedValue({items: [nextWorkspaceRow], total: 1, limit: 25, offset: 0});

        signedInTo("organization-b");
        page.rerender(operationsPage());

        expect(await screen.findByText("Next workspace request")).toBeTruthy();
        expect(screen.queryByText("1 selected")).toBeNull();
        fireEvent.click(screen.getByRole("combobox", {name: "Assigned to"}));
        expect(await screen.findByRole("option", {name: "Anyone"})).toBeTruthy();
        expect(screen.queryByRole("option", {name: "Morgan Lee"})).toBeNull();
    });

    it("saves no file from an export that was running when the workspace changed", async () => {
        const exportPage = pendingQueue();
        vi.mocked(transport.getInformationRequestOperations).mockImplementation(filter => filter.limit === 200
            ? exportPage.read
            : Promise.resolve({items: [row, second], total: 2, limit: 25, offset: 0}));
        URL.createObjectURL = vi.fn(() => "blob:operations");
        URL.revokeObjectURL = vi.fn();
        const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
        signedInTo("organization-a");
        const page = renderOperations();
        fireEvent.click(await screen.findByRole("button", {name: "Export CSV"}));
        await waitFor(() => expect(transport.getInformationRequestOperations)
            .toHaveBeenCalledWith(expect.objectContaining({limit: 200, offset: 0})));

        signedInTo("organization-b");
        page.rerender(operationsPage());
        await act(async () => exportPage.answer({items: [row], total: 401, limit: 200, offset: 0}));
        await settled();

        expect(click).not.toHaveBeenCalled();
        expect(transport.getInformationRequestOperations)
            .not.toHaveBeenCalledWith(expect.objectContaining({limit: 200, offset: 1}));
        click.mockRestore();
    });

    it("opens the chosen tab afresh for the workspace switched to", async () => {
        signedInTo("organization-a");
        const page = renderOperations();
        fireEvent.click(await screen.findByRole("tab", {name: "Privacy"}));
        const earlier = (await screen.findByText("Privacy records")).getAttribute("data-mount");

        signedInTo("organization-b");
        page.rerender(operationsPage());

        const next = (await screen.findByText("Privacy records")).getAttribute("data-mount");
        expect(next).not.toBeNull();
        expect(next).not.toBe(earlier);
    });

    it("titles the page as its level one heading", async () => {
        renderOperations();

        expect(screen.getByRole("heading", {level: 1, name: "Information Request operations"})).toBeTruthy();
        expect(await screen.findByText("Periodic records request")).toBeTruthy();
    });

    it("keeps the page closed to an organization member without operations read access", async () => {
        mockAuth.mockReturnValue({
            appUser: {id: "user-a"},
            currentSession: {userId: "user-a", activeOrganizationId: "organization-a"},
            hasCapability: () => false,
        });
        renderOperations();
        await settled();

        expect(document.getElementById("information-request-operations-forbidden-body")?.textContent)
            .toBe("You do not have access to Information Request operations for this account.");
        expect(screen.queryByRole("tab", {name: "Queue"})).toBeNull();
        expect(transport.getInformationRequestOperations).not.toHaveBeenCalled();
    });

    it("states a refused queue read and reads the queue again on Try again", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockRejectedValueOnce(QUEUE_REFUSAL);
        renderOperations();

        expect((await screen.findByRole("alert")).textContent).toContain("Access denied to this owner's queue");
        expect(document.getElementById("information-request-operations-loading")).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByText("Periodic records request")).toBeTruthy();
        expect(transport.getInformationRequestOperations).toHaveBeenCalledTimes(2);
    });

    it("offers only issued and in progress requests for reminders", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({
            items: [
                {...row, state: InformationRequestState.ISSUED},
                {...second, state: InformationRequestState.DRAFT},
                {...nextWorkspaceRow, state: InformationRequestState.CLOSED},
            ],
            total: 3, limit: 25, offset: 0,
        });
        renderOperations();

        expect(await screen.findByRole("checkbox", {name: "Select Periodic records request"})).toBeTruthy();
        expect(screen.queryByRole("checkbox", {name: "Select Quarterly evidence request"})).toBeNull();
        expect(screen.queryByRole("checkbox", {name: "Select Next workspace request"})).toBeNull();
        expect(screen.getByText("Select issued or in progress requests to remind their responding parties.")).toBeTruthy();
        fireEvent.click(screen.getByRole("checkbox", {name: "Select Periodic records request"}));
        fireEvent.click(screen.getByRole("button", {name: "Send reminders"}));
        const dialog = await screen.findByRole("dialog", {name: "Send reminders"});
        expect(within(dialog).getByText(/If any selected request is no longer issued or in progress, nothing is sent\./)).toBeTruthy();
    });

    it("clears the selection once a filter changes so no hidden request is reminded", async () => {
        renderOperations();
        fireEvent.click(await screen.findByRole("checkbox", {name: "Select Periodic records request"}));
        expect(screen.getByText("1 selected")).toBeTruthy();

        fireEvent.click(screen.getByRole("combobox", {name: "State"}));
        fireEvent.click(await screen.findByRole("menuitemcheckbox", {name: "Closed"}));

        await waitFor(() => expect(transport.getInformationRequestOperations).toHaveBeenLastCalledWith(
            expect.objectContaining({states: [...OPEN_STATES, InformationRequestState.CLOSED]}),
        ));
        expect(screen.queryByText("1 selected")).toBeNull();
        const checkbox = await screen.findByRole("checkbox", {name: "Select Periodic records request"});
        expect((checkbox as HTMLInputElement).checked).toBe(false);
    });

    it("stops selecting at the 100 requests one reminder batch takes", {timeout: 30000}, async () => {
        const many = Array.from({length: 101}, (_, index) => ({
            ...second,
            requestId: `request-${index}`,
            title: `Request ${index}`
        }));
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({
            items: many,
            total: 101,
            limit: 25,
            offset: 0
        });
        renderOperations();
        await screen.findByRole("checkbox", {name: "Select Request 0"});

        many.slice(0, 100).forEach(item =>
            fireEvent.click(document.getElementById(`information-request-operations-row-${item.requestId}-select`) as HTMLElement));

        expect(screen.getByText("100 selected, the most that can be reminded at once.")).toBeTruthy();
        expect((screen.getByRole("checkbox", {name: "Select Request 100"}) as HTMLInputElement).disabled).toBe(true);
        expect((screen.getByRole("checkbox", {name: "Select Request 0"}) as HTMLInputElement).disabled).toBe(false);
    });

    it("says when each request was issued, first viewed, and started", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({
            items: [
                {...row, issuedAt: Date.parse("2026-09-20T09:00:00Z"), firstViewedAt: null, startedAt: null},
                {
                    ...second,
                    issuedAt: Date.parse("2026-09-20T09:00:00Z"),
                    firstViewedAt: Date.parse("2026-09-21T09:00:00Z"),
                    startedAt: Date.parse("2026-09-22T09:00:00Z"),
                },
                {...nextWorkspaceRow, state: InformationRequestState.DRAFT, issuedAt: null},
            ],
            total: 3, limit: 25, offset: 0,
        });
        renderOperations();

        const unopened = (await screen.findByText("Periodic records request")).closest("li") as HTMLElement;
        expect(within(unopened).getByText(shown(
            `Issued ${formatInformationRequestTime(Date.parse("2026-09-20T09:00:00Z"))}, not viewed yet, not started yet`,
        ))).toBeTruthy();
        const started = screen.getByText("Quarterly evidence request").closest("li") as HTMLElement;
        expect(within(started).getByText(shown(
            `Issued ${formatInformationRequestTime(Date.parse("2026-09-20T09:00:00Z"))}, `
            + `first viewed ${formatInformationRequestTime(Date.parse("2026-09-21T09:00:00Z"))}, `
            + `started ${formatInformationRequestTime(Date.parse("2026-09-22T09:00:00Z"))}`,
        ))).toBeTruthy();
        const draft = screen.getByText("Next workspace request").closest("li") as HTMLElement;
        expect(within(draft).getByText("Not issued yet")).toBeTruthy();
    });

    it("counts a single reminder point, notice, and exception in the singular and in the viewer's number format", async () => {
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({
            items: [
                {...row, reminderCount: 1, noticeCounts: {[InformationRequestNoticeDeliveryState.DELIVERED]: 1500}},
                {...second, reminderCount: 1500},
            ],
            total: 2, limit: 25, offset: 0,
        });
        renderOperations();

        const single = (await screen.findByText("Periodic records request")).closest("li") as HTMLElement;
        expect(within(single).getByText(/, 1 clock reminder point$/)).toBeTruthy();
        expect(within(single).getByText(`Notices: ${formatInformationRequestCount(1500)} delivered`)).toBeTruthy();
        expect(within(single).getByText("Needs attention: 1 undeliverable notice")).toBeTruthy();
        const many = screen.getByText("Quarterly evidence request").closest("li") as HTMLElement;
        expect(within(many).getByText(new RegExp(`, ${formatInformationRequestCount(1500)} clock reminder points$`))).toBeTruthy();
    });

    it("states the fallback instead of the markup of a refused reminder batch", async () => {
        vi.mocked(administration.sendInformationRequestReminders)
            .mockRejectedValueOnce("<html><body><h1>502 Bad Gateway</h1></body></html>");
        renderOperations();

        fireEvent.click(await screen.findByRole("checkbox", {name: "Select Periodic records request"}));
        fireEvent.click(screen.getByRole("button", {name: "Send reminders"}));
        fireEvent.click(within(await screen.findByRole("dialog", {name: "Send reminders"})).getByRole("button", {name: "Send reminders"}));

        expect((await screen.findByRole("alert")).textContent).toBe("The change could not be made.");
    });

    it("says which requests were reminded recently and when they can be reminded again", async () => {
        const later = "2026-10-01T12:00:00Z";
        vi.mocked(administration.sendInformationRequestReminders).mockResolvedValue([
            {requestId: "request-a-0000", noticeCount: 2},
            {requestId: "request-b-0000", noticeCount: 0, cooldownUntil: later},
        ]);
        renderOperations();

        fireEvent.click(await screen.findByRole("checkbox", {name: "Select Periodic records request"}));
        fireEvent.click(screen.getByRole("checkbox", {name: "Select Quarterly evidence request"}));
        fireEvent.click(screen.getByRole("button", {name: "Send reminders"}));
        fireEvent.click(within(await screen.findByRole("dialog", {name: "Send reminders"})).getByRole("button", {name: "Send reminders"}));

        expect(await screen.findByText(shown(
            "2 reminder notices were queued for 1 request. 1 request was reminded recently and can be reminded again from "
            + `${formatInformationRequestTime(Date.parse(later))}.`,
        ))).toBeTruthy();
    });

    it("sends reminders again under the same Idempotency-Key after the queue was left and opened again", async () => {
        vi.mocked(administration.sendInformationRequestReminders)
            .mockRejectedValueOnce(new Error("Network Error"))
            .mockResolvedValueOnce([{requestId: "request-a-0000", noticeCount: 1}]);
        renderOperations();
        const byId = (id: string): HTMLElement => document.getElementById(id) as HTMLElement;
        const remind = async () => {
            await waitFor(() => expect(document.getElementById("information-request-operations-row-request-a-0000-select")).not.toBeNull());
            fireEvent.click(byId("information-request-operations-row-request-a-0000-select"));
            fireEvent.click(byId("information-request-operations-remind-btn"));
            await waitFor(() => expect(document.getElementById("information-request-send-reminders-dialog-confirm")).not.toBeNull());
            fireEvent.click(byId("information-request-send-reminders-dialog-confirm"));
        };

        await remind();
        await waitFor(() => expect(document.getElementById("information-request-operations-error")).not.toBeNull());
        fireEvent.click(byId("information-request-operations-policies-tab"));
        expect(await screen.findByText("Policies you manage")).toBeTruthy();
        fireEvent.click(byId("information-request-operations-queue-tab"));
        await remind();

        await waitFor(() => expect(administration.sendInformationRequestReminders).toHaveBeenCalledTimes(2));
        const [sent, resent] = vi.mocked(administration.sendInformationRequestReminders).mock.calls.map(call => call[1]);
        expect(resent).toBe(sent);
    });
    it("names its scheduled reminder count as clock reminder points", async () => {
        renderOperations();
        expect((await screen.findAllByText(/2 clock reminder points/)).length).toBeGreaterThan(0);
    });
});
