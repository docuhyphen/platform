import React, {useState} from "react";
import {MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {useSessionsTabStyles} from "./SessionsTabStyles.tsx";
import {useUserSessions} from "./useUserSessions.ts";
import AllDeviceSignOutDialog from "../profile-tab/all-device-sign-out-dialog/AllDeviceSignOutDialog.tsx";
import SessionsHeader from "./sessions-header/SessionsHeader.tsx";
import SessionsTable from "./sessions-table/SessionsTable.tsx";

const SessionsTab: React.FC = () =>
{
    const styles = useSessionsTabStyles();
    const {sessions, loading, error, revoking, deleting, handleRevoke, handleDelete} = useUserSessions();
    const [isAllDeviceSignOutDialogOpen, setIsAllDeviceSignOutDialogOpen] = useState(false);

    const activeSessionsCount = sessions.filter(session => session.isActive).length;
    const endedSessionsCount = sessions.length - activeSessionsCount;

    return (
        <div
            className={styles.container}
            id={"device-sessions-container"}>
            <SessionsHeader activeSessionsCount={activeSessionsCount}
                            endedSessionsCount={endedSessionsCount}
                            onSignOutAllDevices={() => setIsAllDeviceSignOutDialogOpen(true)}/>

            <Text
                id={"device-sessions-intro"}
                size={200}
                className={styles.subtleText}>
                Active sessions stay at the top. Ended sessions remain visible as revoked or expired so you can review them and delete old records if you want.
            </Text>

            {error && (
                <MessageBar id={"device-sessions-error"}
                            intent="error">
                    <MessageBarBody id={"sessions-tab-message-bar-body-1"}>{error}</MessageBarBody>
                </MessageBar>
            )}

            {loading && <Spinner id={"device-sessions-loading"}
                                 size="small"
                                 label="Loading sessions..."/>}

            {!loading && (
                <SessionsTable
                    sessions={sessions}
                    revoking={revoking}
                    deleting={deleting}
                    onRevoke={handleRevoke}
                    onDelete={handleDelete}
                />
            )}

            <AllDeviceSignOutDialog
                isOpen={isAllDeviceSignOutDialogOpen}
                onDismiss={() => setIsAllDeviceSignOutDialogOpen(false)}
            />
        </div>
    );
};

export default SessionsTab;
