/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import {InformationRequestAuditEventDto} from "../../../models/models.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";
import AuditHistoryPanel from "./AuditHistoryPanel.tsx";

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({
    getInformationRequestAuditEvents: vi.fn(),
    getInformationRequestAuditReconciliation: vi.fn(),
}));

const event = (index: number): InformationRequestAuditEventDto => ({
    eventId: `event-${index}`,
    eventTypeKey: `information_request.request.change_${index}`,
    eventClass: "request",
    category: "BUSINESS",
    outcome: "SUCCESS",
    occurredAt: Date.parse("2026-09-20T08:00:00Z"),
    recordedAt: Date.parse("2026-09-20T08:00:01Z"),
    actorKind: "USER",
    sealed: true,
    payload: {},
    withheldKeyCount: 0,
});

const page = (offset: number, total: number) => ({
    items: Array.from({length: Math.min(50, total - offset)}, (_, index) => event(offset + index)),
    total,
    limit: 50,
    offset,
});

describe("AuditHistoryPanel", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(transport.getInformationRequestAuditReconciliation).mockResolvedValue({
            requestId: "request-a",
            reconciled: true,
            auditedTransitionCount: 2,
            matchedTransitionCount: 2,
            unsealedEventCount: 0,
            missing: [],
            unmatched: [],
        });
        vi.mocked(transport.getInformationRequestAuditEvents).mockImplementation(async (_, request) => page(request.offset, 120));
    });

    afterEach(cleanup);

    it("asks for the first page of the request's audit history", async () => {
        render(<AuditHistoryPanel requestId={"request-a"}/>);

        expect(await screen.findByText("information_request.request.change_0")).toBeTruthy();
        expect(transport.getInformationRequestAuditEvents).toHaveBeenCalledWith("request-a", {limit: 50, offset: 0});
        expect(within(screen.getByRole("list", {name: "Audit records, newest first"})).getAllByRole("listitem")).toHaveLength(50);
    });

    it("pages through a history longer than one page", async () => {
        render(<AuditHistoryPanel requestId={"request-a"}/>);
        expect(await screen.findByText(`1 to 50 of ${formatInformationRequestCount(120)}`)).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Next"}));
        expect(await screen.findByText("information_request.request.change_50")).toBeTruthy();
        expect(transport.getInformationRequestAuditEvents).toHaveBeenLastCalledWith("request-a", {
            limit: 50,
            offset: 50
        });
        expect(screen.getByText(`51 to 100 of ${formatInformationRequestCount(120)}`)).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Next"}));
        expect(await screen.findByText(`101 to 120 of ${formatInformationRequestCount(120)}`)).toBeTruthy();
        expect((screen.getByRole("button", {name: "Next"}) as HTMLButtonElement).disabled).toBe(true);

        fireEvent.click(screen.getByRole("button", {name: "Previous"}));
        await waitFor(() => expect(transport.getInformationRequestAuditEvents).toHaveBeenLastCalledWith("request-a", {
            limit: 50,
            offset: 50
        }));
    });

    it("keeps the shown page and its pager while the next page of the same request loads", async () => {
        vi.mocked(transport.getInformationRequestAuditEvents).mockImplementation(async (_, request) =>
            request.offset === 0 ? page(0, 120) : new Promise<ReturnType<typeof page>>(() => undefined));
        render(<AuditHistoryPanel requestId={"request-a"}/>);
        const next = await screen.findByRole("button", {name: "Next"});
        next.focus();

        fireEvent.click(next);

        await waitFor(() => expect(transport.getInformationRequestAuditEvents).toHaveBeenLastCalledWith("request-a", {
            limit: 50,
            offset: 50
        }));
        expect(screen.getByText("information_request.request.change_0")).toBeTruthy();
        expect(document.activeElement).toBe(next);
    });

    it("withholds one request's audit history once another request is shown", async () => {
        vi.mocked(transport.getInformationRequestAuditEvents).mockImplementation(async (id, request) =>
            id === "request-a" ? page(request.offset, 120) : new Promise<ReturnType<typeof page>>(() => undefined));
        const view = render(<AuditHistoryPanel requestId={"request-a"}/>);
        expect(await screen.findByText("information_request.request.change_0")).toBeTruthy();

        view.rerender(<AuditHistoryPanel requestId={"request-b"}/>);

        await waitFor(() => expect(transport.getInformationRequestAuditEvents).toHaveBeenCalledWith("request-b", {
            limit: 50,
            offset: 0
        }));
        expect(screen.queryByText("information_request.request.change_0")).toBeNull();
    });

    it("offers no pager when the history fits on one page", async () => {
        vi.mocked(transport.getInformationRequestAuditEvents).mockResolvedValue(page(0, 3));
        render(<AuditHistoryPanel requestId={"request-a"}/>);

        expect(await screen.findByText("information_request.request.change_2")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Next"})).toBeNull();
        expect(unnamedControls(document.body)).toEqual([]);
        expect(duplicateIds(document.body)).toEqual([]);
    });
});
