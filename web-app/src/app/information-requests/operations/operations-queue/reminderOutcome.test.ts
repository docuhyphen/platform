import {describe, expect, it} from "vitest";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {reminderOutcomeSentence} from "./reminderOutcome.ts";

describe("reminderOutcomeSentence", () =>
{
    it("counts the notices queued for the requests that were reminded", () =>
    {
        expect(reminderOutcomeSentence([
            {requestId: "request-a", noticeCount: 2},
            {requestId: "request-b", noticeCount: 1},
        ])).toBe("3 reminder notices were queued for 2 requests.");
    });

    it("says which requests were reminded recently and when they can be reminded again", () =>
    {
        const later = "2026-10-01T12:00:00Z";

        expect(reminderOutcomeSentence([
            {requestId: "request-a", noticeCount: 1},
            {requestId: "request-b", noticeCount: 0, cooldownUntil: "2026-10-01T09:00:00Z"},
            {requestId: "request-c", noticeCount: 0, cooldownUntil: later},
        ])).toBe(
            "1 reminder notice was queued for 1 request. 2 requests were reminded recently and can be reminded again from "
            + `${formatInformationRequestTime(later)}.`,
        );
    });
});
