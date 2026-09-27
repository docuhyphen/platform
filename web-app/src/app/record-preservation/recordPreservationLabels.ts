import {BadgeProps} from "@fluentui/react-components";
import {
    RecordDisposalDto,
    RecordDisposalState,
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

export const recordTypeLabel = (resourceType: string): string => resourceType.toLowerCase().replace(/_/g, " ");

export const disposalSummary = (disposal: RecordDisposalDto): string =>
{
    const summary = `${disposalStatePresentation[disposal.state].label} by ${disposalBasisLabels[disposal.basis]}, ${disposal.attemptCount} attempts`;
    return disposal.lastErrorCode ? `${summary}, last error ${recordTypeLabel(disposal.lastErrorCode)}` : summary;
};
