/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, screen, cleanup, fireEvent} from "@testing-library/react";
import AuditEventCardList from "../components/audit-event-card-list/AuditEventCardList.tsx";
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

describe("AuditEventCardList", () =>
{
    it("renders one card per item, using the friendly event type label", () =>
    {
        const items = [makeEvent("e1"), makeEvent("e2")];

        render(
            <AuditEventCardList
                items={items}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        expect(screen.getAllByText("User Login")).toHaveLength(2);
        expect(document.getElementById("audit-event-card-e1")).toBeTruthy();
        expect(document.getElementById("audit-event-card-e2")).toBeTruthy();
    });

    it("disables the Load more button when nextCursor is null", () =>
    {
        render(
            <AuditEventCardList
                items={[makeEvent("e1")]}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        const button = document.getElementById("button-audit-event-card-list-load-more") as HTMLButtonElement;
        expect(button.disabled).toBe(true);
    });

    it("calls onLoadMore when the Load more button is clicked and a cursor is present", () =>
    {
        const onLoadMore = vi.fn();

        render(
            <AuditEventCardList
                items={[makeEvent("e1")]}
                nextCursor={{occurredAt: "2024-01-01T00:00:00Z", eventId: "e1"}}
                onLoadMore={onLoadMore}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        const button = document.getElementById("button-audit-event-card-list-load-more") as HTMLButtonElement;
        expect(button.disabled).toBe(false);

        fireEvent.click(button);

        expect(onLoadMore).toHaveBeenCalledTimes(1);
    });

    it("calls onEventClick with the clicked event", () =>
    {
        const onEventClick = vi.fn();
        const event = makeEvent("e1");

        render(
            <AuditEventCardList
                items={[event]}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={onEventClick}
                loading={false}
            />
        );

        const card = document.getElementById("audit-event-card-e1") as HTMLElement;
        fireEvent.click(card);

        expect(onEventClick).toHaveBeenCalledWith(event);
    });

    it("shows the empty state when there are no items", () =>
    {
        render(
            <AuditEventCardList
                items={[]}
                nextCursor={null}
                onLoadMore={vi.fn()}
                onEventClick={vi.fn()}
                loading={false}
            />
        );

        expect(document.getElementById("audit-event-card-list-empty")).toBeTruthy();
    });
});
