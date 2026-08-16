import {beforeEach, describe, expect, it, vi} from "vitest";

const get = vi.fn();
const patch = vi.fn();
const put = vi.fn();
const post = vi.fn();
const deleteRequest = vi.fn();

vi.mock("../apiClient.ts", () => ({
    default: {
        get: (...args: unknown[]) => get(...args),
        patch: (...args: unknown[]) => patch(...args),
        put: (...args: unknown[]) => put(...args),
        post: (...args: unknown[]) => post(...args),
        delete: (...args: unknown[]) => deleteRequest(...args),
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
            tierCode: "BUSINESS",
            maxUsers: 50,
            subscriptionStatus: "ACTIVE",
            billingFrequency: "ANNUAL",
            currentPeriodStart: "2026-01-01T00:00:00Z",
            currentPeriodEnd: "2027-01-01T00:00:00Z",
            gracePeriodEnd: null,
            changeReason: "Annual renewal",
        });
        await updatePlatformOrganizationFeatureEntitlements("organization-1", {
            entitlements: [{featureCode: "WORKFLOWS", enabled: true}],
            changeReason: "Annual renewal",
        });

        expect(put).toHaveBeenNthCalledWith(
            1,
            "/platform/organizations/organization-1/subscription-policy",
            {
                tierCode: "BUSINESS",
                maxUsers: 50,
                subscriptionStatus: "ACTIVE",
                billingFrequency: "ANNUAL",
                currentPeriodStart: "2026-01-01T00:00:00Z",
                currentPeriodEnd: "2027-01-01T00:00:00Z",
                gracePeriodEnd: null,
                changeReason: "Annual renewal",
            },
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

    it("updates account status through the status subresource", async () =>
    {
        patch.mockResolvedValue({data: {}});
        const {updatePlatformOrganizationStatus} = await import("../platformOrganizationApi.ts");

        await updatePlatformOrganizationStatus("organization-1", {
            active: false,
            verificationComplete: true,
            changeReason: "Account review",
        });

        expect(patch).toHaveBeenCalledWith(
            "/platform/organizations/organization-1/status",
            {
                active: false,
                verificationComplete: true,
                changeReason: "Account review",
            },
        );
    });

    it("starts and extends a Business trial through the trial resources", async () =>
    {
        post.mockResolvedValueOnce({data: {ownerId: "organization-1"}});
        patch.mockResolvedValueOnce({data: {ownerId: "organization-1"}});
        const {
            extendPlatformOrganizationSubscriptionTrial,
            startPlatformOrganizationSubscriptionTrial,
        } = await import("../platformOrganizationApi.ts");
        const startRequest = {
            planCode: "BUSINESS" as const,
            durationDays: 30,
            seatCapacity: 5,
            reason: "Implementation pilot",
        };
        const extensionRequest = {
            currentPeriodEnd: "2026-09-30T00:00:00Z",
            reason: "Implementation extension",
        };

        await startPlatformOrganizationSubscriptionTrial("organization-1", startRequest);
        await extendPlatformOrganizationSubscriptionTrial("organization-1", extensionRequest);

        expect(post).toHaveBeenCalledWith(
            "/platform/organizations/organization-1/subscription-trials",
            startRequest,
        );
        expect(patch).toHaveBeenCalledWith(
            "/platform/organizations/organization-1/subscription-trials/current",
            extensionRequest,
        );
    });

    it("ends and converts a Business trial through dedicated transition resources", async () =>
    {
        deleteRequest.mockResolvedValueOnce({data: {ownerId: "organization-1"}});
        post.mockResolvedValueOnce({data: {ownerId: "organization-1"}});
        const {
            convertPlatformOrganizationSubscriptionTrial,
            endPlatformOrganizationSubscriptionTrial,
        } = await import("../platformOrganizationApi.ts");
        const endRequest = {reason: "Pilot completed"};
        const conversionRequest = {
            billingFrequency: "ANNUAL" as const,
            currentPeriodEnd: "2027-08-16T00:00:00Z",
            seatCapacity: 25,
            reason: "Annual agreement signed",
        };

        await endPlatformOrganizationSubscriptionTrial("organization-1", endRequest);
        await convertPlatformOrganizationSubscriptionTrial("organization-1", conversionRequest);

        expect(deleteRequest).toHaveBeenCalledWith(
            "/platform/organizations/organization-1/subscription-trials/current",
            {data: endRequest},
        );
        expect(post).toHaveBeenCalledWith(
            "/platform/organizations/organization-1/subscription-trials/current/conversions",
            conversionRequest,
        );
    });
});
