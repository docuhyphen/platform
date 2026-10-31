import {useCallback, useEffect, useState} from "react";
import {getAssignedInformationRequests} from "../../../services/informationRequestAuthoringService.ts";
import {InformationRequestAssignedWorkDto} from "../../models/models.tsx";
import {submissionErrorMessage} from "../submission/submissionLabels.ts";

export interface AssignedInformationRequests {
    entries: InformationRequestAssignedWorkDto[] | null;
    error: string | null;
    reload: () => void;
}

export const useAssignedInformationRequests = (): AssignedInformationRequests => {
    const [entries, setEntries] = useState<InformationRequestAssignedWorkDto[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    const reload = useCallback(() => {
        setEntries(null);
        setError(null);
        getAssignedInformationRequests()
            .then(setEntries)
            .catch((caught: unknown) => setError(submissionErrorMessage(caught, "Your requests could not be loaded.")));
    }, []);

    useEffect(reload, [reload]);

    return {entries, error, reload};
};
