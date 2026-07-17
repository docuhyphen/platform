import {beforeEach, describe, expect, it, vi} from "vitest";

const get = vi.fn();
const post = vi.fn();
const patch = vi.fn();
const remove = vi.fn();

vi.mock("../apiClient.ts", () => ({
    default: {
        get: (...args: unknown[]) => get(...args),
        post: (...args: unknown[]) => post(...args),
        patch: (...args: unknown[]) => patch(...args),
        delete: (...args: unknown[]) => remove(...args),
    },
}));

describe("organizationTrust service", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("searches through a JSON body and never puts the query in the URL", async () =>
    {
        post.mockResolvedValueOnce({data: []});
        const {searchOrganizationsForTrust} = await import("../organizationTrust.ts");

        await searchOrganizationsForTrust("Acme 42");

        expect(post).toHaveBeenCalledWith("/organization-directory-searches", {query: "Acme 42"});
    });

    it("sends optimistic versions and policy revisions on mutations", async () =>
    {
        post.mockResolvedValueOnce({data: {}});
        patch.mockResolvedValueOnce({data: {policies: []}});
        const {decideOrganizationTrust, updateOrganizationTrustPolicy} = await import("../organizationTrust.ts");
        const relationship = {
            id: "relationship-1",
            currentOrganizationId: "organization-1",
            partnerOrganizationId: "organization-2",
            partnerOrganizationName: "Partner",
            partnerOrganizationActive: true,
            partnerOrganizationVerified: true,
            requestedByCurrentOrganization: false,
            status: "PENDING" as const,
            requestedAt: 1,
            requestExpiresAt: 2,
            version: 7,
            effectivelySuspended: false,
            suspendedByCurrentOrganization: false,
            suspendedByPartner: false,
        };

        await decideOrganizationTrust(relationship, "ACCEPT");
        await updateOrganizationTrustPolicy("relationship-1", "policy-1", {
            expectedRevision: 4,
            allowExchangesToPartner: true,
            allowExchangesFromPartner: true,
            allowPartnerMemberResolution: false,
            allowPartnerGroupDiscovery: false,
            shareMemberDisplayName: false,
        });

        expect(post).toHaveBeenCalledWith(
            "/organization-trust-relationships/relationship-1/decisions",
            {decision: "ACCEPT", reason: undefined, expectedVersion: 7},
        );
        expect(patch).toHaveBeenCalledWith(
            "/organization-trust-relationships/relationship-1/policies/policy-1",
            expect.objectContaining({expectedRevision: 4}),
        );
    });

    it("loads member-free published groups from the trusted organization resource", async () =>
    {
        get.mockResolvedValueOnce({data: []});
        const {fetchPublishedExchangeGroups} = await import("../organizationTrust.ts");

        await fetchPublishedExchangeGroups("organization-2");

        expect(get).toHaveBeenCalledWith("/organizations/organization-2/published-exchange-groups");
    });

    it("resolves an exact email through a JSON body without placing it in the URL", async () =>
    {
        post.mockResolvedValueOnce({data: {id: "resolution-1"}});
        const {resolveExternalIdentity} = await import("../organizationTrust.ts");

        await resolveExternalIdentity("organization-2", "Member@Example.test");

        expect(post).toHaveBeenCalledWith(
            "/organizations/organization-2/external-identity-resolutions",
            {email: "Member@Example.test"},
        );
    });
});
