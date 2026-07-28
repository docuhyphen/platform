/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, screen, cleanup, fireEvent} from "@testing-library/react";
import AuditEventTable from "../components/audit-event-table/AuditEventTable.tsx";
import {AuditEventDto} from "../../models/models.tsx";

const makeEvent = (eventId: string): AuditEventDto => ({
    eventId,
    category: "SECURITY",
    eventTypeKey: "user.login",
    outcome: "SUCCESS",
    occurredAt: "2024-01-01T00:00:00Z",
    recordedAt: "2024-01-01T00:00:01Z",
    ledgerTime: "2024-01-01T00:00:02Z",
    streamId: "stream-1",
    streamSequence: 1,
    actorKind: "USER",
    actorId: "user-1",
    targetType: "USER",
    targetId: "user-1",
    payload: {},
    eventHash: "hash-1",
});

afterEach(() =>
{
    cleanup();
});

describe("AuditEventTable", () =>
{
    it("renders one row per item", () =>
    {
        const items = [makeEvent("e1"), makeEvent("e2")];

        render(
            <AuditEventTable
                items={items}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        expect(screen.getAllByText("User Login")).toHaveLength(2);
        expect(document.getElementById("audit-event-row-e1")).toBeTruthy();
        expect(document.getElementById("audit-event-row-e2")).toBeTruthy();
    });

    it("keeps pagination outside the scrollable table region", () =>
    {
        render(
            <AuditEventTable
                items={[makeEvent("e1")]}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        const scrollableContent = document.getElementById(
            "audit-event-table-scrollable-content",
        ) as HTMLElement;
        const tableHeader = document.getElementById("audit-event-table-header") as HTMLElement;
        const pagination = document.getElementById("audit-event-table-pagination") as HTMLElement;

        expect(scrollableContent.contains(tableHeader)).toBe(true);
        expect(scrollableContent.contains(pagination)).toBe(false);
    });

    it("disables the next page button when nextCursor is null", () =>
    {
        render(
            <AuditEventTable
                items={[makeEvent("e1")]}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        const button = document.getElementById("audit-event-table-pagination-next") as HTMLButtonElement;
        expect(button.disabled).toBe(true);
    });

    it("calls onLoadMore when the next page button is clicked and a cursor is present", () =>
    {
        const onLoadMore = vi.fn();

        render(
            <AuditEventTable
                items={[makeEvent("e1")]}
                nextCursor={{occurredAt: "2024-01-01T00:00:00Z", eventId: "e1"}}
                onLoadMore={onLoadMore}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        const button = document.getElementById("audit-event-table-pagination-next") as HTMLButtonElement;
        expect(button.disabled).toBe(false);

        fireEvent.click(button);

        expect(onLoadMore).toHaveBeenCalledTimes(1);
    });

    it("shows only one page of rows at a time", () =>
    {
        const items = Array.from({length: 26}, (_value, index) => makeEvent(`e${index + 1}`));

        render(
            <AuditEventTable
                items={items}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        expect(document.getElementById("audit-event-row-e1")).toBeTruthy();
        expect(document.getElementById("audit-event-row-e26")).toBeFalsy();

        fireEvent.click(document.getElementById("audit-event-table-pagination-next") as HTMLButtonElement);

        expect(document.getElementById("audit-event-row-e1")).toBeFalsy();
        expect(document.getElementById("audit-event-row-e26")).toBeTruthy();
    });

    it("lands on the fetched page after advancing past loaded cursor results", () =>
    {
        const firstItems = Array.from({length: 50}, (_value, index) => makeEvent(`e${index + 1}`));
        const nextItems = Array.from({length: 75}, (_value, index) => makeEvent(`e${index + 1}`));
        const onLoadMore = vi.fn();
        const {rerender} = render(
            <AuditEventTable
                items={firstItems}
                nextCursor={{occurredAt: "2024-01-01T00:00:00Z", eventId: "e50"}}
                onLoadMore={onLoadMore}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        fireEvent.click(document.getElementById("audit-event-table-pagination-next") as HTMLButtonElement);
        fireEvent.click(document.getElementById("audit-event-table-pagination-next") as HTMLButtonElement);

        expect(onLoadMore).toHaveBeenCalledTimes(1);

        rerender(
            <AuditEventTable
                items={nextItems}
                nextCursor={null}
                onLoadMore={onLoadMore}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        expect(screen.getByText("3 / 3")).toBeTruthy();
        expect(document.getElementById("audit-event-row-e51")).toBeTruthy();
    });

    it("calls onEventClick with the clicked event", () =>
    {
        const onEventClick = vi.fn();
        const event = makeEvent("e1");

        render(
            <AuditEventTable
                items={[event]}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={onEventClick}
                loading={false}
            />
        );

        const row = document.getElementById("audit-event-row-e1") as HTMLElement;
        fireEvent.click(row);

        expect(onEventClick).toHaveBeenCalledWith(event);
    });
});
