import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    InformationRequestShareRoleKey,
    InformationRequestSubjectKind,
} from "../../../models/models.tsx";
import {
    holderRequestFrom,
    PartyHolder,
    partyHolderProblem,
    PartyHolderSelection,
    TrustedPartyCandidate,
} from "../party-holder-fields/partyHolder.ts";

export interface AddPartyForm extends PartyHolderSelection
{
    roleKey: InformationRequestShareRoleKey;
    subjectId: string;
    subjectKind: InformationRequestSubjectKind;
    authority: string;
    identifierType: string;
    identifierValue: string;
}

export const emptyAddPartyForm = (): AddPartyForm => ({
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    holder: "email",
    email: "",
    userId: "",
    groupId: "",
    trustedRecipientId: "",
    subjectId: "",
    subjectKind: InformationRequestSubjectKind.PERSON,
    authority: "",
    identifierType: "",
    identifierValue: "",
});

const referenceParts = (form: AddPartyForm): string[] =>
    [form.authority, form.identifierType, form.identifierValue].map(part => part.trim());

export const isSubjectRole = (form: AddPartyForm): boolean => form.roleKey === InformationRequestShareRoleKey.SUBJECT;

const signsIn = (roleKey: InformationRequestShareRoleKey): boolean => roleKey === InformationRequestShareRoleKey.REVIEWER;

export const holdersForRole = (roleKey: InformationRequestShareRoleKey, holders: PartyHolder[]): PartyHolder[] =>
    signsIn(roleKey) ? holders.filter(holder => holder !== "email") : holders;

export const withRole = (form: AddPartyForm, roleKey: InformationRequestShareRoleKey): Partial<AddPartyForm> =>
    signsIn(roleKey) && form.holder === "email" ? {roleKey, holder: "user"} : {roleKey};

export const addPartyProblem = (form: AddPartyForm): string | null =>
{
    if (isSubjectRole(form))
    {
        if (form.subjectId) return null;
        const filled = referenceParts(form).filter(Boolean).length;
        return filled === 0 || filled === 3 ? null : "Give the reference's authority, type, and value, or none of them.";
    }
    return partyHolderProblem(form);
};

export const partyRequestFrom = (form: AddPartyForm, trusted: TrustedPartyCandidate[]): AssignInformationRequestPartyRequest =>
{
    if (isSubjectRole(form)) return {roleKey: form.roleKey, subjectIdentityRefId: form.subjectId};
    if (form.holder === "email") return {roleKey: form.roleKey, email: form.email.trim()};
    return {roleKey: form.roleKey, ...holderRequestFrom(form, trusted)};
};

export const subjectRequestFrom = (form: AddPartyForm): AssignInformationRequestSubjectRequest =>
{
    const [authority, identifierType, identifierValue] = referenceParts(form);
    return authority
        ? {subjectKind: form.subjectKind, reference: {authority, identifierType, identifierValue}}
        : {subjectKind: form.subjectKind};
};
