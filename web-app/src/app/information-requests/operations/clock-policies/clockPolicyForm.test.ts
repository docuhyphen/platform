import {describe, expect, it} from "vitest";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockType,
} from "../../../models/models.tsx";
import {ClockPolicyForm, clockPolicyProblems, definitionFromForm, formFromVersion} from "./clockPolicyForm.ts";

const businessForm: ClockPolicyForm = {
    clockType: InformationRequestClockType.BUSINESS,
    businessTimezone: "Africa/Johannesburg",
    workingDays: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
    workdayStart: "08:30",
    workdayEnd: "17:00",
    holidays: "2026-12-25, 2026-12-26",
    standardHours: "48",
    urgentHours: "8",
    reminderHours: "24, 4",
    escalationHours: "2",
    dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
};

describe("clockPolicyForm", () =>
{
    it("turns a business-hours form into minutes, working periods, and holidays", () =>
    {
        expect(clockPolicyProblems(businessForm)).toEqual({});
        expect(definitionFromForm(businessForm)).toEqual({
            clockType: InformationRequestClockType.BUSINESS,
            businessTimezone: "Africa/Johannesburg",
            workingPeriods: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
                .map(dayOfWeek => ({dayOfWeek, startMinute: 510, endMinute: 1020})),
            holidays: ["2026-12-25", "2026-12-26"],
            standardDurationMinutes: 2880,
            urgentDurationMinutes: 480,
            reminderMinutesBeforeDue: [1440, 240],
            escalationAfterMinutes: 120,
            dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
        });
    });

    it("sends a calendar clock without working periods or holidays", () =>
    {
        const definition = definitionFromForm({
            ...businessForm,
            clockType: InformationRequestClockType.CALENDAR,
            escalationHours: "",
            reminderHours: "",
            dueEffect: InformationRequestClockDueEffect.EXPIRE_REQUEST,
        });

        expect(definition).toEqual({
            clockType: InformationRequestClockType.CALENDAR,
            businessTimezone: "Africa/Johannesburg",
            workingPeriods: [],
            holidays: [],
            standardDurationMinutes: 2880,
            urgentDurationMinutes: 480,
            reminderMinutesBeforeDue: [],
            dueEffect: InformationRequestClockDueEffect.EXPIRE_REQUEST,
        });
    });

    it("names each problem the server would refuse and sends nothing", () =>
    {
        const form: ClockPolicyForm = {
            ...businessForm,
            businessTimezone: "Nowhere/Invalid",
            workingDays: [],
            workdayStart: "17:00",
            workdayEnd: "09:00",
            holidays: "25/12/2026",
            standardHours: "4",
            urgentHours: "8",
            reminderHours: "4, 4",
            escalationHours: "-1",
        };

        expect(Object.keys(clockPolicyProblems(form)).sort()).toEqual([
            "businessTimezone",
            "escalationHours",
            "holidays",
            "reminderHours",
            "urgentHours",
            "workdayEnd",
            "workingDays",
        ]);
        expect(definitionFromForm(form)).toBeNull();
    });

    it("reads a published version back into the form", () =>
    {
        expect(formFromVersion({
            id: "version-a",
            versionNumber: 2,
            clockType: InformationRequestClockType.BUSINESS,
            businessTimezone: "Africa/Johannesburg",
            standardDurationMinutes: 2880,
            urgentDurationMinutes: 480,
            escalationAfterMinutes: 120,
            dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
            workingPeriods: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
                .map(dayOfWeek => ({dayOfWeek, startMinute: 510, endMinute: 1020})),
            holidays: ["2026-12-25", "2026-12-26"],
            reminderMinutesBeforeDue: [1440, 240],
            publishedAt: "2026-09-01T08:00:00Z",
        })).toEqual(businessForm);
    });
});
