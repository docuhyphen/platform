import {Button, Spinner} from "@fluentui/react-components";
import {DeleteRegular} from "@fluentui/react-icons";
import {UserSessionDto} from "../../../models/models.tsx";

interface SessionActionButtonProps
{
    session: UserSessionDto;
    isBusy: boolean;
    onRevoke: () => void;
    onDelete: () => void;
}

const getActionLabel = (session: UserSessionDto, isBusy: boolean) =>
{
    if (isBusy)
    {
        return <Spinner id={"session-table-row-spinner-1"}
                        size="tiny"/>;
    }

    if (session.isActive)
    {
        return session.isCurrent ? "Sign out" : "Revoke";
    }

    return "Delete";
};

const SessionActionButton = ({session, isBusy, onRevoke, onDelete}: SessionActionButtonProps) => (
    <div id={`device-session-${session.sessionId}-action`}>
        <Button
            id={session.isActive
                ? `button-revoke-session-${session.sessionId}`
                : `button-delete-session-record-${session.sessionId}`}
            icon={<DeleteRegular/>}
            shape={"circular"}
            appearance="secondary"
            size="small"
            disabled={isBusy}
            onClick={session.isActive ? onRevoke : onDelete}>
            {getActionLabel(session, isBusy)}
        </Button>
    </div>
);

export default SessionActionButton;
