/** @vitest-environment jsdom */
import {renderHook} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    Capability,
    CurrentSessionDto,
    EffectiveSubscriptionDto,
    PlanCode,
    PlanFeature,
    SubscriptionEnforcementMode,
    SubscriptionOwnerType,
    SubscriptionStatus,
} from "../models/models.tsx";
import {useSettingsPlanAvailability} from "./useSettingsPlanAvailability.ts";
import {tabIds} from "./settingsTabs.ts";

const authMock = vi.hoisted(() => ({
    currentSession: null as CurrentSessionDto | null,
    appUserPersonOrganization: null as {isActive?: boolean} | null,
    capabilities: [] as Capability[],
}));

const subscriptionMock = vi.hoisted(() => ({
    current: null as EffectiveSubscriptionDto | null,
}));

vi.mock("../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        currentSession: authMock.currentSession,
        appUserPersonOrganization: authMock.appUserPersonOrganization,
        hasCapability: (capability: Capability) => authMock.capabilities.includes(capability),
    }),
}));

vi.mock("../../hooks/subscription/useCurrentSubscription.ts", () => ({
    useCurrentSubscription: () => subscriptionMock.current,
}));

const subscription = (
    planCode: PlanCode,
    ownerType: SubscriptionOwnerType,
    features: PlanFeature[],
): EffectiveSubscriptionDto => ({
    planCode,
    ownerType,
    ownerId: "owner-1",
    status: SubscriptionStatus.ACTIVE,
    features,
    limits: {seatsArePurchased: ownerType === SubscriptionOwnerType.ORGANIZATION},
    usage: {},
    allowsMutations: true,
    enforcementMode: SubscriptionEnforcementMode.ENFORCE,
});

describe("useSettingsPlanAvailability", () =>
{
    beforeEach(() =>
    {
        authMock.currentSession = null;
        authMock.appUserPersonOrganization = null;
        authMock.capabilities = [];
        subscriptionMock.current = null;
    });

    it("keeps Free settings limited to account basics and billing", () =>
    {
        subscriptionMock.current = subscription(
            PlanCode.FREE,
            SubscriptionOwnerType.USER,
            [PlanFeature.EXCHANGE_CREATE],
        );

        const {result} = renderHook(() => useSettingsPlanAvailability());

        expect(result.current.visibleTabs.has(tabIds.profile)).toBe(true);
        expect(result.current.visibleTabs.has(tabIds.organizationBilling)).toBe(true);
        expect(result.current.visibleTabs.has(tabIds.documents)).toBe(false);
        expect(result.current.visibleTabs.has(tabIds.blueprints)).toBe(false);
        expect(result.current.visibleTabs.has(tabIds.fields)).toBe(false);
        expect(result.current.visibleTabs.has(tabIds.workflows)).toBe(false);
    });

    it("shows Personal document and blueprint settings but withholds Business org surfaces", () =>
    {
        subscriptionMock.current = subscription(
            PlanCode.PERSONAL,
            SubscriptionOwnerType.USER,
            [
                PlanFeature.EXCHANGE_CREATE,
                PlanFeature.DOCUMENT_LIBRARY_USE,
                PlanFeature.BLUEPRINT_USE,
                PlanFeature.BLUEPRINT_MANAGE,
                PlanFeature.MULTIPLE_PARTICIPANTS,
                PlanFeature.ADVANCED_ACCESS_CONTROLS,
                PlanFeature.INFORMATION_REQUESTS,
            ],
        );

        const {result} = renderHook(() => useSettingsPlanAvailability());

        expect(result.current.visibleTabs.has(tabIds.documents)).toBe(true);
        expect(result.current.visibleTabs.has(tabIds.blueprints)).toBe(true);
        expect(result.current.visibleTabs.has(tabIds.informationRequestTemplates)).toBe(true);
        expect(result.current.visibleTabs.has(tabIds.fields)).toBe(false);
        expect(result.current.visibleTabs.has(tabIds.workflows)).toBe(false);
    });

    it("shows Business administration only to organization users with the matching capability", () =>
    {
        subscriptionMock.current = subscription(
            PlanCode.BUSINESS,
            SubscriptionOwnerType.ORGANIZATION,
            [
                PlanFeature.EXCHANGE_CREATE,
                PlanFeature.DOCUMENT_LIBRARY_USE,
                PlanFeature.BLUEPRINT_USE,
                PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
                PlanFeature.WORKFLOW_AUTOMATION,
                PlanFeature.ORGANIZATION_ADMINISTRATION,
                PlanFeature.AUDIT_GOVERNANCE,
            ],
        );
        authMock.currentSession = {
            activeOrganizationId: "org-1",
            availableOrganizations: [],
            capabilities: [Capability.ORG_POLICY_MANAGE, Capability.ORG_BILLING_MANAGE],
            effectiveSubscription: subscriptionMock.current,
        } as CurrentSessionDto;
        authMock.appUserPersonOrganization = {isActive: true};
        authMock.capabilities = [Capability.ORG_POLICY_MANAGE, Capability.ORG_BILLING_MANAGE];

        const {result, rerender} = renderHook(() => useSettingsPlanAvailability());

        expect(result.current.visibleTabs.has(tabIds.organizationBilling)).toBe(true);
        expect(result.current.visibleTabs.has(tabIds.organization)).toBe(true);
        expect(result.current.visibleTabs.has(tabIds.fields)).toBe(true);
        expect(result.current.visibleTabs.has(tabIds.workflows)).toBe(true);

        authMock.capabilities = [];
        rerender();

        expect(result.current.visibleTabs.has(tabIds.organizationBilling)).toBe(false);
        expect(result.current.visibleTabs.has(tabIds.organization)).toBe(false);
        expect(result.current.visibleTabs.has(tabIds.fields)).toBe(false);
    });

    it("shows Information Request Template settings only when the feature is discoverable", () =>
    {
        subscriptionMock.current = subscription(
            PlanCode.BUSINESS,
            SubscriptionOwnerType.ORGANIZATION,
            [
                PlanFeature.EXCHANGE_CREATE,
                PlanFeature.INFORMATION_REQUESTS,
            ],
        );
        authMock.currentSession = {
            activeOrganizationId: "org-1",
            availableOrganizations: [],
            capabilities: [Capability.ORG_POLICY_MANAGE],
            effectiveSubscription: subscriptionMock.current,
        } as CurrentSessionDto;
        authMock.appUserPersonOrganization = {isActive: true};
        authMock.capabilities = [Capability.ORG_POLICY_MANAGE];

        const {result, rerender} = renderHook(() => useSettingsPlanAvailability());

        expect(result.current.visibleTabs.has("InformationRequestTemplatesTab")).toBe(true);

        authMock.capabilities = [];
        rerender();

        expect(result.current.visibleTabs.has("InformationRequestTemplatesTab")).toBe(true);

        subscriptionMock.current = subscription(
            PlanCode.BUSINESS,
            SubscriptionOwnerType.ORGANIZATION,
            [PlanFeature.EXCHANGE_CREATE],
        );
        rerender();

        expect(result.current.visibleTabs.has("InformationRequestTemplatesTab")).toBe(false);
    });
});
