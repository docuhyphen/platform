export const platformAdministrationTabIds = {
    organizations: "organizations",
    userSubscriptions: "user-subscriptions",
    trialRequests: "trial-requests",
    platformContent: "platform-content",
    appAdministrators: "app-administrators",
} as const;

export const platformAdministrationTabLabels: Record<string, string> = {
    [platformAdministrationTabIds.organizations]: "Organizations",
    [platformAdministrationTabIds.userSubscriptions]: "User Subscriptions",
    [platformAdministrationTabIds.trialRequests]: "Trial Requests",
    [platformAdministrationTabIds.platformContent]: "Platform Content",
    [platformAdministrationTabIds.appAdministrators]: "App Administrators",
};
