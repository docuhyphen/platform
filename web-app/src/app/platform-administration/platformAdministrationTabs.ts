export const platformAdministrationTabIds = {
    organizations: "organizations",
    platformContent: "platform-content",
    appAdministrators: "app-administrators",
} as const;

export const platformAdministrationTabLabels: Record<string, string> = {
    [platformAdministrationTabIds.organizations]: "Organizations",
    [platformAdministrationTabIds.platformContent]: "Platform Content",
    [platformAdministrationTabIds.appAdministrators]: "App Administrators",
};
