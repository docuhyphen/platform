/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {MemoryRouter, Route, Routes, useLocation} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import AccountMenu from "../../../components/main-menu/account-menu/AccountMenu.tsx";
import * as templates from "../../../../services/informationRequestTemplateService.ts";
import {
    InformationRequestExchangeListingDto,
    InformationRequestOwnerType,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
} from "../../../models/models.tsx";
import {ExchangeInformationRequestsState} from "../useExchangeInformationRequests.ts";
import ExchangeInformationRequestsTab from "./ExchangeInformationRequestsTab.tsx";

const auth = vi.hoisted(() => {
    const organizations = [
        {organizationId: "organization-a", name: "Records team"},
        {organizationId: "organization-b", name: "Review team"},
    ];
    const sessionFor = (organizationId: string | null) => ({
        userId: "user-a",
        email: "member@process.test",
        activeOrganizationId: organizationId,
        availableOrganizations: organizations,
    });
    let current: ReturnType<typeof sessionFor> | null = sessionFor("organization-a");
    let refresh: () => void = () => undefined;
    const listeners = new Set<() => void>();
    const set = (next: ReturnType<typeof sessionFor> | null) => {
        current = next;
        listeners.forEach(listener => listener());
    };
    return {
        sessionFor,
        set,
        get: () => current,
        subscribe: (listener: () => void) => {
            listeners.add(listener);
            return () => {
                listeners.delete(listener);
            };
        },
        switchOrganization: async (organizationId: string | null) => {
            set(null);
            await new Promise<void>(resolve => {
                refresh = resolve;
            });
            set(sessionFor(organizationId));
        },
        finishRefresh: () => refresh(),
    };
});

vi.mock("../../../../context/AuthContext.tsx", async () => {
    const {useSyncExternalStore} = await import("react");
    return {
        useAuth: () => ({
            appUser: {id: "user-a", email: "member@process.test", person: {firstName: "Process", lastName: "Member"}},
            currentSession: useSyncExternalStore(auth.subscribe, auth.get),
            token: null,
            setToken: () => undefined,
            switchOrganization: auth.switchOrganization,
        }),
    };
});
vi.mock("../../../../services/informationRequestTemplateService.ts", () => ({
    listInformationRequestTemplates: vi.fn(),
    getInformationRequestTemplate: vi.fn(),
}));
vi.mock("../../../../services/blueprintService.ts", () => ({listBlueprints: vi.fn().mockResolvedValue([])}));
vi.mock("../../../../services/fieldsService.ts", () => ({listSchemas: vi.fn().mockResolvedValue([])}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    createInformationRequest: vi.fn(),
    getInformationRequestParties: vi.fn(),
    assignInformationRequestParty: vi.fn(),
}));
vi.mock("../../../../services/informationRequestReviewService.ts", () => ({getInformationRequestReviewQueue: vi.fn()}));
vi.mock("../../../../services/NotificationService.tsx", () => ({realtimeService: {disconnect: vi.fn()}}));
vi.mock("../../../../services/authApi.ts", () => ({signOut: vi.fn()}));

const template = (organizationId: string): InformationRequestTemplateSummaryDto => ({
    id: `${organizationId}-template`,
    scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
    namespace: "records",
    templateKey: "record-check",
    displayName: `${organizationId} records`,
    status: InformationRequestTemplateStatus.PUBLISHED,
    hasEditableVersion: false,
    latestPublishedVersionNumber: 1,
    createdAt: 0,
    updatedAt: 0,
});

const listing: InformationRequestExchangeListingDto = {
    requests: [],
    canCreate: true,
    creationOwner: {ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "organization-a"},
};

const tabState: ExchangeInformationRequestsState = {
    listing,
    loading: false,
    error: null,
    visible: true,
    reload: async () => undefined,
};

const ShownLocation = () => {
    const location = useLocation();
    return <p id={"shown-location"}>{`${location.pathname}${location.search}`}</p>;
};

const renderPage = () => render(
    <MemoryRouter initialEntries={["/exchanges"]}>
        <Routes>
            <Route path={"/exchanges"}
                   element={(
                       <>
                           <AccountMenu onToggleHelpSidebar={() => undefined}/>
                           <ExchangeInformationRequestsTab exchangeId={"exchange-a"}
                                                           state={tabState}/>
                           <ShownLocation/>
                       </>
                   )}/>
        </Routes>
    </MemoryRouter>,
);

const switchTo = async (workspace: string) => {
    fireEvent.click(await screen.findByRole("button", {name: "Account menu for member@process.test"}, {timeout: 5000}));
    fireEvent.click(await screen.findByRole("menuitem", {name: workspace}, {timeout: 5000}));
};

const openDialog = async () => {
    fireEvent.click(screen.getByRole("button", {name: "New Information Request"}));
    await screen.findByRole("dialog", {name: "New Information Request"});
};

const closeDialog = () => {
    fireEvent.click(document.getElementById("create-information-request-dialog-cancel") as HTMLElement);
};

describe("Creating a request across a workspace switch from the account menu", () => {
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
        auth.set(auth.sessionFor("organization-a"));
        vi.mocked(templates.listInformationRequestTemplates).mockReset();
        vi.mocked(templates.listInformationRequestTemplates)
            .mockImplementation(async () => [template(auth.get()?.activeOrganizationId ?? "personal")]);
    });

    afterEach(cleanup);

    it("offers only the Exchange's own workspace's sources after the account menu switches workspace and back", {timeout: 20000}, async () => {
        renderPage();
        await openDialog();
        expect(await screen.findByRole("option", {name: "organization-a records"})).toBeTruthy();
        closeDialog();

        await switchTo("Review team");
        expect(auth.get()).toBeNull();
        await act(async () => auth.finishRefresh());
        await waitFor(() => expect(document.getElementById("shown-location")?.textContent).toBe("/exchanges?tab=inbox"));
        await openDialog();

        expect(await screen.findByText(/This Exchange belongs to another workspace/)).toBeTruthy();
        expect(screen.queryByLabelText("Template")).toBeNull();
        expect(screen.queryByRole("option", {name: "organization-b records"})).toBeNull();
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledTimes(1);
        closeDialog();

        await switchTo("Records team");
        await act(async () => auth.finishRefresh());
        await openDialog();

        expect(await screen.findByRole("option", {name: "organization-a records"})).toBeTruthy();
        expect(screen.queryByText(/This Exchange belongs to another workspace/)).toBeNull();
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledTimes(2);
    });

    it("drops the earlier workspace's sources while the dialog stays open across a session change made elsewhere", async () => {
        renderPage();
        await openDialog();
        expect(await screen.findByRole("option", {name: "organization-a records"})).toBeTruthy();

        act(() => auth.set(null));
        expect(screen.queryByRole("option", {name: "organization-a records"})).toBeNull();
        act(() => auth.set(auth.sessionFor("organization-b")));

        expect(await screen.findByText(/This Exchange belongs to another workspace/)).toBeTruthy();
        expect(screen.queryByLabelText("Template")).toBeNull();
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledTimes(1);
    });
});
