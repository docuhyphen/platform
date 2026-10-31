import {getInformationRequestSubjects} from "../../../../services/informationRequestAuthoringService.ts";
import {fetchPersonalGroups} from "../../../../services/meGroupsApi.ts";
import {
    fetchOrganizationGroups,
    fetchOrganizationUsers,
    OrganizationGroupBasicDto,
} from "../../../../services/organizationApi.ts";
import {fetchRecentContacts, UserContactDto} from "../../../../services/personalContactsApi.ts";
import {AppUserPublicDto, InformationRequestOwnerType, InformationRequestSubjectDto} from "../../../models/models.tsx";
import {informationRequestRefusalMessage, unlessForbidden} from "../../shared/informationRequestRefusal.ts";
import {InformationRequestOwnerFields, useOwnerWorkspace, workspaceScopeOf} from "../../shared/ownerWorkspace.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import {PartyCandidate} from "../party-holder-fields/partyHolder.ts";

const CONTACT_LIMIT = 50;

const personName = (email: string, firstName?: string | null, lastName?: string | null): string =>
{
    const name = [firstName, lastName].map(part => part?.trim()).filter(Boolean).join(" ");
    return name ? `${name} (${email})` : email;
};

const memberCandidates = (members: AppUserPublicDto[]): PartyCandidate[] =>
    members
        .filter(member => member.isActive)
        .map(member => ({
            id: member.id,
            name: personName(member.email, member.person?.firstName, member.person?.lastName)
        }));

const contactCandidates = (contacts: UserContactDto[]): PartyCandidate[] =>
    contacts.flatMap(contact => contact.contactAppUserId
        ? [{id: contact.contactAppUserId, name: personName(contact.email, contact.firstName, contact.lastName)}]
        : []);

interface PartyCandidates {
    groups: PartyCandidate[];
    people: PartyCandidate[];
    subjects: InformationRequestSubjectDto[];
    subjectRegistryAvailable: boolean;
}

const NO_CANDIDATES: PartyCandidates = {groups: [], people: [], subjects: [], subjectRegistryAvailable: false};

const LOAD_FAILURE = "The people, groups and subjects who can be named could not be loaded.";

const loadCandidates = async (organizationId?: string | null): Promise<PartyCandidates> => {
    const [groups, people, subjects] = await Promise.all([
        organizationId
            ? fetchOrganizationGroups(organizationId).then((loaded: OrganizationGroupBasicDto[]) => loaded)
            : fetchPersonalGroups(),
        organizationId
            ? fetchOrganizationUsers(organizationId).then(memberCandidates)
            : fetchRecentContacts(CONTACT_LIMIT).then(contactCandidates),
        unlessForbidden(getInformationRequestSubjects()),
    ]);
    return {
        groups: groups.map(group => ({id: group.id, name: group.name})),
        people,
        subjects: subjects ?? [],
        subjectRegistryAvailable: subjects !== null,
    };
};

export const usePartyCandidates = (owner: InformationRequestOwnerFields | null) => {
    const workspace = useOwnerWorkspace(owner);
    const organizationId = owner?.ownerType === InformationRequestOwnerType.ORGANIZATION ? owner.ownerOrganizationId : null;
    const read = useServerRead(
        workspaceScopeOf(workspace),
        () => workspace.kind === "current" ? loadCandidates(organizationId) : Promise.resolve(NO_CANDIDATES),
        refusal => informationRequestRefusalMessage(refusal, LOAD_FAILURE),
    );
    const candidates = read.value ?? NO_CANDIDATES;

    return {
        ...candidates,
        otherWorkspace: workspace.kind === "other",
        loading: read.loading,
        error: read.error,
        reload: read.reload,
    };
};
