/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, screen, cleanup, fireEvent, waitFor} from "@testing-library/react";
import AuditWorkspace from "../AuditWorkspace.tsx";
import {Capability} from "../../models/models.tsx";

const mockHasCapability = vi.fn();
const mockCurrentSession = vi.fn();

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        hasCapability: mockHasCapability,
        currentSession: mockCurrentSession(),
    }),
}));

const fetchOrganizationAuditEvents = vi.fn().mockResolvedValue({items: [], nextCursor: null});
const fetchPlatformAuditEvents = vi.fn().mockResolvedValue({items: [], nextCursor: null});

vi.mock("../../../services/auditService.ts", () => ({
    fetchOrganizationAuditEvents: (...args: unknown[]) => fetchOrganizationAuditEvents(...args),
    fetchPlatformAuditEvents: (...args: unknown[]) => fetchPlatformAuditEvents(...args),
    getOrganizationAuditIntegrity: vi.fn().mockResolvedValue(null),
    getPlatformAuditIntegrity: vi.fn().mockResolvedValue(null),
    listOrganizationAuditExports: vi.fn().mockResolvedValue([]),
    listPlatformAuditExports: vi.fn().mockResolvedValue([]),
}));

const organizationSession = {
    userId: "user-1",
    activeOrganizationId: "org-1",
};

afterEach(() =>
{
    cleanup();
    mockHasCapability.mockReset();
    mockCurrentSession.mockReset();
    fetchOrganizationAuditEvents.mockClear();
    fetchPlatformAuditEvents.mockClear();
});

describe("AuditWorkspace", () =>
{
    it("renders the not-authorized state when the user lacks both audit capabilities", () =>
    {
        mockHasCapability.mockReturnValue(false);
        mockCurrentSession.mockReturnValue(null);

        render(<AuditWorkspace/>);

        expect(document.getElementById("audit-not-authorized")).toBeTruthy();
        expect(document.getElementById("audit-workspace-container")).toBeFalsy();
    });

    it("never calls the audit event fetchers when not authorized", async () =>
    {
        mockHasCapability.mockReturnValue(false);
        mockCurrentSession.mockReturnValue(null);

        render(<AuditWorkspace/>);

        await waitFor(() => expect(document.getElementById("audit-not-authorized")).toBeTruthy());

        expect(fetchOrganizationAuditEvents).not.toHaveBeenCalled();
        expect(fetchPlatformAuditEvents).not.toHaveBeenCalled();
    });

    it("renders the events section by default when the user has ORG_AUDIT_READ", async () =>
    {
        mockHasCapability.mockImplementation((cap: Capability) => cap === Capability.ORG_AUDIT_READ);
        mockCurrentSession.mockReturnValue(organizationSession);

        render(<AuditWorkspace/>);

        expect(document.getElementById("audit-workspace-container")).toBeTruthy();

        await waitFor(() => expect(document.getElementById("audit-events-section")).toBeTruthy());

        expect(fetchOrganizationAuditEvents).toHaveBeenCalled();
        expect(screen.getByText("Events")).toBeTruthy();
    });

    it("does not show a scope selector when the user only has one of the two audit capabilities", async () =>
    {
        mockHasCapability.mockImplementation((cap: Capability) => cap === Capability.ORG_AUDIT_READ);
        mockCurrentSession.mockReturnValue(organizationSession);

        render(<AuditWorkspace/>);

        await waitFor(() => expect(document.getElementById("audit-events-section")).toBeTruthy());

        expect(document.getElementById("audit-workspace-scope-selector")).toBeFalsy();
    });

    it("shows a scope selector defaulting to organization when the user holds both audit capabilities, and switching it fetches platform-scoped events", async () =>
    {
        mockHasCapability.mockReturnValue(true);
        mockCurrentSession.mockReturnValue(organizationSession);

        render(<AuditWorkspace/>);

        await waitFor(() => expect(fetchOrganizationAuditEvents).toHaveBeenCalled());
        expect(fetchPlatformAuditEvents).not.toHaveBeenCalled();

        const selector = document.getElementById("audit-workspace-scope-selector") as HTMLElement;
        expect(selector).toBeTruthy();

        fireEvent.click(selector);
        const platformOption = await screen.findByText("Platform");
        fireEvent.click(platformOption);

        await waitFor(() => expect(fetchPlatformAuditEvents).toHaveBeenCalled());
    });

    it("uses platform scope without a selector when rendered by the platform route", async () =>
    {
        mockHasCapability.mockReturnValue(true);
        mockCurrentSession.mockReturnValue(organizationSession);

        render(<AuditWorkspace fixedScope={"platform"}/>);

        await waitFor(() => expect(fetchPlatformAuditEvents).toHaveBeenCalled());
        expect(fetchOrganizationAuditEvents).not.toHaveBeenCalled();
        expect(document.getElementById("audit-workspace-scope-selector")).toBeFalsy();
    });

    it("uses left navigation without a scope header on the platform workspace", async () =>
    {
        mockHasCapability.mockReturnValue(true);
        mockCurrentSession.mockReturnValue(organizationSession);

        render(
            <AuditWorkspace
                fixedScope={"platform"}
                navigationMode={"sidebar"}/>
        );

        await waitFor(() => expect(fetchPlatformAuditEvents).toHaveBeenCalled());

        expect(document.getElementById("audit-workspace-sidebar")).toBeTruthy();
        expect(document.getElementById("audit-workspace-main-content")).toBeTruthy();
        expect(document.getElementById("audit-workspace-scope-note")).toBeFalsy();
        expect(document.getElementById("audit-workspace-tabs")).toBeFalsy();
    });
});
