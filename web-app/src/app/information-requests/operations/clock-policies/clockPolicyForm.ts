import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyDefinitionRequest,
    InformationRequestClockPolicyVersionDto,
    InformationRequestClockType,
    InformationRequestWorkingPeriodDto,
} from "../../../models/models.tsx";

export interface ClockPolicyPeriodForm {
    dayOfWeek: string;
    start: string;
    end: string;
    untilMidnight: boolean;
}

export interface ClockPolicyForm
{
    clockType: InformationRequestClockType;
    businessTimezone: string;
    workingPeriods: ClockPolicyPeriodForm[];
    holidays: string;
    standardHours: string;
    urgentHours: string;
    reminderHours: string;
    escalationHours: string;
    dueEffect: InformationRequestClockDueEffect;
    reminderCommunicationId?: string;
    overdueCommunicationId?: string;
}

export type ClockPolicyNoticeField = "reminderCommunicationId" | "overdueCommunicationId";

export type ClockPolicyProblems = Partial<Record<keyof ClockPolicyForm, string>>;

export interface ClockPolicyPeriodProblems {
    start?: string;
    end?: string;
}

export const WEEK_DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

const DEFAULT_START = "09:00";
const DEFAULT_END = "17:00";
const END_OF_DAY = 1440;
const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export const workingPeriodOn = (dayOfWeek: string, start = DEFAULT_START, end = DEFAULT_END, untilMidnight = false): ClockPolicyPeriodForm =>
    ({dayOfWeek, start, end, untilMidnight});

const defaultWorkingPeriods = (): ClockPolicyPeriodForm[] => WEEK_DAYS.slice(0, 5).map(day => workingPeriodOn(day));

export const nextWorkingPeriod = (periods: ClockPolicyPeriodForm[]): ClockPolicyPeriodForm => {
    const last = periods[periods.length - 1];
    if (!last) return workingPeriodOn(WEEK_DAYS[0]);
    return {...last, dayOfWeek: WEEK_DAYS[(WEEK_DAYS.indexOf(last.dayOfWeek) + 1) % WEEK_DAYS.length]};
};

export const defaultClockPolicyForm = (businessTimezone: string): ClockPolicyForm => ({
    clockType: InformationRequestClockType.CALENDAR,
    businessTimezone,
    workingPeriods: defaultWorkingPeriods(),
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

const periodFromVersion = (period: InformationRequestWorkingPeriodDto): ClockPolicyPeriodForm =>
    period.endMinute >= END_OF_DAY
        ? workingPeriodOn(period.dayOfWeek, timeText(period.startMinute), "", true)
        : workingPeriodOn(period.dayOfWeek, timeText(period.startMinute), timeText(period.endMinute));

export const formFromVersion = (version: InformationRequestClockPolicyVersionDto): ClockPolicyForm =>
{
    const escalation = version.escalationAfterMinutes;
    return {
        clockType: version.clockType,
        businessTimezone: version.businessTimezone,
        workingPeriods: version.workingPeriods.length > 0 ? version.workingPeriods.map(periodFromVersion) : defaultWorkingPeriods(),
        holidays: version.holidays.join(", "),
        standardHours: hoursText(version.standardDurationMinutes),
        urgentHours: hoursText(version.urgentDurationMinutes),
        reminderHours: version.reminderMinutesBeforeDue.map(hoursText).join(", "),
        escalationHours: escalation === undefined || escalation === null ? "" : hoursText(escalation),
        dueEffect: version.dueEffect,
        reminderCommunicationId: version.reminderCommunicationId ?? undefined,
        overdueCommunicationId: version.overdueCommunicationId ?? undefined,
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

const startMinuteOf = (period: ClockPolicyPeriodForm): number | null => minuteOfDay(period.start);

const endMinuteOf = (period: ClockPolicyPeriodForm): number | null =>
    period.untilMidnight ? END_OF_DAY : minuteOfDay(period.end);

const overlaps = (periods: ClockPolicyPeriodForm[], index: number): boolean => {
    const period = periods[index];
    const start = startMinuteOf(period);
    const end = endMinuteOf(period);
    if (start === null || end === null || end <= start) return false;
    return periods.some((other, otherIndex) => {
        const otherStart = startMinuteOf(other);
        const otherEnd = endMinuteOf(other);
        return otherIndex !== index && other.dayOfWeek === period.dayOfWeek && otherStart !== null && otherEnd !== null
            && otherStart < end && start < otherEnd;
    });
};

export const workingPeriodProblems = (periods: ClockPolicyPeriodForm[]): ClockPolicyPeriodProblems[] =>
    periods.map((period, index) => {
        const start = startMinuteOf(period);
        const end = endMinuteOf(period);
        const problems: ClockPolicyPeriodProblems = {};
        if (start === null) problems.start = "Enter a time such as 09:00.";
        else if (overlaps(periods, index)) problems.start = "Overlaps another period on the same day.";
        if (end === null) problems.end = "Enter a time such as 17:00, or choose Until midnight.";
        else if (start !== null && end <= start) problems.end = "Ends after it starts. Choose Until midnight to end with the day.";
        return problems;
    });

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

const workingPeriodsProblem = (periods: ClockPolicyPeriodForm[]): string | undefined => {
    if (periods.length === 0) return "Add at least one working period.";
    return workingPeriodProblems(periods).some(problems => problems.start || problems.end)
        ? "Correct the marked working periods."
        : undefined;
};

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
        const periods = workingPeriodsProblem(form.workingPeriods);
        if (periods) problems.workingPeriods = periods;
        if (listOf(form.holidays).some(holiday => !validDate(holiday))) problems.holidays = "List dates such as 2026-12-25, separated by commas.";
    }
    return problems;
};

const workingPeriodsFrom = (periods: ClockPolicyPeriodForm[]): InformationRequestWorkingPeriodDto[] =>
    periods
        .map(period => ({
            dayOfWeek: period.dayOfWeek,
            startMinute: startMinuteOf(period) ?? 0,
            endMinute: endMinuteOf(period) ?? 0,
        }))
        .sort((first, second) =>
            WEEK_DAYS.indexOf(first.dayOfWeek) - WEEK_DAYS.indexOf(second.dayOfWeek) || first.startMinute - second.startMinute);

export const definitionFromForm = (form: ClockPolicyForm): InformationRequestClockPolicyDefinitionRequest | null =>
{
    if (Object.keys(clockPolicyProblems(form)).length > 0) return null;
    const business = form.clockType === InformationRequestClockType.BUSINESS;
    const escalation = minutesOf(form.escalationHours);
    return {
        clockType: form.clockType,
        businessTimezone: form.businessTimezone.trim(),
        workingPeriods: business ? workingPeriodsFrom(form.workingPeriods) : [],
        holidays: business ? listOf(form.holidays) : [],
        standardDurationMinutes: minutesOf(form.standardHours) ?? 0,
        urgentDurationMinutes: minutesOf(form.urgentHours) ?? 0,
        reminderMinutesBeforeDue: listOf(form.reminderHours).map(hours => minutesOf(hours) ?? 0),
        ...(escalation !== null ? {escalationAfterMinutes: escalation} : {}),
        dueEffect: form.dueEffect,
        ...(form.reminderCommunicationId ? {reminderCommunicationId: form.reminderCommunicationId} : {}),
        ...(form.overdueCommunicationId ? {overdueCommunicationId: form.overdueCommunicationId} : {}),
    };
};
