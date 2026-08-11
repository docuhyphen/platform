/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {PlatformUserSubscriptionPolicy} from "../../../services/types/platformUserSubscriptions.ts";
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
        const onSave = vi.fn().mockResolvedValue(undefined);

        render(
            <UserSubscriptionEditorDialog
                user={user}
                onDismiss={vi.fn()}
                onSave={onSave}
            />,
        );

        fireEvent.click(document.querySelector("#platform-user-subscription-editor-save") as HTMLElement);

        expect(await screen.findByText("Change reason is required.")).toBeTruthy();
        expect(onSave).not.toHaveBeenCalled();
        expect(authMock.refreshCurrentSession).not.toHaveBeenCalled();
    });

    it("enforces lifecycle-specific date requirements before the admin save reaches the API", async () =>
    {
        const onSave = vi.fn().mockResolvedValue(undefined);

        render(
            <UserSubscriptionEditorDialog
                user={user}
                onDismiss={vi.fn()}
                onSave={onSave}
            />,
        );

        changeField("#platform-user-subscription-status-select", "PAST_DUE");
        changeField("#platform-user-subscription-reason-input", "Payment failed during renewal");
        fireEvent.click(document.querySelector("#platform-user-subscription-editor-save") as HTMLElement);

        expect(await screen.findByText("Past-due subscriptions require a grace period end.")).toBeTruthy();
        expect(onSave).not.toHaveBeenCalled();
    });

    it("saves valid changes, refreshes the active session, and closes the dialog", async () =>
    {
        const onDismiss = vi.fn();
        const onSave = vi.fn().mockResolvedValue(undefined);

        render(
            <UserSubscriptionEditorDialog
                user={user}
                onDismiss={onDismiss}
                onSave={onSave}
            />,
        );

        changeField("#platform-user-subscription-plan-select", "PERSONAL");
        changeField("#platform-user-subscription-frequency-select", "MONTHLY");
        changeField("#platform-user-subscription-period-start-input", "2026-08-11T00:00");
        changeField("#platform-user-subscription-period-end-input", "2026-09-11T00:00");
        changeField("#platform-user-subscription-reason-input", "Customer upgraded after verification");
        fireEvent.click(document.querySelector("#platform-user-subscription-editor-save") as HTMLElement);

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
        expect(authMock.refreshCurrentSession).toHaveBeenCalledTimes(1);
        expect(onDismiss).toHaveBeenCalledTimes(1);
    });
});
