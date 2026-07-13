/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import AuditExportRequestDialog from "../AuditExportRequestDialog.tsx";
import {dateOnlyToRangeEndInstant, dateOnlyToRangeStartInstant} from "../../../auditDateRange.ts";

afterEach(cleanup);

describe("AuditExportRequestDialog", () =>
{
    it("submits selected categories with a half-open date range", async () =>
    {
        const onSubmit = vi.fn();
        render(
            <AuditExportRequestDialog
                open
                submitting={false}
                error={null}
                onDismiss={vi.fn()}
                onSubmit={onSubmit}
            />
        );
        fireEvent.change(document.getElementById("audit-export-occurred-after")!, {
            target: {value: "2024-03-10"},
        });
        fireEvent.change(document.getElementById("audit-export-occurred-before")!, {
            target: {value: "2024-03-10"},
        });
        fireEvent.change(document.getElementById("audit-export-purpose")!, {
            target: {value: "investigation"},
        });
        fireEvent.click(document.getElementById("audit-export-categories")!);
        fireEvent.click(await screen.findByText("Security"));

        const submit = document.getElementById("button-audit-export-request-submit") as HTMLButtonElement;
        await waitFor(() => expect(submit.disabled).toBe(false));
        fireEvent.click(submit);

        expect(onSubmit).toHaveBeenCalledWith({
            categories: ["SECURITY"],
            occurredAfter: dateOnlyToRangeStartInstant("2024-03-10"),
            occurredBefore: dateOnlyToRangeEndInstant("2024-03-10"),
            purpose: "investigation",
            caseReference: undefined,
            legalBasis: undefined,
        });
    });
});
