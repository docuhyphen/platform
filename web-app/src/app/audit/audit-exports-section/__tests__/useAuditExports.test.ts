/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {act, renderHook, waitFor} from "@testing-library/react";
import {useAuditExports} from "../useAuditExports.ts";
import {
    approveOrganizationAuditExport,
    downloadOrganizationAuditExport,
    listOrganizationAuditExports,
    listPlatformAuditExports,
    requestOrganizationAuditExport,
} from "../../../../services/auditService.ts";

vi.mock("../../../../services/auditService.ts", () => ({
    listOrganizationAuditExports: vi.fn(),
    listPlatformAuditExports: vi.fn(),
    requestOrganizationAuditExport: vi.fn(),
    requestPlatformAuditExport: vi.fn(),
    approveOrganizationAuditExport: vi.fn(),
    approvePlatformAuditExport: vi.fn(),
    downloadOrganizationAuditExport: vi.fn(),
    downloadPlatformAuditExport: vi.fn(),
}));

afterEach(() =>
{
    vi.mocked(listOrganizationAuditExports).mockReset();
    vi.mocked(listPlatformAuditExports).mockReset();
    vi.mocked(requestOrganizationAuditExport).mockReset();
    vi.mocked(approveOrganizationAuditExport).mockReset();
    vi.mocked(downloadOrganizationAuditExport).mockReset();
});

describe("useAuditExports stale scope-switch protection", () =>
{
    it("does not let a slow organization-scope response overwrite a faster platform-scope response after a scope switch", async () =>
    {
        let resolveOrg: (value: unknown) => void = () => {};
        const orgPending = new Promise((resolve) =>
        {
            resolveOrg = resolve;
        });
        vi.mocked(listOrganizationAuditExports).mockReturnValue(orgPending as never);
        vi.mocked(listPlatformAuditExports).mockResolvedValue([
            {exportId: "platform-export"} as never,
        ]);

        const {result, rerender} = renderHook(
            ({scope}) => useAuditExports(scope),
            {initialProps: {scope: {kind: "organization" as const, organizationId: "org-1"}}}
        );

        rerender({scope: {kind: "platform"} as never});

        await waitFor(() => expect(result.current.exports).toEqual([{exportId: "platform-export"}]));

        resolveOrg([{exportId: "org-export"}]);
        await new Promise((resolve) => setTimeout(resolve, 20));

        expect(result.current.exports).toEqual([{exportId: "platform-export"}]);
    });

    it("does not reload a superseded organization scope after an export mutation completes", async () =>
    {
        let resolveRequest: () => void = () => {};
        const pendingRequest = new Promise<void>((resolve) =>
        {
            resolveRequest = resolve;
        });
        vi.mocked(listOrganizationAuditExports).mockResolvedValue([]);
        vi.mocked(listPlatformAuditExports).mockResolvedValue([{exportId: "platform-export"} as never]);
        vi.mocked(requestOrganizationAuditExport).mockReturnValue(pendingRequest as never);
        const {result, rerender} = renderHook(
            ({scope}) => useAuditExports(scope),
            {initialProps: {scope: {kind: "organization" as const, organizationId: "org-1"}}}
        );
        await waitFor(() => expect(result.current.loading).toBe(false));

        let mutationResult: boolean | undefined;
        let mutation: Promise<void>;
        act(() =>
        {
            mutation = result.current.requestExport({
                categories: ["SECURITY"],
                occurredAfter: "2024-01-01T00:00:00Z",
                occurredBefore: "2024-01-02T00:00:00Z",
                purpose: "investigation",
            }).then((value) =>
            {
                mutationResult = value;
            });
        });
        rerender({scope: {kind: "platform"} as never});
        await waitFor(() => expect(result.current.exports).toEqual([{exportId: "platform-export"}]));
        await act(async () =>
        {
            resolveRequest();
            await mutation;
        });

        expect(mutationResult).toBe(false);
        expect(listOrganizationAuditExports).toHaveBeenCalledTimes(1);
    });
});

describe("useAuditExports error normalization", () =>
{
    it("shows the backend errorMessage when loading rejects with a ResponseError-shaped body, not an Error instance", async () =>
    {
        vi.mocked(listOrganizationAuditExports).mockRejectedValue({errorMessage: "Insufficient privileges", reasonCode: "FORBIDDEN"});

        const {result} = renderHook(
            ({scope}) => useAuditExports(scope),
            {initialProps: {scope: {kind: "organization" as const, organizationId: "org-1"}}}
        );

        await waitFor(() => expect(result.current.loading).toBe(false));

        expect(result.current.error).toBe("Insufficient privileges");
    });

    it("shows the backend errorMessage when requesting an export rejects with a ResponseError-shaped body, not an Error instance", async () =>
    {
        vi.mocked(listOrganizationAuditExports).mockResolvedValue([]);
        vi.mocked(requestOrganizationAuditExport).mockRejectedValue({errorMessage: "No export-permitted engagement", reasonCode: "FORBIDDEN"});

        const {result} = renderHook(
            ({scope}) => useAuditExports(scope),
            {initialProps: {scope: {kind: "organization" as const, organizationId: "org-1"}}}
        );

        await waitFor(() => expect(result.current.loading).toBe(false));

        await act(async () =>
        {
            await result.current.requestExport({
                categories: [],
                occurredAfter: "2024-01-01T00:00:00Z",
                occurredBefore: "2024-01-02T00:00:00Z",
                purpose: "investigation",
            });
        });

        expect(result.current.submitError).toBe("No export-permitted engagement");
    });

    it("shows a normalized error when export approval fails", async () =>
    {
        vi.mocked(listOrganizationAuditExports).mockResolvedValue([]);
        vi.mocked(approveOrganizationAuditExport).mockRejectedValue({errorMessage: "Approval denied"});
        const {result} = renderHook(
            ({scope}) => useAuditExports(scope),
            {initialProps: {scope: {kind: "organization" as const, organizationId: "org-1"}}}
        );
        await waitFor(() => expect(result.current.loading).toBe(false));

        await act(async () => result.current.approveExport("export-1"));

        expect(result.current.error).toBe("Approval denied");
    });

    it("shows a normalized error when export download fails", async () =>
    {
        vi.mocked(listOrganizationAuditExports).mockResolvedValue([]);
        vi.mocked(downloadOrganizationAuditExport).mockRejectedValue({errorMessage: "Download expired"});
        const {result} = renderHook(
            ({scope}) => useAuditExports(scope),
            {initialProps: {scope: {kind: "organization" as const, organizationId: "org-1"}}}
        );
        await waitFor(() => expect(result.current.loading).toBe(false));

        await act(async () => result.current.downloadExport({exportId: "export-1"} as never));

        expect(result.current.error).toBe("Download expired");
    });
});
