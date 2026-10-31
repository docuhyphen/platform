/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, Mock, vi} from "vitest";
import {PlatformFeatureEntitlement} from "../../../services/types/platformFeatureEntitlements.ts";
import {
    PlatformUserSubscriptionPolicy,
    PlatformUserSubscriptionPolicyRequest,
} from "../../../services/types/platformUserSubscriptions.ts";
import {PlanFeature} from "../../models/models.tsx";
import UserSubscriptionEditorDialog from "./UserSubscriptionEditorDialog.tsx";

class TestResizeObserver
{
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

globalThis.ResizeObserver = TestResizeObserver;

const authMock = vi.hoisted(() => ({
    refreshCurrentSession: vi.fn(),
}));

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        refreshCurrentSession: authMock.refreshCurrentSession,
    }),
}));

const user: PlatformUserSubscriptionPolicy = {
    appUserId: "user-1",
    email: "tier-user@example.com",
    displayName: "Tier User",
    planCode: "FREE",
    subscriptionStatus: "ACTIVE",
    billingFrequency: null,
    currentPeriodStart: null,
    currentPeriodEnd: null,
    gracePeriodEnd: null,
    changeReason: null,
    createdDate: "2026-08-11T00:00:00Z",
    updatedDate: "2026-08-11T00:00:00Z",
};

const changeField = (selector: string, value: string) =>
{
    fireEvent.change(document.querySelector(selector) as HTMLInputElement, {target: {value}});
};

const save = () => fireEvent.click(document.querySelector("#platform-user-subscription-editor-save") as HTMLElement);

type SaveSubscription = (user: PlatformUserSubscriptionPolicy, request: PlatformUserSubscriptionPolicyRequest) => Promise<void>;
type LoadOverrides = (user: PlatformUserSubscriptionPolicy) => Promise<PlatformFeatureEntitlement[]>;
type SaveOverrides = (user: PlatformUserSubscriptionPolicy, overrides: PlatformFeatureEntitlement[], changeReason: string) => Promise<void>;

const loadingOverrides = (overrides: PlatformFeatureEntitlement[]): Mock<LoadOverrides> =>
    vi.fn<LoadOverrides>().mockResolvedValue(overrides);

const renderDialog = ({
                          onDismiss = vi.fn<() => void>(),
                          onSave = vi.fn<SaveSubscription>().mockResolvedValue(undefined),
                          onLoadFeatureOverrides = loadingOverrides([]),
                          onSaveFeatureOverrides = vi.fn<SaveOverrides>().mockResolvedValue(undefined),
                      }: {
    onDismiss?: Mock<() => void>;
    onSave?: Mock<SaveSubscription>;
    onLoadFeatureOverrides?: Mock<LoadOverrides>;
    onSaveFeatureOverrides?: Mock<SaveOverrides>;
} = {}) => {
    render(
        <UserSubscriptionEditorDialog
            user={user}
            onDismiss={onDismiss}
            onSave={onSave}
            onLoadFeatureOverrides={onLoadFeatureOverrides}
            onSaveFeatureOverrides={onSaveFeatureOverrides}
        />,
    );
    return {onDismiss, onSave, onLoadFeatureOverrides, onSaveFeatureOverrides};
};

describe("UserSubscriptionEditorDialog", () =>
{
    beforeEach(() =>
    {
        authMock.refreshCurrentSession.mockResolvedValue(null);
    });

    afterEach(() =>
    {
        cleanup();
        vi.clearAllMocks();
    });

    it("requires an audited change reason before saving a subscription update", async () =>
    {
        const {onSave} = renderDialog();

        save();

        expect(await screen.findByText("Change reason is required.")).toBeTruthy();
        expect(onSave).not.toHaveBeenCalled();
        expect(authMock.refreshCurrentSession).not.toHaveBeenCalled();
    });

    it("enforces lifecycle-specific date requirements before the admin save reaches the API", async () =>
    {
        const {onSave} = renderDialog();

        changeField("#platform-user-subscription-status-select", "PAST_DUE");
        changeField("#platform-user-subscription-reason-input", "Payment failed during renewal");
        save();

        expect(await screen.findByText("Past-due subscriptions require a grace period end.")).toBeTruthy();
        expect(onSave).not.toHaveBeenCalled();
    });

    it("saves valid changes, refreshes the active session, and closes the dialog", async () =>
    {
        const {onSave, onDismiss, onSaveFeatureOverrides} = renderDialog();
        await screen.findByRole("combobox", {name: "Features"});

        changeField("#platform-user-subscription-plan-select", "PERSONAL");
        changeField("#platform-user-subscription-frequency-select", "MONTHLY");
        changeField("#platform-user-subscription-period-start-input", "2026-08-11T00:00");
        changeField("#platform-user-subscription-period-end-input", "2026-09-11T00:00");
        changeField("#platform-user-subscription-reason-input", "Customer upgraded after verification");
        save();

        await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
        expect(onSave).toHaveBeenCalledWith(user, {
            planCode: "PERSONAL",
            subscriptionStatus: "ACTIVE",
            billingFrequency: "MONTHLY",
            currentPeriodStart: new Date("2026-08-11T00:00").toISOString(),
            currentPeriodEnd: new Date("2026-09-11T00:00").toISOString(),
            gracePeriodEnd: null,
            changeReason: "Customer upgraded after verification",
        });
        await waitFor(() => expect(onDismiss).toHaveBeenCalledTimes(1));
        expect(authMock.refreshCurrentSession).toHaveBeenCalledTimes(1);
        expect(onSaveFeatureOverrides).not.toHaveBeenCalled();
    });

    it("shows the account's feature overrides and saves a changed set with the audited reason", async () => {
        const {onSaveFeatureOverrides, onLoadFeatureOverrides, onDismiss} = renderDialog({
            onLoadFeatureOverrides: loadingOverrides([{featureCode: PlanFeature.INFORMATION_REQUESTS, enabled: true}]),
        });

        const enabled = await screen.findByRole("switch", {name: "Information requests enabled"});
        expect(onLoadFeatureOverrides).toHaveBeenCalledWith(user);
        fireEvent.click(enabled);
        fireEvent.click(screen.getByRole("combobox", {name: "Features"}));
        fireEvent.click(document.getElementById("platform-user-feature-option-workflow_automation") as HTMLElement);
        changeField("#platform-user-subscription-reason-input", "Granted for a pilot");
        save();

        await waitFor(() => expect(onSaveFeatureOverrides).toHaveBeenCalledWith(user, [
            {featureCode: PlanFeature.INFORMATION_REQUESTS, enabled: false},
            {featureCode: PlanFeature.WORKFLOW_AUTOMATION, enabled: true},
        ], "Granted for a pilot"));
        await waitFor(() => expect(onDismiss).toHaveBeenCalledTimes(1));
    });

    it("removes an override so the plan decides the feature again", async () => {
        const {onSaveFeatureOverrides} = renderDialog({
            onLoadFeatureOverrides: loadingOverrides([{featureCode: PlanFeature.INFORMATION_REQUESTS, enabled: false}]),
        });

        fireEvent.click(await screen.findByRole("button", {name: "Remove the Information requests override"}));
        changeField("#platform-user-subscription-reason-input", "Back to the plan");
        save();

        await waitFor(() => expect(onSaveFeatureOverrides).toHaveBeenCalledWith(user, [], "Back to the plan"));
    });

    it("never replaces overrides it could not read", async () => {
        const {onSave, onSaveFeatureOverrides} = renderDialog({
            onLoadFeatureOverrides: vi.fn<LoadOverrides>().mockRejectedValue({errorMessage: "Forbidden"}),
        });

        expect(await screen.findByText("The account's feature overrides could not be loaded, so they are left as they are.")).toBeTruthy();
        expect(screen.queryByRole("combobox", {name: "Features"})).toBeNull();
        changeField("#platform-user-subscription-reason-input", "Support correction");
        save();

        await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
        expect(onSaveFeatureOverrides).not.toHaveBeenCalled();
    });
});
