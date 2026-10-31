import {describe, expect, it} from "vitest";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyVersionDto,
    InformationRequestClockType,
    InformationRequestWorkingPeriodDto,
} from "../../../models/models.tsx";
import {
    clockPolicyProblems,
    definitionFromForm,
    formFromVersion,
    nextWorkingPeriod,
    workingPeriodOn,
    workingPeriodProblems,
} from "./clockPolicyForm.ts";

const versionWith = (workingPeriods: InformationRequestWorkingPeriodDto[]): InformationRequestClockPolicyVersionDto => ({
    id: "calendar-version",
    versionNumber: 1,
    clockType: InformationRequestClockType.BUSINESS,
    businessTimezone: "Europe/Berlin",
    standardDurationMinutes: 480,
    urgentDurationMinutes: 120,
    escalationAfterMinutes: null,
    dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
    workingPeriods,
    holidays: [],
    reminderMinutesBeforeDue: [],
    reminderCommunicationId: null,
    overdueCommunicationId: null,
    publishedAt: Date.parse("2026-03-01T08:00:00Z"),
});

const calendars: Record<string, InformationRequestWorkingPeriodDto[]> = {
    "a uniform working week": ["MONDAY", "FRIDAY"].map(dayOfWeek => ({dayOfWeek, startMinute: 540, endMinute: 1020})),
    "two working periods on one day": [
        {dayOfWeek: "MONDAY", startMinute: 540, endMinute: 720},
        {dayOfWeek: "MONDAY", startMinute: 780, endMinute: 1020},
    ],
    "different hours on different days": [
        {dayOfWeek: "MONDAY", startMinute: 540, endMinute: 1020},
        {dayOfWeek: "FRIDAY", startMinute: 540, endMinute: 780},
    ],
    "a whole day": [{dayOfWeek: "MONDAY", startMinute: 0, endMinute: 1440}],
    "a shorter period ending at midnight": [
        {dayOfWeek: "SATURDAY", startMinute: 1020, endMinute: 1440},
        {dayOfWeek: "SUNDAY", startMinute: 0, endMinute: 240},
    ],
    "adjacent periods": [
        {dayOfWeek: "WEDNESDAY", startMinute: 540, endMinute: 720},
        {dayOfWeek: "WEDNESDAY", startMinute: 720, endMinute: 1020},
    ],
};

describe("clock policy working periods", () => {
    for (const [shape, workingPeriods] of Object.entries(calendars)) {
        it(`keeps ${shape} exactly when only the urgent hours change`, () => {
            const form = formFromVersion(versionWith(workingPeriods));

            expect(clockPolicyProblems(form)).toEqual({});
            const definition = definitionFromForm({...form, urgentHours: "1"});
            expect(definition?.urgentDurationMinutes).toBe(60);
            expect(definition?.workingPeriods).toEqual(workingPeriods);
        });
    }

    it("reads an end of day as Until midnight and sends it back as the end of the day", () => {
        const form = formFromVersion(versionWith(calendars["a whole day"]));

        expect(form.workingPeriods).toEqual([workingPeriodOn("MONDAY", "00:00", "", true)]);
        expect(definitionFromForm(form)?.workingPeriods).toEqual([{
            dayOfWeek: "MONDAY",
            startMinute: 0,
            endMinute: 1440
        }]);
    });

    it("sends the periods in week order whatever order they were added in", () => {
        const form = {
            ...formFromVersion(versionWith(calendars["a uniform working week"])),
            workingPeriods: [
                workingPeriodOn("FRIDAY", "13:00", "17:00"),
                workingPeriodOn("MONDAY", "13:00", "17:00"),
                workingPeriodOn("FRIDAY", "09:00", "12:00"),
            ],
        };

        expect(definitionFromForm(form)?.workingPeriods).toEqual([
            {dayOfWeek: "MONDAY", startMinute: 780, endMinute: 1020},
            {dayOfWeek: "FRIDAY", startMinute: 540, endMinute: 720},
            {dayOfWeek: "FRIDAY", startMinute: 780, endMinute: 1020},
        ]);
    });

    it("names each period that cannot be sent and refuses an empty working week", () => {
        const periods = [
            workingPeriodOn("MONDAY", "", "12:00"),
            workingPeriodOn("MONDAY", "13:00", "00:00"),
            workingPeriodOn("TUESDAY", "09:00", "12:00"),
            workingPeriodOn("TUESDAY", "11:00", "", true),
            workingPeriodOn("WEDNESDAY", "09:00", ""),
            workingPeriodOn("THURSDAY", "17:00", "09:00"),
        ];
        const base = formFromVersion(versionWith(calendars["a uniform working week"]));

        expect(workingPeriodProblems(periods)).toEqual([
            {start: "Enter a time such as 09:00."},
            {end: "Ends after it starts. Choose Until midnight to end with the day."},
            {start: "Overlaps another period on the same day."},
            {start: "Overlaps another period on the same day."},
            {end: "Enter a time such as 17:00, or choose Until midnight."},
            {end: "Ends after it starts. Choose Until midnight to end with the day."},
        ]);
        expect(clockPolicyProblems({
            ...base,
            workingPeriods: periods
        }).workingPeriods).toBe("Correct the marked working periods.");
        expect(definitionFromForm({...base, workingPeriods: periods})).toBeNull();
        expect(clockPolicyProblems({
            ...base,
            workingPeriods: []
        }).workingPeriods).toBe("Add at least one working period.");
        expect(clockPolicyProblems({
            ...base,
            clockType: InformationRequestClockType.CALENDAR,
            workingPeriods: []
        })).toEqual({});
    });

    it("adds the next period on the following day with the same hours", () => {
        expect(nextWorkingPeriod([])).toEqual(workingPeriodOn("MONDAY"));
        expect(nextWorkingPeriod([workingPeriodOn("MONDAY", "08:00", "12:00")])).toEqual(workingPeriodOn("TUESDAY", "08:00", "12:00"));
        expect(nextWorkingPeriod([workingPeriodOn("SUNDAY", "10:00", "", true)])).toEqual(workingPeriodOn("MONDAY", "10:00", "", true));
    });
});
