import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyVersionDto,
    InformationRequestClockType,
} from "../../../models/models.tsx";
import {formattedTime} from "../operationsLabels.ts";
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
    const escalation = version.escalationAfterMinutes;
    return [
        `${clockTypeLabels[version.clockType]} in ${version.businessTimezone}.`,
        `Standard ${hoursPhrase(version.standardDurationMinutes)}, urgent ${hoursPhrase(version.urgentDurationMinutes)}.`,
        reminders.length > 0 ? `${reminders.length === 1 ? "Reminder" : "Reminders"} ${listed(reminders)} before due.` : "",
        escalation !== undefined && escalation !== null ? `Escalated ${hoursPhrase(escalation)} after due.` : "",
        dueEffectSentences[version.dueEffect],
    ].filter(Boolean).join(" ");
};

export const latestVersion = <T extends {versionNumber: number}>(versions: T[]): T | undefined =>
    [...versions].sort((first, second) => second.versionNumber - first.versionNumber)[0];

const timeOfDay = (minute: number): string =>
    minute >= 1440 ? "midnight" : `${String(Math.floor(minute / 60)).padStart(2, "0")}:${String(minute % 60).padStart(2, "0")}`;

const calendarSentence = (version: InformationRequestClockPolicyVersionDto): string => {
    if (version.clockType !== InformationRequestClockType.BUSINESS) return "";
    const periods = version.workingPeriods.map(period =>
        `${dayLabels[period.dayOfWeek] ?? period.dayOfWeek} ${timeOfDay(period.startMinute)} to ${timeOfDay(period.endMinute)}`);
    return [
        periods.length > 0 ? `Counts ${periods.join(", ")}.` : "",
        version.holidays.length > 0 ? `Skips ${version.holidays.join(", ")}.` : "",
    ].filter(Boolean).join(" ");
};

const noticeSentence = (version: InformationRequestClockPolicyVersionDto, names: Record<string, string>): string => {
    const wording = (communicationId?: string | null): string =>
        communicationId ? names[communicationId] ?? "a chosen Communication" : "the platform wording";
    if (!version.reminderCommunicationId && !version.overdueCommunicationId) return "Reminder and overdue notices use the platform wording.";
    return `Reminder notice ${wording(version.reminderCommunicationId)}, overdue notice ${wording(version.overdueCommunicationId)}.`;
};

export const clockPolicyVersionSentence = (version: InformationRequestClockPolicyVersionDto, names: Record<string, string>): string =>
    [
        `Version ${version.versionNumber}, published ${formattedTime(version.publishedAt)}:`,
        clockPolicySentence(version),
        calendarSentence(version),
        noticeSentence(version, names),
    ].filter(Boolean).join(" ");
