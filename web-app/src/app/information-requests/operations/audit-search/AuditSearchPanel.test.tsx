/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import {InformationRequestAuditEventDto} from "../../../models/models.tsx";
import AuditSearchPanel from "./AuditSearchPanel.tsx";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({searchInformationRequestAuditEvents: vi.fn()}));

const event = (index: number): InformationRequestAuditEventDto => ({
    eventId: `event-${index}`,
    eventTypeKey: "information_request.party.assign",
    eventClass: "party",
    category: "BUSINESS",
    outcome: "SUCCESS",
    occurredAt: Date.parse("2026-09-20T08:00:00Z"),
    recordedAt: Date.parse("2026-09-20T08:00:01Z"),
    actorKind: "USER",
    actorId: "user-a",
    requestId: "request-a-0000",
    sealed: true,
    payload: {roleKey: "CONTRIBUTOR"},
    withheldKeyCount: 1,
});

const unansweredSearch = () =>
    new Promise<Awaited<ReturnType<typeof administration.searchInformationRequestAuditEvents>>>(() => undefined);

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
        expect(within(found).getByText("roleKey: CONTRIBUTOR, 1 value withheld")).toBeTruthy();

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

        expect(duplicateIds(document.body)).toEqual([]);
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

    it("keeps the shown events and the pager while the next page of the same search loads", async () => {
        vi.mocked(administration.searchInformationRequestAuditEvents).mockImplementation(search => search.offset === 0
            ? Promise.resolve({
                items: Array.from({length: 50}, (_, index) => event(index)),
                total: 120,
                limit: 50,
                offset: 0
            })
            : unansweredSearch());
        render(<AuditSearchPanel/>);
        const next = await screen.findByRole("button", {name: "Next"});
        next.focus();

        fireEvent.click(next);

        await waitFor(() => expect(administration.searchInformationRequestAuditEvents).toHaveBeenLastCalledWith({
            limit: 50,
            offset: 50
        }));
        expect(within(screen.getByRole("list", {name: "Audit events"})).getAllByRole("listitem")).toHaveLength(50);
        expect(document.activeElement).toBe(next);
    });

    it("withholds the earlier search's events while a new search loads", async () => {
        vi.mocked(administration.searchInformationRequestAuditEvents).mockImplementation(search => search.eventClass
            ? unansweredSearch()
            : Promise.resolve({items: [event(1)], total: 1, limit: 50, offset: 0}));
        render(<AuditSearchPanel/>);
        await screen.findByRole("list", {name: "Audit events"});

        fireEvent.change(screen.getByLabelText(/Event class/), {target: {value: "party"}});
        fireEvent.click(screen.getByRole("button", {name: "Search"}));

        await waitFor(() => expect(administration.searchInformationRequestAuditEvents).toHaveBeenLastCalledWith(
            expect.objectContaining({eventClass: "party"}),
        ));
        expect(screen.queryByRole("list", {name: "Audit events"})).toBeNull();
    });

    it("withholds the shown events and their count once repeating the same search is refused", async () => {
        vi.mocked(administration.searchInformationRequestAuditEvents)
            .mockResolvedValueOnce({items: [event(1)], total: 1, limit: 50, offset: 0})
            .mockRejectedValue({
                errorMessage: "Access denied to this owner's audit record",
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
            });
        render(<AuditSearchPanel/>);
        await screen.findByRole("list", {name: "Audit events"});

        fireEvent.click(screen.getByRole("button", {name: "Search"}));

        expect((await screen.findByRole("alert")).textContent).toContain("Access denied to this owner's audit record");
        expect(screen.queryByRole("list", {name: "Audit events"})).toBeNull();
        expect(screen.queryByText("1 event found.")).toBeNull();
        expect(document.getElementById("information-request-audit-search-pager")).toBeNull();
    });

    it("keeps refused events withheld while the same search is repeated and shows only the answer that follows", async () => {
        let answer: (page: Awaited<ReturnType<typeof administration.searchInformationRequestAuditEvents>>) => void = () => undefined;
        vi.mocked(administration.searchInformationRequestAuditEvents)
            .mockResolvedValueOnce({items: [event(1)], total: 1, limit: 50, offset: 0})
            .mockRejectedValueOnce({
                errorMessage: "Access denied to this owner's audit record",
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
            })
            .mockImplementationOnce(() => new Promise(resolve => {
                answer = resolve;
            }));
        render(<AuditSearchPanel/>);
        await screen.findByRole("list", {name: "Audit events"});
        fireEvent.click(screen.getByRole("button", {name: "Search"}));
        await screen.findByText("Access denied to this owner's audit record");

        fireEvent.click(screen.getByRole("button", {name: "Search"}));

        await waitFor(() => expect(administration.searchInformationRequestAuditEvents).toHaveBeenCalledTimes(3));
        expect(screen.queryByText("Access denied to this owner's audit record")).toBeNull();
        expect(screen.queryByRole("list", {name: "Audit events"})).toBeNull();
        expect(screen.getByText("Searching the audit record")).toBeTruthy();
        answer({items: [{...event(7), payload: {stageKey: "current-stage"}}], total: 1, limit: 50, offset: 0});
        const results = await screen.findByRole("list", {name: "Audit events"});
        expect(within(results).getAllByRole("listitem")).toHaveLength(1);
        expect(within(results).getByText("stageKey: current-stage, 1 value withheld")).toBeTruthy();
        expect(screen.getByText("1 event found.")).toBeTruthy();
    });

    it("searches the events of a found event's actor or request from its row", async () => {
        render(<AuditSearchPanel/>);
        const found = within(await screen.findByRole("list", {name: "Audit events"})).getAllByRole("listitem")[0];

        fireEvent.click(within(found).getByRole("button", {name: "Search the events of actor user-a"}));

        await waitFor(() => expect(administration.searchInformationRequestAuditEvents)
            .toHaveBeenLastCalledWith({actorId: "user-a", limit: 50, offset: 0}));
        expect((screen.getByLabelText(/Actor id/) as HTMLInputElement).value).toBe("user-a");
        const again = within(await screen.findByRole("list", {name: "Audit events"})).getAllByRole("listitem")[0];
        fireEvent.click(within(again).getByRole("button", {name: "Search the events of request request-a-0000"}));

        await waitFor(() => expect(administration.searchInformationRequestAuditEvents)
            .toHaveBeenLastCalledWith({requestId: "request-a-0000", actorId: "user-a", limit: 50, offset: 0}));
        expect((screen.getByLabelText(/Request id/) as HTMLInputElement).value).toBe("request-a-0000");
    });

    it("offers Try again when the audit record cannot be searched and searches it again", async () => {
        vi.mocked(administration.searchInformationRequestAuditEvents)
            .mockRejectedValueOnce({errorMessage: "The audit record is not available right now"});
        render(<AuditSearchPanel/>);

        expect((await screen.findByRole("alert")).textContent).toContain("The audit record is not available right now");
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByRole("list", {name: "Audit events"})).toBeTruthy();
        expect(administration.searchInformationRequestAuditEvents).toHaveBeenCalledTimes(2);
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
