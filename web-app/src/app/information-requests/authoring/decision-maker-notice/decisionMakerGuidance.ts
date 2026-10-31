import {
    InformationRequestPartyDto,
    InformationRequestShareRoleKey,
    InformationRequestState
} from "../../../models/models.tsx";

export type DecisionMakerGuidanceKind =
    | { kind: "NAME_DECISION_MAKER" }
    | { kind: "ONLY_DECISION_MAKER_ISSUES"; decisionMaker: InformationRequestPartyDto };

export const decisionMakerGuidance = (
    state: InformationRequestState,
    parties: InformationRequestPartyDto[],
    canChangeParties: boolean,
    callerMayIssue: boolean,
    callerUserId: string | undefined,
): DecisionMakerGuidanceKind | null => {
    if (state !== InformationRequestState.DRAFT) return null;
    const decisionMaker = parties.find(party => party.active && party.roleKey === InformationRequestShareRoleKey.DECISION_MAKER);
    if (!decisionMaker) return canChangeParties ? {kind: "NAME_DECISION_MAKER"} : null;
    const callerHoldsRole = decisionMaker.principalKind === "USER" && decisionMaker.principalId === callerUserId;
    return callerMayIssue || callerHoldsRole ? null : {kind: "ONLY_DECISION_MAKER_ISSUES", decisionMaker};
};
