/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import SubscriptionTrialDialog from "./SubscriptionTrialDialog.tsx";

class TestResizeObserver
{
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

globalThis.ResizeObserver = TestResizeObserver;

const authMock = vi.hoisted(() => ({refreshCurrentSession: vi.fn()}));

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({refreshCurrentSession: authMock.refreshCurrentSession}),
}));

const changeField = (selector: string, value: string) =>
    fireEvent.change(document.querySelector(selector) as HTMLInputElement, {target: {value}});

describe("SubscriptionTrialDialog", () =>
{
    beforeEach(() => authMock.refreshCurrentSession.mockResolvedValue(null));

    afterEach(() =>
    {
        cleanup();
        vi.clearAllMocks();
    });

    it("starts an organization trial with duration, seats, and an audited reason", async () =>
    {
        const onStart = vi.fn().mockResolvedValue(undefined);
        const onSaved = vi.fn();
        const onDismiss = vi.fn();
        render(
            <SubscriptionTrialDialog
                open
                ownerName={"Acme"}
                ownerKind={"organization"}
                isExtension={false}
                currentPeriodEnd={null}
                onDismiss={onDismiss}
                onSaved={onSaved}
                onStart={onStart}
                onExtend={vi.fn()}/>,
        );

        changeField("#platform-organization-subscription-trial-duration-input", "45");
        changeField("#platform-organization-subscription-trial-seats-input", "8");
        changeField("#platform-organization-subscription-trial-reason-input", "Implementation pilot");
        fireEvent.click(document.querySelector("#platform-organization-subscription-trial-confirm") as HTMLElement);

        await waitFor(() => expect(onStart).toHaveBeenCalledWith(45, 8, "Implementation pilot"));
        expect(authMock.refreshCurrentSession).toHaveBeenCalledTimes(1);
        expect(onSaved).toHaveBeenCalledTimes(1);
        expect(onDismiss).toHaveBeenCalledTimes(1);
    });

    it("requires an extension after the current trial end", async () =>
    {
        const onExtend = vi.fn().mockResolvedValue(undefined);
        render(
            <SubscriptionTrialDialog
                open
                ownerName={"trial@example.com"}
                ownerKind={"user"}
                isExtension
                currentPeriodEnd={"2026-08-30T00:00:00Z"}
                onDismiss={vi.fn()}
                onSaved={vi.fn()}
                onStart={vi.fn()}
                onExtend={onExtend}/>,
        );

        changeField("#platform-user-subscription-trial-reason-input", "Support extension");
        fireEvent.click(document.querySelector("#platform-user-subscription-trial-confirm") as HTMLElement);

        expect(await screen.findByText("The new trial end must be after the current trial end.")).toBeTruthy();
        expect(onExtend).not.toHaveBeenCalled();
    });
});
