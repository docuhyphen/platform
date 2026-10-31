import {BadgeProps} from "@fluentui/react-components";
import {countedText, formattedTime} from "../information-requests/operations/operationsLabels.ts";
import {formatInformationRequestCount} from "../information-requests/shared/informationRequestFormatting.ts";
import {
    RecordDisposalDto,
    RecordDisposalState,
    RecordPreservationHoldEventDto,
    RecordPreservationHoldStatus,
    RecordPreservationScope,
} from "../models/models.tsx";

type BadgeColor = NonNullable<BadgeProps["color"]>;

export const holdScopeLabels: Record<RecordPreservationScope, string> = {
    [RecordPreservationScope.RESOURCE]: "This record only",
    [RecordPreservationScope.DESCENDANTS_AND_REFERENCES]: "This record, its descendants, and the records it refers to",
};

export const holdStatusPresentation: Record<RecordPreservationHoldStatus, {label: string; color: BadgeColor}> = {
    [RecordPreservationHoldStatus.ACTIVE]: {label: "Active", color: "warning"},
    [RecordPreservationHoldStatus.RELEASED]: {label: "Released", color: "subtle"},
};

export const disposalStatePresentation: Record<RecordDisposalState, {label: string; color: BadgeColor}> = {
    [RecordDisposalState.CLAIMED]: {label: "Claimed for disposal", color: "informative"},
    [RecordDisposalState.OBJECTS_DELETED]: {label: "Stored files deleted", color: "warning"},
    [RecordDisposalState.FINALIZED]: {label: "Disposed", color: "success"},
};

export const disposalBasisLabels: Record<RecordDisposalDto["basis"], string> = {
    RETENTION_SCHEDULE: "retention schedule",
    PRIVACY_DELETION: "privacy deletion",
};

const principalLabels: Record<string, string> = {
    USER: "a person",
    APPLICATION: "an application",
    SERVICE_ACCOUNT: "a service account",
};

export const recordTypeLabel = (resourceType: string): string => resourceType.toLowerCase().replace(/_/g, " ");

const lowerFirst = (text: string): string => text.charAt(0).toLowerCase() + text.slice(1);

export const disposalSummary = (disposal: RecordDisposalDto): string =>
{
    const summary = `${disposalStatePresentation[disposal.state].label} by ${disposalBasisLabels[disposal.basis]}, `
        + countedText(disposal.attemptCount, "attempt", "attempts");
    return disposal.lastErrorCode ? `${summary}, last error ${recordTypeLabel(disposal.lastErrorCode)}` : summary;
};

export const disposalObjectsSummary = (disposal: RecordDisposalDto): string => {
    const tombstone = disposal.tombstone;
    const kept = tombstone ? tombstone.retainedObjectCount : disposal.objects.filter(entry => entry.retained).length;
    const removed = tombstone ? tombstone.deletedObjectCount : disposal.objects.length - kept;
    return `${countedText(removed, "stored file", "stored files")} ${tombstone ? "deleted" : "to delete"}, `
        + `${formatInformationRequestCount(kept)} kept because other records share ${kept === 1 ? "it" : "them"}`;
};

export const holdEventSentence = (event: RecordPreservationHoldEventDto, previous?: RecordPreservationHoldEventDto): string => {
    const actor = `${principalLabels[event.principalKind] ?? recordTypeLabel(event.principalKind)} (${event.principalId})`;
    const when = `${formattedTime(event.occurredAt)} by ${actor}`;
    const covers = lowerFirst(holdScopeLabels[event.scope]);
    if (event.eventKind === "PLACED") return `Placed ${when}, covering ${covers}: ${event.reason}`;
    if (event.eventKind === "RELEASED") return `Released ${when}: ${event.reason}`;
    const from = previous ? ` from ${lowerFirst(holdScopeLabels[previous.scope])}` : "";
    return `Scope changed ${when}${from} to ${covers}: ${event.reason}`;
};
