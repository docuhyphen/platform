import {describe, expect, it} from "vitest";
import {formatWorkflowOverdueDuration} from "../workflowOverdueLabel.ts";

describe("formatWorkflowOverdueDuration", () =>
{
    const dueAt = new Date("2026-07-10T12:00:00.000Z");

    it("describes an overdue period under 24 hours without showing zero days", () =>
    {
        const now = new Date("2026-07-11T11:59:59.999Z");

        expect(formatWorkflowOverdueDuration(dueAt, now)).toBe("less than 1 day");
    });

    it("uses the singular label for one full overdue day", () =>
    {
        const now = new Date("2026-07-11T12:00:00.000Z");

        expect(formatWorkflowOverdueDuration(dueAt, now)).toBe("1 day");
    });

    it("uses the plural label for multiple full overdue days", () =>
    {
        const now = new Date("2026-07-12T12:00:00.000Z");

        expect(formatWorkflowOverdueDuration(dueAt, now)).toBe("2 days");
    });
});
