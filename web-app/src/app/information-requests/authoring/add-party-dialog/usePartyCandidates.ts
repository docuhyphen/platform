import {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {getInformationRequestSubjects} from "../../../../services/informationRequestAuthoringService.ts";
import {fetchPersonalGroups} from "../../../../services/meGroupsApi.ts";
import {fetchOrganizationGroups, OrganizationGroupBasicDto} from "../../../../services/organizationApi.ts";
import {InformationRequestSubjectDto} from "../../../models/models.tsx";

export interface PartyGroupCandidate
{
    id: string;
    name: string;
}

export const usePartyCandidates = () =>
{
    const {currentSession} = useAuth();
    const organizationId = currentSession?.activeOrganizationId;
    const [groups, setGroups] = useState<PartyGroupCandidate[]>([]);
    const [subjects, setSubjects] = useState<InformationRequestSubjectDto[]>([]);

    useEffect(() =>
    {
        let active = true;
        const loadGroups: Promise<PartyGroupCandidate[]> = organizationId
            ? fetchOrganizationGroups(organizationId).then((loaded: OrganizationGroupBasicDto[]) => loaded)
            : fetchPersonalGroups();
        loadGroups
            .then(loaded =>
            {
                if (active) setGroups(loaded.map(group => ({id: group.id, name: group.name})));
            })
            .catch(() => undefined);
        getInformationRequestSubjects()
            .then(loaded =>
            {
                if (active) setSubjects(loaded);
            })
            .catch(() => undefined);
        return () =>
        {
            active = false;
        };
    }, [organizationId]);

    return {groups, subjects};
};
