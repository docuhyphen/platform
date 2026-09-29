import {describe, expect, it} from "vitest";
import {
    formatInformationRequestCount,
    formatInformationRequestSize,
    formatInformationRequestTime,
} from "./informationRequestFormatting.ts";

const plain = (text: string): string => text.replace(/\u202f/g, " ");

describe("Information Request formatting", () =>
{
    it("states a time in the viewer's timezone with the zone named", () =>
    {
        expect(plain(formatInformationRequestTime("2026-09-27T08:30:00Z", {locale: "en-US", timeZone: "UTC"})))
            .toBe("Sep 27, 2026, 8:30 AM UTC");
        expect(plain(formatInformationRequestTime("2026-09-27T08:30:00Z", {locale: "en-US", timeZone: "America/New_York"})))
            .toBe("Sep 27, 2026, 4:30 AM EDT");
        expect(formatInformationRequestTime(undefined, {locale: "en-US", timeZone: "UTC"})).toBe("");
    });

    it("states counts and sizes by locale", () =>
    {
        expect(formatInformationRequestCount(12345, "en-US")).toBe("12,345");
        expect(formatInformationRequestCount(12345, "de-DE")).toBe("12.345");
        expect(formatInformationRequestSize(512, "en-US")).toBe("512 bytes");
        expect(formatInformationRequestSize(1536, "en-US")).toBe("1.5 KB");
        expect(formatInformationRequestSize(5 * 1024 * 1024, "de-DE")).toBe("5 MB");
        expect(formatInformationRequestSize(2.25 * 1024 * 1024 * 1024, "en-US")).toBe("2.3 GB");
    });
});
