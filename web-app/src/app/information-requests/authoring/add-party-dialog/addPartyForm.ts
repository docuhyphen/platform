import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    InformationRequestShareRoleKey,
    InformationRequestSubjectKind,
} from "../../../models/models.tsx";

export type PartyHolder = "email" | "group";

export interface AddPartyForm
{
    roleKey: InformationRequestShareRoleKey;
    holder: PartyHolder;
    email: string;
    groupId: string;
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
    groupId: "",
    subjectId: "",
    subjectKind: InformationRequestSubjectKind.PERSON,
    authority: "",
    identifierType: "",
    identifierValue: "",
});

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const referenceParts = (form: AddPartyForm): string[] =>
    [form.authority, form.identifierType, form.identifierValue].map(part => part.trim());

export const isSubjectRole = (form: AddPartyForm): boolean => form.roleKey === InformationRequestShareRoleKey.SUBJECT;

export const addPartyProblem = (form: AddPartyForm): string | null =>
{
    if (isSubjectRole(form))
    {
        if (form.subjectId) return null;
        const filled = referenceParts(form).filter(Boolean).length;
        return filled === 0 || filled === 3 ? null : "Give the reference's authority, type, and value, or none of them.";
    }
    if (form.holder === "group") return form.groupId ? null : "Choose a group.";
    return EMAIL_PATTERN.test(form.email.trim()) ? null : "Enter an email address.";
};

export const partyRequestFrom = (form: AddPartyForm): AssignInformationRequestPartyRequest =>
{
    if (isSubjectRole(form)) return {roleKey: form.roleKey, subjectIdentityRefId: form.subjectId};
    return form.holder === "group"
        ? {roleKey: form.roleKey, principalGroupId: form.groupId}
        : {roleKey: form.roleKey, email: form.email.trim()};
};

export const subjectRequestFrom = (form: AddPartyForm): AssignInformationRequestSubjectRequest =>
{
    const [authority, identifierType, identifierValue] = referenceParts(form);
    return authority
        ? {subjectKind: form.subjectKind, reference: {authority, identifierType, identifierValue}}
        : {subjectKind: form.subjectKind};
};
