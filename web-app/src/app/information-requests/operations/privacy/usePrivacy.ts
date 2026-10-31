import {useState} from "react";
import {
    getInformationRequestPrivacyRequests,
    getInformationRequestSubjectRestrictions,
    liftInformationRequestSubjectRestriction,
    recordInformationRequestPrivacyRequest,
} from "../../../../services/informationRequestAdministrationService.ts";
import {getInformationRequestSubjects} from "../../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestPrivacyRequestDto,
    InformationRequestSubjectDto,
    InformationRequestSubjectRestrictionDto,
    RecordInformationRequestPrivacyRequestRequest,
} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {useStableIdempotencyKey} from "../../shared/useStableIdempotencyKey.ts";
import {recordedSentence} from "./privacyLabels.ts";

export interface PrivacyRecords {
    subjects: InformationRequestSubjectDto[];
    requests: InformationRequestPrivacyRequestDto[];
    restrictions: InformationRequestSubjectRestrictionDto[];
}

const PRIVACY_RECORDS = "privacy-records";

const readPrivacyRecords = async (): Promise<PrivacyRecords> => {
    const [subjects, requests, restrictions] = await Promise.all([
        getInformationRequestSubjects(),
        getInformationRequestPrivacyRequests(),
        getInformationRequestSubjectRestrictions(),
    ]);
    return {subjects, requests, restrictions};
};

const loadRefusal = (refusal: unknown) =>
    informationRequestRefusalMessage(refusal, "The privacy records could not be loaded.");

export const usePrivacy = () =>
{
    const records = useServerRead(PRIVACY_RECORDS, readPrivacyRecords, loadRefusal);
    const [notice, setNotice] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const keyed = useStableIdempotencyKey();

    const run = async (work: () => Promise<string>): Promise<string | null> =>
    {
        setBusy(true);
        setNotice(null);
        try
        {
            setNotice(await work());
            await records.reload();
            return null;
        }
        catch (caught: unknown)
        {
            return informationRequestRefusalMessage(caught, "The privacy request could not be recorded.");
        }
        finally
        {
            setBusy(false);
        }
    };

    return {
        records: records.value,
        loadError: records.error,
        reload: records.reload,
        notice,
        busy,
        record: (request: RecordInformationRequestPrivacyRequestRequest) =>
            run(async () => recordedSentence(await keyed("privacy-request", request, key =>
                recordInformationRequestPrivacyRequest(request, key)))),
        lift: (restriction: InformationRequestSubjectRestrictionDto, reasonCode: string) =>
            run(async () =>
            {
                await keyed(`lift:${restriction.id}`, reasonCode, key =>
                    liftInformationRequestSubjectRestriction(restriction.id, reasonCode, key));
                return "The restriction was lifted.";
            }),
    };
};

export type PrivacyState = ReturnType<typeof usePrivacy>;
