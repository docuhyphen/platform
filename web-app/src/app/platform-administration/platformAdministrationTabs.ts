export const platformAdministrationTabIds = {
    organizations: "organizations",
    userSubscriptions: "user-subscriptions",
    platformContent: "platform-content",
    appAdministrators: "app-administrators",
} as const;

export const platformAdministrationTabLabels: Record<string, string> = {
    [platformAdministrationTabIds.organizations]: "Organizations",
    [platformAdministrationTabIds.userSubscriptions]: "User Subscriptions",
    [platformAdministrationTabIds.platformContent]: "Platform Content",
    [platformAdministrationTabIds.appAdministrators]: "App Administrators",
};
