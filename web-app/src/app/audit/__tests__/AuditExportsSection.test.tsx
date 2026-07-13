/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, cleanup, waitFor} from "@testing-library/react";
import AuditExportsSection from "../audit-exports-section/AuditExportsSection.tsx";
import {Capability} from "../../models/models.tsx";

const mockHasCapability = vi.fn();

const {readyExport, pendingOwnExport} = vi.hoisted(() => ({
    readyExport: {
        exportId: "export-1",
        requestedByUserId: "user-1",
        requestedAt: "2024-03-01T00:00:00Z",
        categories: ["SECURITY"],
        occurredAfter: "2024-02-01T00:00:00Z",
        occurredBefore: "2024-03-01T00:00:00Z",
        purpose: "investigation",
        status: "READY",
        requiredApprovals: 1,
        approvalCount: 1,
        downloadCount: 0,
    },
    pendingOwnExport: {
        exportId: "export-2",
        requestedByUserId: "user-1",
        requestedAt: "2024-03-01T00:00:00Z",
        categories: ["SECURITY"],
        occurredAfter: "2024-02-01T00:00:00Z",
        occurredBefore: "2024-03-01T00:00:00Z",
        purpose: "own investigation",
        status: "APPROVAL_PENDING",
        requiredApprovals: 1,
        approvalCount: 0,
        downloadCount: 0,
    },
}));

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        hasCapability: mockHasCapability,
        currentSession: {userId: "user-1"},
    }),
}));

vi.mock("../../../services/auditService.ts", () => ({
    listOrganizationAuditExports: vi.fn().mockResolvedValue([readyExport, pendingOwnExport]),
    listPlatformAuditExports: vi.fn().mockResolvedValue([readyExport, pendingOwnExport]),
}));

afterEach(() =>
{
    cleanup();
    mockHasCapability.mockReset();
});

describe("AuditExportsSection export-request gating", () =>
{
    it("hides the request-export button for an organization-scoped user without ORG_AUDIT_EXPORT", async () =>
    {
        mockHasCapability.mockReturnValue(false);

        render(<AuditExportsSection scope={{kind: "organization", organizationId: "org-1"}}/>);

        await waitFor(() => expect(document.getElementById("audit-exports-section")).toBeTruthy());

        expect(document.getElementById("button-audit-export-request-open")).toBeFalsy();
    });

    it("shows the request-export button for an organization-scoped user with ORG_AUDIT_EXPORT", async () =>
    {
        mockHasCapability.mockImplementation((cap: Capability) => cap === Capability.ORG_AUDIT_EXPORT);

        render(<AuditExportsSection scope={{kind: "organization", organizationId: "org-1"}}/>);

        await waitFor(() => expect(document.getElementById("button-audit-export-request-open")).toBeTruthy());
    });

    it("shows the request-export button for a platform-scoped user with APP_AUDIT_EXPORT", async () =>
    {
        mockHasCapability.mockImplementation((cap: Capability) => cap === Capability.APP_AUDIT_EXPORT);

        render(<AuditExportsSection scope={{kind: "platform"}}/>);

        await waitFor(() => expect(document.getElementById("button-audit-export-request-open")).toBeTruthy());
    });

    it("hides the request-export button for a platform-scoped user without APP_AUDIT_EXPORT", async () =>
    {
        mockHasCapability.mockReturnValue(false);

        render(<AuditExportsSection scope={{kind: "platform"}}/>);

        await waitFor(() => expect(document.getElementById("audit-exports-section")).toBeTruthy());

        expect(document.getElementById("button-audit-export-request-open")).toBeFalsy();
    });

    it("hides the download button on a ready export for a user without the export capability", async () =>
    {
        mockHasCapability.mockReturnValue(false);

        render(<AuditExportsSection scope={{kind: "organization", organizationId: "org-1"}}/>);

        await waitFor(() => expect(document.getElementById(`audit-export-card-${readyExport.exportId}`)).toBeTruthy());

        expect(document.getElementById(`button-audit-export-download-${readyExport.exportId}`)).toBeFalsy();
    });

    it("shows the download button on a ready export for a user with the export capability", async () =>
    {
        mockHasCapability.mockImplementation((cap: Capability) => cap === Capability.ORG_AUDIT_EXPORT);

        render(<AuditExportsSection scope={{kind: "organization", organizationId: "org-1"}}/>);

        await waitFor(() => expect(document.getElementById(`button-audit-export-download-${readyExport.exportId}`)).toBeTruthy());
    });

    it("hides approval for an export requested by the current user", async () =>
    {
        mockHasCapability.mockImplementation((cap: Capability) => cap === Capability.AUDIT_EXPORT_APPROVE);

        render(<AuditExportsSection scope={{kind: "organization", organizationId: "org-1"}}/>);

        await waitFor(() => expect(document.getElementById(`audit-export-card-${pendingOwnExport.exportId}`)).toBeTruthy());
        expect(document.getElementById(`button-audit-export-approve-${pendingOwnExport.exportId}`)).toBeFalsy();
    });
});
