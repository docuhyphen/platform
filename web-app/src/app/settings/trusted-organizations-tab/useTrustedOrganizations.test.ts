// @vitest-environment jsdom

import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {OrganizationTrustRelationship} from "../../../services/organizationTrust.ts";
import {useTrustedOrganizations} from "./useTrustedOrganizations.ts";

const state = vi.hoisted(() => ({
    activeOrganizationId: "organization-a" as string | null,
    notifications: [] as {data?: {source?: string}}[],
}));

const mocks = vi.hoisted(() => ({
    fetchRelationships: vi.fn(),
    fetchPolicies: vi.fn(),
    resume: vi.fn(),
    terminate: vi.fn(),
}));

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        currentSession: {activeOrganizationId: state.activeOrganizationId},
        hasCapability: () => true,
    }),
}));

vi.mock("../../../context/NotificationContext.tsx", () => ({
    useNotifications: () => ({notifications: state.notifications}),
}));

vi.mock("../../../services/organizationTrust.ts", () => ({
    fetchTrustRelationships: (...args: unknown[]) => mocks.fetchRelationships(...args),
    fetchTrustPolicies: (...args: unknown[]) => mocks.fetchPolicies(...args),
    resumeOrganizationTrust: (...args: unknown[]) => mocks.resume(...args),
    terminateOrganizationTrust: (...args: unknown[]) => mocks.terminate(...args),
    decideOrganizationTrust: vi.fn(),
    requestOrganizationTrust: vi.fn(),
    suspendOrganizationTrust: vi.fn(),
    updateOrganizationTrustPolicy: vi.fn(),
    withdrawOrganizationTrust: vi.fn(),
}));

const relationship = (id: string, overrides: Partial<OrganizationTrustRelationship> = {}): OrganizationTrustRelationship => ({
    id,
    currentOrganizationId: "organization-a",
    partnerOrganizationId: "organization-partner",
    partnerOrganizationName: "Partner Organization",
    partnerOrganizationActive: true,
    partnerOrganizationVerified: true,
    requestedByCurrentOrganization: true,
    status: "ACTIVE",
    requestedAt: 1,
    requestExpiresAt: 2,
    version: 1,
    effectivelySuspended: false,
    suspendedByCurrentOrganization: false,
    suspendedByPartner: false,
    ...overrides,
});

const deferred = <T>() =>
{
    let resolvePromise: (value: T) => void = () => undefined;
    const promise = new Promise<T>((resolve) =>
    {
        resolvePromise = resolve;
    });
    return {promise, resolve: resolvePromise};
};

describe("useTrustedOrganizations", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        state.activeOrganizationId = "organization-a";
        state.notifications = [];
        mocks.fetchRelationships.mockResolvedValue([]);
        mocks.fetchPolicies.mockResolvedValue({relationshipId: "relationship-a", policies: []});
    });

    it("ignores a relationship response that started under a previous active organization", async () =>
    {
        const organizationA = deferred<OrganizationTrustRelationship[]>();
        mocks.fetchRelationships
            .mockReturnValueOnce(organizationA.promise)
            .mockResolvedValueOnce([relationship("relationship-b")]);

        const {result, rerender} = renderHook(() => useTrustedOrganizations());

        state.activeOrganizationId = "organization-b";
        rerender();
        await waitFor(() => expect(result.current.relationships.map(item => item.id)).toEqual(["relationship-b"]));

        await act(async () => organizationA.resolve([relationship("relationship-a")]));

        expect(result.current.relationships.map(item => item.id)).toEqual(["relationship-b"]);
    });

    it("ignores a stale policy response after the selected relationship changes", async () =>
    {
        mocks.fetchRelationships.mockResolvedValue([relationship("relationship-1"), relationship("relationship-2")]);
        const firstPolicies = deferred<{relationshipId: string; policies: []}>();
        const secondPolicies = deferred<{relationshipId: string; policies: []}>();
        mocks.fetchPolicies.mockReturnValueOnce(firstPolicies.promise).mockReturnValueOnce(secondPolicies.promise);

        const {result} = renderHook(() => useTrustedOrganizations());
        await waitFor(() => expect(result.current.relationships).toHaveLength(2));

        act(() => result.current.setSelectedId("relationship-2"));

        await act(async () => secondPolicies.resolve({relationshipId: "relationship-2", policies: []}));
        await act(async () => firstPolicies.resolve({relationshipId: "relationship-1", policies: []}));

        await waitFor(() => expect(result.current.policies?.relationshipId).toBe("relationship-2"));
    });

    it("refreshes relationships when a trusted-organization notification arrives", async () =>
    {
        mocks.fetchRelationships.mockResolvedValue([relationship("relationship-1")]);
        const {result, rerender} = renderHook(() => useTrustedOrganizations());
        await waitFor(() => expect(result.current.relationships).toHaveLength(1));
        expect(mocks.fetchRelationships).toHaveBeenCalledTimes(1);

        state.notifications = [{data: {source: "organization-trust"}}];
        rerender();

        await waitFor(() => expect(mocks.fetchRelationships).toHaveBeenCalledTimes(2));
    });

    it("fails a resume without mutation when no suspension id is present", async () =>
    {
        mocks.fetchRelationships.mockResolvedValue([relationship("relationship-1")]);
        const {result} = renderHook(() => useTrustedOrganizations());
        await waitFor(() => expect(result.current.relationships).toHaveLength(1));

        await act(async () =>
        {
            await expect(result.current.act("RESUME", relationship("relationship-1"))).rejects.toThrow();
        });

        expect(mocks.resume).not.toHaveBeenCalled();
        expect(mocks.terminate).not.toHaveBeenCalled();
        expect(result.current.error).toBeTruthy();
    });
});
