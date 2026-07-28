import {beforeEach, describe, expect, it, vi} from "vitest";

const get = vi.fn();
const put = vi.fn();

vi.mock("../apiClient.ts", () => ({
    default: {
        get: (...args: unknown[]) => get(...args),
        put: (...args: unknown[]) => put(...args),
    },
}));

describe("platformOrganizationApi", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("lists organizations through the restricted platform resource", async () =>
    {
        get.mockResolvedValueOnce({data: {total: 0, limit: 20, offset: 0, items: []}});
        const {fetchPlatformOrganizations} = await import("../platformOrganizationApi.ts");
        const query = {
            query: "Acme",
            status: "ACTIVE" as const,
            tierCode: "PRO",
            sort: "name" as const,
            direction: "asc" as const,
            limit: 20,
            offset: 0,
        };

        await fetchPlatformOrganizations(query);

        expect(get).toHaveBeenCalledWith("/platform/organizations", {params: query});
    });

    it("updates account policy and entitlements only through platform subresources", async () =>
    {
        put.mockResolvedValue({data: {}});
        const {
            updatePlatformOrganizationFeatureEntitlements,
            updatePlatformOrganizationSubscriptionPolicy,
        } = await import("../platformOrganizationApi.ts");

        await updatePlatformOrganizationSubscriptionPolicy("organization-1", {
            tierCode: "PRO",
            maxUsers: 50,
            changeReason: "Annual renewal",
        });
        await updatePlatformOrganizationFeatureEntitlements("organization-1", {
            entitlements: [{featureCode: "WORKFLOWS", enabled: true}],
            changeReason: "Annual renewal",
        });

        expect(put).toHaveBeenNthCalledWith(
            1,
            "/platform/organizations/organization-1/subscription-policy",
            {tierCode: "PRO", maxUsers: 50, changeReason: "Annual renewal"},
        );
        expect(put).toHaveBeenNthCalledWith(
            2,
            "/platform/organizations/organization-1/feature-entitlements",
            {
                entitlements: [{featureCode: "WORKFLOWS", enabled: true}],
                changeReason: "Annual renewal",
            },
        );
    });
});
