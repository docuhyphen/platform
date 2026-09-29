import {useCallback, useEffect, useState} from "react";
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
import {recordedSentence} from "./privacyLabels.ts";

export const usePrivacy = () =>
{
    const [subjects, setSubjects] = useState<InformationRequestSubjectDto[] | null>(null);
    const [requests, setRequests] = useState<InformationRequestPrivacyRequestDto[]>([]);
    const [restrictions, setRestrictions] = useState<InformationRequestSubjectRestrictionDto[]>([]);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    const load = useCallback(async () =>
    {
        try
        {
            const [loadedSubjects, loadedRequests, loadedRestrictions] = await Promise.all([
                getInformationRequestSubjects(),
                getInformationRequestPrivacyRequests(),
                getInformationRequestSubjectRestrictions(),
            ]);
            setSubjects(loadedSubjects);
            setRequests(loadedRequests);
            setRestrictions(loadedRestrictions);
            setLoadError(null);
        }
        catch (caught: unknown)
        {
            setLoadError(informationRequestRefusalMessage(caught, "The privacy records could not be loaded."));
        }
    }, []);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const run = async (work: () => Promise<string>): Promise<string | null> =>
    {
        setBusy(true);
        setNotice(null);
        try
        {
            setNotice(await work());
            await load();
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
        subjects,
        requests,
        restrictions,
        loadError,
        notice,
        busy,
        record: (request: RecordInformationRequestPrivacyRequestRequest) =>
            run(async () => recordedSentence(await recordInformationRequestPrivacyRequest(request))),
        lift: (restriction: InformationRequestSubjectRestrictionDto, reasonCode: string) =>
            run(async () =>
            {
                await liftInformationRequestSubjectRestriction(restriction.id, reasonCode);
                return "The restriction was lifted.";
            }),
    };
};

export type PrivacyState = ReturnType<typeof usePrivacy>;
