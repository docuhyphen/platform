import {describe, expect, it} from "vitest";
import {formatHoursAsDuration, formatMinutesAsDuration} from "./securitySessionDuration.ts";

describe("security session duration formatting", () =>
{
    it("uses minutes for short durations", () =>
    {
        expect(formatMinutesAsDuration(5)).toBe("5 minutes");
        expect(formatMinutesAsDuration(30)).toBe("30 minutes");
    });

    it("uses hours and days for longer durations", () =>
    {
        expect(formatMinutesAsDuration(60)).toBe("1 hour");
        expect(formatMinutesAsDuration(1_440)).toBe("1 day");
        expect(formatMinutesAsDuration(43_200)).toBe("30 days");
        expect(formatHoursAsDuration(168)).toBe("7 days");
    });

    it("preserves mixed hour and minute durations", () =>
    {
        expect(formatMinutesAsDuration(90)).toBe("1 hour 30 minutes");
    });
});
