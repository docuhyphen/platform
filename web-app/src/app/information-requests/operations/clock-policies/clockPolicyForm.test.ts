import {describe, expect, it} from "vitest";
import {InformationRequestClockDueEffect, InformationRequestClockType,} from "../../../models/models.tsx";
import {
    ClockPolicyForm,
    clockPolicyProblems,
    defaultClockPolicyForm,
    definitionFromForm,
    formFromVersion,
    workingPeriodOn,
} from "./clockPolicyForm.ts";

const businessForm: ClockPolicyForm = {
    clockType: InformationRequestClockType.BUSINESS,
    businessTimezone: "Africa/Johannesburg",
    workingPeriods: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"].map(day => workingPeriodOn(day, "08:30", "17:00")),
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
            workingPeriods: [],
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
            "workingPeriods",
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
            publishedAt: Date.parse("2026-09-01T08:00:00Z"),
        })).toEqual(businessForm);
    });

    it("carries the reminder and overdue Communications of a published version into the next one", () => {
        const form = formFromVersion({
            id: "version-b",
            versionNumber: 3,
            clockType: InformationRequestClockType.CALENDAR,
            businessTimezone: "UTC",
            standardDurationMinutes: 1440,
            urgentDurationMinutes: 240,
            dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
            workingPeriods: [],
            holidays: [],
            reminderMinutesBeforeDue: [60],
            reminderCommunicationId: "communication-reminder",
            overdueCommunicationId: "communication-overdue",
            publishedAt: Date.parse("2026-09-02T08:00:00Z"),
        });

        expect(form.reminderCommunicationId).toBe("communication-reminder");
        expect(form.overdueCommunicationId).toBe("communication-overdue");
        expect(definitionFromForm(form)).toEqual(expect.objectContaining({
            reminderCommunicationId: "communication-reminder",
            overdueCommunicationId: "communication-overdue",
        }));
        expect(definitionFromForm({
            ...form,
            overdueCommunicationId: undefined
        })).not.toHaveProperty("overdueCommunicationId");
    });

    it("keeps escalation off and the platform wording when the server sends a version's unset values as null", () => {
        const form = formFromVersion({
            id: "version-c",
            versionNumber: 4,
            clockType: InformationRequestClockType.CALENDAR,
            businessTimezone: "UTC",
            standardDurationMinutes: 1440,
            urgentDurationMinutes: 240,
            escalationAfterMinutes: null,
            dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
            workingPeriods: [],
            holidays: [],
            reminderMinutesBeforeDue: [],
            reminderCommunicationId: null,
            overdueCommunicationId: null,
            publishedAt: Date.parse("2026-09-03T08:00:00Z"),
        });

        expect(form.escalationHours).toBe("");
        expect(form.reminderCommunicationId).toBeUndefined();
        expect(form.overdueCommunicationId).toBeUndefined();
        const definition = definitionFromForm({...form, urgentHours: "2"});
        expect(definition).toEqual(expect.objectContaining({urgentDurationMinutes: 120}));
        expect(definition).not.toHaveProperty("escalationAfterMinutes");
        expect(definition).not.toHaveProperty("reminderCommunicationId");
        expect(definition).not.toHaveProperty("overdueCommunicationId");
    });

    it("keeps escalation off for a version without escalation and for a new policy", () => {
        const form = formFromVersion({
            id: "version-e",
            versionNumber: 6,
            clockType: InformationRequestClockType.CALENDAR,
            businessTimezone: "UTC",
            standardDurationMinutes: 1440,
            urgentDurationMinutes: 240,
            dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
            workingPeriods: [],
            holidays: [],
            reminderMinutesBeforeDue: [],
            publishedAt: Date.parse("2026-09-05T08:00:00Z"),
        });
        const created = {...defaultClockPolicyForm("UTC"), standardHours: "24", urgentHours: "4"};

        expect(form.escalationHours).toBe("");
        expect(definitionFromForm(form)).not.toHaveProperty("escalationAfterMinutes");
        expect(definitionFromForm(created)).not.toHaveProperty("escalationAfterMinutes");
        expect(definitionFromForm({
            ...created,
            escalationHours: "2"
        })).toEqual(expect.objectContaining({escalationAfterMinutes: 120}));
    });

    it("keeps escalation at the due time for a version that escalates after zero minutes", () => {
        const form = formFromVersion({
            id: "version-d",
            versionNumber: 5,
            clockType: InformationRequestClockType.CALENDAR,
            businessTimezone: "UTC",
            standardDurationMinutes: 1440,
            urgentDurationMinutes: 240,
            escalationAfterMinutes: 0,
            dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
            workingPeriods: [],
            holidays: [],
            reminderMinutesBeforeDue: [],
            publishedAt: Date.parse("2026-09-04T08:00:00Z"),
        });

        expect(form.escalationHours).toBe("0");
        expect(definitionFromForm(form)).toEqual(expect.objectContaining({escalationAfterMinutes: 0}));
    });
});
