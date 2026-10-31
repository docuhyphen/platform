import {InformationRequestLineageViewDto, InformationRequestState} from "../../../models/models.tsx";

const SCHEDULING_ENDED = new Set<InformationRequestState>([
    InformationRequestState.CANCELLED,
    InformationRequestState.SUPERSEDED,
    InformationRequestState.EXPIRED,
]);

const FOLLOW_UPS_ENDED = new Set<InformationRequestState>([
    InformationRequestState.DRAFT,
    InformationRequestState.CANCELLED,
    InformationRequestState.SUPERSEDED,
]);

export const schedulesFollowUps = (state: InformationRequestState): boolean => !SCHEDULING_ENDED.has(state);

export const startsFollowUps = (state: InformationRequestState): boolean => !FOLLOW_UPS_ENDED.has(state);

export const nextOccurrenceReady = (lineage: InformationRequestLineageViewDto | null, now: number): boolean => {
    if (!(lineage?.recurrence ?? lineage?.series) || lineage?.everyOccurrenceCreated) return false;
    return lineage.nextOccurrenceDueAt ? lineage.nextOccurrenceDueAt <= now : true;
};
