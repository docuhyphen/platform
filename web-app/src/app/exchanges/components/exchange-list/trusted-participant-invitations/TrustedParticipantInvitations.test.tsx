/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import TrustedParticipantInvitations from "./TrustedParticipantInvitations.tsx";

const mocks = vi.hoisted(() => ({
    fetchPending: vi.fn(),
    decide: vi.fn(),
    realtimeHandler: undefined as undefined | (() => void),
}));

vi.mock("../../../../../services/exchangeApi.ts", () => ({
    fetchPendingExchangeRecipientInvitations: (...args: unknown[]) => mocks.fetchPending(...args),
    decideExchangeRecipientInvitation: (...args: unknown[]) => mocks.decide(...args),
}));

vi.mock("../../../../../services/NotificationService.tsx", () => ({
    realtimeService: {
        on: vi.fn((_type: string, handler: () => void) =>
        {
            mocks.realtimeHandler = handler;
            return vi.fn();
        }),
    },
}));

describe("TrustedParticipantInvitations", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() =>
    {
        mocks.fetchPending.mockReset();
        mocks.decide.mockReset();
        mocks.realtimeHandler = undefined;
    });

    afterEach(cleanup);

    it("accepts one invitation without deciding the Exchange", async () =>
    {
        mocks.fetchPending.mockResolvedValue([
            {
                id: "recipient-1",
                exchangeId: "exchange-1",
                selectionType: "TRUSTED_PERSON",
                createdAt: "2026-07-18T12:00:00Z",
            },
        ]);
        mocks.decide.mockResolvedValue(undefined);
        const onCountChange = vi.fn();

        render(<TrustedParticipantInvitations onCountChange={onCountChange}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Accept access"}));
        await waitFor(() =>
        {
            expect(mocks.decide).toHaveBeenCalledWith("recipient-1", "ACCEPT");
            expect(screen.queryByText("Trusted participant invitation")).toBeNull();
        });
        expect(onCountChange).toHaveBeenLastCalledWith(0);
    });

    it("keeps a stale invitation pending when acceptance is denied", async () =>
    {
        mocks.fetchPending.mockResolvedValue([
            {
                id: "recipient-2",
                exchangeId: "exchange-2",
                selectionType: "TRUSTED_GROUP",
                createdAt: "2026-07-18T12:00:00Z",
            },
        ]);
        mocks.decide.mockRejectedValue(new Error("stale trust"));

        render(<TrustedParticipantInvitations onCountChange={vi.fn()}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Accept access"}));
        expect(
            await screen.findByText("This trusted participant invitation can no longer be accepted."),
        ).toBeTruthy();
        expect(screen.getByText("Trusted participant invitation")).toBeTruthy();
    });

    it("refreshes pending invitations after an Exchange list event", async () =>
    {
        mocks.fetchPending
            .mockResolvedValueOnce([])
            .mockResolvedValueOnce([
                {
                    id: "recipient-3",
                    exchangeId: "exchange-3",
                    selectionType: "TRUSTED_GROUP",
                    createdAt: "2026-07-18T12:00:00Z",
                },
            ]);

        render(<TrustedParticipantInvitations onCountChange={vi.fn()}/>);
        await waitFor(() => expect(mocks.fetchPending).toHaveBeenCalledTimes(1));

        mocks.realtimeHandler?.();

        expect(await screen.findByText("Exchange reference: exchange-3")).toBeTruthy();
        expect(mocks.fetchPending).toHaveBeenCalledTimes(2);
    });
});
