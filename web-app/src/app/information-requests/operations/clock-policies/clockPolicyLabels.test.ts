import {describe, expect, it} from "vitest";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyVersionDto,
    InformationRequestClockType,
} from "../../../models/models.tsx";
import {clockPolicySentence} from "./clockPolicyLabels.ts";

const version = (escalationAfterMinutes?: number | null): InformationRequestClockPolicyVersionDto => ({
    id: "version-a",
    versionNumber: 1,
    clockType: InformationRequestClockType.CALENDAR,
    businessTimezone: "UTC",
    standardDurationMinutes: 1440,
    urgentDurationMinutes: 240,
    escalationAfterMinutes,
    dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
    workingPeriods: [],
    holidays: [],
    reminderMinutesBeforeDue: [],
    publishedAt: Date.parse("2026-09-01T08:00:00Z"),
});

describe("clockPolicySentence", () => {
    it("states escalation only for a version that escalates, including at the due time", () => {
        expect(clockPolicySentence(version(null))).toBe(
            "Calendar time in UTC. Standard 24 hours, urgent 4 hours. When due, the request is marked overdue.",
        );
        expect(clockPolicySentence(version(undefined))).not.toContain("Escalated");
        expect(clockPolicySentence(version(0))).toContain("Escalated 0 hours after due.");
        expect(clockPolicySentence(version(120))).toContain("Escalated 2 hours after due.");
    });
});
