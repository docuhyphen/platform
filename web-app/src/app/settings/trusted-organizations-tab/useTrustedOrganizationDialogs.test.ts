// @vitest-environment jsdom

import {act, renderHook} from "@testing-library/react";
import {describe, expect, it, vi} from "vitest";
import {OrganizationTrustRelationship} from "../../../services/organizationTrust.ts";
import {useTrustedOrganizationDialogs} from "./useTrustedOrganizationDialogs.ts";
import {useTrustedOrganizations} from "./useTrustedOrganizations.ts";

const relationship = (version: number): OrganizationTrustRelationship => ({
    id: "relationship-1",
    currentOrganizationId: "organization-a",
    partnerOrganizationId: "organization-b",
    partnerOrganizationName: "Partner Organization",
    partnerOrganizationActive: true,
    partnerOrganizationVerified: true,
    requestedByCurrentOrganization: true,
    status: "ACTIVE",
    suspendedByCurrentOrganization: false,
    suspendedByPartnerOrganization: false,
    version,
});

const trustState = (
    current: OrganizationTrustRelationship,
    actOnRelationship: ReturnType<typeof vi.fn>,
    setError: ReturnType<typeof vi.fn>,
) => ({
    relationships: [current],
    act: actOnRelationship,
    setError,
    requestTrust: vi.fn(),
}) as unknown as ReturnType<typeof useTrustedOrganizations>;

describe("useTrustedOrganizationDialogs", () =>
{
    it("confirms the exact relationship version captured when the action opened", async () =>
    {
        const actOnRelationship = vi.fn().mockResolvedValue(undefined);
        const state = trustState(relationship(3), actOnRelationship, vi.fn());
        const {result} = renderHook(() => useTrustedOrganizationDialogs("organization-a", state));

        act(() => result.current.openAction("SUSPEND", relationship(3)));
        await act(() => result.current.confirmAction("review"));

        expect(actOnRelationship).toHaveBeenCalledWith("SUSPEND", expect.objectContaining({version: 3}), "review");
        expect(result.current.pending).toBeNull();
    });

    it("rejects a stale confirmation without redirecting it to the refreshed relationship", async () =>
    {
        const actOnRelationship = vi.fn();
        const setError = vi.fn();
        const state = trustState(relationship(4), actOnRelationship, setError);
        const {result} = renderHook(() => useTrustedOrganizationDialogs("organization-a", state));

        act(() => result.current.openAction("TERMINATE", relationship(3)));
        await act(() => result.current.confirmAction());

        expect(actOnRelationship).not.toHaveBeenCalled();
        expect(setError).toHaveBeenCalledWith(expect.stringContaining("changed before the action was confirmed"));
        expect(result.current.pending).toBeNull();
    });
});
