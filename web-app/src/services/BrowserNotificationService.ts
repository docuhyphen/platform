import {NotificationDto} from "../app/models/models.tsx";

export type BrowserNotificationPermission = NotificationPermission | "unsupported";

export const getBrowserNotificationPermission = (): BrowserNotificationPermission =>
{
    if (!("Notification" in window))
    {
        return "unsupported";
    }

    return Notification.permission;
};

export const requestBrowserNotificationPermission = async (): Promise<BrowserNotificationPermission> =>
{
    if (!("Notification" in window))
    {
        return "unsupported";
    }

    return Notification.requestPermission();
};

export const showBrowserNotification = (notification: NotificationDto): void =>
{
    if (!("Notification" in window) || Notification.permission !== "granted")
    {
        return;
    }

    const browserNotification = new Notification("DocuHyphen", {
        body: notification.message,
        tag: notification.id,
    });

    browserNotification.onclick = () =>
    {
        window.focus();
        browserNotification.close();
    };
};
