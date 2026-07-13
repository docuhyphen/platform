import {describe, expect, it} from "vitest";
import {dateOnlyToRangeEndInstant, dateOnlyToRangeStartInstant} from "../auditDateRange.ts";

describe("auditDateRange", () =>
{
    it("converts a date-only value to the local start-of-day instant", () =>
    {
        const instant = dateOnlyToRangeStartInstant("2024-03-10");
        const parsed = new Date(instant);

        expect(parsed.getFullYear()).toBe(2024);
        expect(parsed.getMonth()).toBe(2);
        expect(parsed.getDate()).toBe(10);
        expect(parsed.getHours()).toBe(0);
        expect(parsed.getMinutes()).toBe(0);
        expect(parsed.getSeconds()).toBe(0);
        expect(parsed.getMilliseconds()).toBe(0);
    });

    it("converts a date-only value to the exclusive start of the next local day", () =>
    {
        const instant = dateOnlyToRangeEndInstant("2024-03-10");
        const parsed = new Date(instant);

        expect(parsed.getFullYear()).toBe(2024);
        expect(parsed.getMonth()).toBe(2);
        expect(parsed.getDate()).toBe(11);
        expect(parsed.getHours()).toBe(0);
        expect(parsed.getMinutes()).toBe(0);
        expect(parsed.getSeconds()).toBe(0);
        expect(parsed.getMilliseconds()).toBe(0);
    });

    it("rolls the end-of-day instant over month and year boundaries", () =>
    {
        const instant = dateOnlyToRangeEndInstant("2024-12-31");
        const parsed = new Date(instant);

        expect(parsed.getFullYear()).toBe(2025);
        expect(parsed.getMonth()).toBe(0);
        expect(parsed.getDate()).toBe(1);
        expect(parsed.getHours()).toBe(0);
        expect(parsed.getMinutes()).toBe(0);
    });

    it("produces a start instant strictly before the end instant for the same date", () =>
    {
        const start = new Date(dateOnlyToRangeStartInstant("2024-03-10")).getTime();
        const end = new Date(dateOnlyToRangeEndInstant("2024-03-10")).getTime();

        expect(start).toBeLessThan(end);
    });
});
