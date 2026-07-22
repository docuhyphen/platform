import {beforeEach, describe, expect, it, vi} from "vitest";
import {hasExchangeWorkspaceContent} from "./exchangeWorkspaceAvailability.ts";

const mocks = vi.hoisted(() => ({
    checkHasExchanges: vi.fn(),
    fetchPendingInvitations: vi.fn(),
}));

vi.mock("../../services/exchangeApi.ts", () => ({
    checkSignedInAppUserHasExchanges: (...args: unknown[]) => mocks.checkHasExchanges(...args),
    fetchPendingExchangeRecipientInvitations: (...args: unknown[]) => mocks.fetchPendingInvitations(...args),
}));

describe("hasExchangeWorkspaceContent", () =>
{
    beforeEach(() =>
    {
        mocks.checkHasExchanges.mockReset();
        mocks.fetchPendingInvitations.mockReset();
    });

    it("opens the workspace for a user whose only content is a trusted participant invitation", async () =>
    {
        mocks.checkHasExchanges.mockResolvedValue(false);
        mocks.fetchPendingInvitations.mockResolvedValue([{id: "recipient-1"}]);

        await expect(hasExchangeWorkspaceContent("access-token")).resolves.toBe(true);
    });

    it("keeps the workspace available when the invitation lookup fails but an Exchange exists", async () =>
    {
        mocks.checkHasExchanges.mockResolvedValue(true);
        mocks.fetchPendingInvitations.mockRejectedValue(new Error("invitation lookup failed"));

        await expect(hasExchangeWorkspaceContent("access-token")).resolves.toBe(true);
    });

    it("keeps the workspace available when the Exchange lookup fails but an invitation exists", async () =>
    {
        mocks.checkHasExchanges.mockRejectedValue(new Error("Exchange lookup failed"));
        mocks.fetchPendingInvitations.mockResolvedValue([{id: "recipient-1"}]);

        await expect(hasExchangeWorkspaceContent("access-token")).resolves.toBe(true);
    });

    it("reports no workspace content when both successful lookups are empty", async () =>
    {
        mocks.checkHasExchanges.mockResolvedValue(false);
        mocks.fetchPendingInvitations.mockResolvedValue([]);

        await expect(hasExchangeWorkspaceContent(null)).resolves.toBe(false);
    });

    it("does not report an empty workspace when the invitation lookup fails", async () =>
    {
        mocks.checkHasExchanges.mockResolvedValue(false);
        mocks.fetchPendingInvitations.mockRejectedValue(new Error("invitation lookup failed"));

        await expect(hasExchangeWorkspaceContent("access-token")).rejects.toThrow("invitation lookup failed");
    });

    it("does not report an empty workspace when the Exchange lookup fails", async () =>
    {
        mocks.checkHasExchanges.mockRejectedValue(new Error("Exchange lookup failed"));
        mocks.fetchPendingInvitations.mockResolvedValue([]);

        await expect(hasExchangeWorkspaceContent("access-token")).rejects.toThrow("Exchange lookup failed");
    });
});
