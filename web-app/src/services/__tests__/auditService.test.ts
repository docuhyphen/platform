import {describe, expect, it, vi} from "vitest";

const get = vi.fn();
const post = vi.fn();

vi.mock("../apiClient.ts", () => ({
    default: {
        get: (...args: unknown[]) => get(...args),
        post: (...args: unknown[]) => post(...args),
    },
}));

// AuditExportDto shape (see web-app/src/app/models/models.tsx) as actually returned by
// AuditOrganizationExportsResource.approveOrganizationExport / AuditExportResource.approvePlatformExport
// - both endpoints return the mutated export, not an approval record.
const exportResponseBody = {
    exportId: "export-1",
    requestedByUserId: "user-1",
    requestedAt: "2024-03-01T00:00:00Z",
    categories: ["SECURITY"],
    occurredAfter: "2024-02-01T00:00:00Z",
    occurredBefore: "2024-03-01T00:00:00Z",
    purpose: "investigation",
    status: "PENDING_APPROVAL",
    requiredApprovals: 2,
    approvalCount: 1,
    downloadCount: 0,
};

describe("auditService export approval contract", () =>
{
    it("approveOrganizationAuditExport resolves the mutated export, not an approval record", async () =>
    {
        post.mockResolvedValueOnce({data: exportResponseBody});
        const {approveOrganizationAuditExport} = await import("../auditService.ts");

        const result = await approveOrganizationAuditExport("org-1", "export-1", "note");

        expect(result).toEqual(exportResponseBody);
        expect(result).not.toHaveProperty("approvedByUserId");
        expect(post).toHaveBeenCalledWith(
            "/organizations/org-1/audit-exports/export-1/approvals",
            {note: "note"},
        );
    });

    it("approvePlatformAuditExport resolves the mutated export, not an approval record", async () =>
    {
        post.mockResolvedValueOnce({data: exportResponseBody});
        const {approvePlatformAuditExport} = await import("../auditService.ts");

        const result = await approvePlatformAuditExport("export-1", "note");

        expect(result).toEqual(exportResponseBody);
        expect(result).not.toHaveProperty("approvedByUserId");
    });

    it("listPlatformAuditExportApprovals calls the now-existing platform approvals route", async () =>
    {
        const approvalBody = [{approvedByUserId: "user-2", approvedAt: "2024-03-01T00:00:00Z", note: null}];
        get.mockResolvedValueOnce({data: approvalBody});
        const {listPlatformAuditExportApprovals} = await import("../auditService.ts");

        const result = await listPlatformAuditExportApprovals("export-1");

        expect(result).toEqual(approvalBody);
        expect(get).toHaveBeenCalledWith("/platform/audit-exports/export-1/approvals");
    });
});
