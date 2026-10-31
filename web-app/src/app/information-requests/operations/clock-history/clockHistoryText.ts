import {
    InformationRequestClockDto,
    InformationRequestClockEventDto,
    InformationRequestClockPolicyDto,
    InformationRequestClockPolicyVersionDto,
} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {clockPolicySentence} from "../clock-policies/clockPolicyLabels.ts";
import {clockEventLabels, formattedTime} from "../operationsLabels.ts";

export interface ClockPolicyVersionEntry {
    policy: InformationRequestClockPolicyDto;
    version: InformationRequestClockPolicyVersionDto;
}

const COUNTING_RESETS: Record<string, string> = {
    REQUEST_ISSUED: "Request issued",
    EXCHANGE_ACCEPTED: "Exchange accepted",
};

const urgencyLabels: Record<InformationRequestClockDto["urgency"], string> = {
    STANDARD: "Standard",
    URGENT: "Urgent",
};

const clockTypeNames: Record<InformationRequestClockDto["clockType"], string> = {
    CALENDAR: "calendar time",
    BUSINESS: "business hours",
};

const known = (value?: number | null): value is number => value !== undefined && value !== null;

export const clockInputsText = (clock: InformationRequestClockDto): string =>
    `${urgencyLabels[clock.urgency]} duration in ${clockTypeNames[clock.clockType]}, received ${formattedTime(clock.receivedAt)}`;

export const clockPolicyText = (clock: InformationRequestClockDto, versions: Map<string, ClockPolicyVersionEntry>): string => {
    const entry = versions.get(clock.policyVersionId);
    return entry
        ? `${entry.policy.displayName} version ${entry.version.versionNumber}: ${clockPolicySentence(entry.version)}`
        : `Policy version ${clock.policyVersionNumber}`;
};

export const clockEventSentence = (event: InformationRequestClockEventDto): string => {
    const reset = event.reasonCode ? COUNTING_RESETS[event.reasonCode] : undefined;
    const parts = [`${reset ? "Counting reset" : clockEventLabels[event.eventKind]} ${formattedTime(event.occurredAt)}`];
    if (event.reasonCode) parts.push(reset ?? humanizedKey(event.reasonCode));
    if (known(event.pointOrdinal)) parts.push(`reminder point ${event.pointOrdinal}`);
    if (known(event.dueAt)) parts.push(`due ${formattedTime(event.dueAt)}`);
    return parts.join(", ");
};
