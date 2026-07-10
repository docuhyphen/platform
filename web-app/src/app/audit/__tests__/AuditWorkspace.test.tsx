/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, screen, cleanup, waitFor} from "@testing-library/react";
import AuditWorkspace from "../AuditWorkspace.tsx";
import {Capability, OrganizationDetailedDto} from "../../models/models.tsx";

const mockHasCapability = vi.fn();
const mockAppUserPersonOrganization = vi.fn<[], OrganizationDetailedDto | null>();

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        hasCapability: mockHasCapability,
        appUserPersonOrganization: mockAppUserPersonOrganization(),
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

const organization: OrganizationDetailedDto = {
    id: "org-1",
    name: "Acme",
    registrationNumber: "12345",
    isActive: true,
    contactDetails: {},
};

afterEach(() =>
{
    cleanup();
    mockHasCapability.mockReset();
    mockAppUserPersonOrganization.mockReset();
    fetchOrganizationAuditEvents.mockClear();
    fetchPlatformAuditEvents.mockClear();
});

describe("AuditWorkspace", () =>
{
    it("renders the not-authorized state when the user lacks both audit capabilities", () =>
    {
        mockHasCapability.mockReturnValue(false);
        mockAppUserPersonOrganization.mockReturnValue(null);

        render(<AuditWorkspace/>);

        expect(document.getElementById("audit-not-authorized")).toBeTruthy();
        expect(document.getElementById("audit-workspace-container")).toBeFalsy();
    });

    it("never calls the audit event fetchers when not authorized", async () =>
    {
        mockHasCapability.mockReturnValue(false);
        mockAppUserPersonOrganization.mockReturnValue(null);

        render(<AuditWorkspace/>);

        await waitFor(() => expect(document.getElementById("audit-not-authorized")).toBeTruthy());

        expect(fetchOrganizationAuditEvents).not.toHaveBeenCalled();
        expect(fetchPlatformAuditEvents).not.toHaveBeenCalled();
    });

    it("renders the events section by default when the user has ORG_AUDIT_READ", async () =>
    {
        mockHasCapability.mockImplementation((cap: Capability) => cap === Capability.ORG_AUDIT_READ);
        mockAppUserPersonOrganization.mockReturnValue(organization);

        render(<AuditWorkspace/>);

        expect(document.getElementById("audit-workspace-container")).toBeTruthy();

        await waitFor(() => expect(document.getElementById("audit-events-section")).toBeTruthy());

        expect(fetchOrganizationAuditEvents).toHaveBeenCalled();
        expect(screen.getByText("Events")).toBeTruthy();
    });
});
