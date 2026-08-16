/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import SubscriptionTrialTransitionDialog from "./SubscriptionTrialTransitionDialog.tsx";

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

describe("SubscriptionTrialTransitionDialog", () =>
{
    beforeEach(() => authMock.refreshCurrentSession.mockResolvedValue(null));

    afterEach(() =>
    {
        cleanup();
        vi.clearAllMocks();
    });

    it("ends a user trial with an audited reason and refreshes the session", async () =>
    {
        const onEnd = vi.fn().mockResolvedValue(undefined);
        const onSaved = vi.fn();
        render(
            <SubscriptionTrialTransitionDialog
                open
                ownerName={"trial@example.com"}
                ownerKind={"user"}
                mode={"END"}
                defaultSeatCapacity={null}
                onDismiss={vi.fn()}
                onSaved={onSaved}
                onEnd={onEnd}
                onConvert={vi.fn()}/>,
        );

        changeField("#platform-user-trial-end-reason-input", "Customer ended evaluation");
        fireEvent.click(document.querySelector("#platform-user-trial-end-confirm") as HTMLElement);

        await waitFor(() => expect(onEnd).toHaveBeenCalledWith("Customer ended evaluation"));
        expect(authMock.refreshCurrentSession).toHaveBeenCalledTimes(1);
        expect(onSaved).toHaveBeenCalledTimes(1);
    });

    it("converts an organization trial with paid lifecycle and seat values", async () =>
    {
        const onConvert = vi.fn().mockResolvedValue(undefined);
        render(
            <SubscriptionTrialTransitionDialog
                open
                ownerName={"Acme"}
                ownerKind={"organization"}
                mode={"CONVERT"}
                defaultSeatCapacity={5}
                onDismiss={vi.fn()}
                onSaved={vi.fn()}
                onEnd={vi.fn()}
                onConvert={onConvert}/>,
        );

        changeField("#platform-organization-trial-convert-frequency-select", "ANNUAL");
        changeField("#platform-organization-trial-convert-period-end-input", "2027-08-16T12:00");
        changeField("#platform-organization-trial-convert-seats-input", "25");
        changeField("#platform-organization-trial-convert-reason-input", "Annual agreement signed");
        fireEvent.click(document.querySelector("#platform-organization-trial-convert-confirm") as HTMLElement);

        await waitFor(() => expect(onConvert).toHaveBeenCalledWith(
            "ANNUAL",
            new Date("2027-08-16T12:00").toISOString(),
            25,
            "Annual agreement signed",
        ));
    });
});
