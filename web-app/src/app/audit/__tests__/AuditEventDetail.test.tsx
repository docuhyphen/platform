/** @vitest-environment jsdom */
import {describe, expect, it, vi, afterEach} from "vitest";
import {render, screen, cleanup, fireEvent, waitFor} from "@testing-library/react";
import AuditEventDetail from "../components/audit-event-detail/AuditEventDetail.tsx";
import {AuditEventDto} from "../../models/models.tsx";

const event: AuditEventDto = {
    eventId: "e1",
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
    actorRole: "MEMBER",
    targetType: "USER",
    targetId: "user-1",
    reason: "login attempt",
    payload: {ip: "127.0.0.1"},
    eventHash: "hash-1",
    prevHash: "hash-0",
};

afterEach(() =>
{
    cleanup();
});

describe("AuditEventDetail", () =>
{
    it("renders the event fields when open", () =>
    {
        render(
            <AuditEventDetail
                event={event}
                open={true}
                onDismiss={vi.fn()}
            />
        );

        expect(screen.getByText("User Login")).toBeTruthy();
        expect(screen.getByText("login attempt")).toBeTruthy();
    });

    it("hides the event hash, payload, and redaction note under a collapsed More info accordion", () =>
    {
        render(
            <AuditEventDetail
                event={event}
                open={true}
                onDismiss={vi.fn()}
            />
        );

        expect(screen.queryByText("hash-1")).toBeFalsy();
        expect(document.getElementById("audit-event-detail-redaction-note")).toBeFalsy();

        fireEvent.click(screen.getByText("More info"));

        expect(screen.getByText("hash-1")).toBeTruthy();
        expect(document.getElementById("audit-event-detail-redaction-note")).toBeTruthy();
    });

    it("does not render dialog content when closed", () =>
    {
        render(
            <AuditEventDetail
                event={event}
                open={false}
                onDismiss={vi.fn()}
            />
        );

        expect(document.getElementById("audit-event-detail-surface")).toBeFalsy();
    });

    it("copies the actor name and id, comma-separated, when the actor copy button is clicked", async () =>
    {
        const writeText = vi.fn().mockResolvedValue(undefined);
        Object.assign(navigator, {clipboard: {writeText}});

        render(
            <AuditEventDetail
                event={event}
                open={true}
                onDismiss={vi.fn()}
            />
        );

        const copyButton = document.getElementById("button-audit-event-detail-actor-copy") as HTMLButtonElement;
        fireEvent.click(copyButton);

        await waitFor(() => expect(writeText).toHaveBeenCalledWith("USER, user-1"));
    });

    it("never renders the raw actor or target id as visible text", () =>
    {
        render(
            <AuditEventDetail
                event={event}
                open={true}
                onDismiss={vi.fn()}
            />
        );

        expect(screen.queryByText(/user-1/)).toBeFalsy();
    });

    it("calls onDismiss when the close button is clicked", async () =>
    {
        const onDismiss = vi.fn();

        render(
            <AuditEventDetail
                event={event}
                open={true}
                onDismiss={onDismiss}
            />
        );

        const closeButton = document.getElementById("button-audit-event-detail-dismiss") as HTMLButtonElement;
        fireEvent.click(closeButton);

        await waitFor(() => expect(onDismiss).toHaveBeenCalledTimes(1));
    });
});
