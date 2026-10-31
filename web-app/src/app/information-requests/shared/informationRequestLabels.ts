import {
    InformationRequestNextAction,
    InformationRequestResponseDisposition,
    InformationRequestResponseEditLock,
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
    [InformationRequestShareRoleKey.RECORD_OWNER]: "Record owner",
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

export const responseEditLockLabels: Record<InformationRequestResponseEditLock, string> = {
    [InformationRequestResponseEditLock.SUBMITTED]: "Submitted. Withdraw the submission to change this answer.",
    [InformationRequestResponseEditLock.NOT_RETURNED_FOR_CORRECTION]:
        "Not returned for correction, so this answer stays as it was submitted.",
    [InformationRequestResponseEditLock.GIVEN_ONCE]: "Given once, so this answer cannot change after it was submitted.",
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
