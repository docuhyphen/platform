/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, fireEvent, cleanup, waitFor} from "@testing-library/react";
import AuditEventsSection from "../audit-events-section/AuditEventsSection.tsx";
import {dateOnlyToRangeEndInstant, dateOnlyToRangeStartInstant} from "../auditDateRange.ts";

const fetchOrganizationAuditEvents = vi.fn().mockResolvedValue({items: [], nextCursor: null});
const fetchPlatformAuditEvents = vi.fn().mockResolvedValue({items: [], nextCursor: null});

vi.mock("../../../services/auditService.ts", () => ({
    fetchOrganizationAuditEvents: (...args: unknown[]) => fetchOrganizationAuditEvents(...args),
    fetchPlatformAuditEvents: (...args: unknown[]) => fetchPlatformAuditEvents(...args),
}));

afterEach(() =>
{
    cleanup();
    fetchOrganizationAuditEvents.mockClear();
    fetchPlatformAuditEvents.mockClear();
});

describe("AuditEventsSection date filters", () =>
{
    it("converts date-only organization filter values into inclusive start/end instants", async () =>
    {
        render(<AuditEventsSection scope={{kind: "organization", organizationId: "org-1"}}/>);

        await waitFor(() => expect(fetchOrganizationAuditEvents).toHaveBeenCalled());
        fetchOrganizationAuditEvents.mockClear();

        const afterInput = document.getElementById("audit-events-occurred-after") as HTMLInputElement;
        const beforeInput = document.getElementById("audit-events-occurred-before") as HTMLInputElement;
        fireEvent.change(afterInput, {target: {value: "2024-03-01"}});
        fireEvent.change(beforeInput, {target: {value: "2024-03-10"}});

        await waitFor(() => expect(fetchOrganizationAuditEvents).toHaveBeenCalled());

        const [, params] = fetchOrganizationAuditEvents.mock.calls[fetchOrganizationAuditEvents.mock.calls.length - 1];
        expect(params.occurredAfter).toBe(dateOnlyToRangeStartInstant("2024-03-01"));
        expect(params.occurredBefore).toBe(dateOnlyToRangeEndInstant("2024-03-10"));
    });

    it("converts date-only platform filter values into inclusive start/end instants", async () =>
    {
        render(<AuditEventsSection scope={{kind: "platform"}}/>);

        await waitFor(() => expect(fetchPlatformAuditEvents).toHaveBeenCalled());
        fetchPlatformAuditEvents.mockClear();

        const afterInput = document.getElementById("audit-events-occurred-after") as HTMLInputElement;
        fireEvent.change(afterInput, {target: {value: "2024-03-01"}});

        await waitFor(() => expect(fetchPlatformAuditEvents).toHaveBeenCalled());

        const [params] = fetchPlatformAuditEvents.mock.calls[fetchPlatformAuditEvents.mock.calls.length - 1];
        expect(params.occurredAfter).toBe(dateOnlyToRangeStartInstant("2024-03-01"));
    });

    it("omits occurred range params entirely when the date fields are empty", async () =>
    {
        render(<AuditEventsSection scope={{kind: "organization", organizationId: "org-1"}}/>);

        await waitFor(() => expect(fetchOrganizationAuditEvents).toHaveBeenCalled());

        const [, params] = fetchOrganizationAuditEvents.mock.calls[0];
        expect(params.occurredAfter).toBeUndefined();
        expect(params.occurredBefore).toBeUndefined();
    });
});
