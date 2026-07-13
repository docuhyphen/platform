/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, cleanup, waitFor} from "@testing-library/react";
import AuditIntegritySection from "../audit-integrity-section/AuditIntegritySection.tsx";
import {getOrganizationAuditIntegrity, getPlatformAuditIntegrity} from "../../../services/auditService.ts";

vi.mock("../../../services/auditService.ts", () => ({
    getOrganizationAuditIntegrity: vi.fn(),
    getPlatformAuditIntegrity: vi.fn(),
}));

afterEach(() =>
{
    cleanup();
    vi.mocked(getOrganizationAuditIntegrity).mockReset();
    vi.mocked(getPlatformAuditIntegrity).mockReset();
});

describe("AuditIntegritySection stale scope-switch protection", () =>
{
    it("does not let a slow organization-scope response overwrite a faster platform-scope response after a scope switch", async () =>
    {
        let resolveOrg: (value: unknown) => void = () => {};
        const orgPending = new Promise((resolve) =>
        {
            resolveOrg = resolve;
        });
        vi.mocked(getOrganizationAuditIntegrity).mockReturnValue(orgPending as never);
        vi.mocked(getPlatformAuditIntegrity).mockResolvedValue({
            organizationId: null,
            platformOnly: true,
            allValid: true,
            streams: [{streamId: "platform-stream", chainValid: true, chainNote: "ok", segmentsValid: 1, segmentsChecked: 1, segmentFailureNotes: []}],
        });

        const {rerender} = render(<AuditIntegritySection scope={{kind: "organization", organizationId: "org-1"}}/>);

        rerender(<AuditIntegritySection scope={{kind: "platform"}}/>);

        await waitFor(() => expect(document.getElementById("audit-integrity-stream-platform-stream")).toBeTruthy());

        resolveOrg({
            organizationId: "org-1",
            platformOnly: false,
            allValid: false,
            streams: [{streamId: "org-stream", chainValid: false, chainNote: "broken", segmentsValid: 0, segmentsChecked: 1, segmentFailureNotes: ["bad"]}],
        });

        // Give the stale org response's setState every chance to land before asserting it didn't.
        await new Promise((resolve) => setTimeout(resolve, 20));

        expect(document.getElementById("audit-integrity-stream-platform-stream")).toBeTruthy();
        expect(document.getElementById("audit-integrity-stream-org-stream")).toBeFalsy();
    });
});

describe("AuditIntegritySection error normalization", () =>
{
    it("shows the backend errorMessage when the request rejects with a ResponseError-shaped body, not an Error instance", async () =>
    {
        vi.mocked(getOrganizationAuditIntegrity).mockRejectedValue({errorMessage: "Insufficient privileges", reasonCode: "FORBIDDEN"});

        render(<AuditIntegritySection scope={{kind: "organization", organizationId: "org-1"}}/>);

        await waitFor(() => expect(document.getElementById("audit-integrity-error")).toBeTruthy());

        expect(document.getElementById("audit-integrity-error")?.textContent).toBe("Insufficient privileges");
    });
});
