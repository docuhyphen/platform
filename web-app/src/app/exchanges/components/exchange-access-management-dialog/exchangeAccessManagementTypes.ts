export type ExchangeParticipantView = {
    id: string;
    participantType?: string;
    addedDate?: string;
    appUserEmail?: string;
    appUserFirstName?: string;
    appUserLastName?: string;
    organizationGroupName?: string;
};

export type AccessManagementView = "list" | "add-person" | "replace-primary";

export const accessManagementTabIds = {
    people: "people",
    access: "access",
    settings: "settings",
} as const;
