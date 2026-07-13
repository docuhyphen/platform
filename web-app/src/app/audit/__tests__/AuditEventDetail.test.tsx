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

    it("shows friendly detail text instead of raw audit codes", () =>
    {
        render(
            <AuditEventDetail
                event={{
                    ...event,
                    actorKind: "APP_USER",
                    actorLabel: "Christopher Mahlangu <mchrizy@gmail.com>",
                    actorRole: "APP_USER",
                    eventTypeKey: "document.preview",
                    payload: {
                        document_title: "Bank Statements (3 Months)",
                        exchange_id: "8358a44e-7c2f-4ea9-947e-b9e595498386",
                        exchange_name: "Quarterly Finance Review",
                    },
                }}
                open={true}
                onDismiss={vi.fn()}
            />
        );

        fireEvent.click(screen.getByText("More info"));

        expect(screen.getAllByText("Document previewed").length).toBeGreaterThan(0);
        expect(screen.getByText("Christopher Mahlangu <mchrizy@gmail.com> - Application user")).toBeTruthy();
        expect(screen.getByText("Successful")).toBeTruthy();
        expect(screen.getByText("Document title:")).toBeTruthy();
        expect(screen.getByText("Exchange:")).toBeTruthy();
        expect(screen.getByText("Quarterly Finance Review")).toBeTruthy();
        expect(screen.queryByText("document.preview")).toBeFalsy();
        expect(screen.queryByText("document_title:")).toBeFalsy();
        expect(screen.queryByText("Exchange name:")).toBeFalsy();
        expect(screen.queryByText("8358a44e-7c2f-4ea9-947e-b9e595498386")).toBeFalsy();
        expect(screen.queryByText("APP_USER")).toBeFalsy();
        expect(screen.queryByText("SUCCESS")).toBeFalsy();
    });

    it("uses the target label when a payload entity id points at the event target", () =>
    {
        render(
            <AuditEventDetail
                event={{
                    ...event,
                    targetType: "DOCUMENT",
                    targetLabel: "Bank Statements (3 Months)",
                    payload: {
                        document_id: "8358a44e-7c2f-4ea9-947e-b9e595498386",
                    },
                }}
                open={true}
                onDismiss={vi.fn()}
            />
        );

        fireEvent.click(screen.getByText("More info"));

        expect(screen.getByText("Document:")).toBeTruthy();
        expect(screen.getAllByText("Bank Statements (3 Months)").length).toBeGreaterThan(0);
        expect(screen.queryByText("8358a44e-7c2f-4ea9-947e-b9e595498386")).toBeFalsy();
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

        await waitFor(() => expect(writeText).toHaveBeenCalledWith("User - Member, user-1"));
    });

    it("copies the target type and id, comma-separated, when the target copy button is clicked", async () =>
    {
        const writeText = vi.fn().mockResolvedValue(undefined);
        Object.assign(navigator, {clipboard: {writeText}});

        render(
            <AuditEventDetail
                event={{
                    ...event,
                    targetType: "USER",
                    targetId: "user-2",
                }}
                open={true}
                onDismiss={vi.fn()}
            />
        );

        const copyButton = document.getElementById("button-audit-event-detail-target-copy") as HTMLButtonElement;
        fireEvent.click(copyButton);

        await waitFor(() => expect(writeText).toHaveBeenCalledWith("User, user-2"));
    });

    it("falls back to the execCommand copy path without throwing when the Clipboard API rejects", async () =>
    {
        const writeText = vi.fn().mockRejectedValue(new Error("denied"));
        Object.assign(navigator, {clipboard: {writeText}});
        const execCommand = vi.fn().mockReturnValue(true);
        Object.assign(document, {execCommand});

        render(
            <AuditEventDetail
                event={event}
                open={true}
                onDismiss={vi.fn()}
            />
        );

        const copyButton = document.getElementById("button-audit-event-detail-actor-copy") as HTMLButtonElement;
        fireEvent.click(copyButton);

        await waitFor(() => expect(execCommand).toHaveBeenCalledWith("copy"));
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
