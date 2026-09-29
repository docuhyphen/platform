import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyDefinitionRequest,
    InformationRequestClockPolicyVersionDto,
    InformationRequestClockType,
} from "../../../models/models.tsx";

export interface ClockPolicyForm
{
    clockType: InformationRequestClockType;
    businessTimezone: string;
    workingDays: string[];
    workdayStart: string;
    workdayEnd: string;
    holidays: string;
    standardHours: string;
    urgentHours: string;
    reminderHours: string;
    escalationHours: string;
    dueEffect: InformationRequestClockDueEffect;
}

export type ClockPolicyProblems = Partial<Record<keyof ClockPolicyForm, string>>;

export const WEEK_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

const DEFAULT_START = "09:00";
const DEFAULT_END = "17:00";
const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export const defaultClockPolicyForm = (businessTimezone: string): ClockPolicyForm => ({
    clockType: InformationRequestClockType.CALENDAR,
    businessTimezone,
    workingDays: WEEK_DAYS.slice(0, 5),
    workdayStart: DEFAULT_START,
    workdayEnd: DEFAULT_END,
    holidays: "",
    standardHours: "",
    urgentHours: "",
    reminderHours: "",
    escalationHours: "",
    dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
});

export const hoursText = (minutes: number): string => String(Math.round((minutes / 60) * 100) / 100);

const timeText = (minute: number): string =>
    `${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;

export const formFromVersion = (version: InformationRequestClockPolicyVersionDto): ClockPolicyForm =>
{
    const first = version.workingPeriods[0];
    const days = new Set(version.workingPeriods.map(period => period.dayOfWeek));
    return {
        clockType: version.clockType,
        businessTimezone: version.businessTimezone,
        workingDays: first ? WEEK_DAYS.filter(day => days.has(day)) : WEEK_DAYS.slice(0, 5),
        workdayStart: first ? timeText(first.startMinute) : DEFAULT_START,
        workdayEnd: first ? timeText(first.endMinute) : DEFAULT_END,
        holidays: version.holidays.join(", "),
        standardHours: hoursText(version.standardDurationMinutes),
        urgentHours: hoursText(version.urgentDurationMinutes),
        reminderHours: version.reminderMinutesBeforeDue.map(hoursText).join(", "),
        escalationHours: version.escalationAfterMinutes === undefined ? "" : hoursText(version.escalationAfterMinutes),
        dueEffect: version.dueEffect,
    };
};

const minutesOf = (hours: string): number | null =>
{
    const value = Number(hours.trim());
    return hours.trim() && Number.isFinite(value) ? Math.round(value * 60) : null;
};

const minuteOfDay = (time: string): number | null =>
{
    const match = TIME.exec(time.trim());
    return match ? Number(match[1]) * 60 + Number(match[2]) : null;
};

const listOf = (text: string): string[] => text.split(",").map(part => part.trim()).filter(Boolean);

const validZone = (zone: string): boolean =>
{
    try
    {
        new Intl.DateTimeFormat("en", {timeZone: zone.trim()});
        return zone.trim().length > 0;
    }
    catch
    {
        return false;
    }
};

const validDate = (value: string): boolean => DATE.test(value) && !Number.isNaN(new Date(`${value}T00:00:00Z`).getTime())
    && new Date(`${value}T00:00:00Z`).toISOString().startsWith(value);

export const clockPolicyProblems = (form: ClockPolicyForm): ClockPolicyProblems =>
{
    const problems: ClockPolicyProblems = {};
    const standard = minutesOf(form.standardHours);
    const urgent = minutesOf(form.urgentHours);
    const reminders = listOf(form.reminderHours).map(minutesOf);
    const escalation = minutesOf(form.escalationHours);
    if (!validZone(form.businessTimezone)) problems.businessTimezone = "Name a time zone such as Europe/London.";
    if (standard === null || standard <= 0) problems.standardHours = "Enter the hours a request normally has.";
    if (urgent === null || urgent <= 0) problems.urgentHours = "Enter the hours an urgent request has.";
    else if (standard !== null && urgent > standard) problems.urgentHours = "An urgent request has no longer than a standard one.";
    if (reminders.some(minutes => minutes === null || minutes <= 0) || new Set(reminders).size !== reminders.length)
    {
        problems.reminderHours = "List different positive hours, separated by commas.";
    }
    if (form.escalationHours.trim() && (escalation === null || escalation < 0)) problems.escalationHours = "Enter zero or more hours.";
    if (form.clockType === InformationRequestClockType.BUSINESS)
    {
        const start = minuteOfDay(form.workdayStart);
        const end = minuteOfDay(form.workdayEnd);
        if (form.workingDays.length === 0) problems.workingDays = "Choose at least one working day.";
        if (start === null) problems.workdayStart = "Enter a time such as 09:00.";
        if (end === null || (start !== null && end <= start)) problems.workdayEnd = "The working day ends after it starts.";
        if (listOf(form.holidays).some(holiday => !validDate(holiday))) problems.holidays = "List dates such as 2026-12-25, separated by commas.";
    }
    return problems;
};

export const definitionFromForm = (form: ClockPolicyForm): InformationRequestClockPolicyDefinitionRequest | null =>
{
    if (Object.keys(clockPolicyProblems(form)).length > 0) return null;
    const business = form.clockType === InformationRequestClockType.BUSINESS;
    const startMinute = minuteOfDay(form.workdayStart) ?? 0;
    const endMinute = minuteOfDay(form.workdayEnd) ?? 0;
    const escalation = minutesOf(form.escalationHours);
    return {
        clockType: form.clockType,
        businessTimezone: form.businessTimezone.trim(),
        workingPeriods: business
            ? WEEK_DAYS.filter(day => form.workingDays.includes(day)).map(dayOfWeek => ({dayOfWeek, startMinute, endMinute}))
            : [],
        holidays: business ? listOf(form.holidays) : [],
        standardDurationMinutes: minutesOf(form.standardHours) ?? 0,
        urgentDurationMinutes: minutesOf(form.urgentHours) ?? 0,
        reminderMinutesBeforeDue: listOf(form.reminderHours).map(hours => minutesOf(hours) ?? 0),
        ...(escalation !== null ? {escalationAfterMinutes: escalation} : {}),
        dueEffect: form.dueEffect,
    };
};
