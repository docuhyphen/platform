import {
    getInformationRequestTrustedRecipientCandidates
} from "../../../../services/informationRequestAuthoringService.ts";
import {InformationRequestTrustedRecipientCandidateDto} from "../../../models/models.tsx";
import {informationRequestRefusalMessage, unlessForbidden} from "../../shared/informationRequestRefusal.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {TrustedPartyCandidate} from "../party-holder-fields/partyHolder.ts";

const LOAD_FAILURE = "The trusted recipients who can be named could not be loaded.";

const NO_CANDIDATES: TrustedPartyCandidate[] = [];

const candidateOf = (candidate: InformationRequestTrustedRecipientCandidateDto): TrustedPartyCandidate[] =>
    candidate.principalKind === "PARTICIPANT"
        ? []
        : [{
            exchangeRecipientId: candidate.exchangeRecipientId,
            principalKind: candidate.principalKind,
            principalId: candidate.principalId,
            name: candidate.targetOrganizationName ? `${candidate.label} (${candidate.targetOrganizationName})` : candidate.label,
            trustSuspended: candidate.trustSuspended,
        }];

const loadCandidates = async (requestId: string): Promise<TrustedPartyCandidate[]> => {
    if (!requestId) return NO_CANDIDATES;
    const loaded = await unlessForbidden(getInformationRequestTrustedRecipientCandidates(requestId));
    return loaded?.flatMap(candidateOf) ?? NO_CANDIDATES;
};

export const useTrustedRecipientCandidates = (requestId: string) => {
    const read = useServerRead(
        requestId,
        () => loadCandidates(requestId),
        refusal => informationRequestRefusalMessage(refusal, LOAD_FAILURE),
    );

    return {candidates: read.value ?? NO_CANDIDATES, error: read.error, reload: read.reload};
};
