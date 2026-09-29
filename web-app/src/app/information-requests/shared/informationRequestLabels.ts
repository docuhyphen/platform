import {
    InformationRequestNextAction,
    InformationRequestResponseDisposition,
    InformationRequestShareRoleKey,
    InformationRequestSubjectKind,
} from "../../models/models.tsx";

export const shareRoleLabels: Record<InformationRequestShareRoleKey, string> = {
    [InformationRequestShareRoleKey.SUBJECT]: "Subject",
    [InformationRequestShareRoleKey.CONTRIBUTOR]: "Contributor",
    [InformationRequestShareRoleKey.PREPARER]: "Preparer",
    [InformationRequestShareRoleKey.ATTESTOR]: "Attestor",
    [InformationRequestShareRoleKey.REVIEWER]: "Reviewer",
    [InformationRequestShareRoleKey.DECISION_MAKER]: "Decision Maker",
};

export const nextActionLabels: Record<InformationRequestNextAction, string> = {
    [InformationRequestNextAction.COMPLETE_SETUP]: "Finish setup",
    [InformationRequestNextAction.RESPOND]: "Respond",
    [InformationRequestNextAction.REVIEW]: "Review",
    [InformationRequestNextAction.MANAGE]: "Manage",
    [InformationRequestNextAction.VIEW]: "View",
};

export const dispositionLabels: Record<InformationRequestResponseDisposition, string> = {
    [InformationRequestResponseDisposition.NOT_ANSWERED]: "Not answered",
    [InformationRequestResponseDisposition.PROVIDED]: "Provided",
    [InformationRequestResponseDisposition.PARTIALLY_PROVIDED]: "Partly provided",
    [InformationRequestResponseDisposition.NOT_APPLICABLE]: "Not applicable",
    [InformationRequestResponseDisposition.UNAVAILABLE]: "Unavailable",
    [InformationRequestResponseDisposition.EXCEPTION_REQUESTED]: "Exception requested",
    [InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE]: "Provided elsewhere",
    [InformationRequestResponseDisposition.WAIVED]: "Waived",
};

export const subjectKindLabels: Record<InformationRequestSubjectKind, string> = {
    [InformationRequestSubjectKind.PERSON]: "Person",
    [InformationRequestSubjectKind.ORGANIZATION]: "Organization",
    [InformationRequestSubjectKind.ASSET]: "Asset",
    [InformationRequestSubjectKind.RECORD]: "Record",
    [InformationRequestSubjectKind.OTHER]: "Other",
};

export const rolesSentence = (roles: InformationRequestShareRoleKey[]): string =>
    roles.map(role => shareRoleLabels[role]).join(", ");
