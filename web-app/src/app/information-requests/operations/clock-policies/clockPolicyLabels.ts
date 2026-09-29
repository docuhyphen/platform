import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyVersionDto,
    InformationRequestClockType,
} from "../../../models/models.tsx";
import {hoursText} from "./clockPolicyForm.ts";

export const clockTypeLabels: Record<InformationRequestClockType, string> = {
    [InformationRequestClockType.CALENDAR]: "Calendar time",
    [InformationRequestClockType.BUSINESS]: "Business hours",
};

export const dueEffectLabels: Record<InformationRequestClockDueEffect, string> = {
    [InformationRequestClockDueEffect.MARK_OVERDUE]: "Mark the request overdue",
    [InformationRequestClockDueEffect.EXPIRE_REQUEST]: "Expire the request",
};

const dueEffectSentences: Record<InformationRequestClockDueEffect, string> = {
    [InformationRequestClockDueEffect.MARK_OVERDUE]: "When due, the request is marked overdue.",
    [InformationRequestClockDueEffect.EXPIRE_REQUEST]: "When due, the request expires.",
};

export const dayLabels: Record<string, string> = {
    MONDAY: "Monday",
    TUESDAY: "Tuesday",
    WEDNESDAY: "Wednesday",
    THURSDAY: "Thursday",
    FRIDAY: "Friday",
    SATURDAY: "Saturday",
    SUNDAY: "Sunday",
};

export const hoursPhrase = (minutes: number): string => `${hoursText(minutes)} ${minutes === 60 ? "hour" : "hours"}`;

const listed = (items: string[]): string =>
    items.length <= 1 ? items.join("") : `${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`;

export const clockPolicySentence = (version: InformationRequestClockPolicyVersionDto): string =>
{
    const reminders = version.reminderMinutesBeforeDue.map(hoursPhrase);
    return [
        `${clockTypeLabels[version.clockType]} in ${version.businessTimezone}.`,
        `Standard ${hoursPhrase(version.standardDurationMinutes)}, urgent ${hoursPhrase(version.urgentDurationMinutes)}.`,
        reminders.length > 0 ? `${reminders.length === 1 ? "Reminder" : "Reminders"} ${listed(reminders)} before due.` : "",
        version.escalationAfterMinutes !== undefined ? `Escalated ${hoursPhrase(version.escalationAfterMinutes)} after due.` : "",
        dueEffectSentences[version.dueEffect],
    ].filter(Boolean).join(" ");
};

export const latestVersion = <T extends {versionNumber: number}>(versions: T[]): T | undefined =>
    [...versions].sort((first, second) => second.versionNumber - first.versionNumber)[0];
