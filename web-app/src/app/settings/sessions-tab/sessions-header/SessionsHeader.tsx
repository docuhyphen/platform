import {Button, Text} from "@fluentui/react-components";
import {SignOutButtonIcon} from "../../../components/IconBundles.tsx";
import {useSessionsHeaderStyles} from "./SessionsHeaderStyles.tsx";

interface SessionsHeaderProps
{
    activeSessionsCount: number;
    endedSessionsCount: number;
    onSignOutAllDevices: () => void;
}

const SessionsHeader = ({activeSessionsCount, endedSessionsCount, onSignOutAllDevices}: SessionsHeaderProps) =>
{
    const styles = useSessionsHeaderStyles();

    return (
        <div
            id={"device-sessions-header"}
            className={styles.header}>
            <div
                id={"device-sessions-summary"}
                className={styles.summaryBlock}>
                <Text
                    id={"device-sessions-title"}
                    size={600}
                    weight={"semibold"}>
                    Device sessions
                </Text>
                <Text
                    id={"device-sessions-count"}
                    size={200}
                    className={styles.subtleText}>
                    {activeSessionsCount} active {activeSessionsCount === 1 ? "session" : "sessions"}
                    {endedSessionsCount > 0 ? `, ${endedSessionsCount} ended` : ""}
                </Text>
            </div>

            <Button
                id={"button-sign-out-all-devices"}
                appearance={"secondary"}
                shape="circular"
                icon={<SignOutButtonIcon/>}
                onClick={onSignOutAllDevices}
                size={"medium"}>
                Sign out of all devices
            </Button>
        </div>
    );
};

export default SessionsHeader;
