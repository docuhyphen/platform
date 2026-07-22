export type AccessManagementView = "list" | "add-person" | "replace-primary";

export const accessManagementTabIds = {
    people: "people",
    access: "access",
    settings: "settings",
} as const;
