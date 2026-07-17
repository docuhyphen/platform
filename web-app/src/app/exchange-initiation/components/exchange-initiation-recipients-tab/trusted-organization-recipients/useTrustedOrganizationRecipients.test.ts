// @vitest-environment jsdom

import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {useTrustedOrganizationRecipients} from "./useTrustedOrganizationRecipients.ts";

const mocks = vi.hoisted(() => ({
    fetchRelationships: vi.fn(),
    fetchGroups: vi.fn(),
    resolveIdentity: vi.fn(),
}));

vi.mock("../../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        currentSession: {activeOrganizationId: "caller-organization"},
        hasCapability: () => true,
    }),
}));

vi.mock("../../../../../services/organizationTrust.ts", () => ({
    fetchTrustRelationships: (...args: unknown[]) => mocks.fetchRelationships(...args),
    fetchPublishedExchangeGroups: (...args: unknown[]) => mocks.fetchGroups(...args),
    resolveExternalIdentity: (...args: unknown[]) => mocks.resolveIdentity(...args),
}));

const relationship = {
    id: "relationship-1",
    currentOrganizationId: "caller-organization",
    partnerOrganizationId: "target-organization",
    partnerOrganizationName: "Target Organization",
    partnerOrganizationActive: true,
    partnerOrganizationVerified: true,
    requestedByCurrentOrganization: true,
    status: "ACTIVE" as const,
    requestedAt: 1,
    requestExpiresAt: 2,
    version: 1,
    effectivelySuspended: false,
    suspendedByCurrentOrganization: false,
    suspendedByPartner: false,
};

const deferred = <T>() =>
{
    let resolvePromise: (value: T) => void = () => undefined;
    const promise = new Promise<T>((resolve) =>
    {
        resolvePromise = resolve;
    });
    return {promise, resolve: resolvePromise};
};

describe("useTrustedOrganizationRecipients", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        mocks.fetchRelationships.mockResolvedValue([relationship]);
        mocks.fetchGroups.mockResolvedValue([]);
    });

    it("ignores stale resolution responses and clears verification when the email changes", async () =>
    {
        const first = deferred<{
            id: string;
            organizationId: string;
            organizationName: string;
            email: string;
            verifiedAt: number;
            expiresAt: number;
        }>();
        const second = deferred<{
            id: string;
            organizationId: string;
            organizationName: string;
            email: string;
            verifiedAt: number;
            expiresAt: number;
        }>();
        mocks.resolveIdentity.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
        const props = {
            setRecipientOrg: vi.fn(),
            setRecipientOrgUser: vi.fn(),
            setRecipientOrgGroup: vi.fn(),
            setRecipientResolution: vi.fn(),
        };
        const {result} = renderHook(() => useTrustedOrganizationRecipients(props));
        await waitFor(() => expect(result.current.relationships).toHaveLength(1));

        act(() => result.current.selectRelationship("relationship-1"));
        act(() => result.current.changeEmail("first@example.test"));
        act(() => result.current.resolvePerson());
        act(() => result.current.changeEmail("second@example.test"));
        act(() => result.current.resolvePerson());
        await act(async () => second.resolve({
            id: "resolution-2",
            organizationId: "target-organization",
            organizationName: "Target Organization",
            email: "second@example.test",
            verifiedAt: Date.now(),
            expiresAt: Date.now() + 60_000,
        }));
        await act(async () => first.resolve({
            id: "resolution-1",
            organizationId: "target-organization",
            organizationName: "Target Organization",
            email: "first@example.test",
            verifiedAt: Date.now(),
            expiresAt: Date.now() + 60_000,
        }));

        await waitFor(() => expect(result.current.resolution?.id).toBe("resolution-2"));
        act(() => result.current.changeEmail("third@example.test"));
        expect(result.current.resolution).toBeUndefined();
    });
});
