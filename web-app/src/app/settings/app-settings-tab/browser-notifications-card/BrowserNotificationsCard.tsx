import {Badge, Button, Text} from "@fluentui/react-components";
import {useState} from "react";
import {
    BrowserNotificationPermission,
    getBrowserNotificationPermission,
    requestBrowserNotificationPermission
} from "../../../../services/BrowserNotificationService.ts";
import {useBrowserNotificationsCardStyles} from "./BrowserNotificationsCardStyles.tsx";

const permissionDescriptions: Record<BrowserNotificationPermission, string> = {
    default: "Allow this browser to show alerts when new Push notifications arrive.",
    granted: "Browser alerts are enabled on this device.",
    denied: "Browser alerts are blocked. Enable notifications in your browser site settings.",
    unsupported: "Browser alerts are not supported by this browser.",
};

const BrowserNotificationsCard = () =>
{
    const styles = useBrowserNotificationsCardStyles();
    const [permission, setPermission] = useState<BrowserNotificationPermission>(
        getBrowserNotificationPermission,
    );
    const [isRequesting, setIsRequesting] = useState(false);

    const requestPermission = async () =>
    {
        setIsRequesting(true);
        try
        {
            setPermission(await requestBrowserNotificationPermission());
        }
        finally
        {
            setIsRequesting(false);
        }
    };

    return (
        <section id="browser-notifications-card"
                 className={styles.container}>
            <div id="browser-notifications-content"
                 className={styles.content}>
                <Text id="browser-notifications-title"
                      weight="semibold">
                    Browser notifications
                </Text>
                <Text id="browser-notifications-description"
                      className={styles.description}
                      size={200}>
                    {permissionDescriptions[permission]}
                </Text>
            </div>
            {permission === "default" && (
                <Button id="enable-browser-notifications"
                        className={styles.action}
                        appearance="secondary"
                        shape="circular"
                        disabled={isRequesting}
                        onClick={requestPermission}>
                    {isRequesting ? "Requesting" : "Enable"}
                </Button>
            )}
            {permission === "granted" && (
                <Badge id="browser-notifications-enabled"
                       className={styles.action}
                       appearance="filled"
                       color="success">
                    Enabled
                </Badge>
            )}
        </section>
    );
};

export default BrowserNotificationsCard;
