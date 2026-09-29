/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import {InformationRequestAuditEventDto} from "../../../models/models.tsx";
import AuditSearchPanel from "./AuditSearchPanel.tsx";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({searchInformationRequestAuditEvents: vi.fn()}));

const event = (index: number): InformationRequestAuditEventDto => ({
    eventId: `event-${index}`,
    eventTypeKey: "information_request.party.assign",
    eventClass: "party",
    category: "BUSINESS",
    outcome: "SUCCESS",
    occurredAt: "2026-09-20T08:00:00Z",
    recordedAt: "2026-09-20T08:00:01Z",
    actorKind: "USER",
    actorId: "user-a",
    requestId: "request-a-0000",
    sealed: true,
    payload: {roleKey: "CONTRIBUTOR"},
    withheldKeyCount: 1,
});

describe("AuditSearchPanel", () =>
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
        vi.clearAllMocks();
        vi.mocked(administration.searchInformationRequestAuditEvents).mockResolvedValue({items: [event(1)], total: 1, limit: 50, offset: 0});
    });

    afterEach(cleanup);

    it("searches the owner's audit record by request, class, and time and lists what it found", async () =>
    {
        render(<AuditSearchPanel/>);

        const results = await screen.findByRole("list", {name: "Audit events"});
        await waitFor(() => expect(administration.searchInformationRequestAuditEvents).toHaveBeenCalledWith({limit: 50, offset: 0}));
        const found = within(results).getAllByRole("listitem")[0];
        expect(within(found).getByText("information_request.party.assign")).toBeTruthy();
        expect(within(found).getByText(/request request-/)).toBeTruthy();
        expect(within(found).getByText("roleKey: CONTRIBUTOR, 1 values withheld")).toBeTruthy();

        fireEvent.change(screen.getByLabelText(/Request id/), {target: {value: "request-a-0000"}});
        fireEvent.change(screen.getByLabelText(/Event class/), {target: {value: "party"}});
        fireEvent.change(screen.getByLabelText(/From/), {target: {value: "2026-09-19T00:00"}});
        fireEvent.click(screen.getByRole("button", {name: "Search"}));

        await waitFor(() => expect(administration.searchInformationRequestAuditEvents).toHaveBeenLastCalledWith({
            requestId: "request-a-0000",
            eventClass: "party",
            occurredAfter: new Date("2026-09-19T00:00").toISOString(),
            limit: 50,
            offset: 0,
        }));
        expect(await screen.findByText("1 event found.")).toBeTruthy();
    });

    it("names every control and region of the search", async () =>
    {
        render(<AuditSearchPanel/>);
        await screen.findByRole("list", {name: "Audit events"});

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("pages through a long result", async () =>
    {
        vi.mocked(administration.searchInformationRequestAuditEvents).mockResolvedValue({
            items: Array.from({length: 50}, (_, index) => event(index)),
            total: 120,
            limit: 50,
            offset: 0,
        });
        render(<AuditSearchPanel/>);

        fireEvent.click(await screen.findByRole("button", {name: "Next"}));

        await waitFor(() => expect(administration.searchInformationRequestAuditEvents).toHaveBeenLastCalledWith({limit: 50, offset: 50}));
    });

    it("refuses a period that ends before it starts without searching", async () =>
    {
        render(<AuditSearchPanel/>);
        await screen.findByRole("list", {name: "Audit events"});

        fireEvent.change(screen.getByLabelText(/From/), {target: {value: "2026-09-20T00:00"}});
        fireEvent.change(screen.getByLabelText(/Until/), {target: {value: "2026-09-19T00:00"}});

        expect(screen.getByText("The period ends after it starts.")).toBeTruthy();
        expect((screen.getByRole("button", {name: "Search"}) as HTMLButtonElement).disabled).toBe(true);
    });
});
