import {getInformationRequestPrivacyRequests} from "../../../../services/informationRequestAdministrationService.ts";
import {InformationRequestPartyDto, InformationRequestPrivacyRequestKind} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {partyLabel} from "../author-workspace/useAuthorWorkspace.ts";

export interface RecordedCorrection {
    id: string;
    submissionItemId: string;
    subjectLabel: string;
    reasonCode: string;
    recordedAt: number;
}

const LOAD_FAILURE = "The corrections already recorded could not be loaded.";

const correctionsOf = async (requestId: string, subject: InformationRequestPartyDto): Promise<RecordedCorrection[]> =>
    (await getInformationRequestPrivacyRequests(subject.subjectIdentityRefId ?? undefined)).flatMap(privacy =>
        privacy.requestKind === InformationRequestPrivacyRequestKind.CORRECTION && privacy.correction?.requestId === requestId
            ? [{
                id: privacy.correction.id,
                submissionItemId: privacy.correction.submissionItemId,
                subjectLabel: partyLabel(subject),
                reasonCode: privacy.correction.reasonCode,
                recordedAt: privacy.correction.recordedAt,
            }]
            : []);

export interface RecordedCorrections {
    corrections: RecordedCorrection[];
    error: string | null;
    reload: () => Promise<void>;
}

export const useRecordedCorrections = (requestId: string, subjects: InformationRequestPartyDto[]): RecordedCorrections => {
    const read = useServerRead(
        `${requestId}|${subjects.map(subject => subject.subjectIdentityRefId).join("|")}`,
        async () => (await Promise.all(subjects.map(subject => correctionsOf(requestId, subject)))).flat(),
        refusal => informationRequestRefusalMessage(refusal, LOAD_FAILURE),
    );

    return {corrections: read.value ?? [], error: read.error, reload: read.reload};
};
