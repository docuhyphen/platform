export type PartyHolder = "email" | "user" | "group" | "trusted";

export interface PartyHolderSelection {
    holder: PartyHolder;
    email: string;
    userId: string;
    groupId: string;
    trustedRecipientId: string;
}

export interface PartyCandidate {
    id: string;
    name: string;
}

export interface TrustedPartyCandidate {
    exchangeRecipientId: string;
    principalKind: "USER" | "PRINCIPAL_GROUP";
    principalId: string;
    name: string;
    trustSuspended: boolean;
}

export const OTHER_WORKSPACE_HOLDERS: PartyHolder[] = ["email", "trusted"];

export const OTHER_WORKSPACE_NOTICE = "This request belongs to another workspace. Switch to it to choose its people, groups, " +
    "or subjects; until then you can name a person by email or a trusted recipient.";

export const offeredHolders = (holders: PartyHolder[], otherWorkspace: boolean): PartyHolder[] =>
    otherWorkspace ? holders.filter(holder => OTHER_WORKSPACE_HOLDERS.includes(holder)) : holders;

export const partyHolderLabels: Record<PartyHolder, string> = {
    email: "A person by email",
    user: "A person with an account",
    group: "A group",
    trusted: "A trusted recipient",
};

export const trustedHolderAvailable = (trusted: TrustedPartyCandidate[]): boolean => trusted.length > 0;

export const holderRequestFrom = (
    selection: PartyHolderSelection,
    trusted: TrustedPartyCandidate[],
): { userId?: string; principalGroupId?: string; exchangeRecipientId?: string } => {
    if (selection.holder === "trusted") {
        const chosen = trusted.find(candidate => candidate.exchangeRecipientId === selection.trustedRecipientId);
        if (!chosen) return {};
        return chosen.principalKind === "PRINCIPAL_GROUP"
            ? {principalGroupId: chosen.principalId, exchangeRecipientId: chosen.exchangeRecipientId}
            : {userId: chosen.principalId, exchangeRecipientId: chosen.exchangeRecipientId};
    }
    return selection.holder === "group" ? {principalGroupId: selection.groupId} : {userId: selection.userId};
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const partyHolderProblem = (selection: PartyHolderSelection): string | null => {
    if (selection.holder === "trusted") return selection.trustedRecipientId ? null : "Choose a trusted recipient.";
    if (selection.holder === "group") return selection.groupId ? null : "Choose a group.";
    if (selection.holder === "user") return selection.userId ? null : "Choose a person.";
    return EMAIL_PATTERN.test(selection.email.trim()) ? null : "Enter an email address.";
};
