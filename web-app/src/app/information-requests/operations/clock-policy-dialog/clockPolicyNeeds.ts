import {ClockPolicyForm, ClockPolicyProblems} from "../clock-policies/clockPolicyForm.ts";

const FIELD_LABELS: Record<keyof ClockPolicyForm, string> = {
    clockType: "Clock",
    businessTimezone: "Time zone",
    workingPeriods: "Working hours",
    holidays: "Holidays",
    standardHours: "Standard hours",
    urgentHours: "Urgent hours",
    reminderHours: "Reminders",
    escalationHours: "Escalate after",
    dueEffect: "When due",
    reminderCommunicationId: "Reminder notice",
    overdueCommunicationId: "Overdue notice",
};

const listed = (items: string[]): string =>
    items.length <= 2 ? items.join(" and ") : `${items.slice(0, -1).join(", ")}, and ${items[items.length - 1]}`;

export const clockPolicyNeeds = (action: string, missing: string[], problems: ClockPolicyProblems): string | null => {
    const fields = Object.keys(problems).filter((field): field is keyof ClockPolicyForm => field in FIELD_LABELS);
    const needed = [...missing, ...fields.map(field => FIELD_LABELS[field])];
    return needed.length > 0 ? `${action} needs ${listed(needed)}.` : null;
};
